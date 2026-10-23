package com.railway;

import com.railway.document.BookingDocument;
import com.railway.document.BookingDocument.PassengerInfo;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.ClassInventory;
import com.railway.repository.Repos.BookingRepo;
import com.railway.repository.Repos.TrainRepo;
import com.railway.service.BookingService;
import com.railway.service.HeldBookingExpiryJob;
import com.railway.service.NotificationService;
import com.railway.service.PaymentService;
import com.railway.service.TicketFulfillmentService;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A promoted waitlist ticket must be HELD until paid, and an unpaid promotion must pass the seat on. */
@SpringBootTest
class WaitlistPromotionTest {
    @Autowired BookingService booking;
    @Autowired BookingRepo bookings;
    @Autowired TrainRepo trains;
    @Autowired MongoTemplate mongo;
    @Autowired HeldBookingExpiryJob expiryJob;
    @MockBean PaymentService payments;                 // no real Stripe calls
    @MockBean TicketFulfillmentService fulfillment;    // no PDF / blob storage
    @MockBean NotificationService notifier;            // no SMS / email

    private static PaymentIntent intent(String id) {
        PaymentIntent p = new PaymentIntent();
        p.setId(id); p.setClientSecret(id + "_secret");
        return p;
    }

    private String seedTrain(String number, int seats) {
        mongo.remove(Query.query(Criteria.where("trainNumber").is(number)), TrainDocument.class);
        mongo.remove(Query.query(Criteria.where("trainNumber").is(number)), BookingDocument.class);
        TrainDocument t = new TrainDocument();
        t.trainNumber = number; t.trainName = "Test Express";
        ClassInventory inv = new ClassInventory(); inv.totalSeats = seats; inv.availableSeats = seats; inv.fare = 100;
        t.classInventory.put("SL", inv);
        trains.save(t);
        return LocalDate.now().plusDays(5).toString();
    }

    private BookingDocument book(String train, String date, String user) throws InterruptedException {
        BookingDocument b = booking.book(train, date, "SL", "GENERAL",
                List.of(PassengerInfo.of("Pax " + user, 30, "M", null)), user).booking();
        Thread.sleep(10);   // Mongo stores milliseconds; keeps waitlist order (createdAt) unambiguous
        return b;
    }

    private BookingDocument reload(String pnr) { return bookings.findByPnr(pnr).orElseThrow(); }

    private int available(String train) { return trains.findByTrainNumber(train).orElseThrow().classInventory.get("SL").availableSeats; }

    @Test
    void promotedTicketIsHeldUntilPaid_thenConfirmed() throws Exception {
        when(payments.createTicketIntent(any())).thenAnswer(i -> intent("pi_" + ((BookingDocument) i.getArgument(0)).pnr));
        String date = seedTrain("T-WL1", 1);

        BookingDocument a = book("T-WL1", date, "user-a");
        BookingDocument b = book("T-WL1", date, "user-b");
        assertEquals("HELD", a.bookingStatus);
        assertEquals("WAITLISTED", b.bookingStatus);

        booking.cancel(a.pnr);                                   // frees the seat -> B is promoted

        BookingDocument promoted = reload(b.pnr);
        assertEquals("HELD", promoted.bookingStatus, "promoted ticket must wait for payment, not auto-confirm");
        assertEquals("PENDING", promoted.paymentStatus);
        assertNotNull(promoted.paymentIntentId);
        assertNotNull(promoted.expiresAt);
        assertTrue(promoted.expiresAt.isAfter(Instant.now()));
        assertEquals(1, promoted.seats.size());
        assertEquals(0, available("T-WL1"));
        verify(notifier).notify(eq("user-b"), eq("A seat opened up"), anyString());

        booking.confirmPaid(b.pnr, promoted.paymentIntentId);    // same path the Stripe webhook / mock-pay uses
        BookingDocument paid = reload(b.pnr);
        assertEquals("CONFIRMED", paid.bookingStatus);
        assertEquals("PAID", paid.paymentStatus);
    }

    @Test
    void unpaidPromotionExpires_andNextWaitlistedGetsTheSeat() throws Exception {
        when(payments.createTicketIntent(any())).thenAnswer(i -> intent("pi_" + ((BookingDocument) i.getArgument(0)).pnr));
        String date = seedTrain("T-WL2", 1);

        BookingDocument a = book("T-WL2", date, "user-a");
        BookingDocument b = book("T-WL2", date, "user-b");
        BookingDocument c = book("T-WL2", date, "user-c");
        assertEquals("WAITLISTED", b.bookingStatus);
        assertEquals("WAITLISTED", c.bookingStatus);

        booking.cancel(a.pnr);
        assertEquals("HELD", reload(b.pnr).bookingStatus);       // oldest waiter first
        assertEquals("WAITLISTED", reload(c.pnr).bookingStatus);

        // B never pays: push the deadline into the past and run the expiry job
        mongo.updateFirst(Query.query(Criteria.where("pnr").is(b.pnr)),
                new Update().set("expiresAt", Instant.now().minusSeconds(5)), BookingDocument.class);
        expiryJob.expire();

        assertEquals("EXPIRED", reload(b.pnr).bookingStatus);
        assertEquals("HELD", reload(c.pnr).bookingStatus, "the seat must pass to the next waitlisted passenger");
        assertEquals(0, available("T-WL2"));
    }

    @Test
    void paymentFailureDuringPromotion_keepsPassengerOnWaitlist_andReturnsTheSeat() throws Exception {
        when(payments.createTicketIntent(any()))
                .thenReturn(intent("pi_first"))                  // A's own booking
                .thenThrow(new RuntimeException("payment provider down"));   // B's promotion
        String date = seedTrain("T-WL3", 1);

        BookingDocument a = book("T-WL3", date, "user-a");
        BookingDocument b = book("T-WL3", date, "user-b");
        assertEquals("WAITLISTED", b.bookingStatus);

        booking.cancel(a.pnr);

        BookingDocument stillWaiting = reload(b.pnr);
        assertEquals("WAITLISTED", stillWaiting.bookingStatus);
        assertEquals("NONE", stillWaiting.paymentStatus);
        assertNull(stillWaiting.expiresAt);
        assertEquals(1, available("T-WL3"), "the seat must be given back when the promotion fails");
    }
}
