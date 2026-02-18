package com.sentri.access_control.utils;

import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;

import java.util.function.Consumer;

/**
 * Helper class for creating comments in Firestore.
 */
public class CommentHelper {
    private final CommentRepository commentRepository;

    public CommentHelper(FirebaseFirestore db) {
        this.commentRepository = new FirestoreCommentRepository(db);
    }

    /**
     * Adds a comment to Firestore with auto-generated ID.
     *
     * @param businessId    Business document ID
     * @param customerId    Customer document ID
     * @param businessPrefix Business prefix for ID generation
     * @param entityType    Entity type (e.g., "payment", "shift", "customer")
     * @param text          Comment text
     * @param createdBy     Email of the user creating the comment
     * @param onSuccess     Callback on success
     * @param onError       Callback on error
     */
    public void addComment(String businessId, String customerId, String businessPrefix,
                          String entityType, String text, String createdBy,
                          Runnable onSuccess, Consumer<Exception> onError) {
        commentRepository.addComment(
                businessId,
                customerId,
                businessPrefix,
                entityType,
                text,
                createdBy,
                onSuccess,
                onError
        );
    }

    /**
     * Simplified version with just success callback.
     */
    public void addComment(String businessId, String customerId, String businessPrefix,
                          String entityType, String text, String createdBy,
                          Runnable onSuccess) {
        addComment(businessId, customerId, businessPrefix, entityType, text, createdBy, 
                  onSuccess, null);
    }
}
