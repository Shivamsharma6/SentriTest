package com.sentri.access_control;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
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

public class UserNotifications extends AppCompatActivity {

    private static final int MAX_NOTIFICATIONS = 50;

    private NotificationAdapter adapter;
    private NotificationRepository notificationRepository;

    private String businessId;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_notifications);

        PrefsManager prefsManager = new PrefsManager(this);
        businessId = prefsManager.getCurrentBizId();

        String userName = getIntent().getStringExtra("userName");
        userEmail = getIntent().getStringExtra("email");
        String widgetText = getIntent().getStringExtra("widgetText");
        String cardNumber = getIntent().getStringExtra("cardNumber");
        boolean isActive = getIntent().getBooleanExtra("isActive", false);

        if (userEmail == null || userEmail.trim().isEmpty()) {
            userEmail = prefsManager.getUserEmail();
        }

        if (businessId == null || businessId.trim().isEmpty() || userEmail == null || userEmail.trim().isEmpty()) {
            Toast.makeText(this, "Missing business/user details", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        CommentRepository commentRepository = new FirestoreCommentRepository(FirebaseFirestore.getInstance());
        notificationRepository = new FirestoreNotificationRepository(commentRepository);

        ImageView backButton = findViewById(R.id.backButton);
        ImageView ivUserImage = findViewById(R.id.ivUserImage);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvEmail = findViewById(R.id.tvEmail);
        TextView tvWidget = findViewById(R.id.tvWidget);
        TextView tvCardNumber = findViewById(R.id.tvCardNumber);
        Switch swActive = findViewById(R.id.swActive);

        backButton.setOnClickListener(v -> finish());

        tvTitle.setText(userName != null ? userName : "User");
        tvEmail.setText(userEmail);
        tvWidget.setText(widgetText != null ? widgetText : "");
        tvCardNumber.setText(cardNumber != null ? cardNumber : "");
        swActive.setChecked(isActive);
        ivUserImage.setImageResource(R.drawable.ic_user);

        RecyclerView recyclerNotifications = findViewById(R.id.recyclerNotifications);
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationAdapter(new ArrayList<>());
        recyclerNotifications.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        notificationRepository.fetchUserNotifications(
                businessId,
                userEmail,
                MAX_NOTIFICATIONS,
                this::onNotificationsLoaded,
                e -> Toast.makeText(this, "Failed to load notifications: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void onNotificationsLoaded(List<NotificationItem> notifications) {
        adapter.updateList(notifications != null ? notifications : new ArrayList<>());
    }
}
