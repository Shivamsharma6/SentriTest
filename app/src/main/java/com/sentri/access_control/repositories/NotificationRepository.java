package com.sentri.access_control.repositories;

import com.sentri.access_control.models.NotificationItem;

import java.util.List;
import java.util.function.Consumer;

public interface NotificationRepository {
    void fetchBusinessNotifications(String businessId, int limit, Consumer<List<NotificationItem>> onSuccess, Consumer<Exception> onError);

    void fetchCustomerNotifications(String businessId,
                                    String customerId,
                                    int limit,
                                    Consumer<List<NotificationItem>> onSuccess,
                                    Consumer<Exception> onError);

    void fetchUserNotifications(String businessId,
                                String userEmail,
                                int limit,
                                Consumer<List<NotificationItem>> onSuccess,
                                Consumer<Exception> onError);
}
