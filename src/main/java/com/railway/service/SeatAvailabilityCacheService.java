package com.railway.service;

import com.railway.document.TrainDocument;
import com.railway.repository.Repos.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class SeatAvailabilityCacheService {
    private final StringRedisTemplate redis;
    private final TrainRepo trains;
    private final BookingRepo bookings;

    public SeatAvailabilityCacheService(StringRedisTemplate redis, TrainRepo trains, BookingRepo bookings) {
        this.redis = redis; this.trains = trains; this.bookings = bookings;
    }

    private String key(String train, String cls) { return "seat:" + train + ":" + cls; }

    /** returns {availableSeats, waitlistCount} */
    public int[] get(String train, String cls) {
        String v = redis.opsForValue().get(key(train, cls));
        if (v != null) { String[] p = v.split(","); return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
        TrainDocument t = trains.findByTrainNumber(train)
                .orElseThrow(() -> new BookingException("TRAIN_NOT_FOUND", "Unknown train " + train));
        if (!t.classInventory.containsKey(cls)) throw new BookingException("CLASS_NOT_FOUND", "Unknown class " + cls);
        int avail = t.classInventory.get(cls).availableSeats;
        int wl = (int) bookings.countByTrainNumberAndTravelClassAndBookingStatus(train, cls, "WAITLISTED");
        redis.opsForValue().set(key(train, cls), avail + "," + wl, Duration.ofSeconds(5));
        return new int[]{avail, wl};
    }

    public void evict(String train, String cls) { redis.delete(key(train, cls)); }
}
