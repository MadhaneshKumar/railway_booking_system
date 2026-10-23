package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.TrainDocument;
import com.railway.repository.Repos.*;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class WaitlistPromotionService {
    private final BookingRepo bookings;
    private final SeatInventoryService seats;
    private final TicketFulfillmentService fulfillment;
    private final SeatAvailabilityCacheService cache;
    private final MongoTemplate mongo;

    public WaitlistPromotionService(BookingRepo bookings, SeatInventoryService seats, TicketFulfillmentService fulfillment,
                                    SeatAvailabilityCacheService cache, MongoTemplate mongo) {
        this.bookings = bookings; this.seats = seats; this.fulfillment = fulfillment; this.cache = cache; this.mongo = mongo;
    }

    /** Called after any seat release. Oldest first; each promotion re-uses the atomic reserve(). */
    public void promote(String train, String cls) {
        for (BookingDocument w : bookings.findByTrainNumberAndTravelClassAndBookingStatusOrderByCreatedAtAsc(train, cls, "WAITLISTED")) {
            int n = w.passengers.size();
            TrainDocument after = seats.reserve(train, cls, n);      // atomic: only succeeds if enough seats
            if (after == null) continue;                              // this one doesn't fit; try the next

            // Claim the booking atomically, so two concurrent promotions can't promote the same one.
            BookingDocument claimed = mongo.findAndModify(
                    Query.query(Criteria.where("pnr").is(w.pnr).and("bookingStatus").is("WAITLISTED")),
                    new Update().set("bookingStatus", "CONFIRMED").set("paymentStatus", "WAIVED"),
                    FindAndModifyOptions.options().returnNew(true), BookingDocument.class);
            if (claimed == null) { seats.release(train, cls, n); continue; }

            seats.assign(claimed, after);
            mongo.updateFirst(Query.query(Criteria.where("pnr").is(claimed.pnr)),
                    new Update().set("seats", claimed.seats), BookingDocument.class);
            // NOTE: simplified. Production would pre-authorize the card at waitlist time and capture it here.
            fulfillment.fulfill(claimed, "Waitlist promoted: ticket confirmed");
        }
        cache.evict(train, cls);
    }
}
