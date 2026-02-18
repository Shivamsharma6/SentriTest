package com.sentri.access_control.repositories;

import android.text.format.DateFormat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.sentri.access_control.models.NotificationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class FirestoreNotificationRepository implements NotificationRepository {
    private final CommentRepository commentRepository;

    public FirestoreNotificationRepository(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Override
    public void fetchBusinessNotifications(String businessId, int limit, Consumer<List<NotificationItem>> onSuccess, Consumer<Exception> onError) {
        commentRepository.fetchRecentComments(
                businessId,
                limit,
                snapshot -> onSuccess.accept(mapToNotifications(snapshot.getDocuments())),
                onError
        );
    }

    @Override
    public void fetchCustomerNotifications(String businessId,
                                           String customerId,
                                           int limit,
                                           Consumer<List<NotificationItem>> onSuccess,
                                           Consumer<Exception> onError) {
        commentRepository.fetchCommentsByCustomer(
                businessId,
                customerId,
                limit,
                snapshot -> onSuccess.accept(mapToNotifications(snapshot.getDocuments())),
                onError
        );
    }

    @Override
    public void fetchUserNotifications(String businessId,
                                       String userEmail,
                                       int limit,
                                       Consumer<List<NotificationItem>> onSuccess,
                                       Consumer<Exception> onError) {
        commentRepository.fetchCommentsByCreator(
                businessId,
                userEmail,
                limit,
                snapshot -> onSuccess.accept(mapToNotifications(snapshot.getDocuments())),
                onError
        );
    }

    private List<NotificationItem> mapToNotifications(List<DocumentSnapshot> docs) {
        List<NotificationItem> items = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            String entityType = normalize(doc.getString("comment_entity_type"));
            String message = normalize(doc.getString("comment_text"));
            String creator = normalize(doc.getString("created_by"));
            String customer = normalize(doc.getString("comment_customer_id"));

            Timestamp createdAt = doc.getTimestamp("created_at");
            String date = createdAt != null
                    ? DateFormat.format("d MMM, yyyy hh:mm a", createdAt.toDate()).toString()
                    : "-";

            StringBuilder textBuilder = new StringBuilder();
            if (!entityType.isEmpty()) {
                textBuilder.append("[").append(entityType.toUpperCase(Locale.US)).append("] ");
            }
            if (!message.isEmpty()) {
                textBuilder.append(message);
            } else {
                textBuilder.append("Notification");
            }

            if (!customer.isEmpty() || !creator.isEmpty()) {
                textBuilder.append(" (");
                if (!customer.isEmpty()) {
                    textBuilder.append(customer);
                }
                if (!creator.isEmpty()) {
                    if (!customer.isEmpty()) {
                        textBuilder.append(" - ");
                    }
                    textBuilder.append(creator);
                }
                textBuilder.append(")");
            }

            items.add(new NotificationItem(textBuilder.toString(), date));
        }
        return items;
    }

    private String normalize(String value) {
        return value != null ? value.trim() : "";
    }
}
