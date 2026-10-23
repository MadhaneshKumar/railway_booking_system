package com.railway.endpoint;

import com.railway.document.BookingDocument;
import com.railway.document.BookingDocument.PassengerInfo;
import com.railway.document.FoodOrderDocument;
import com.railway.document.TrainDocument.StationStop;
import com.railway.document.TrainLocationDocument;
import com.railway.document.UpgradeRequestDocument;
import com.railway.service.*;
import com.railway.ws.*;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@WebService(serviceName = "RailwayService", portName = "RailwayPort",
        endpointInterface = "com.railway.ws.RailwayPort",
        targetNamespace = "http://railway.example.com/booking",
        wsdlLocation = "classpath:wsdl/booking.wsdl")
public class BookingEndpoint implements RailwayPort {
    private static final Logger log = LoggerFactory.getLogger(BookingEndpoint.class);

    private final TrainService trains; private final BookingService booking; private final RefundService refunds;
    private final FoodOrderService food; private final TrainTrackingService tracking;
    private final SeatUpgradeService upgrades; private final JourneyAlertService alerts;

    public BookingEndpoint(TrainService trains, BookingService booking, RefundService refunds, FoodOrderService food,
                           TrainTrackingService tracking, SeatUpgradeService upgrades, JourneyAlertService alerts) {
        this.trains = trains; this.booking = booking; this.refunds = refunds; this.food = food;
        this.tracking = tracking; this.upgrades = upgrades; this.alerts = alerts;
    }

    // ---------- helpers: translate domain exceptions into the WSDL-defined BookingFaultMessage ----------
    @FunctionalInterface private interface Op<T> { T run() throws Exception; }

    private <T> T call(Op<T> op) throws BookingFaultMessage {
        try { return op.run(); }
        catch (BookingException e) { throw fault(e.code, e.getMessage()); }
        catch (Exception e) { log.error("Unexpected error", e); throw fault("INTERNAL_ERROR", String.valueOf(e.getMessage())); }
    }

    private BookingFaultMessage fault(String code, String message) {
        BookingFault f = new BookingFault();
        f.setCode(code); f.setMessage(message);
        return new BookingFaultMessage(message, f);
    }

    // ---------- train operations ----------
    @Override
    public SearchTrainsResponse searchTrains(SearchTrainsRequest r) throws BookingFaultMessage {
        return call(() -> {
            SearchTrainsResponse out = new SearchTrainsResponse();
            for (TrainService.TrainInfoDto t : trains.search(r.getSource(), r.getDestination(), r.getTravelDate())) {
                TrainInfo ti = new TrainInfo();
                ti.setTrainNumber(t.trainNumber()); ti.setTrainName(t.trainName());
                ti.setDepartureTime(t.departureTime()); ti.setArrivalTime(t.arrivalTime());
                ti.getAvailableClasses().addAll(t.classes());
                out.getTrains().add(ti);
            }
            return out;
        });
    }

    @Override
    public CheckSeatAvailabilityResponse checkSeatAvailability(CheckSeatAvailabilityRequest r) throws BookingFaultMessage {
        return call(() -> {
            int[] a = trains.availability(r.getTrainNumber(), r.getTravelClass());
            CheckSeatAvailabilityResponse out = new CheckSeatAvailabilityResponse();
            out.setAvailableSeats(a[0]); out.setWaitlistCount(a[1]);
            return out;
        });
    }

    // ---------- booking ----------
    @Override
    public BookTicketResponse bookTicket(BookTicketRequest r) throws BookingFaultMessage {
        return call(() -> {
            List<PassengerInfo> ps = r.getPassengers().stream()
                    .map(p -> PassengerInfo.of(p.getName(), p.getAge(), p.getGender(), p.getPreferredBerth())).toList();
            BookingService.BookResult res = booking.book(r.getTrainNumber(), r.getTravelDate(), r.getTravelClass(),
                    r.getQuotaType(), ps, r.getUserId());
            BookTicketResponse out = new BookTicketResponse();
            out.setPnr(res.booking().pnr);
            out.setBookingStatus(res.booking().bookingStatus);
            res.booking().seats.forEach(s -> {
                SeatAssignment a = new SeatAssignment();
                a.setPassengerName(s.passengerName); a.setCoach(s.coach); a.setSeatNumber(s.seatNumber);
                out.getSeatAssignments().add(a);
            });
            out.setPaymentClientSecret(res.clientSecret());
            return out;
        });
    }

    @Override
    public CancelTicketResponse cancelTicket(CancelTicketRequest r) throws BookingFaultMessage {
        return call(() -> {
            double refund = booking.cancel(r.getPnr());
            CancelTicketResponse out = new CancelTicketResponse();
            out.setPnr(r.getPnr()); out.setStatus("CANCELLED"); out.setRefundAmount(refund);
            return out;
        });
    }

    @Override
    public GetPNRStatusResponse getPNRStatus(GetPNRStatusRequest r) throws BookingFaultMessage {
        return call(() -> {
            BookingDocument b = booking.require(r.getPnr());
            GetPNRStatusResponse out = new GetPNRStatusResponse();
            out.setPnr(b.pnr); out.setBookingStatus(b.bookingStatus); out.setTrainNumber(b.trainNumber);
            out.setTravelDate(b.travelDate); out.setTicketDownloadUrl(booking.downloadUrl(b));
            if ("HELD".equals(b.bookingStatus) && b.expiresAt != null) out.setHoldExpiresAt(b.expiresAt.toString());
            return out;
        });
    }

    @Override
    public GetRefundDetailsResponse getRefundDetails(GetRefundDetailsRequest r) throws BookingFaultMessage {
        return call(() -> {
            BookingDocument b = booking.require(r.getPnr());
            GetRefundDetailsResponse out = new GetRefundDetailsResponse();
            out.setRefundableAmount(refunds.refundable(b)); out.setCancellationCharges(refunds.charges(b));
            return out;
        });
    }

