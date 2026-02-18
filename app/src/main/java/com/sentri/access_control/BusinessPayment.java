package com.sentri.access_control;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.adapters.PaymentAdapter;
import com.sentri.access_control.models.PaymentItem;
import com.sentri.access_control.repositories.FirestorePaymentRepository;
import com.sentri.access_control.repositories.PaymentRepository;
import com.sentri.access_control.utils.CurrencyUtils;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BusinessPayment extends AppCompatActivity {

    private TextView tvStartDateValue;
    private TextView tvEndDateValue;
    private TextView tvTitle;
    private TextView tvTotalAmountValue;
    private Button btnStartDate;
    private Button btnEndDate;

    private RecyclerView recyclerHistory;
    private PaymentAdapter adapter;
    private final List<PaymentItem> paymentList = new ArrayList<>();

    private PaymentRepository paymentRepository;
    private String businessId;

    private Calendar startCal;
    private Calendar endCal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_payment);

        PrefsManager prefsManager = new PrefsManager(this);
        businessId = prefsManager.getCurrentBizId();
        String businessName = prefsManager.getCurrentBizName();

        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        paymentRepository = new FirestorePaymentRepository(FirebaseFirestore.getInstance());

        bindViews();
        wireNavigation();
        setupRecycler();

        tvTitle.setText(businessName != null ? businessName : "Business");

        startCal = Calendar.getInstance();
        endCal = Calendar.getInstance();
        updateDateText(tvStartDateValue, startCal);
        updateDateText(tvEndDateValue, endCal);

        btnStartDate.setOnClickListener(v -> pickDate(startCal, tvStartDateValue));
        btnEndDate.setOnClickListener(v -> pickDate(endCal, tvEndDateValue));

        loadPayments();
    }

    private void bindViews() {
        tvStartDateValue = findViewById(R.id.tvStartDateValue);
        tvEndDateValue = findViewById(R.id.tvEndDateValue);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        tvTotalAmountValue = findViewById(R.id.tvTotalAmountValue);
        tvTitle = findViewById(R.id.tvTitle);
        recyclerHistory = findViewById(R.id.recyclerPaymentHistory);
    }

    private void wireNavigation() {
        ImageView ivBack = findViewById(R.id.backButton);
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PaymentAdapter(paymentList);
        recyclerHistory.setAdapter(adapter);
    }

    private void pickDate(Calendar calendar, TextView dateView) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int day) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, day);
                    updateDateText(dateView, calendar);
                    loadPayments();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateDateText(TextView target, Calendar calendar) {
        target.setText(DateFormat.format("d MMM, yyyy", calendar));
    }

    private void loadPayments() {
        if (startCal.after(endCal)) {
            Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp startTimestamp = buildStartOfDay(startCal);
        Timestamp endTimestamp = buildEndOfDay(endCal);

        paymentRepository.fetchBusinessPayments(
                businessId,
                startTimestamp,
                endTimestamp,
                this::onPaymentsLoaded,
                e -> Toast.makeText(this, "Error loading payments: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private Timestamp buildStartOfDay(Calendar source) {
        Calendar clone = (Calendar) source.clone();
        clone.set(Calendar.HOUR_OF_DAY, 0);
        clone.set(Calendar.MINUTE, 0);
        clone.set(Calendar.SECOND, 0);
        clone.set(Calendar.MILLISECOND, 0);
        return new Timestamp(clone.getTime());
    }

    private Timestamp buildEndOfDay(Calendar source) {
        Calendar clone = (Calendar) source.clone();
        clone.set(Calendar.HOUR_OF_DAY, 23);
        clone.set(Calendar.MINUTE, 59);
        clone.set(Calendar.SECOND, 59);
        clone.set(Calendar.MILLISECOND, 999);
        return new Timestamp(clone.getTime());
    }

    private void onPaymentsLoaded(QuerySnapshot snapshots) {
        paymentList.clear();

        double total = 0.0;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Timestamp createdAt = doc.getTimestamp("created_at");
            String when = createdAt != null
                    ? DateFormat.format("d MMM, yyyy 'at' hh:mm a", createdAt.toDate()).toString()
                    : "-";

            String method = safeString(doc.getString("payment_method"));
            String processedBy = safeString(doc.getString("payment_processed_by"));
            String type = safeString(doc.getString("payment_type"));
            String rate = safeString(doc.getString("payment_rate"));
            String amount = safeString(doc.getString("payment_amount"));

            double numericAmount = parseAmount(amount);
            boolean isPositive = type.isEmpty() || "Credit".equalsIgnoreCase(type);
            total += isPositive ? numericAmount : -numericAmount;

            paymentList.add(new PaymentItem(
                    when,
                    method,
                    processedBy,
                    type,
                    formatMoney(rate),
                    formatMoney(String.valueOf(Math.abs(numericAmount))),
                    isPositive
            ));
        }

        adapter.updateList(paymentList);
        renderTotal(total);
    }

    private void renderTotal(double total) {
        String formatted = String.format(Locale.US, "Rs %.2f", Math.abs(total));
        if (total < 0) {
            formatted = "-" + formatted;
        }
        tvTotalAmountValue.setText(formatted);
        tvTotalAmountValue.setTextColor(total >= 0 ? Color.parseColor("#00C853") : Color.parseColor("#FF5252"));
    }

    private String safeString(String value) {
        return value != null ? value.trim() : "";
    }

    private double parseAmount(String value) {
        return CurrencyUtils.parseAmount(value);
    }

    private String formatMoney(String raw) {
        double amount = parseAmount(raw);
        return String.format(Locale.US, "%.2f", amount);
    }
}
