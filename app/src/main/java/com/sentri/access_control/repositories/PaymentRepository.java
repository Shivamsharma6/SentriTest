package com.sentri.access_control.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Map;
import java.util.function.Consumer;

public interface PaymentRepository {
    void fetchBusinessPrefix(String businessId, Consumer<String> onSuccess, Consumer<Exception> onError);

    void fetchNextPaymentId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError);

    void createPayment(String businessId, String paymentId, Map<String, Object> paymentData, Runnable onSuccess, Consumer<Exception> onError);

    void fetchCustomerPayments(String businessId, String customerId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError);

    void fetchBusinessPayments(String businessId,
                              Timestamp startInclusive,
                              Timestamp endInclusive,
                              Consumer<QuerySnapshot> onSuccess,
                              Consumer<Exception> onError);
}
