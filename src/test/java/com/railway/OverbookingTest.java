package com.railway;

import com.railway.document.BookingDocument.PassengerInfo;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.ClassInventory;
import com.railway.repository.Repos.TrainRepo;
import com.railway.service.BookingService;
import com.railway.service.PaymentService;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class OverbookingTest {
    @Autowired BookingService booking;
    @Autowired TrainRepo trains;
    @Autowired MongoTemplate mongo;
    @MockBean PaymentService payments;     // no real Stripe calls

    @Test
    void fiftyConcurrentUsers_fiveSeats_neverOverbooks() throws Exception {
        PaymentIntent pi = new PaymentIntent();
        pi.setId("pi_test"); pi.setClientSecret("pi_test_secret_x");
        when(payments.createTicketIntent(any())).thenReturn(pi);

        mongo.remove(Query.query(Criteria.where("trainNumber").is("T-TEST")), TrainDocument.class);
        TrainDocument t = new TrainDocument();
        t.trainNumber = "T-TEST"; t.trainName = "Test Express";
        ClassInventory inv = new ClassInventory(); inv.totalSeats = 5; inv.availableSeats = 5; inv.fare = 100;
        t.classInventory.put("SL", inv);
        trains.save(t);

        String date = LocalDate.now().plusDays(5).toString();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int id = i;
            results.add(pool.submit(() -> {
                go.await();                                    // all 50 threads start at the same instant
                return booking.book("T-TEST", date, "SL", "GENERAL",
                        List.of(PassengerInfo.of("P" + id, 30, "M", null)), "load-user-" + id).booking().bookingStatus;
            }));
        }
        go.countDown();

        int held = 0, waitlisted = 0;
        for (Future<String> f : results) {
            String s = f.get(30, TimeUnit.SECONDS);
            if (s.equals("HELD")) held++; else if (s.equals("WAITLISTED")) waitlisted++;
        }
        pool.shutdown();

        int left = trains.findByTrainNumber("T-TEST").orElseThrow().classInventory.get("SL").availableSeats;
        System.out.printf("HELD=%d WAITLISTED=%d availableSeats=%d%n", held, waitlisted, left);
        assertEquals(5, held);
        assertEquals(45, waitlisted);
        assertEquals(0, left);
        assertTrue(left >= 0);
    }
}
