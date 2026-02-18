package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.utils.FirestoreIdGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreCustomerRepository implements CustomerRepository {
    private final FirebaseFirestore db;

    public FirestoreCustomerRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchCustomers(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchCustomer(String businessId, String customerId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchNextCustomerId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(
                        FirestoreIdGenerator.generateNextCustomerId(businessPrefix, snapshot)
                ))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void saveCustomer(String businessId, String customerId, Map<String, Object> customerData, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .set(customerData)
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
    public void deactivateCustomer(String businessId, String customerId, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(
                        "customer_status", false,
                        "customer_current_payment_rate", "",
                        "customer_current_shift_id", Collections.emptyList(),
                        "customer_current_seat", Collections.emptyList(),
                        "customer_subscription_start_date", Collections.emptyList(),
                        "customer_subscription_end_date", Collections.emptyList(),
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
    public void removeCustomerShiftAssignment(String businessId,
                                              String customerId,
                                              String shiftId,
                                              String seat,
                                              Timestamp shiftStart,
                                              Timestamp shiftEnd,
                                              Runnable onSuccess,
                                              Consumer<Exception> onError) {
        if (shiftId == null || shiftId.trim().isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("customer_current_shift_id", FieldValue.arrayRemove(shiftId));
        if (seat != null && !seat.trim().isEmpty()) {
            updates.put("customer_current_seat", FieldValue.arrayRemove(seat));
        }
        if (shiftStart != null) {
            updates.put("customer_subscription_start_date", FieldValue.arrayRemove(shiftStart));
        }
        if (shiftEnd != null) {
            updates.put("customer_subscription_end_date", FieldValue.arrayRemove(shiftEnd));
        }
        updates.put(FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp());

        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(updates)
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
    public void updateCustomerAfterPayment(String businessId,
                                           String customerId,
                                           String paymentRate,
                                           String lastPaymentDate,
                                           Runnable onSuccess,
                                           Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(
                        "customer_current_payment_rate", paymentRate,
                        "customer_last_payment_date", lastPaymentDate,
                        "customer_status", true,
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
    public void appendShiftAssignment(String businessId,
                                      String customerId,
                                      String shiftId,
                                      String seat,
                                      Timestamp shiftStart,
                                      Timestamp shiftEnd,
                                      String paymentRate,
                                      String lastPaymentDate,
                                      Runnable onSuccess,
                                      Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(
                        "customer_status", true,
                        "customer_current_payment_rate", paymentRate,
                        "customer_last_payment_date", lastPaymentDate,
                        "customer_current_shift_id", FieldValue.arrayUnion(shiftId),
                        "customer_current_seat", FieldValue.arrayUnion(seat),
                        "customer_subscription_start_date", FieldValue.arrayUnion(shiftStart),
                        "customer_subscription_end_date", FieldValue.arrayUnion(shiftEnd),
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
    public void updateRenewedSubscription(String businessId,
                                          String customerId,
                                          List<Timestamp> newSubscriptionEndDates,
                                          String paymentRate,
                                          Runnable onSuccess,
                                          Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(
                        "customer_subscription_end_date", newSubscriptionEndDates,
                        "customer_current_payment_rate", paymentRate,
                        "customer_status", true,
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
    public void replaceCurrentShiftAssignments(String businessId,
                                               String customerId,
                                               List<String> shiftIds,
                                               List<String> seats,
                                               List<Timestamp> shiftStarts,
                                               List<Timestamp> shiftEnds,
                                               String paymentRate,
                                               String lastPaymentDate,
                                               Runnable onSuccess,
                                               Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(
                        "customer_status", true,
                        "customer_current_payment_rate", paymentRate,
                        "customer_last_payment_date", lastPaymentDate,
                        "customer_current_shift_id", shiftIds != null ? shiftIds : Collections.emptyList(),
                        "customer_current_seat", seats != null ? seats : Collections.emptyList(),
                        "customer_subscription_start_date", shiftStarts != null ? shiftStarts : Collections.emptyList(),
                        "customer_subscription_end_date", shiftEnds != null ? shiftEnds : Collections.emptyList(),
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
    public void updateCustomerProfile(String businessId,
                                      String customerId,
                                      Map<String, Object> updates,
                                      Runnable onSuccess,
                                      Consumer<Exception> onError) {
        if (updates == null || updates.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        updates.put(FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp());

        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .update(updates)
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
}
