package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.TrainDocument;
import com.railway.repository.Repos.*;
import com.stripe.model.PaymentIntent;
import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.time.Instant;

/**
 * When seats are released, the oldest waitlisted bookings that fit are moved to HELD and given a payment deadline.
 * They are NOT confirmed: the passenger must pay (HELD -> CONFIRMED through the normal payment path).
 * If they don't pay in time, HeldBookingExpiryJob expires the hold, releases the seats and promotes the next person.
 */
@Service
public class WaitlistPromotionService {
    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionService.class);

    private final BookingRepo bookings;
    private final SeatInventoryService seats;
    private final PaymentService payments;
    private final NotificationService notifier;
    private final SeatAvailabilityCacheService cache;
    private final MongoTemplate mongo;

    /** How long a promoted passenger has to pay. */
    @Value("${booking.promotion-hold-minutes:30}")
    private long holdMinutes;

    public WaitlistPromotionService(BookingRepo bookings, SeatInventoryService seats, PaymentService payments,
                                    NotificationService notifier, SeatAvailabilityCacheService cache, MongoTemplate mongo) {
        this.bookings = bookings; this.seats = seats; this.payments = payments;
        this.notifier = notifier; this.cache = cache; this.mongo = mongo;
    }

    /** Called after any seat release. Oldest first; each promotion re-uses the atomic reserve(). */
    public void promote(String train, String cls) {
        for (BookingDocument w : bookings.findByTrainNumberAndTravelClassAndBookingStatusOrderByCreatedAtAsc(train, cls, "WAITLISTED")) {
            int n = w.passengers.size();
            TrainDocument after = seats.reserve(train, cls, n);      // atomic: only succeeds if enough seats
            if (after == null) continue;                              // this one doesn't fit; try the next

            // Claim the booking atomically, so two concurrent promotions can't promote the same one.
            Instant deadline = Instant.now().plusSeconds(holdMinutes * 60);
            BookingDocument claimed = mongo.findAndModify(
                    Query.query(Criteria.where("pnr").is(w.pnr).and("bookingStatus").is("WAITLISTED")),
                    new Update().set("bookingStatus", "HELD").set("paymentStatus", "PENDING").set("expiresAt", deadline),
                    FindAndModifyOptions.options().returnNew(true), BookingDocument.class);
            if (claimed == null) { seats.release(train, cls, n); continue; }

            seats.assign(claimed, after);
            String intentId;
            try {
                PaymentIntent pi = payments.createTicketIntent(claimed);
                intentId = pi.getId();
            } catch (RuntimeException e) {
                // Compensate: back to the waitlist and give the seats back. Only if it is still HELD, so a booking
                // that was cancelled in the meantime (which already released its seats) is not resurrected.
                log.error("Could not create payment for promoted booking {}: {}", claimed.pnr, e.getMessage(), e);
                UpdateResult r = mongo.updateFirst(
                        Query.query(Criteria.where("pnr").is(claimed.pnr).and("bookingStatus").is("HELD")),
                        new Update().set("bookingStatus", "WAITLISTED").set("paymentStatus", "NONE").unset("expiresAt"),
                        BookingDocument.class);
                if (r.getModifiedCount() > 0) seats.release(train, cls, n);
                continue;
            }
            mongo.updateFirst(Query.query(Criteria.where("pnr").is(claimed.pnr)),
                    new Update().set("seats", claimed.seats).set("paymentIntentId", intentId), BookingDocument.class);

            notifier.notify(claimed.userId, "A seat opened up",
                    "PNR " + claimed.pnr + " is off the waitlist. Pay within " + holdMinutes + " minutes to confirm "
                            + claimed.trainNumber + " on " + claimed.travelDate + " (" + claimed.travelClass + "), or the seat goes to the next passenger.");
        }
        cache.evict(train, cls);
    }
}
