package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.TrainDocument;
import com.railway.document.UpgradeRequestDocument;
import com.railway.repository.Repos.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class SeatUpgradeService {
    private final BookingService bookings; private final TrainRepo trains; private final UpgradeRepo upgrades;
    private final SeatInventoryService seats; private final WaitlistPromotionService waitlist;
    private final SeatAvailabilityCacheService cache; private final NotificationService notifier; private final MongoTemplate mongo;

    public SeatUpgradeService(BookingService bookings, TrainRepo trains, UpgradeRepo upgrades, SeatInventoryService seats,
                              WaitlistPromotionService waitlist, SeatAvailabilityCacheService cache,
                              NotificationService notifier, MongoTemplate mongo) {
        this.bookings = bookings; this.trains = trains; this.upgrades = upgrades; this.seats = seats;
        this.waitlist = waitlist; this.cache = cache; this.notifier = notifier; this.mongo = mongo;
    }

    /** NOTE: simplified. Instant upgrade if seats exist in the preferred class, otherwise PENDING. Fare difference is not charged. */
    public UpgradeRequestDocument request(String pnr, String preferred) {
        BookingDocument b = bookings.require(pnr);
        if (!"CONFIRMED".equals(b.bookingStatus)) throw new BookingException("NOT_CONFIRMED", "Only confirmed tickets can be upgraded");
        TrainDocument t = trains.findByTrainNumber(b.trainNumber).orElseThrow();
        if (!t.classInventory.containsKey(preferred) || preferred.equals(b.travelClass))
            throw new BookingException("INVALID_CLASS", "Choose a different, valid class");

        UpgradeRequestDocument r = new UpgradeRequestDocument();
        r.requestId = "U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        r.pnr = pnr; r.preferredClass = preferred;

        int n = b.passengers.size();
        TrainDocument after = seats.reserve(b.trainNumber, preferred, n);    // same atomic reservation
        if (after != null) {
            String old = b.travelClass;
            seats.release(b.trainNumber, old, n);
            b.travelClass = preferred;
            seats.assign(b, after);
            mongo.updateFirst(Query.query(Criteria.where("pnr").is(pnr)),
                    new Update().set("travelClass", preferred).set("seats", b.seats), BookingDocument.class);
            r.status = "UPGRADED"; r.upgradedClass = preferred;
            cache.evict(b.trainNumber, old); cache.evict(b.trainNumber, preferred);
            notifier.notify(b.userId, "Seat upgrade", "PNR " + pnr + " upgraded to " + preferred);
            waitlist.promote(b.trainNumber, old);
        } else {
            r.status = "PENDING";
        }
        return upgrades.save(r);
    }

    public UpgradeRequestDocument status(String requestId) {
        return upgrades.findByRequestId(requestId).orElseThrow(() -> new BookingException("REQUEST_NOT_FOUND", "Unknown request"));
    }
}
