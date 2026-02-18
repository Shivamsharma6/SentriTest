package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.sentri.access_control.data.FirestorePaths;

import java.util.Map;
import java.util.function.Consumer;

public class FirestoreUserRepository implements UserRepository {
    private final FirebaseFirestore db;

    public FirestoreUserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchUserDocument(String email, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError) {
        FirestorePaths.user(db, email)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void updateLastLogin(String email, Runnable onSuccess, Consumer<Exception> onError) {
        FirestorePaths.user(db, email)
                .update(
                        "last_login", FieldValue.serverTimestamp(),
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
    public void createUserDocument(String email,
                                   Map<String, Object> data,
                                   Runnable onSuccess,
                                   Consumer<Exception> onError) {
        FirestorePaths.user(db, email)
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
    public void mergeUserDocument(String email,
                                  Map<String, Object> data,
                                  Runnable onSuccess,
                                  Consumer<Exception> onError) {
        FirestorePaths.user(db, email)
                .set(data, SetOptions.merge())
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
    public void appendBusinessAccess(String email,
                                     String businessId,
                                     String businessName,
                                     String accessLevel,
                                     String grantedAt,
                                     boolean activeStatus,
                                     Runnable onSuccess,
                                     Consumer<Exception> onError) {
        FirestorePaths.user(db, email)
                .update(
                        "user_business_access_id", FieldValue.arrayUnion(businessId),
                        "user_business_access_levels", FieldValue.arrayUnion(accessLevel),
                        "user_business_access_name", FieldValue.arrayUnion(businessName),
                        "user_business_granted", FieldValue.arrayUnion(grantedAt),
                        "user_business_status", FieldValue.arrayUnion(activeStatus),
                        FirestorePaths.FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                )
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
