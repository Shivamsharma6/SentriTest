package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.function.Consumer;

public interface HistoryRepository {
    void fetchPagedRecords(String businessId,
                           String collection,
                           String filterField,
                           String filterValue,
                           String orderField,
                           DocumentSnapshot lastDoc,
                           int pageSize,
                           Consumer<QuerySnapshot> onSuccess,
                           Consumer<Exception> onError);

    void fetchPagedComments(String businessId,
                            String customerId,
                            String entityType,
                            DocumentSnapshot lastDoc,
                            int pageSize,
                            Consumer<QuerySnapshot> onSuccess,
                            Consumer<Exception> onError);
}
