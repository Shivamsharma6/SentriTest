package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ShiftRepository {
    void fetchAllShifts(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError);

    void fetchActiveShifts(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError);

    void fetchCustomerShiftIds(String businessId, String customerId, Consumer<List<String>> onSuccess, Consumer<Exception> onError);

    void fetchCustomerShifts(String businessId, String customerId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchShiftsByIds(String businessId, List<String> shiftIds, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchActiveUnallocatedShifts(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchNextShiftId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError);

    void createShift(String businessId, String shiftId, Map<String, Object> shiftData, Runnable onSuccess, Consumer<Exception> onError);

    void extendShifts(String businessId,
                      Map<String, Timestamp> newShiftEndTimes,
                      String paymentRate,
                      Runnable onSuccess,
                      Consumer<Exception> onError);

    void markShiftInactive(String businessId, String shiftId, Runnable onSuccess, Consumer<Exception> onError);

    void markShiftsInactive(String businessId, List<String> shiftIds, Runnable onSuccess, Consumer<Exception> onError);
}
