package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.data.FirestorePaths;

import java.util.function.Consumer;

public class FirestoreHistoryRepository implements HistoryRepository {
    private final FirebaseFirestore db;

    public FirestoreHistoryRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void fetchPagedRecords(String businessId,
                                  String collection,
                                  String filterField,
                                  String filterValue,
                                  String orderField,
                                  DocumentSnapshot lastDoc,
                                  int pageSize,
                                  Consumer<QuerySnapshot> onSuccess,
                                  Consumer<Exception> onError) {
        Query query = FirestorePaths.business(db, businessId)
                .collection(collection)
                .whereEqualTo(filterField, filterValue)
                .orderBy(orderField, Query.Direction.DESCENDING)
                .limit(pageSize);

        if (lastDoc != null) {
            query = query.startAfter(lastDoc);
        }

        query.get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchPagedComments(String businessId,
                                   String customerId,
                                   String entityType,
                                   DocumentSnapshot lastDoc,
                                   int pageSize,
                                   Consumer<QuerySnapshot> onSuccess,
                                   Consumer<Exception> onError) {
        Query query = FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_COMMENTS)
                .whereEqualTo("comment_customer_id", customerId)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(pageSize);

        if (entityType != null) {
            query = query.whereEqualTo("comment_entity_type", entityType);
        }
        if (lastDoc != null) {
            query = query.startAfter(lastDoc);
        }

        query.get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }
}
