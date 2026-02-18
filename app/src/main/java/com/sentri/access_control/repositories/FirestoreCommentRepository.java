package com.sentri.access_control.repositories;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.data.FirestorePaths;
import com.sentri.access_control.utils.FirestoreIdGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FirestoreCommentRepository implements CommentRepository {
    private static final String ENTITY_COM = "COM";

    private final FirebaseFirestore db;

    public FirestoreCommentRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void addComment(String businessId,
                           String customerId,
                           String businessPrefix,
                           String entityType,
                           String text,
                           String createdBy,
                           Runnable onSuccess,
                           Consumer<Exception> onError) {
        FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_COMMENTS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String commentId = FirestoreIdGenerator.generateNextId(
                            businessPrefix,
                            ENTITY_COM,
                            "comment_id",
                            snapshot
                    );

                    Map<String, Object> comment = new HashMap<>();
                    comment.put("comment_business_id", businessId);
                    comment.put("comment_customer_id", customerId);
                    comment.put("comment_entity_type", entityType);
                    comment.put("comment_text", text);
                    comment.put("comment_id", commentId);
                    comment.put(FirestorePaths.FIELD_CREATED_AT, FieldValue.serverTimestamp());
                    comment.put("created_by", createdBy);

                    FirestorePaths.business(db, businessId)
                            .collection(FirestorePaths.SUB_COMMENTS)
                            .document(commentId)
                            .set(comment)
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
                })
                .addOnFailureListener(e -> {
                    if (onError != null) {
                        onError.accept(e);
                    }
                });
    }

    @Override
    public void fetchRecentComments(String businessId, int limit, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError) {
        baseCommentQuery(businessId, limit)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchCommentsByCustomer(String businessId,
                                        String customerId,
                                        int limit,
                                        Consumer<QuerySnapshot> onSuccess,
                                        Consumer<Exception> onError) {
        baseCommentQuery(businessId, limit)
                .whereEqualTo("comment_customer_id", customerId)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    @Override
    public void fetchCommentsByCreator(String businessId,
                                       String createdBy,
                                       int limit,
                                       Consumer<QuerySnapshot> onSuccess,
                                       Consumer<Exception> onError) {
        baseCommentQuery(businessId, limit)
                .whereEqualTo("created_by", createdBy)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(onError::accept);
    }

    private Query baseCommentQuery(String businessId, int limit) {
        Query query = FirestorePaths.business(db, businessId)
                .collection(FirestorePaths.SUB_COMMENTS)
                .orderBy(FirestorePaths.FIELD_CREATED_AT, Query.Direction.DESCENDING);
        if (limit > 0) {
            query = query.limit(limit);
        }
        return query;
    }
}
