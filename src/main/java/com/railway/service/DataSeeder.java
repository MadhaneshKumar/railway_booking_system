package com.railway.service;

import com.railway.document.*;
import com.railway.document.FoodMenuDocument.MenuItem;
import com.railway.document.TrainDocument.ClassInventory;
import com.railway.document.TrainDocument.StationStop;
import com.railway.repository.Repos.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {
    private final TrainRepo trains; private final ScheduleRepo schedules; private final UserRepo users; private final FoodMenuRepo menus;

    public DataSeeder(TrainRepo trains, ScheduleRepo schedules, UserRepo users, FoodMenuRepo menus) {
        this.trains = trains; this.schedules = schedules; this.users = users; this.menus = menus;
    }

    private static StationStop stop(String code, String name, String arr, String dep, double lat, double lon) {
        StationStop s = new StationStop(); s.code = code; s.name = name; s.arrival = arr; s.departure = dep; s.lat = lat; s.lon = lon; return s;
    }
    private static ClassInventory inv(int total, double fare) {
        ClassInventory c = new ClassInventory(); c.totalSeats = total; c.availableSeats = total; c.fare = fare; return c;
    }

    @Override
    public void run(String... args) {
        if (trains.count() > 0) return;

        UserDocument u = new UserDocument();
        u.userId = "user1"; u.name = "Demo User"; u.email = "demo@example.com"; u.phone = "+910000000000";
        users.save(u);

        for (String[] t : new String[][]{{"12607", "Lalbagh Express"}, {"22625", "Double Decker"}}) {
            TrainDocument d = new TrainDocument();
            d.trainNumber = t[0]; d.trainName = t[1];
            d.route = List.of(
                    stop("MAS", "Chennai Central", "-", "06:00", 13.0827, 80.2707),
                    stop("KPD", "Katpadi", "08:00", "08:02", 12.9692, 79.1372),
                    stop("SBC", "Bengaluru City", "11:30", "-", 12.9784, 77.5696));
            d.classInventory.put("SL", inv(100, 450));
            d.classInventory.put("3A", inv(50, 1150));
            d.classInventory.put("2A", inv(30, 1650));
            trains.save(d);

            ScheduleDocument s = new ScheduleDocument();
            s.trainNumber = t[0]; s.runDays = List.of(1, 2, 3, 4, 5, 6, 7);
            schedules.save(s);
        }

        for (String code : List.of("MAS", "KPD", "SBC")) {
            FoodMenuDocument m = new FoodMenuDocument();
            m.stationCode = code;
            m.items = List.of(MenuItem.of("VEG01", "Veg Meals", 120), MenuItem.of("BIR01", "Chicken Biryani", 180), MenuItem.of("SND01", "Sandwich", 70));
            menus.save(m);
        }
    }
}
