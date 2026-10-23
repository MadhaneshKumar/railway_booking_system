package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.BookingDocument.Seat;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.ClassInventory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

@Service
public class SeatInventoryService {
    private final MongoTemplate mongo;
    public SeatInventoryService(MongoTemplate mongo) { this.mongo = mongo; }

    /**
     * ATOMIC RESERVATION: one findAndModify = one single-document atomic operation in MongoDB.
     * The filter (availableSeats >= n) and the update ($inc -n) are evaluated together,
     * so two concurrent requests can never both take the last seats. No application-level locks.
     * Returns the updated train document, or null when not enough seats (-> waitlist).
     * `cls` must be validated against classInventory keys before calling (it is part of a field path).
     */
    public TrainDocument reserve(String trainNumber, String cls, int n) {
        String field = "classInventory." + cls + ".availableSeats";
        Query q = Query.query(Criteria.where("trainNumber").is(trainNumber).and(field).gte(n));
        Update u = new Update().inc(field, -n);
        return mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), TrainDocument.class);
    }

    /** Atomic release (cancellation, expiry, upgrade). */
    public void release(String trainNumber, String cls, int n) {
        String field = "classInventory." + cls + ".availableSeats";
        mongo.updateFirst(Query.query(Criteria.where("trainNumber").is(trainNumber)),
                new Update().inc(field, n), TrainDocument.class);
    }

    /**
     * Assigns seat numbers from the document returned by reserve() (returned atomically with the decrement,
     * so concurrent reservations get disjoint ranges).
     * NOTE: simplified numbering; after cancellations numbers can repeat. Production would keep a per-seat bitmap.
     */
    public void assign(BookingDocument b, TrainDocument after) {
        ClassInventory inv = after.classInventory.get(b.travelClass);
        int n = b.passengers.size();
        int first = inv.totalSeats - inv.availableSeats - n + 1;
        b.seats = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Seat s = new Seat();
            s.passengerName = b.passengers.get(i).name;
            s.seatNumber = first + i;
            s.coach = b.travelClass + "-" + ((s.seatNumber - 1) / 72 + 1);
            b.seats.add(s);
        }
    }
}
