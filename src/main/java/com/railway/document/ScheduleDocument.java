package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.*;

@Document("schedules")
public class ScheduleDocument {
    @Id public String id;
    @Indexed public String trainNumber;
    public List<Integer> runDays = new ArrayList<>();   // 1=Mon ... 7=Sun
}
