package com.sentri.access_control.repositories;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.utils.FirestoreIdGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreShiftRepository implements ShiftRepository {
    private final FirebaseFirestore db;

    public FirestoreShiftRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchAllShifts(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchActiveShifts(String businessId, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .whereEqualTo("shift_status", true)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchCustomerShiftIds(String businessId, String customerId, Consumer<List<String>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_CUSTOMERS)
                .document(customerId)
                .get()
                .addOnSuccessListener(customerDoc -> onSuccess.accept(readStringList(customerDoc.get("customer_current_shift_id"))))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchCustomerShifts(String businessId, String customerId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .whereEqualTo("shift_customer_id", customerId)
                .orderBy("shift_start_time", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchShiftsByIds(String businessId, List<String> shiftIds, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        if (shiftIds == null || shiftIds.isEmpty()) {
            onSuccess.accept(Collections.emptyList());
            return;
        }

        List<List<String>> chunks = chunkIds(shiftIds, 10);
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (List<String> chunk : chunks) {
            tasks.add(
                    FirestorePaths.business(db, businessId)
                            .collection(FirestorePaths.SUB_SHIFTS)
                            .whereIn(FieldPath.documentId(), chunk)
                            .get()
            );
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<DocumentSnapshot> merged = new ArrayList<>();
                    for (Object result : results) {
                        if (result instanceof QuerySnapshot) {
                            merged.addAll(((QuerySnapshot) result).getDocuments());
                        }
                    }
                    onSuccess.accept(merged);
                })
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchActiveUnallocatedShifts(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .whereEqualTo("shift_seat", "unallocated")
                .whereEqualTo("shift_status", true)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchNextShiftId(String businessId, String businessPrefix, Consumer<String> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(
                        FirestoreIdGenerator.generateNextId(businessPrefix, "SHIFT", "shift_id", snapshot)
                ))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void createShift(String businessId, String shiftId, Map<String, Object> shiftData, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .document(shiftId)
                .set(shiftData)
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
    public void extendShifts(String businessId,
                             Map<String, Timestamp> newShiftEndTimes,
                             String paymentRate,
                             Runnable onSuccess,
                             Consumer<Exception> onError) {
        if (newShiftEndTimes == null || newShiftEndTimes.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        WriteBatch batch = db.batch();
        DocumentReference businessRef = FirestorePaths.business(db, businessId);
        for (Map.Entry<String, Timestamp> entry : newShiftEndTimes.entrySet()) {
            String shiftId = entry.getKey();
            Timestamp newEnd = entry.getValue();
            if (shiftId == null || shiftId.trim().isEmpty() || newEnd == null) {
                continue;
            }
            DocumentReference shiftRef = businessRef.collection(FirestorePaths.SUB_SHIFTS).document(shiftId);
            batch.update(
                    shiftRef,
                    "shift_end_time", newEnd,
                    "shift_payment_rate", paymentRate,
                    "shift_status", true
            );
        }

        batch.commit()
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
    public void markShiftInactive(String businessId, String shiftId, Runnable onSuccess, Consumer<Exception> onError) {
        if (shiftId == null || shiftId.trim().isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_SHIFTS)
                .document(shiftId)
                .update("shift_status", false)
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
    public void markShiftsInactive(String businessId, List<String> shiftIds, Runnable onSuccess, Consumer<Exception> onError) {
        if (shiftIds == null || shiftIds.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        WriteBatch batch = db.batch();
        DocumentReference businessRef = FirestorePaths.business(db, businessId);
        for (String shiftId : shiftIds) {
            if (shiftId == null || shiftId.trim().isEmpty()) {
                continue;
            }
            DocumentReference shiftRef = businessRef.collection(FirestorePaths.SUB_SHIFTS).document(shiftId);
            batch.update(shiftRef, "shift_status", false);
        }

        batch.commit()
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

    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?>)) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private List<List<String>> chunkIds(List<String> ids, int chunkSize) {
        List<List<String>> chunks = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return chunks;
        }
        for (int i = 0; i < ids.size(); i += chunkSize) {
            int end = Math.min(ids.size(), i + chunkSize);
            chunks.add(new ArrayList<>(ids.subList(i, end)));
        }
        return chunks;
    }
}
