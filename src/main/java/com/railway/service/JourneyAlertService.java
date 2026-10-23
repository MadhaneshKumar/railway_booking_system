package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.repository.Repos.BookingRepo;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class JourneyAlertService {
    private final BookingRepo bookings; private final NotificationService notifier; private final MongoTemplate mongo;

    public JourneyAlertService(BookingRepo bookings, NotificationService notifier, MongoTemplate mongo) {
        this.bookings = bookings; this.notifier = notifier; this.mongo = mongo;
    }

    public void subscribe(String pnr, boolean on) {
        bookings.findByPnr(pnr).orElseThrow(() -> new BookingException("PNR_NOT_FOUND", "Unknown PNR"));
        mongo.updateFirst(Query.query(Criteria.where("pnr").is(pnr)), new Update().set("alertsSubscribed", on), BookingDocument.class);
    }

    /** 24-hour journey reminder, checked every hour. */
    @Scheduled(cron = "0 0 * * * *")
    public void sendReminders() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        for (BookingDocument b : bookings.findByBookingStatusAndAlertsSubscribedAndTravelDateAndReminderSent("CONFIRMED", true, tomorrow, false)) {
            notifier.notify(b.userId, "Journey reminder", "Your train " + b.trainNumber + " departs tomorrow (" + b.travelDate + "). PNR " + b.pnr);
            mongo.updateFirst(Query.query(Criteria.where("pnr").is(b.pnr)), new Update().set("reminderSent", true), BookingDocument.class);
        }
    }

    public void notifyDelay(String train, int minutes) {
        for (BookingDocument b : bookings.findByTrainNumberAndTravelDateAndBookingStatusAndAlertsSubscribed(
                train, LocalDate.now().toString(), "CONFIRMED", true)) {
            notifier.notify(b.userId, "Train delay", "Train " + train + " is running " + minutes + " minutes late. PNR " + b.pnr);
        }
    }
}
