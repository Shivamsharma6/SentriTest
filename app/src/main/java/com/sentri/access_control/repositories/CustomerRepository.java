package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface CustomerRepository {
    void fetchCustomers(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError);

    void fetchCustomer(String businessId, String customerId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError);

    void fetchNextCustomerId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError);

    void saveCustomer(String businessId, String customerId, Map<String, Object> customerData, Runnable onSuccess, Consumer<Exception> onError);

    void deactivateCustomer(String businessId, String customerId, Runnable onSuccess, Consumer<Exception> onError);

    void removeCustomerShiftAssignment(String businessId,
                                       String customerId,
                                       String shiftId,
                                       String seat,
                                       Timestamp shiftStart,
                                       Timestamp shiftEnd,
                                       Runnable onSuccess,
                                       Consumer<Exception> onError);

    void updateCustomerAfterPayment(String businessId,
                                    String customerId,
                                    String paymentRate,
                                    String lastPaymentDate,
                                    Runnable onSuccess,
                                    Consumer<Exception> onError);

    void appendShiftAssignment(String businessId,
                               String customerId,
                               String shiftId,
                               String seat,
                               Timestamp shiftStart,
                               Timestamp shiftEnd,
                               String paymentRate,
                               String lastPaymentDate,
                               Runnable onSuccess,
                               Consumer<Exception> onError);

    void updateRenewedSubscription(String businessId,
                                   String customerId,
                                   List<Timestamp> newSubscriptionEndDates,
                                   String paymentRate,
                                   Runnable onSuccess,
                                   Consumer<Exception> onError);

    void replaceCurrentShiftAssignments(String businessId,
                                        String customerId,
                                        List<String> shiftIds,
                                        List<String> seats,
                                        List<Timestamp> shiftStarts,
                                        List<Timestamp> shiftEnds,
                                        String paymentRate,
                                        String lastPaymentDate,
                                        Runnable onSuccess,
                                        Consumer<Exception> onError);

    /**
     * Update editable profile fields for an existing customer without touching subscriptions/shifts.
     */
    void updateCustomerProfile(String businessId,
                               String customerId,
                               Map<String, Object> updates,
                               Runnable onSuccess,
                               Consumer<Exception> onError);
}
