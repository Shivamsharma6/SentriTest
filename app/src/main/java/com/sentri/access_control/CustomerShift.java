package com.sentri.access_control;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.adapters.ShiftAdapter;
import com.sentri.access_control.models.ShiftItem;
import com.sentri.access_control.repositories.FirestoreShiftRepository;
import com.sentri.access_control.repositories.ShiftRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerShift extends AppCompatActivity {

    private static final String PLACEHOLDER = "-";
    private static final String SEAT_PLACEHOLDER = "Seat needs to be selected";

    private TextView tvStartTimeValue;
    private TextView tvEndTimeValue;
    private TextView tvStartDateValue;
    private TextView tvEndDateValue;
    private TextView tvSeat;
    private TextView tvShiftDayCount;

    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnSubmit;
    private Button btnSeat;

    private EditText etComments;
    private RecyclerView rvHistory;

    private ShiftRepository shiftRepository;

    private String businessId;
    private String customerId;

    private Calendar calStart = Calendar.getInstance();
    private Calendar calEnd = Calendar.getInstance();
    private Calendar finalStartTime = Calendar.getInstance();
    private Calendar finalEndTime = Calendar.getInstance();

    private boolean calledFromSeatSelection;

    private int selectedSeat;
    private int startTimeHour;
    private int endTimeHour;

    private String startDateLabel;
    private String endDateLabel;
    private String allocatedSeatString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_shift);

        shiftRepository = new FirestoreShiftRepository(FirebaseFirestore.getInstance());

        readIntentExtras();
        if (businessId == null || customerId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupActions();
        applySelectionDataIfPresent();
        setupHistory();
    }

    private void readIntentExtras() {
        Intent intent = getIntent();
        businessId = intent.getStringExtra("businessDocId");
        customerId = intent.getStringExtra("customerDocId");

        calledFromSeatSelection = intent.getBooleanExtra("calledFromSeatSelection", false);
        selectedSeat = intent.getIntExtra("selectedSeat", -1);
        allocatedSeatString = intent.getStringExtra("allocatedSeatString");

        startTimeHour = intent.getIntExtra("startTime", -1);
        endTimeHour = intent.getIntExtra("endTime", -1);

        startDateLabel = intent.getStringExtra("startDate");
        endDateLabel = intent.getStringExtra("endDate");
    }

    private void bindViews() {
        tvStartTimeValue = findViewById(R.id.tvStartTimeValue);
        tvEndTimeValue = findViewById(R.id.tvEndTimeValue);
        tvStartDateValue = findViewById(R.id.tvStartDateValue);
        tvEndDateValue = findViewById(R.id.tvEndDateValue);
        tvSeat = findViewById(R.id.tvSeat);
        tvShiftDayCount = findViewById(R.id.tvShiftDayCount);

        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnSeat = findViewById(R.id.enterSeat);

        etComments = findViewById(R.id.etComments);
        rvHistory = findViewById(R.id.recyclerShiftHistory);
    }

    private void setupActions() {
        ImageView back = findViewById(R.id.backButton);
        back.setOnClickListener(v -> finish());

        btnStartDate.setOnClickListener(v -> showDatePicker(calStart, tvStartDateValue, true));
        btnEndDate.setOnClickListener(v -> showDatePicker(calEnd, tvEndDateValue, false));

        btnSeat.setOnClickListener(v -> openSeatSelection());
        btnSubmit.setOnClickListener(v -> submitToPayment());
        updateShiftDayCount();
    }

    private void applySelectionDataIfPresent() {
        if (!calledFromSeatSelection) {
            return;
        }

        String seatDisplay = resolveSeatDisplay();
        tvSeat.setText(seatDisplay.isEmpty() ? SEAT_PLACEHOLDER : seatDisplay);

        if (startTimeHour >= 0) {
            finalStartTime.set(Calendar.HOUR_OF_DAY, startTimeHour);
            finalStartTime.set(Calendar.MINUTE, 0);
            finalStartTime.set(Calendar.SECOND, 0);
            finalStartTime.set(Calendar.MILLISECOND, 0);
            tvStartTimeValue.setText(DateFormat.format("hh:mm a", finalStartTime));
        }

        if (endTimeHour >= 0) {
            finalEndTime.set(Calendar.HOUR_OF_DAY, endTimeHour);
            finalEndTime.set(Calendar.MINUTE, 0);
            finalEndTime.set(Calendar.SECOND, 0);
            finalEndTime.set(Calendar.MILLISECOND, 0);
            tvEndTimeValue.setText(DateFormat.format("hh:mm a", finalEndTime));
        }

        Date parsedStartDate = parseDate(startDateLabel);
        if (parsedStartDate != null) {
            calStart.setTime(parsedStartDate);
            finalStartTime.set(Calendar.YEAR, calStart.get(Calendar.YEAR));
            finalStartTime.set(Calendar.MONTH, calStart.get(Calendar.MONTH));
            finalStartTime.set(Calendar.DAY_OF_MONTH, calStart.get(Calendar.DAY_OF_MONTH));
            tvStartDateValue.setText(startDateLabel);
        }

        Date parsedEndDate = parseDate(endDateLabel);
        if (parsedEndDate != null) {
            calEnd.setTime(parsedEndDate);
            finalEndTime.set(Calendar.YEAR, calEnd.get(Calendar.YEAR));
            finalEndTime.set(Calendar.MONTH, calEnd.get(Calendar.MONTH));
            finalEndTime.set(Calendar.DAY_OF_MONTH, calEnd.get(Calendar.DAY_OF_MONTH));
            tvEndDateValue.setText(endDateLabel);
        }
        updateShiftDayCount();
    }

    private void showDatePicker(Calendar targetCalendar, TextView targetView, boolean isStart) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int day) -> {
                    targetCalendar.set(Calendar.YEAR, year);
                    targetCalendar.set(Calendar.MONTH, month);
                    targetCalendar.set(Calendar.DAY_OF_MONTH, day);
                    targetView.setText(DateFormat.format("d MMM, yyyy", targetCalendar));

                    if (isStart && !PLACEHOLDER.contentEquals(tvStartTimeValue.getText())) {
                        syncDateOnFinalCalendar(finalStartTime, targetCalendar);
                    }
                    if (!isStart && !PLACEHOLDER.contentEquals(tvEndTimeValue.getText())) {
                        syncDateOnFinalCalendar(finalEndTime, targetCalendar);
                    }
                    updateShiftDayCount();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void syncDateOnFinalCalendar(Calendar finalCalendar, Calendar sourceDate) {
        finalCalendar.set(Calendar.YEAR, sourceDate.get(Calendar.YEAR));
        finalCalendar.set(Calendar.MONTH, sourceDate.get(Calendar.MONTH));
        finalCalendar.set(Calendar.DAY_OF_MONTH, sourceDate.get(Calendar.DAY_OF_MONTH));
    }

    private void openSeatSelection() {
        String startDate = safeText(tvStartDateValue);
        String endDate = safeText(tvEndDateValue);

        if (PLACEHOLDER.equals(startDate) || PLACEHOLDER.equals(endDate)) {
            Toast.makeText(this, "Please select start/end date", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SeatSelection.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", customerId);
        intent.putExtra("startDate", startDate);
        intent.putExtra("endDate", endDate);
        startActivity(intent);
    }

    private void submitToPayment() {
        String seat = safeText(tvSeat);
        String comments = safeText(etComments);

        String startTime = safeText(tvStartTimeValue);
        String endTime = safeText(tvEndTimeValue);
        String startDate = safeText(tvStartDateValue);
        String endDate = safeText(tvEndDateValue);

        if (startTime.isEmpty() || PLACEHOLDER.equals(startTime)
                || endTime.isEmpty() || PLACEHOLDER.equals(endTime)
                || startDate.isEmpty() || PLACEHOLDER.equals(startDate)
                || endDate.isEmpty() || PLACEHOLDER.equals(endDate)) {
            Toast.makeText(this, "Please select start/end date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (seat.isEmpty() || SEAT_PLACEHOLDER.equalsIgnoreCase(seat)) {
            Toast.makeText(this, "Please select seat", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(CustomerShift.this, CustomerPayment.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", customerId);
        intent.putExtra("shiftStartMs", finalStartTime.getTimeInMillis());
        intent.putExtra("shiftEndMs", finalEndTime.getTimeInMillis());
        intent.putExtra("shiftSeat", seat);
        intent.putExtra("shiftComments", comments);
        startActivity(intent);
        finish();
    }

    private void setupHistory() {
        ShiftAdapter adapter = new ShiftAdapter(new ArrayList<>());
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
        loadShiftHistory(adapter);
    }

    private void loadShiftHistory(ShiftAdapter adapter) {
        shiftRepository.fetchCustomerShifts(
                businessId,
                customerId,
                docs -> {
                    List<ShiftItem> items = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        items.add(mapShiftItem(doc));
                    }
                    adapter.updateList(items);
                },
                e -> Toast.makeText(this,
                        "Failed to load shift history: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private ShiftItem mapShiftItem(DocumentSnapshot shiftDoc) {
        Timestamp createdTs = shiftDoc.getTimestamp("created_at");
        String createdDate = createdTs != null
                ? DateFormat.format("d MMM, yyyy", createdTs.toDate()).toString()
                : PLACEHOLDER;
        String admin = safeValue(shiftDoc.getString("created_by"), PLACEHOLDER);

        Timestamp startTs = shiftDoc.getTimestamp("shift_start_time");
        Timestamp endTs = shiftDoc.getTimestamp("shift_end_time");

        String startTime = startTs != null
                ? DateFormat.format("hh:mm a", startTs.toDate()).toString()
                : PLACEHOLDER;
        String endTime = endTs != null
                ? DateFormat.format("hh:mm a", endTs.toDate()).toString()
                : PLACEHOLDER;

        String subStart = startTs != null
                ? DateFormat.format("d MMM, yyyy", startTs.toDate()).toString()
                : PLACEHOLDER;
        String subEnd = endTs != null
                ? DateFormat.format("d MMM, yyyy", endTs.toDate()).toString()
                : PLACEHOLDER;

        String seat = safeValue(shiftDoc.getString("shift_seat"), PLACEHOLDER);
        String rate = safeValue(shiftDoc.getString("shift_payment_rate"), "0.00");

        String dateAdmin = createdDate + ",\n" + admin;
        String timeSlot = startTime + " - " + endTime;
        String seatInfo = seat + ", Rs " + rate;

        return new ShiftItem(dateAdmin, timeSlot, subStart, subEnd, seatInfo);
    }

    private String resolveSeatDisplay() {
        if (allocatedSeatString != null && !allocatedSeatString.trim().isEmpty()) {
            return allocatedSeatString.trim();
        }
        if (selectedSeat > 0) {
            return String.valueOf(selectedSeat);
        }
        return "";
    }

    private Date parseDate(String label) {
        if (label == null || label.trim().isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("d MMM, yyyy", Locale.getDefault()).parse(label.trim());
        } catch (ParseException ignored) {
            return null;
        }
    }

    private String safeText(TextView textView) {
        return textView.getText() != null ? textView.getText().toString().trim() : "";
    }

    private String safeText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String safeValue(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void updateShiftDayCount() {
        if (tvShiftDayCount == null) {
            return;
        }
        String startDate = safeText(tvStartDateValue);
        String endDate = safeText(tvEndDateValue);
        if (PLACEHOLDER.equals(startDate) || PLACEHOLDER.equals(endDate)) {
            tvShiftDayCount.setText("Days: -");
            return;
        }

        long startMs = finalStartTime.getTimeInMillis();
        long endMs = finalEndTime.getTimeInMillis();
        if (endMs < startMs) {
            tvShiftDayCount.setText("Days: 0");
            return;
        }
        long days = (endMs - startMs) / (24L * 60L * 60L * 1000L);
        tvShiftDayCount.setText("Days: " + days);
    }
}
