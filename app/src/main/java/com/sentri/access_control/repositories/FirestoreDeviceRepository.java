package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.data.FirestorePaths;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreDeviceRepository implements DeviceRepository {
    private final FirebaseFirestore db;

    public FirestoreDeviceRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchDevices(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_DEVICES)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchDevice(String businessId, String deviceId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_DEVICES)
                .document(deviceId)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void updateDevice(String businessId,
                             String deviceId,
                             Map<String, Object> updates,
                             Runnable onSuccess,
                             Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_DEVICES)
                .document(deviceId)
                .update(updates)
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
}
