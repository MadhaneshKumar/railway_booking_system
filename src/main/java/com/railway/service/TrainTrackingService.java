package com.railway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.StationStop;
import com.railway.document.TrainLocationDocument;
import com.railway.repository.Repos.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Service
public class TrainTrackingService {
    private final LocationRepo locations; private final TrainRepo trains; private final StringRedisTemplate redis;
    private final ObjectMapper mapper; private final AzureMapsClient maps; private final JourneyAlertService alerts;

    public TrainTrackingService(LocationRepo locations, TrainRepo trains, StringRedisTemplate redis, ObjectMapper mapper,
                                AzureMapsClient maps, JourneyAlertService alerts) {
        this.locations = locations; this.trains = trains; this.redis = redis; this.mapper = mapper; this.maps = maps; this.alerts = alerts;
    }

    /** Read path: Redis (10 s TTL) -> MongoDB. */
    public TrainLocationDocument location(String train) {
        try {
            String c = redis.opsForValue().get("track:" + train);
            if (c != null) return mapper.readValue(c, TrainLocationDocument.class);
            TrainLocationDocument d = locations.findByTrainNumber(train)
                    .orElseThrow(() -> new BookingException("NO_LIVE_DATA", "No live data yet for " + train + ", wait a few seconds"));
            redis.opsForValue().set("track:" + train, mapper.writeValueAsString(d), Duration.ofSeconds(10));
            return d;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new RuntimeException(e); }
    }

    /** GPS SIMULATOR (replace with a real feed). Moves each train along its route every 10 s. */
    @Scheduled(fixedRate = 10_000)
    public void simulate() {
        for (TrainDocument t : trains.findAll()) {
            List<StationStop> r = t.route;
            if (r.size() < 2) continue;
            double total = r.size() - 1;
            double prog = ((System.currentTimeMillis() / 1000) % 600) / 600.0 * total;      // full trip every 10 minutes
            int i = (int) Math.min(Math.floor(prog), total - 1);
            double f = prog - i;
            StationStop a = r.get(i), b = r.get(i + 1);

            TrainLocationDocument d = locations.findByTrainNumber(t.trainNumber).orElseGet(TrainLocationDocument::new);
            d.trainNumber = t.trainNumber;
            d.lat = a.lat + (b.lat - a.lat) * f;
            d.lon = a.lon + (b.lon - a.lon) * f;
            d.speed = Math.round((70 + 30 * Math.sin(prog)) * 10) / 10.0;
            int oldDelay = d.delayMinutes;
            d.delayMinutes = (int) ((System.currentTimeMillis() / 60000) % 30);              // 0..29 min, fake
            d.currentStation = a.name; d.nextStation = b.name;
            d.expectedArrival = LocalTime.now().plusMinutes(maps.etaMinutes(d.lat, d.lon, b.lat, b.lon, d.speed)).withNano(0).toString();
            d.updatedAt = Instant.now().toString();
            locations.save(d);
            try { redis.opsForValue().set("track:" + t.trainNumber, mapper.writeValueAsString(d), Duration.ofSeconds(10)); } catch (Exception ignored) { }

            if (oldDelay < 15 && d.delayMinutes >= 15) alerts.notifyDelay(t.trainNumber, d.delayMinutes);   // crossed the 15-minute threshold
        }
    }
}
