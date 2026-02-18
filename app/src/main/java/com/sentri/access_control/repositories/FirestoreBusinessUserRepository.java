package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.data.FirestorePaths;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreBusinessUserRepository implements BusinessUserRepository {
    private final FirebaseFirestore db;

    public FirestoreBusinessUserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchBusinessUsers(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_USERS)
                .orderBy("user_email")
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(snapshot.getDocuments()))
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchBusinessUser(String businessId, String userId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void saveBusinessUser(String businessId,
                                 String userId,
                                 Map<String, Object> data,
                                 Runnable onSuccess,
                                 Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_USERS)
                .document(userId)
                .set(data)
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
    public void updateBusinessUser(String businessId,
                                   String userId,
                                   Map<String, Object> updates,
                                   Runnable onSuccess,
                                   Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_BUSINESS_USERS)
                .document(userId)
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
