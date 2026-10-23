package com.railway.controller;

import com.railway.auth.AccessGuard;
import com.railway.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/tickets")
public class TicketDownloadController {
    private final BookingService bookings; private final AccessGuard guard;
    public TicketDownloadController(BookingService bookings, AccessGuard guard) { this.bookings = bookings; this.guard = guard; }

    /** Redirects to a fresh 15-minute SAS URL. Only the booking's owner (Authorization: Bearer token) may use it. */
    @GetMapping("/{pnr}/download")
    public ResponseEntity<Void> download(@PathVariable String pnr) {
        String url = bookings.downloadUrl(guard.booking(pnr));
        if (url.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }
}
