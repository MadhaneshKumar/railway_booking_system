package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.BookingDocument.PassengerInfo;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.ClassInventory;
import com.railway.repository.Repos.*;
import com.stripe.model.PaymentIntent;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {
    public record BookResult(BookingDocument booking, String clientSecret) {}

    private static final SecureRandom RND = new SecureRandom();
    private final RateLimiterService limiter;
    private final TrainRepo trains;
    private final BookingRepo bookings;
    private final SeatInventoryService seats;
    private final PaymentService payments;
    private final RefundService refunds;
    private final WaitlistPromotionService waitlist;
    private final TicketFulfillmentService fulfillment;
    private final SeatAvailabilityCacheService cache;
    private final NotificationService notifier;
    private final BlobStorageService blobs;
    private final MongoTemplate mongo;

    public BookingService(RateLimiterService limiter, TrainRepo trains, BookingRepo bookings, SeatInventoryService seats,
                          PaymentService payments, RefundService refunds, WaitlistPromotionService waitlist,
                          TicketFulfillmentService fulfillment, SeatAvailabilityCacheService cache,
                          NotificationService notifier, BlobStorageService blobs, MongoTemplate mongo) {
        this.limiter = limiter; this.trains = trains; this.bookings = bookings; this.seats = seats; this.payments = payments;
        this.refunds = refunds; this.waitlist = waitlist; this.fulfillment = fulfillment; this.cache = cache;
        this.notifier = notifier; this.blobs = blobs; this.mongo = mongo;
    }

    private static String newPnr() { return String.valueOf(1_000_000_000L + (long) (RND.nextDouble() * 8_999_999_999L)); }

    public BookingDocument require(String pnr) {
        return bookings.findByPnr(pnr).orElseThrow(() -> new BookingException("PNR_NOT_FOUND", "No booking for PNR " + pnr));
    }

    /** Booking steps 1-4: rate limit -> atomic reserve -> waitlist or HELD + PaymentIntent. */
    public BookResult book(String trainNumber, String travelDate, String cls, String quota,
                           List<PassengerInfo> passengers, String userId) {
        // Step 1: rate limiting
        if (!limiter.allow(userId)) throw new BookingException("RATE_LIMITED", "Maximum 10 requests per minute exceeded");
        if (passengers == null || passengers.isEmpty() || passengers.size() > 6)
            throw new BookingException("INVALID_PASSENGERS", "1 to 6 passengers are required");
        try { if (LocalDate.parse(travelDate).isBefore(LocalDate.now())) throw new BookingException("INVALID_DATE", "Travel date is in the past"); }
        catch (java.time.format.DateTimeParseException e) { throw new BookingException("INVALID_DATE", "Use yyyy-MM-dd"); }

        TrainDocument train = trains.findByTrainNumber(trainNumber)
                .orElseThrow(() -> new BookingException("TRAIN_NOT_FOUND", "Unknown train " + trainNumber));
        ClassInventory inv = train.classInventory.get(cls);
        if (inv == null) throw new BookingException("CLASS_NOT_FOUND", "Unknown class " + cls);

        int n = passengers.size();
        BookingDocument b = new BookingDocument();
        b.pnr = newPnr(); b.userId = userId; b.trainNumber = trainNumber; b.travelDate = travelDate;
        b.travelClass = cls; b.quotaType = quota; b.passengers = passengers; b.amount = inv.fare * n;

        // Step 2: ATOMIC seat reservation (findAndModify, no app-level locks)
        TrainDocument after = seats.reserve(trainNumber, cls, n);

        // Step 3: not enough seats -> waitlist
        if (after == null) {
            b.bookingStatus = "WAITLISTED"; b.paymentStatus = "NONE";
            bookings.save(b);
            cache.evict(trainNumber, cls);
            return new BookResult(b, null);
        }

        // Step 4: HELD + Stripe PaymentIntent
        seats.assign(b, after);
        b.bookingStatus = "HELD"; b.paymentStatus = "PENDING";
        b.expiresAt = Instant.now().plusSeconds(10 * 60);
        try {
            PaymentIntent pi = payments.createTicketIntent(b);
            b.paymentIntentId = pi.getId();
            bookings.save(b);
            cache.evict(trainNumber, cls);
            return new BookResult(b, pi.getClientSecret());
        } catch (RuntimeException e) {
            seats.release(trainNumber, cls, n);        // compensate: give the seats back
            cache.evict(trainNumber, cls);
            throw e;
        }
    }

    /** Step 5: called by the Stripe webhook. Idempotent: only a HELD booking can become CONFIRMED. */
    public void confirmPaid(String pnr, String paymentIntentId) {
        BookingDocument b = mongo.findAndModify(
                Query.query(Criteria.where("pnr").is(pnr).and("bookingStatus").is("HELD")),
                new Update().set("bookingStatus", "CONFIRMED").set("paymentStatus", "PAID").unset("expiresAt"),
                FindAndModifyOptions.options().returnNew(true), BookingDocument.class);
        if (b == null) {
            BookingDocument cur = bookings.findByPnr(pnr).orElse(null);
            // Paid after the hold expired or after cancellation: money was taken for nothing -> refund in full.
            if (cur != null && List.of("EXPIRED", "CANCELLED").contains(cur.bookingStatus) && !"REFUNDED".equals(cur.paymentStatus)) {
                payments.refund(paymentIntentId, cur.amount);
                mongo.updateFirst(Query.query(Criteria.where("pnr").is(pnr)), new Update().set("paymentStatus", "REFUNDED"), BookingDocument.class);
            }
            return;   // duplicate webhook delivery or already handled
        }
        fulfillment.fulfill(b, "Booking & payment confirmed");
    }

    private BookingDocument transition(String pnr, List<String> from, String to) {
        return mongo.findAndModify(Query.query(Criteria.where("pnr").is(pnr).and("bookingStatus").in(from)),
                new Update().set("bookingStatus", to), FindAndModifyOptions.options().returnNew(false), BookingDocument.class);
    }

    /** Step 6: cancellation. The atomic status flip guarantees seats are released exactly once. */
    public double cancel(String pnr) {
        BookingDocument old = transition(pnr, List.of("HELD", "CONFIRMED", "WAITLISTED"), "CANCELLED");
        if (old == null) throw new BookingException("NOT_CANCELLABLE", "PNR not found or already cancelled");
        double refund = 0;
        if (!"WAITLISTED".equals(old.bookingStatus)) {
            seats.release(old.trainNumber, old.travelClass, old.passengers.size());
            if ("PAID".equals(old.paymentStatus)) refund = refunds.refund(old);
            else if (old.paymentIntentId != null) payments.cancelIntent(old.paymentIntentId);
        }
        cache.evict(old.trainNumber, old.travelClass);
        if (!"WAITLISTED".equals(old.bookingStatus)) waitlist.promote(old.trainNumber, old.travelClass);   // Step 7
        notifier.notify(old.userId, "Cancellation confirmed", "PNR " + pnr + " cancelled. Refund: INR " + refund);
        return refund;
    }

    /** New PNR on a new date. NOTE: inventory is per train/class (not per date) in this simplified model, so seats carry over. */
    public BookingDocument reschedule(String pnr, String newDate) {
        if (LocalDate.parse(newDate).isBefore(LocalDate.now())) throw new BookingException("INVALID_DATE", "Date is in the past");
        BookingDocument old = transition(pnr, List.of("CONFIRMED"), "RESCHEDULED");
        if (old == null) throw new BookingException("NOT_RESCHEDULABLE", "Only CONFIRMED bookings can be rescheduled");
        BookingDocument nb = new BookingDocument();
        nb.pnr = newPnr(); nb.userId = old.userId; nb.trainNumber = old.trainNumber; nb.travelDate = newDate;
        nb.travelClass = old.travelClass; nb.quotaType = old.quotaType; nb.passengers = old.passengers; nb.seats = old.seats;
        nb.amount = old.amount; nb.paymentStatus = "PAID"; nb.paymentIntentId = old.paymentIntentId; nb.bookingStatus = "CONFIRMED";
        bookings.save(nb);
        fulfillment.fulfill(nb, "Ticket rescheduled");
        return nb;
    }

    /** 15-minute SAS link, generated on demand. */
    public String downloadUrl(BookingDocument b) {
        return "CONFIRMED".equals(b.bookingStatus) && b.pdfBlobName != null ? blobs.sasUrl(b.pdfBlobName) : "";
    }
}
