package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.repository.Repos.BookingRepo;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class HeldBookingExpiryJob {
    private final BookingRepo bookings; private final MongoTemplate mongo; private final SeatInventoryService seats;
    private final PaymentService payments; private final WaitlistPromotionService waitlist; private final SeatAvailabilityCacheService cache;

    public HeldBookingExpiryJob(BookingRepo bookings, MongoTemplate mongo, SeatInventoryService seats, PaymentService payments,
                                WaitlistPromotionService waitlist, SeatAvailabilityCacheService cache) {
        this.bookings = bookings; this.mongo = mongo; this.seats = seats; this.payments = payments; this.waitlist = waitlist; this.cache = cache;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expire() {
        for (BookingDocument h : bookings.findByBookingStatusAndExpiresAtBefore("HELD", Instant.now())) {
            BookingDocument b = mongo.findAndModify(
                    Query.query(Criteria.where("pnr").is(h.pnr).and("bookingStatus").is("HELD")),
                    new Update().set("bookingStatus", "EXPIRED"), FindAndModifyOptions.options().returnNew(true), BookingDocument.class);
            if (b == null) continue;     // paid or cancelled in the meantime
            seats.release(b.trainNumber, b.travelClass, b.passengers.size());
            if (b.paymentIntentId != null) payments.cancelIntent(b.paymentIntentId);
            cache.evict(b.trainNumber, b.travelClass);
            waitlist.promote(b.trainNumber, b.travelClass);
        }
    }
}
