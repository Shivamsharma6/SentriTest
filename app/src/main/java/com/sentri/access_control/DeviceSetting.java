package com.sentri.access_control;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.DeviceRepository;
import com.sentri.access_control.repositories.FirestoreDeviceRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DeviceSetting extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvMac;
    private TextView tvLastOnline;
    private TextView tvUpdatedAt;
    private TextView tvUpdatedBy;
    private TextView tvRoleBadge;
    private TextInputEditText etName;
    private TextInputEditText etSsid;
    private TextInputEditText etPassword;
    private Switch swStatus;
    private Button btnEditSave;

    private DeviceRepository deviceRepository;
    private String businessId;
    private String deviceDocId;
    private boolean canEdit = false;
    private boolean editing = false;
    private String currentUpdatedBy = "";
    private Timestamp currentUpdatedAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_setting);

        businessId = getIntent().getStringExtra("businessDocId");
        deviceDocId = getIntent().getStringExtra("deviceDocId");
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(deviceDocId)) {
            Toast.makeText(this, "Missing device reference.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        PrefsManager prefsManager = new PrefsManager(this);
        canEdit = "admin".equalsIgnoreCase(prefsManager.getCurrentBizAccessLevel());
        currentUpdatedBy = prefsManager.getUserEmail();

        deviceRepository = new FirestoreDeviceRepository(FirebaseFirestore.getInstance());

        bindViews();
        setupActions();
        loadDevice();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        tvRoleBadge = findViewById(R.id.tvRoleBadge);
        tvMac = findViewById(R.id.tvMac);
        tvLastOnline = findViewById(R.id.tvLastOnline);
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt);
        tvUpdatedBy = findViewById(R.id.tvUpdatedBy);
        etName = findViewById(R.id.etName);
        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        swStatus = findViewById(R.id.swStatus);
        btnEditSave = findViewById(R.id.btnEditSave);
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> onBackPressed());
        tvRoleBadge.setText(canEdit ? "ADMIN" : "VIEW");

        setEditingEnabled(false);
        btnEditSave.setText(canEdit ? "Edit" : "Close");
        btnEditSave.setOnClickListener(v -> {
            if (!canEdit) {
                finish();
                return;
            }

            if (!editing) {
                setEditingEnabled(true);
                editing = true;
                btnEditSave.setText("Save");
                return;
            }
            saveChanges();
        });
    }

    private void setEditingEnabled(boolean enabled) {
        etName.setEnabled(enabled);
        etSsid.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        swStatus.setEnabled(enabled);
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

                    String mac = valueOf(doc.getString("device_id"), deviceDocId);
                    String name = valueOf(doc.getString("device_name"), deviceDocId);
                    String ssid = valueOf(doc.getString("device_network_ssid"), "-");
                    String password = valueOf(doc.getString("device_network_password"), "");
                    Boolean status = doc.getBoolean("device_status");
                    Timestamp lastOnline = doc.getTimestamp("device_last_online");
                    currentUpdatedAt = doc.getTimestamp("updated_at");
                    String updatedBy = valueOf(doc.getString("updated_by"), "-");

                    tvMac.setText(mac);
                    etName.setText(name);
                    etSsid.setText(ssid);
                    etPassword.setText(password);
                    swStatus.setChecked(status != null && status);

                    tvLastOnline.setText(formatTimestamp(lastOnline));
                    tvUpdatedAt.setText(formatTimestamp(currentUpdatedAt));
                    tvUpdatedBy.setText(updatedBy);

                    setEditingEnabled(false);
                    editing = false;
                    btnEditSave.setText(canEdit ? "Edit" : "Close");
                },
                exception -> Toast.makeText(this, "Failed to load: " + exception.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void saveChanges() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("device_name", trim(etName.getText()));
        updates.put("device_network_ssid", trim(etSsid.getText()));
        updates.put("device_network_password", trim(etPassword.getText()));
        updates.put("device_status", swStatus.isChecked());
        updates.put("updated_at", FieldValue.serverTimestamp());
        updates.put("updated_by", currentUpdatedBy != null ? currentUpdatedBy : "");

        btnEditSave.setEnabled(false);
        deviceRepository.updateDevice(
                businessId,
                deviceDocId,
                updates,
                () -> {
                    btnEditSave.setEnabled(true);
                    Toast.makeText(this, "Device updated.", Toast.LENGTH_SHORT).show();
                    loadDevice();
                },
                exception -> {
                    btnEditSave.setEnabled(true);
                    Toast.makeText(this, "Update failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private String trim(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String valueOf(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String formatTimestamp(Timestamp timestamp) {
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