    @Override
    public RescheduleTicketResponse rescheduleTicket(RescheduleTicketRequest r) throws BookingFaultMessage {
        return call(() -> {
            BookingDocument nb = booking.reschedule(r.getPnr(), r.getNewTravelDate());
            RescheduleTicketResponse out = new RescheduleTicketResponse();
            out.setNewPnr(nb.pnr); out.setStatus(nb.bookingStatus);
            return out;
        });
    }

    // ---------- food ----------
    @Override
    public OrderFoodResponse orderFood(OrderFoodRequest r) throws BookingFaultMessage {
        return call(() -> {
            Map<String, Integer> items = new LinkedHashMap<>();
            r.getFoodItems().forEach(i -> items.merge(i.getItemId(), i.getQuantity(), Integer::sum));
            FoodOrderService.OrderResult res = food.order(r.getPnr(), r.getStationCode(), items);
            OrderFoodResponse out = new OrderFoodResponse();
            out.setOrderId(res.order().orderId); out.setTotalAmount(res.order().totalAmount);
            out.setStatus(res.order().status); out.setPaymentClientSecret(res.clientSecret());
            return out;
        });
    }

    @Override
    public GetFoodOrderStatusResponse getFoodOrderStatus(GetFoodOrderStatusRequest r) throws BookingFaultMessage {
        return call(() -> {
            FoodOrderDocument o = food.status(r.getOrderId());
            GetFoodOrderStatusResponse out = new GetFoodOrderStatusResponse();
            out.setOrderStatus(o.status); out.setDeliveryStation(o.stationCode);
            out.setEstimatedDeliveryTime(o.estimatedDelivery == null ? "" : o.estimatedDelivery);
            return out;
        });
    }

    @Override
    public CancelFoodOrderResponse cancelFoodOrder(CancelFoodOrderRequest r) throws BookingFaultMessage {
        return call(() -> {
            Object[] res = food.cancel(r.getOrderId());
            CancelFoodOrderResponse out = new CancelFoodOrderResponse();
            out.setRefundAmount((Double) res[0]); out.setOrderStatus((String) res[1]);
            return out;
        });
    }

    // ---------- tracking ----------
    @Override
    public TrackTrainResponse trackTrain(TrackTrainRequest r) throws BookingFaultMessage {
        return call(() -> {
            TrainLocationDocument d = tracking.location(r.getTrainNumber());
            TrackTrainResponse out = new TrackTrainResponse();
            out.setCurrentLocation(d.lat + "," + d.lon); out.setNextStation(d.nextStation);
            out.setDelayMinutes(d.delayMinutes); out.setSpeed(d.speed);
            return out;
        });
    }

    @Override
    public GetTrainLiveStatusResponse getTrainLiveStatus(GetTrainLiveStatusRequest r) throws BookingFaultMessage {
        return call(() -> {
            TrainLocationDocument d = tracking.location(r.getTrainNumber());
            GetTrainLiveStatusResponse out = new GetTrainLiveStatusResponse();
            out.setTrainStatus(d.delayMinutes >= 5 ? "DELAYED" : "ON_TIME");
            out.setCurrentStation(d.currentStation); out.setExpectedArrival(d.expectedArrival);
            return out;
        });
    }

    @Override
    public GetTrainScheduleResponse getTrainSchedule(GetTrainScheduleRequest r) throws BookingFaultMessage {
        return call(() -> {
            GetTrainScheduleResponse out = new GetTrainScheduleResponse();
            for (StationStop s : trains.schedule(r.getTrainNumber())) {
                Station st = new Station();
                st.setCode(s.code); st.setArrivalTime(s.arrival); st.setDepartureTime(s.departure);
                out.getStations().add(st);
            }
            return out;
        });
    }

    // ---------- upgrades ----------
    @Override
    public RequestSeatUpgradeResponse requestSeatUpgrade(RequestSeatUpgradeRequest r) throws BookingFaultMessage {
        return call(() -> {
            UpgradeRequestDocument u = upgrades.request(r.getPnr(), r.getPreferredClass());
            RequestSeatUpgradeResponse out = new RequestSeatUpgradeResponse();
            out.setRequestId(u.requestId); out.setStatus(u.status);
            return out;
        });
    }

    @Override
    public GetUpgradeStatusResponse getUpgradeStatus(GetUpgradeStatusRequest r) throws BookingFaultMessage {
        return call(() -> {
            UpgradeRequestDocument u = upgrades.status(r.getRequestId());
            GetUpgradeStatusResponse out = new GetUpgradeStatusResponse();
            out.setUpgradedClass(u.upgradedClass); out.setStatus(u.status);
            return out;
        });
    }

    // ---------- alerts ----------
    @Override
    public SubscribeJourneyAlertsResponse subscribeJourneyAlerts(SubscribeJourneyAlertsRequest r) throws BookingFaultMessage {
        return call(() -> {
            alerts.subscribe(r.getPnr(), true);
            SubscribeJourneyAlertsResponse out = new SubscribeJourneyAlertsResponse();
            out.setStatus("SUBSCRIBED");
            return out;
        });
    }

    @Override
    public UnsubscribeJourneyAlertsResponse unsubscribeJourneyAlerts(UnsubscribeJourneyAlertsRequest r) throws BookingFaultMessage {
        return call(() -> {
            alerts.subscribe(r.getPnr(), false);
            UnsubscribeJourneyAlertsResponse out = new UnsubscribeJourneyAlertsResponse();
            out.setStatus("UNSUBSCRIBED");
            return out;
        });
    }
}
