package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.sentri.access_control.models.BusinessConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface BusinessRepository {
    void fetchBusinessPrefix(String businessId, Consumer<String> onSuccess, Consumer<Exception> onError);

    void fetchBusinessConfig(String businessId, Consumer<BusinessConfig> onSuccess, Consumer<Exception> onError);

    void fetchActiveCustomers(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchNextBusinessId(int year, Consumer<String> onSuccess, Consumer<Exception> onError);

    void createBusiness(String businessId, Map<String, Object> businessData, Runnable onSuccess, Consumer<Exception> onError);

    void initializeBusinessCollections(String businessId, Runnable onSuccess, Consumer<Exception> onError);
}
