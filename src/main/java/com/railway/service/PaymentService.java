package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.FoodOrderDocument;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PaymentService {

    public PaymentIntent createTicketIntent(BookingDocument b) {
        return create(b.amount, Map.of("type", "TICKET", "pnr", b.pnr));
    }

    public PaymentIntent createFoodIntent(FoodOrderDocument o) {
        return create(o.totalAmount, Map.of("type", "FOOD", "orderId", o.orderId));
    }

    private PaymentIntent create(double amountInr, Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams p = PaymentIntentCreateParams.builder()
                    .setAmount(Math.round(amountInr * 100))        // paise
                    .setCurrency("inr")                            // switch to "usd" if your Stripe account rejects INR
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                    .putAllMetadata(metadata)
                    .build();
            return PaymentIntent.create(p);
        } catch (StripeException e) {
            throw new BookingException("PAYMENT_ERROR", e.getMessage());
        }
    }

    /** Refund (partial or full) against a succeeded PaymentIntent. Returns the Stripe refund id. */
    public String refund(String paymentIntentId, double amountInr) {
        try {
            Refund r = Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId).setAmount(Math.round(amountInr * 100)).build());
            return r.getId();
        } catch (StripeException e) {
            throw new BookingException("REFUND_ERROR", e.getMessage());
        }
    }

    public void cancelIntent(String paymentIntentId) {
        try { PaymentIntent.retrieve(paymentIntentId).cancel(); } catch (Exception ignored) { /* already succeeded/cancelled */ }
    }
}
