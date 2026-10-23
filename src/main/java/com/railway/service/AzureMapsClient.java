package com.railway.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class AzureMapsClient {
    private final String key;
    private final RestClient http = RestClient.create();

    public AzureMapsClient(@Value("${azure.maps.key:}") String key) { this.key = key; }

    /** ETA in minutes. Uses Azure Maps Route API when a key is set, otherwise straight-line distance / speed. */
    public int etaMinutes(double lat1, double lon1, double lat2, double lon2, double speedKmh) {
        if (!key.isBlank()) {
            try {
                JsonNode n = http.get().uri("https://atlas.microsoft.com/route/directions/json?api-version=1.0&query={q}&subscription-key={k}",
                        Map.of("q", lat1 + "," + lon1 + ":" + lat2 + "," + lon2, "k", key)).retrieve().body(JsonNode.class);
                return n.at("/routes/0/summary/travelTimeInSeconds").asInt() / 60;
            } catch (Exception ignored) { /* fall back below */ }
        }
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        double km = 2 * r * Math.asin(Math.sqrt(a));
        return (int) Math.round(km / Math.max(speedKmh, 20) * 60);
    }
}
