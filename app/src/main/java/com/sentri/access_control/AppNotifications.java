package com.sentri.access_control;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.adapters.NotificationAdapter;
import com.sentri.access_control.models.NotificationItem;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;
import com.sentri.access_control.repositories.FirestoreNotificationRepository;
import com.sentri.access_control.repositories.NotificationRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class AppNotifications extends AppCompatActivity {

    private static final int MAX_NOTIFICATIONS = 50;

    private NotificationAdapter adapter;
    private NotificationRepository notificationRepository;
    private String businessId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_notifications);

        businessId = new PrefsManager(this).getCurrentBizId();
        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        CommentRepository commentRepository = new FirestoreCommentRepository(FirebaseFirestore.getInstance());
        notificationRepository = new FirestoreNotificationRepository(commentRepository);

        RecyclerView recyclerView = findViewById(R.id.rvNotifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        ImageView back = findViewById(R.id.ivBack);
        back.setOnClickListener(v -> finish());

        loadNotifications();
    }

    private void loadNotifications() {
        notificationRepository.fetchBusinessNotifications(
                businessId,
                MAX_NOTIFICATIONS,
                this::onNotificationsLoaded,
                e -> Toast.makeText(this, "Failed to load notifications: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void onNotificationsLoaded(List<NotificationItem> notifications) {
        adapter.updateList(notifications != null ? notifications : new ArrayList<>());
    }
}
