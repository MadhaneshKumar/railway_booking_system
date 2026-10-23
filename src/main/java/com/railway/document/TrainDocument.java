package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.*;

@Document("trains")
public class TrainDocument {
    @Id public String id;
    @Indexed(unique = true) public String trainNumber;
    public String trainName;
    public List<StationStop> route = new ArrayList<>();
    /** key = class code (SL, 3A, 2A). The reservation decrements classInventory.<class>.availableSeats atomically. */
    public Map<String, ClassInventory> classInventory = new HashMap<>();

    public static class StationStop { public String code, name, arrival, departure; public double lat, lon; }
    public static class ClassInventory { public int totalSeats, availableSeats; public double fare; }
}
