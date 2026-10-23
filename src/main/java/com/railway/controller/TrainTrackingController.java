package com.railway.controller;

import com.railway.document.TrainLocationDocument;
import com.railway.service.TrainTrackingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracking")
public class TrainTrackingController {
    private final TrainTrackingService tracking;
    public TrainTrackingController(TrainTrackingService tracking) { this.tracking = tracking; }

    @GetMapping("/{trainNumber}")
    public TrainLocationDocument track(@PathVariable String trainNumber) { return tracking.location(trainNumber); }
}
