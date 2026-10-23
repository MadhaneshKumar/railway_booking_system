package com.railway.auth;

import com.railway.document.BookingDocument;
import com.railway.document.FoodOrderDocument;
import com.railway.service.BookingException;
import com.railway.service.BookingService;
import com.railway.service.FoodOrderService;
import org.springframework.stereotype.Component;

/**
 * Ownership checks. Someone else's booking looks exactly like a booking that does not exist, so PNRs and order ids
 * cannot be probed to find out which ones are real.
 */
@Component
public class AccessGuard {
    private final BookingService bookings; private final FoodOrderService food;

    public AccessGuard(BookingService bookings, FoodOrderService food) { this.bookings = bookings; this.food = food; }

    public BookingDocument booking(String pnr) {
        String me = CurrentUser.require();
        BookingDocument b = bookings.require(pnr);
        if (!me.equals(b.userId)) throw new BookingException("PNR_NOT_FOUND", "No booking for PNR " + pnr);
        return b;
    }

    public FoodOrderDocument foodOrder(String orderId) {
        String me = CurrentUser.require();
        FoodOrderDocument o = food.status(orderId);
        if (!me.equals(o.userId)) throw new BookingException("ORDER_NOT_FOUND", "Unknown order");
        return o;
    }
}
