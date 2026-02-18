package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface BusinessUserRepository {
    void fetchBusinessUsers(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchBusinessUser(String businessId, String userId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError);

    void saveBusinessUser(String businessId,
                          String userId,
                          Map<String, Object> data,
                          Runnable onSuccess,
                          Consumer<Exception> onError);

    void updateBusinessUser(String businessId,
                            String userId,
                            Map<String, Object> updates,
                            Runnable onSuccess,
                            Consumer<Exception> onError);
}
