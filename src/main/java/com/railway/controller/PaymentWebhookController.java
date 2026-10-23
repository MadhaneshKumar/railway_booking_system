package com.railway.controller;

import com.railway.service.BookingService;
import com.railway.service.FoodOrderService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class PaymentWebhookController {
    private final BookingService bookings;
    private final FoodOrderService food;
    private final String secret;

    public PaymentWebhookController(BookingService bookings, FoodOrderService food, @Value("${stripe.webhook-secret:}") String secret) {
        this.bookings = bookings; this.food = food; this.secret = secret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripe(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) throws Exception {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);   // verifies the signature
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("invalid signature");
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            StripeObject obj = event.getDataObjectDeserializer().deserializeUnsafe();
            PaymentIntent pi = (PaymentIntent) obj;
            Map<String, String> meta = pi.getMetadata();
            if ("TICKET".equals(meta.get("type"))) bookings.confirmPaid(meta.get("pnr"), pi.getId());   // HELD -> CONFIRMED
            else if ("FOOD".equals(meta.get("type"))) food.confirmPaid(meta.get("orderId"));
            // events without our metadata (e.g. `stripe trigger`) are ignored
        }
        return ResponseEntity.ok("ok");
    }
}
