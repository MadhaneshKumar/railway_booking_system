package com.railway.service;

import com.railway.document.BookingDocument;
import com.railway.document.RefundTransactionDocument;
import com.railway.repository.Repos.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class RefundService {
    private final PaymentService payments;
    private final RefundRepo refundRepo;
    private final MongoTemplate mongo;

    public RefundService(PaymentService payments, RefundRepo refundRepo, MongoTemplate mongo) {
        this.payments = payments; this.refundRepo = refundRepo; this.mongo = mongo;
    }

    /** >= 2 days before travel: 10% fee, 1 day: 25%, same day: 50%. Unpaid bookings have no fee. */
    public double charges(BookingDocument b) {
        if (!"PAID".equals(b.paymentStatus)) return 0;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(b.travelDate));
        double pct = days >= 2 ? 0.10 : days == 1 ? 0.25 : 0.50;
        return Math.round(b.amount * pct * 100) / 100.0;
    }

    public double refundable(BookingDocument b) {
        return "PAID".equals(b.paymentStatus) ? b.amount - charges(b) : 0;
    }

    /** Triggers the Stripe refund, records it, updates the booking. */
    public double refund(BookingDocument b) {
        double amt = refundable(b);
        if (amt <= 0) return 0;
        String stripeId = payments.refund(b.paymentIntentId, amt);
        RefundTransactionDocument t = new RefundTransactionDocument();
        t.reference = b.pnr; t.stripeRefundId = stripeId; t.amount = amt; t.reason = "TICKET_CANCELLATION";
        refundRepo.save(t);
        mongo.updateFirst(Query.query(Criteria.where("pnr").is(b.pnr)),
                new Update().set("paymentStatus", "REFUNDED").set("refundAmount", amt), BookingDocument.class);
        return amt;
    }
}
