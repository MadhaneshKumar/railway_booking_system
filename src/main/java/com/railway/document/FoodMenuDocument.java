package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.*;

@Document("food_menus")
public class FoodMenuDocument {
    @Id public String id;
    @Indexed(unique = true) public String stationCode;
    public List<MenuItem> items = new ArrayList<>();
    public static class MenuItem {
        public String itemId, name; public double price;
        public static MenuItem of(String id, String name, double price) { MenuItem m = new MenuItem(); m.itemId = id; m.name = name; m.price = price; return m; }
    }
}
