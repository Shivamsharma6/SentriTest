package com.sentri.access_control.repositories;

import com.google.firebase.firestore.QuerySnapshot;

import java.util.function.Consumer;

public interface CommentRepository {
    void addComment(String businessId,
                    String customerId,
                    String businessPrefix,
                    String entityType,
                    String text,
                    String createdBy,
                    Runnable onSuccess,
                    Consumer<Exception> onError);

    void fetchRecentComments(String businessId, int limit, Consumer<QuerySnapshot> onSuccess, Consumer<Exception> onError);

    void fetchCommentsByCustomer(String businessId,
                                 String customerId,
                                 int limit,
                                 Consumer<QuerySnapshot> onSuccess,
                                 Consumer<Exception> onError);

    void fetchCommentsByCreator(String businessId,
                                String createdBy,
                                int limit,
                                Consumer<QuerySnapshot> onSuccess,
                                Consumer<Exception> onError);
}
