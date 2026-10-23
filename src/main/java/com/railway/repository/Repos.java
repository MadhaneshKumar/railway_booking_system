package com.railway.repository;

import com.railway.document.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.*;

public final class Repos {
    private Repos() {}

    public interface TrainRepo extends MongoRepository<TrainDocument, String> {
        Optional<TrainDocument> findByTrainNumber(String trainNumber);
    }
    public interface ScheduleRepo extends MongoRepository<ScheduleDocument, String> {
        Optional<ScheduleDocument> findByTrainNumber(String trainNumber);
    }
    public interface BookingRepo extends MongoRepository<BookingDocument, String> {
        Optional<BookingDocument> findByPnr(String pnr);
        List<BookingDocument> findByBookingStatusAndExpiresAtBefore(String status, Instant before);
        List<BookingDocument> findByTrainNumberAndTravelClassAndBookingStatusOrderByCreatedAtAsc(String train, String cls, String status);
        List<BookingDocument> findTop50ByUserIdOrderByCreatedAtDesc(String userId);
        long countByTrainNumberAndTravelClassAndBookingStatus(String train, String cls, String status);
        List<BookingDocument> findByBookingStatusAndAlertsSubscribedAndTravelDateAndReminderSent(String status, boolean subscribed, String date, boolean reminderSent);
        List<BookingDocument> findByTrainNumberAndTravelDateAndBookingStatusAndAlertsSubscribed(String train, String date, String status, boolean subscribed);
    }
    public interface UserRepo extends MongoRepository<UserDocument, String> {
        Optional<UserDocument> findByUserId(String userId);
    }
    public interface FoodMenuRepo extends MongoRepository<FoodMenuDocument, String> {
        Optional<FoodMenuDocument> findByStationCode(String stationCode);
    }
    public interface FoodOrderRepo extends MongoRepository<FoodOrderDocument, String> {
        Optional<FoodOrderDocument> findByOrderId(String orderId);
    }
    public interface LocationRepo extends MongoRepository<TrainLocationDocument, String> {
        Optional<TrainLocationDocument> findByTrainNumber(String trainNumber);
    }
    public interface RefundRepo extends MongoRepository<RefundTransactionDocument, String> {}
    public interface UpgradeRepo extends MongoRepository<UpgradeRequestDocument, String> {
        Optional<UpgradeRequestDocument> findByRequestId(String requestId);
    }
    public interface NotificationLogRepo extends MongoRepository<NotificationLogDocument, String> {}
}
