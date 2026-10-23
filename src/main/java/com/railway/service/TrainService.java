package com.railway.service;

import com.railway.document.ScheduleDocument;
import com.railway.document.TrainDocument;
import com.railway.document.TrainDocument.StationStop;
import com.railway.repository.Repos.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class TrainService {
    public record TrainInfoDto(String trainNumber, String trainName, String departureTime, String arrivalTime, List<String> classes) {}

    private final TrainRepo trains; private final ScheduleRepo schedules; private final SeatAvailabilityCacheService cache;

    public TrainService(TrainRepo trains, ScheduleRepo schedules, SeatAvailabilityCacheService cache) {
        this.trains = trains; this.schedules = schedules; this.cache = cache;
    }

    private static int indexOf(TrainDocument t, String code) {
        for (int i = 0; i < t.route.size(); i++) if (t.route.get(i).code.equalsIgnoreCase(code)) return i;
        return -1;
    }

    public List<TrainInfoDto> search(String source, String destination, String date) {
        int dow = LocalDate.parse(date).getDayOfWeek().getValue();
        List<TrainInfoDto> out = new ArrayList<>();
        for (TrainDocument t : trains.findAll()) {
            int s = indexOf(t, source), d = indexOf(t, destination);
            if (s < 0 || d < 0 || s >= d) continue;
            Optional<ScheduleDocument> sch = schedules.findByTrainNumber(t.trainNumber);
            if (sch.isPresent() && !sch.get().runDays.contains(dow)) continue;
            StationStop a = t.route.get(s), z = t.route.get(d);
            out.add(new TrainInfoDto(t.trainNumber, t.trainName, a.departure, z.arrival, new ArrayList<>(t.classInventory.keySet())));
        }
        return out;
    }

    public int[] availability(String train, String cls) { return cache.get(train, cls); }

    public List<StationStop> schedule(String train) {
        return trains.findByTrainNumber(train).orElseThrow(() -> new BookingException("TRAIN_NOT_FOUND", "Unknown train")).route;
    }
}
