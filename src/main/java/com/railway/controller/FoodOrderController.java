package com.railway.controller;

import com.railway.document.FoodMenuDocument;
import com.railway.service.FoodOrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/food")
public class FoodOrderController {
    private final FoodOrderService food;
    public FoodOrderController(FoodOrderService food) { this.food = food; }

    @GetMapping("/menu/{station}")
    public FoodMenuDocument menu(@PathVariable String station) { return food.menu(station); }
}
