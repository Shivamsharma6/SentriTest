package com.sentri.access_control.repositories;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.utils.FirestoreIdGenerator;

import java.util.Map;
import java.util.function.Consumer;

public class FirestorePaymentRepository implements PaymentRepository {
    private static final String ENTITY_PAY = "PAY";

    private final FirebaseFirestore db;

    public FirestorePaymentRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchBusinessPrefix(String businessId, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .get()
                .addOnSuccessListener(doc -> onSuccess.accept(doc.getString(FirestorePaths.FIELD_BUSINESS_PREFIX)))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchNextPaymentId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_PAYMENTS)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(
                        FirestoreIdGenerator.generateNextId(businessPrefix, ENTITY_PAY, "payment_id", snapshot)
                ))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void createPayment(String businessId, String paymentId, Map<String, Object> paymentData, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_PAYMENTS)
                .document(paymentId)
                .set(paymentData)
                .addOnSuccessListener(ignored -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    if (onError != null) {
                        onError.accept(e);
                    }
                });
    }

    @Override
    public void fetchCustomerPayments(String businessId, String customerId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_PAYMENTS)
                .whereEqualTo("payment_customer_id", customerId)
                .orderBy(FirestorePaths.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchBusinessPayments(String businessId,
                                      Timestamp startInclusive,
                                      Timestamp endInclusive,
                                      Consumer<QuerySnapshot> onSuccess,
                                      Consumer<Exception> onError) {
        Query query = FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_PAYMENTS);

        if (startInclusive != null && endInclusive != null) {
            query = query
                    .whereGreaterThanOrEqualTo(FirestorePaths.FIELD_CREATED_AT, startInclusive)
                    .whereLessThanOrEqualTo(FirestorePaths.FIELD_CREATED_AT, endInclusive);
        }

        query.orderBy(FirestorePaths.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }
}
