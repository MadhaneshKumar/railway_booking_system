package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

@Document("bookings")
@CompoundIndexes({
    @CompoundIndex(def = "{'trainNumber':1,'travelClass':1,'bookingStatus':1,'createdAt':1}"),
    @CompoundIndex(def = "{'userId':1,'createdAt':-1}")
})
public class BookingDocument {
    @Id public String id;
    @Indexed(unique = true) public String pnr;
    public String userId, trainNumber, travelDate, travelClass, quotaType;
    public List<PassengerInfo> passengers = new ArrayList<>();
    public List<Seat> seats = new ArrayList<>();
    public double amount, refundAmount;
    /** NONE | PENDING | PAID | REFUNDED */
    public String paymentStatus = "NONE";
    /** HELD | CONFIRMED | WAITLISTED | CANCELLED | EXPIRED | RESCHEDULED */
    public String bookingStatus;
    public String paymentIntentId, pdfBlobName;
    public Instant createdAt = Instant.now(), expiresAt;
    public boolean alertsSubscribed, reminderSent;

    public static class PassengerInfo {
        public String name, gender, berth;
        public int age;
        public static PassengerInfo of(String name, int age, String gender, String berth) {
            PassengerInfo p = new PassengerInfo();
            p.name = name; p.age = age; p.gender = gender; p.berth = berth;
            return p;
        }
    }
    public static class Seat { public String passengerName, coach; public int seatNumber; }
}
