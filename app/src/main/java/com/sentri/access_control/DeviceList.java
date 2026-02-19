package com.sentri.access_control;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.adapters.DeviceAdapter;
import com.sentri.access_control.models.DeviceItem;
import com.sentri.access_control.repositories.DeviceRepository;
import com.sentri.access_control.repositories.FirestoreDeviceRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class DeviceList extends AppCompatActivity {
    private static final long ONLINE_THRESHOLD_MS = 20L * 60L * 1000L;

    private ImageView ivBack;
    private EditText etSearch;
    private RecyclerView rvDevices;

    private final List<DeviceItem> devices = new ArrayList<>();
    private DeviceAdapter adapter;

    private DeviceRepository deviceRepository;
    private String businessId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        deviceRepository = new FirestoreDeviceRepository(FirebaseFirestore.getInstance());
        businessId = new PrefsManager(this).getCurrentBizId();
        if (businessId == null || businessId.trim().isEmpty()) {
            businessId = getIntent().getStringExtra("businessDocId");
        }
        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        setupRecycler();
        setupActions();
        loadDevices();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        etSearch = findViewById(R.id.etSearch);
        rvDevices = findViewById(R.id.recyclerDevices);
    }

    private void setupRecycler() {
        adapter = new DeviceAdapter(item -> {
            Intent intent = new Intent(DeviceList.this, DeviceDetails.class);
            intent.putExtra("businessDocId", businessId);
            intent.putExtra("deviceDocId", item.getId());
            startActivity(intent);
        });
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        rvDevices.setAdapter(adapter);
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> onBackPressed());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadDevices() {
        deviceRepository.fetchDevices(
                businessId,
                docs -> {
                    devices.clear();
                    for (DocumentSnapshot doc : docs) {
                        devices.add(mapDevice(doc));
                    }
                    adapter.setItems(devices);
                },
                exception -> Toast.makeText(this,
                        "Failed to load devices: " + exception.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private DeviceItem mapDevice(DocumentSnapshot doc) {
        String id = doc.getId();
        String mac = valueOf(doc.getString("device_mac"), id);
        String name = valueOf(doc.getString("device_name"), id);
        String ssid = valueOf(doc.getString("device_network_ssid"), "-");
        Timestamp lastOnline = doc.getTimestamp("device_last_online");

        boolean isWithinThreshold = false;
        if (lastOnline != null) {
            long ageMs = System.currentTimeMillis() - lastOnline.toDate().getTime();
            isWithinThreshold = ageMs >= 0L && ageMs <= ONLINE_THRESHOLD_MS;
        }
        boolean isOnline = isWithinThreshold;

        return new DeviceItem(id, mac, name, ssid, isOnline, lastOnline);
    }

    private String valueOf(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
