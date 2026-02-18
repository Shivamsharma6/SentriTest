package com.sentri.access_control;

import android.content.Intent;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.CardRepository;
import com.sentri.access_control.repositories.DeviceRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreCardRepository;
import com.sentri.access_control.repositories.FirestoreDeviceRepository;
import com.sentri.access_control.repositories.FirestorePaymentRepository;
import com.sentri.access_control.repositories.PaymentRepository;
import com.sentri.access_control.services.DashboardMetrics;
import com.sentri.access_control.services.DashboardMetricsCalculator;
import com.sentri.access_control.utils.CurrencyUtils;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int LOW_CARD_THRESHOLD = 50;

    private DrawerLayout drawer;
    private Spinner spnBusinessSelector;

    private TextView tvCustomersValue;
    private TextView tvContractsValue;
    private ProgressBar pbContract;
    private ProgressBar pbDevice;
    private TextView valueActiveSubscriptions;
    private TextView valuePendingPayments;
    private TextView valueExpectedOverall;
    private TextView tvLowCardsAlert;
    private View cardLowCards;

    private Spinner spinnerDuration;
    private View chartPlaceholder;
    private LinearLayout layoutMiniDeviceList;
    private TextView revenuePercent;

    private TextView tvDrawerBusiness;
    private TextView tvDrawerEmail;
    private TextView tvDrawerAccessLevel;
    private ImageView ivUserImage;

    private final ArrayList<String> businessIds = new ArrayList<>();
    private final ArrayList<String> businessNames = new ArrayList<>();
    private final ArrayList<String> accessLevels = new ArrayList<>();

    private String currentBusinessId;
    private String userEmail;
    private String userPhotoUrl;
    private int selectedBusinessIndex = 0;

    private PrefsManager prefsManager;
    private BusinessRepository businessRepository;
    private CardRepository cardRepository;
    private DeviceRepository deviceRepository;
    private PaymentRepository paymentRepository;
    private DashboardMetricsCalculator metricsCalculator;
    private DashboardMetrics latestMetrics;
    private final List<DocumentSnapshot> recentPaymentDocs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        prefsManager = new PrefsManager(this);
        businessRepository = new FirestoreBusinessRepository(firestore);
        cardRepository = new FirestoreCardRepository(firestore);
        deviceRepository = new FirestoreDeviceRepository(firestore);
        paymentRepository = new FirestorePaymentRepository(firestore);
        metricsCalculator = new DashboardMetricsCalculator();

        bindViews();
        setupToolbarAndDrawer();
        readSessionAndIntentData();

        if (businessIds.isEmpty()) {
            Toast.makeText(this, "Missing business data, please login again", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupBusinessSelector();
        setupDurationSpinner();
        setupAddBusinessButton();
        updateDrawerHeader();

        String initialBusinessId = (currentBusinessId != null && !currentBusinessId.isEmpty())
                ? currentBusinessId
                : businessIds.get(0);
        loadDashboard(initialBusinessId);
    }

    private void bindViews() {
        spnBusinessSelector = findViewById(R.id.spnBusinessSelector);
        tvCustomersValue = findViewById(R.id.tvCustomersValue);
        tvContractsValue = findViewById(R.id.tvContractsValue);
        pbContract = findViewById(R.id.pbContract);
        pbDevice = findViewById(R.id.pbDevice);
        valueActiveSubscriptions = findViewById(R.id.value_active_subscriptions);
        valuePendingPayments = findViewById(R.id.value_pending_payments);
        valueExpectedOverall = findViewById(R.id.value_expected_overall);

        tvLowCardsAlert = findViewById(R.id.tvLowCardsAlert);
        cardLowCards = findViewById(R.id.card_low_cards);
        spinnerDuration = findViewById(R.id.spinner_duration);
        chartPlaceholder = findViewById(R.id.chart_placeholder);
        layoutMiniDeviceList = findViewById(R.id.layoutMiniDeviceList);
        revenuePercent = findViewById(R.id.revenue_percent);
    }

    private void setupToolbarAndDrawer() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawer,
                toolbar,
                R.string.nav_open,
                R.string.nav_close
        );
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navView = findViewById(R.id.navView);
        navView.setNavigationItemSelectedListener(this);

        View header = navView.getHeaderView(0);
        tvDrawerBusiness = header.findViewById(R.id.tvDrawerBusiness);
        tvDrawerEmail = header.findViewById(R.id.tvDrawerEmail);
        tvDrawerAccessLevel = header.findViewById(R.id.tvDrawerAccessLevel);
        ivUserImage = header.findViewById(R.id.ivUserImage);
    }

    private void readSessionAndIntentData() {
        Intent intent = getIntent();

        List<String> intentBusinessIds = intent.getStringArrayListExtra("EXTRA_BIZ_IDS");
        List<String> intentBusinessNames = intent.getStringArrayListExtra("EXTRA_BIZ_NAMES");

        if (intentBusinessIds != null && !intentBusinessIds.isEmpty()) {
            businessIds.addAll(intentBusinessIds);
        } else {
            businessIds.addAll(prefsManager.getBizIds());
        }

        if (intentBusinessNames != null && !intentBusinessNames.isEmpty()) {
            businessNames.addAll(cleanBusinessNames(intentBusinessNames));
        } else {
            businessNames.addAll(cleanBusinessNames(prefsManager.getBizNames()));
        }

        accessLevels.addAll(prefsManager.getBizAccessLevels());
        while (businessNames.size() < businessIds.size()) {
            businessNames.add(businessIds.get(businessNames.size()));
        }
        while (accessLevels.size() < businessIds.size()) {
            accessLevels.add("");
        }

        currentBusinessId = intent.getStringExtra("EXTRA_CURRENT_BIZ");
        if (currentBusinessId == null || currentBusinessId.trim().isEmpty()) {
            currentBusinessId = prefsManager.getCurrentBizId();
        }

        userEmail = prefsManager.getUserEmail();
        userPhotoUrl = prefsManager.getUserPhotoUrl();
    }

    private List<String> cleanBusinessNames(List<String> rawNames) {
        List<String> cleaned = new ArrayList<>();
        for (String name : rawNames) {
            if (name == null) {
                cleaned.add("");
                continue;
            }
            String normalized = name.trim();
            if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            cleaned.add(normalized);
        }
        return cleaned;
    }

    private void setupBusinessSelector() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                businessNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnBusinessSelector.setAdapter(adapter);

        int defaultIndex = 0;
        if (currentBusinessId != null) {
            int index = businessIds.indexOf(currentBusinessId);
            if (index >= 0) {
                defaultIndex = index;
            }
        }
        selectedBusinessIndex = defaultIndex;
        spnBusinessSelector.setSelection(defaultIndex);

        spnBusinessSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBusinessIndex = position;
                currentBusinessId = businessIds.get(position);

                String selectedBusinessName = businessNames.size() > position
                        ? businessNames.get(position)
                        : "";
                String selectedAccessLevel = accessLevels.size() > position
                        ? accessLevels.get(position)
                        : "";

                prefsManager.setCurrentBizId(currentBusinessId);
                prefsManager.setCurrentBizName(selectedBusinessName);
                prefsManager.setCurrentBizAccessLevel(selectedAccessLevel);

                updateDrawerHeader();
                loadDashboard(currentBusinessId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupDurationSpinner() {
        if (spinnerDuration == null) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"7D", "15D", "30D"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDuration.setAdapter(adapter);
        spinnerDuration.setSelection(0);
        spinnerDuration.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderGraphForDuration(String.valueOf(parent.getItemAtPosition(position)), false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupAddBusinessButton() {
//        Button addBusinessButton = findViewById(R.id.button);
//        addBusinessButton.setOnClickListener(v -> startActivity(new Intent(this, AddBusiness.class)));
    }

    private void loadDashboard(String businessId) {
        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid business selected", Toast.LENGTH_SHORT).show();
            return;
        }

        renderDefaultDashboardState();
        loadLowCardsAlert(businessId);
        loadMiniDevices(businessId);
        loadRevenueData(businessId);

        businessRepository.fetchActiveCustomers(
                businessId,
                docs -> renderDashboard(metricsCalculator.calculate(docs)),
                e -> Toast.makeText(this, "Failed to load dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void renderDefaultDashboardState() {
        tvCustomersValue.setText("0");
        tvContractsValue.setText("0");
        valueActiveSubscriptions.setText("0");
        valuePendingPayments.setText(CurrencyUtils.formatRupees(0));
        valueExpectedOverall.setText(CurrencyUtils.formatRupees(0));
        pbContract.setProgress(0);
        pbDevice.setProgress(0);
        latestMetrics = null;
        recentPaymentDocs.clear();
        if (revenuePercent != null) {
            revenuePercent.setText(CurrencyUtils.formatRupees(0));
            revenuePercent.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
        }
        renderGraphForDuration("7D", true);
    }

    private void renderDashboard(DashboardMetrics metrics) {
        latestMetrics = metrics;
        tvCustomersValue.setText(String.valueOf(metrics.getNewCustomersInLast10Days()));
        tvContractsValue.setText(String.valueOf(metrics.getTotalActiveCustomers()));
        valueActiveSubscriptions.setText(String.valueOf(metrics.getTotalActiveCustomers()));
        valuePendingPayments.setText(CurrencyUtils.formatRupees(metrics.getPendingPayments()));
        valueExpectedOverall.setText(CurrencyUtils.formatRupees(metrics.getExpectedPaymentsThisMonth()));

        int base = Math.max(1, metrics.getTotalActiveCustomers());
        int contractProgress = Math.min(100, (metrics.getNewCustomersInLast10Days() * 100) / base);
        int deviceProgress = Math.min(100, (metrics.getSubscriptionsEndingToday() * 100) / base);

        pbContract.setProgress(contractProgress);
        pbDevice.setProgress(deviceProgress);

        String duration = spinnerDuration != null && spinnerDuration.getSelectedItem() != null
                ? String.valueOf(spinnerDuration.getSelectedItem())
                : "7D";
        renderGraphForDuration(duration, false);
    }

    private void renderGraphForDuration(String duration, boolean fallbackZero) {
        if (!(chartPlaceholder instanceof ViewGroup)) {
            return;
        }
        ViewGroup graphContainer = (ViewGroup) chartPlaceholder;
        graphContainer.removeAllViews();

        int[] points = fallbackZero ? generateZeroPoints(duration) : buildRevenueBucketHeights(duration);

        LinearLayout bars = new LinearLayout(this);
        bars.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(android.view.Gravity.BOTTOM);

        for (int point : points) {
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(point), 1f);
            lp.setMargins(dp(4), 0, dp(4), dp(6));
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(Color.parseColor("#2F8DFF"));
            bars.addView(bar);
        }

        graphContainer.addView(bars);
    }

    private int[] generateZeroPoints(String duration) {
        int barCount = resolveDurationDays(duration);
        int[] points = new int[barCount];
        for (int i = 0; i < barCount; i++) {
            points[i] = 8;
        }
        return points;
    }

    private int[] buildRevenueBucketHeights(String duration) {
        int days = resolveDurationDays(duration);
        double[] buckets = computeRevenueBuckets(days);
        double max = 0.0;
        for (double value : buckets) {
            if (value > max) {
                max = value;
            }
        }
        int[] heights = new int[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            if (max <= 0.0) {
                heights[i] = 8;
            } else {
                heights[i] = Math.max(8, (int) ((buckets[i] / max) * 120.0));
            }
        }
        updateRevenueTrend(days, buckets);
        return heights;
    }

    private int resolveDurationDays(String duration) {
        if ("30D".equals(duration)) {
            return 30;
        }
        if ("15D".equals(duration)) {
            return 15;
        }
        return 7;
    }

    private double[] computeRevenueBuckets(int days) {
        double[] buckets = new double[days];
        Calendar startCal = Calendar.getInstance();
        resetToDayStart(startCal);
        startCal.add(Calendar.DAY_OF_MONTH, -(days - 1));
        long startMs = startCal.getTimeInMillis();

        for (DocumentSnapshot doc : recentPaymentDocs) {
            com.google.firebase.Timestamp createdAt = doc.getTimestamp("created_at");
            if (createdAt == null) {
                continue;
            }
            long createdMs = createdAt.toDate().getTime();
            if (createdMs < startMs) {
                continue;
            }
            int index = (int) ((createdMs - startMs) / (24L * 60L * 60L * 1000L));
            if (index < 0 || index >= days) {
                continue;
            }

            double amount = CurrencyUtils.parseAmount(doc.get("payment_amount"));
            String type = doc.getString("payment_type");
            if ("debit".equalsIgnoreCase(type)) {
                amount = -amount;
            }
            buckets[index] += amount;
        }
        return buckets;
    }

    private void updateRevenueTrend(int days, double[] currentBuckets) {
        if (revenuePercent == null) {
            return;
        }

        double currentTotal = 0.0;
        for (double value : currentBuckets) {
            currentTotal += value;
        }

        double previousTotal = computePreviousPeriodRevenue(days);
        String amountText = CurrencyUtils.formatRupees(currentTotal);

        if (previousTotal <= 0.0) {
            revenuePercent.setText(amountText);
            revenuePercent.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
            return;
        }

        double deltaPct = ((currentTotal - previousTotal) / previousTotal) * 100.0;
        String arrow = deltaPct >= 0 ? "↑" : "↓";
        revenuePercent.setText(String.format(Locale.getDefault(), "%s  %.1f%%  %s", arrow, Math.abs(deltaPct), amountText));
        revenuePercent.setTextColor(deltaPct >= 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
    }

    private double computePreviousPeriodRevenue(int days) {
        Calendar startCurrent = Calendar.getInstance();
        resetToDayStart(startCurrent);
        startCurrent.add(Calendar.DAY_OF_MONTH, -(days - 1));

        Calendar startPrevious = (Calendar) startCurrent.clone();
        startPrevious.add(Calendar.DAY_OF_MONTH, -days);
        long startPrevMs = startPrevious.getTimeInMillis();
        long endPrevMs = startCurrent.getTimeInMillis() - 1L;

        double total = 0.0;
        for (DocumentSnapshot doc : recentPaymentDocs) {
            com.google.firebase.Timestamp createdAt = doc.getTimestamp("created_at");
            if (createdAt == null) {
                continue;
            }
            long createdMs = createdAt.toDate().getTime();
            if (createdMs < startPrevMs || createdMs > endPrevMs) {
                continue;
            }
            double amount = CurrencyUtils.parseAmount(doc.get("payment_amount"));
            String type = doc.getString("payment_type");
            if ("debit".equalsIgnoreCase(type)) {
                amount = -amount;
            }
            total += amount;
        }
        return total;
    }

    private void loadRevenueData(String businessId) {
        Calendar end = Calendar.getInstance();
        Calendar start = Calendar.getInstance();
        resetToDayStart(start);
        start.add(Calendar.DAY_OF_MONTH, -59);

        paymentRepository.fetchBusinessPayments(
                businessId,
                new com.google.firebase.Timestamp(start.getTime()),
                new com.google.firebase.Timestamp(end.getTime()),
                snapshot -> {
                    recentPaymentDocs.clear();
                    recentPaymentDocs.addAll(snapshot.getDocuments());
                    String duration = spinnerDuration != null && spinnerDuration.getSelectedItem() != null
                            ? String.valueOf(spinnerDuration.getSelectedItem())
                            : "7D";
                    renderGraphForDuration(duration, false);
                },
                e -> {
                    recentPaymentDocs.clear();
                    renderGraphForDuration("7D", true);
                    if (revenuePercent != null) {
                        revenuePercent.setText("Revenue unavailable");
                        revenuePercent.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
                    }
                }
        );
    }

    private void resetToDayStart(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void loadLowCardsAlert(String businessId) {
        cardRepository.fetchBusinessCards(
                businessId,
                false,
                docs -> {
                    int unassignedCount = 0;
                    for (DocumentSnapshot doc : docs) {
                        Boolean cardStatus = doc.getBoolean("card_status");
                        if (!Boolean.TRUE.equals(cardStatus)) {
                            unassignedCount++;
                        }
                    }

                    if (cardLowCards != null) cardLowCards.setVisibility(View.VISIBLE);
                    if (unassignedCount <= LOW_CARD_THRESHOLD) {
                        tvLowCardsAlert.setText("Low Remaining Cards: " + unassignedCount);
                        tvLowCardsAlert.setTextColor(Color.parseColor("#FFB300"));
                    } else {
                        tvLowCardsAlert.setText("Remaining Cards: " + unassignedCount);
                        tvLowCardsAlert.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
                    }
                },
                e -> {
                    if (cardLowCards != null) cardLowCards.setVisibility(View.VISIBLE);
                    tvLowCardsAlert.setText("Cards: unavailable");
                    tvLowCardsAlert.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
                }
        );
    }

    private void loadMiniDevices(String businessId) {
        if (layoutMiniDeviceList == null) {
            return;
        }
        layoutMiniDeviceList.removeAllViews();

        deviceRepository.fetchDevices(
                businessId,
                docs -> {
                    layoutMiniDeviceList.removeAllViews();
                    if (docs.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No devices available");
                        empty.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));
                        layoutMiniDeviceList.addView(empty);
                        return;
                    }

                    int maxItems = Math.min(6, docs.size());
                    for (int i = 0; i < maxItems; i++) {
                        layoutMiniDeviceList.addView(buildMiniDeviceRow(businessId, docs.get(i)));
                    }
                },
                e -> {
                    layoutMiniDeviceList.removeAllViews();
                    TextView error = new TextView(this);
                    error.setText("Failed to load devices");
                    error.setTextColor(Color.parseColor("#B00020"));
                    layoutMiniDeviceList.addView(error);
                }
        );
    }

    private View buildMiniDeviceRow(String businessId, DocumentSnapshot doc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(dotLp);

        Boolean status = doc.getBoolean("device_status");
        boolean isOnline = Boolean.TRUE.equals(status);
        dot.setBackgroundColor(isOnline ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

        TextView name = new TextView(this);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameLp);
        String deviceName = doc.getString("device_name");
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = doc.getId();
        }
        name.setText(deviceName);
        name.setTextColor(resolveThemeColor(android.R.attr.textColorPrimary));

        ImageView lock = new ImageView(this);
        lock.setImageResource(isOnline ? R.drawable.ic_lock_open : R.drawable.ic_lock_close);
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        lockLp.setMargins(dp(8), 0, 0, 0);
        lock.setLayoutParams(lockLp);
        lock.setColorFilter(getLockTintColor(isOnline));
        lock.setPadding(dp(2), dp(2), dp(2), dp(2));

        String deviceId = doc.getId();
        View.OnClickListener toggleListener = v -> animateAndToggleDeviceStatus(businessId, deviceId, isOnline, lock);
        lock.setOnClickListener(toggleListener);
        row.setOnClickListener(toggleListener);

        row.addView(dot);
        row.addView(name);
        row.addView(lock);
        return row;
    }

    private void animateAndToggleDeviceStatus(String businessId, String deviceId, boolean currentStatus, ImageView lockIcon) {
        boolean nextStatus = !currentStatus;
        int fromColor = getLockTintColor(currentStatus);
        int toColor = getLockTintColor(nextStatus);

        lockIcon.setEnabled(false);
        lockIcon.setImageResource(nextStatus ? R.drawable.ic_lock_open : R.drawable.ic_lock_close);

        ValueAnimator tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        tintAnimator.setDuration(220L);
        tintAnimator.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            lockIcon.setColorFilter(color);
        });
        tintAnimator.start();

        toggleDeviceStatus(businessId, deviceId, currentStatus);
    }

    private int getLockTintColor(boolean isOpen) {
        return isOpen ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828");
    }

    private void toggleDeviceStatus(String businessId, String deviceId, boolean currentStatus) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("device_status", !currentStatus);

        deviceRepository.updateDevice(
                businessId,
                deviceId,
                updates,
                () -> loadMiniDevices(businessId),
                e -> Toast.makeText(this, "Failed to update device: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private int resolveThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }

    private void updateDrawerHeader() {
        String businessName = businessNames.size() > selectedBusinessIndex
                ? businessNames.get(selectedBusinessIndex)
                : "";
        String accessLevel = accessLevels.size() > selectedBusinessIndex
                ? accessLevels.get(selectedBusinessIndex)
                : "";

        tvDrawerBusiness.setText(businessName);
        tvDrawerEmail.setText(userEmail != null ? userEmail : "");
        tvDrawerAccessLevel.setText(accessLevel);

        if (userPhotoUrl != null && !userPhotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(userPhotoUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(ivUserImage);
        } else {
            ivUserImage.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            prefsManager.clearSession();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        if (id == R.id.nav_customers) {
            startActivity(new Intent(this, CustomerList.class));
        } else if (id == R.id.nav_cards) {
            Intent intent = new Intent(this, CardAssignment.class);
            intent.putExtra("business_id", currentBusinessId);
            startActivity(intent);
        } else if (id == R.id.nav_seats) {
            Intent intent = new Intent(this, BusinessSeat.class);
            intent.putExtra("businessDocId", currentBusinessId);
            startActivity(intent);
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, Settings.class));
        } else if (id == R.id.nav_payments) {
            startActivity(new Intent(this, BusinessPayment.class));
        } else if (id == R.id.nav_notifications) {
            startActivity(new Intent(this, AppNotifications.class));
        }

        drawer.closeDrawers();
        return true;
    }

    @Override
    public void onBackPressed() {
        NavigationView navView = findViewById(R.id.navView);
        if (drawer != null && drawer.isDrawerOpen(navView)) {
            drawer.closeDrawers();
            return;
        }
        super.onBackPressed();
    }
}
