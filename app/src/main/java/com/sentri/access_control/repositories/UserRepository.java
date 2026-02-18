package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;
import java.util.function.Consumer;

public interface UserRepository {
    void fetchUserDocument(String email, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError);

    void updateLastLogin(String email, Runnable onSuccess, Consumer<Exception> onError);

    void createUserDocument(String email,
                            Map<String, Object> data,
                            Runnable onSuccess,
                            Consumer<Exception> onError);

    void mergeUserDocument(String email,
                           Map<String, Object> data,
                           Runnable onSuccess,
                           Consumer<Exception> onError);

    void appendBusinessAccess(String email,
                              String businessId,
                              String businessName,
                              String accessLevel,
                              String grantedAt,
                              boolean activeStatus,
                              Runnable onSuccess,
                              Consumer<Exception> onError);
}
