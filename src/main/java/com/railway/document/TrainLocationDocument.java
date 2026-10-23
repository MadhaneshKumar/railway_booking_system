package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("train_locations")
public class TrainLocationDocument {
    @Id public String id;
    @Indexed(unique = true) public String trainNumber;
    public double lat, lon, speed;
    public int delayMinutes;
    public String currentStation, nextStation, expectedArrival, updatedAt;
}
