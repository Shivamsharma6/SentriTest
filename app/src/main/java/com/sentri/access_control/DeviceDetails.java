package com.sentri.access_control;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.DeviceRepository;
import com.sentri.access_control.repositories.FirestoreDeviceRepository;

import java.text.DateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DeviceDetails extends AppCompatActivity {
    private static final long ONLINE_THRESHOLD_MS = 20L * 60L * 1000L;

    private ImageView ivBack;
    private TextView tvMac;
    private TextView tvName;
    private TextView tvSsid;
    private TextView tvStatus;
    private TextView tvLastOnline;
    private TextView tvUpdatedAt;
    private TextView tvUpdatedBy;

    private DeviceRepository deviceRepository;
    private String businessId;
    private String deviceDocId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);

        businessId = getIntent().getStringExtra("businessDocId");
        deviceDocId = getIntent().getStringExtra("deviceDocId");
        
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(deviceDocId)) {
            Toast.makeText(this, "Missing device reference.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        deviceRepository = new FirestoreDeviceRepository(FirebaseFirestore.getInstance());
        bindViews();
        setupActions();
        loadDevice();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        tvMac = findViewById(R.id.tvMac);
        tvName = findViewById(R.id.tvName);
        tvSsid = findViewById(R.id.tvSsid);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastOnline = findViewById(R.id.tvLastOnline);
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt);
        tvUpdatedBy = findViewById(R.id.tvUpdatedBy);
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> onBackPressed());
    }

    private void loadDevice() {
        deviceRepository.fetchDevice(
                businessId,
                deviceDocId,
                doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Device not found.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    String mac = doc.getString("device_mac");
                    if (mac == null || mac.trim().isEmpty()) {
                        mac = deviceDocId;
                    }
                    
                    String name = doc.getString("device_name");
                    if (name == null || name.trim().isEmpty()) {
                        name = deviceDocId;
                    }
                    
                    String ssid = doc.getString("device_network_ssid");
                    if (ssid == null || ssid.trim().isEmpty()) {
                        ssid = "-";
                    }
                    
                    String status = "Offline";
                    Timestamp lastOnline = doc.getTimestamp("device_last_online");
                    if (lastOnline != null) {
                        long ageMs = System.currentTimeMillis() - lastOnline.toDate().getTime();
                        if (ageMs >= 0 && ageMs <= ONLINE_THRESHOLD_MS) {
                            status = "Online";
                        }
                    }
                    
                    String updatedAt = formatTimestamp(doc.getTimestamp("updated_at"));
                    String updatedBy = doc.getString("updated_by");
                    if (updatedBy == null || updatedBy.trim().isEmpty()) {
                        updatedBy = "-";
                    }

                    tvMac.setText(mac);
                    tvName.setText(name);
                    tvSsid.setText(ssid);
                    tvStatus.setText(status);
                    tvLastOnline.setText(formatTimestamp(lastOnline));
                    tvUpdatedAt.setText(updatedAt);
                    tvUpdatedBy.setText(updatedBy);
                },
                exception -> {
                    Toast.makeText(this, "Failed to load device: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
        );
    }

    private String formatTimestamp(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
        ).format(timestamp.toDate());
    }
}