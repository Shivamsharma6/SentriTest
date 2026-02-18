package com.sentri.access_control.repositories;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.utils.FirestoreIdGenerator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreLeaveRepository implements LeaveRepository {
    private static final String ENTITY_LEAVES = "LEAVES";

    private final FirebaseFirestore db;

    public FirestoreLeaveRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchNextLeaveId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_LEAVES)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(
                        FirestoreIdGenerator.generateNextId(businessPrefix, ENTITY_LEAVES, "leaves_id", snapshot)
                ))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void createLeave(String businessId, String leaveId, Map<String, Object> leaveData, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_LEAVES)
                .document(leaveId)
                .set(leaveData)
                .addOnSuccessListener(ignored -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(exception -> {
                    if (onError != null) {
                        onError.accept(exception);
                    }
                });
    }

    @Override
    public void applyLeaveAdjustments(String businessId,
                                      String customerId,
                                      int leaveDays,
                                      Runnable onSuccess,
                                      Consumer<Exception> onError) {
        if (leaveDays <= 0) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        DocumentReference businessRef = FirestorePaths.business(db, businessId);
        Task<QuerySnapshot> shiftsTask = businessRef
                .collection(FirestorePaths.SUB_SHIFTS)
                .whereEqualTo("shift_customer_id", customerId)
                .get();
        Task<DocumentSnapshot> customerTask = businessRef
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .get();

        Tasks.whenAllSuccess(shiftsTask, customerTask)
                .addOnSuccessListener(results -> {
                    QuerySnapshot shiftsSnapshot = (QuerySnapshot) results.get(0);
                    DocumentSnapshot customerSnapshot = (DocumentSnapshot) results.get(1);

                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot shiftDoc : shiftsSnapshot.getDocuments()) {
                        Timestamp oldShiftEnd = shiftDoc.getTimestamp("shift_end_time");
                        if (oldShiftEnd == null) {
                            continue;
                        }
                        batch.update(
                                shiftDoc.getReference(),
                                "shift_end_time",
                                addDays(oldShiftEnd, leaveDays)
                        );
                    }

                    List<Timestamp> extendedEndDates = extendTimestampList(
                            customerSnapshot.get("customer_subscription_end_date"),
                            leaveDays
                    );

                    Map<String, Object> customerUpdates = new HashMap<>();
                    customerUpdates.put("customer_subscription_end_date", extendedEndDates);
                    customerUpdates.put(FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp());

                    DocumentReference customerRef = businessRef
                            .collection(FirestorePaths.SUB_CUSTOMERS)
                            .document(customerId);
                    batch.update(customerRef, customerUpdates);

                    batch.commit()
                            .addOnSuccessListener(ignored -> {
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            })
                            .addOnFailureListener(exception -> {
                                if (onError != null) {
                                    onError.accept(exception);
                                }
                            });
                })
                .addOnFailureListener(exception -> {
                    if (onError != null) {
                        onError.accept(exception);
                    }
                });
    }

    private List<Timestamp> extendTimestampList(Object rawTimestamps, int daysToAdd) {
        List<Timestamp> output = new ArrayList<>();
        if (!(rawTimestamps instanceof List<?>)) {
            return output;
        }

        for (Object value : (List<?>) rawTimestamps) {
            if (value instanceof Timestamp) {
                output.add(addDays((Timestamp) value, daysToAdd));
            }
        }
        return output;
    }

    private Timestamp addDays(Timestamp timestamp, int daysToAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp.toDate());
        calendar.add(Calendar.DATE, daysToAdd);
        return new Timestamp(calendar.getTime());
    }
}
