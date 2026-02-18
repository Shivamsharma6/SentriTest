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

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.CustomerRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;
import com.sentri.access_control.repositories.FirestoreCustomerRepository;
import com.sentri.access_control.repositories.FirestoreLeaveRepository;
import com.sentri.access_control.repositories.LeaveRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerLeave extends AppCompatActivity {

    private static final String ENTITY_TYPE_LEAVES = "leaves";

    private ImageView ivBack;
    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnSubmit;
    private TextView tvStartDateValue;
    private TextView tvEndDateValue;
    private EditText etNoOfDays;
    private EditText etComments;

    private Calendar calStart;
    private Calendar calEnd;
    private int numberOfDays;
    private boolean isStartSet;
    private boolean isEndSet;

    private BusinessRepository businessRepository;
    private CustomerRepository customerRepository;
    private CommentRepository commentRepository;
    private LeaveRepository leaveRepository;

    private String businessId;
    private String customerId;
    private String businessPrefix;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_leave);

        bindViews();
        initializeDependencies();
        initializeDates();
        if (!readIntentData()) {
            return;
        }
        setupActions();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        btnStartDate = findViewById(R.id.btnStartDate);
        tvStartDateValue = findViewById(R.id.tvStartDateValue);
        btnEndDate = findViewById(R.id.btnEndDate);
        tvEndDateValue = findViewById(R.id.tvEndDateValue);
        etNoOfDays = findViewById(R.id.etNoOfDays);
        etComments = findViewById(R.id.etComments);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void initializeDependencies() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        businessRepository = new FirestoreBusinessRepository(firestore);
        customerRepository = new FirestoreCustomerRepository(firestore);
        commentRepository = new FirestoreCommentRepository(firestore);
        leaveRepository = new FirestoreLeaveRepository(firestore);
        userEmail = new PrefsManager(this).getUserEmail();
    }

    private void initializeDates() {
        calStart = Calendar.getInstance();
        calEnd = Calendar.getInstance();
        setCalendarToStartOfDay(calStart);
        setCalendarToStartOfDay(calEnd);
    }

    private boolean readIntentData() {
        Intent intent = getIntent();
        businessId = intent.getStringExtra("businessDocId");
        customerId = intent.getStringExtra("customerDocId");
        if (businessId == null || customerId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        return true;
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> finish());

        btnStartDate.setOnClickListener(v -> showDatePicker(calStart, dateText -> {
            isStartSet = true;
            tvStartDateValue.setText(dateText);
            recalculateLeaveDays();
        }));

        btnEndDate.setOnClickListener(v -> showDatePicker(calEnd, dateText -> {
            isEndSet = true;
            tvEndDateValue.setText(dateText);
            recalculateLeaveDays();
        }));

        btnSubmit.setOnClickListener(v -> onSubmitLeave());
    }

    private void showDatePicker(Calendar targetCalendar, java.util.function.Consumer<String> onPicked) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (DatePicker picker, int year, int month, int dayOfMonth) -> {
                    targetCalendar.set(Calendar.YEAR, year);
                    targetCalendar.set(Calendar.MONTH, month);
                    targetCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    setCalendarToStartOfDay(targetCalendar);
                    String formatted = DateFormat.format("d MMM, yyyy", targetCalendar).toString();
                    onPicked.accept(formatted);
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void recalculateLeaveDays() {
        if (!isStartSet || !isEndSet) {
            return;
        }

        long diffMs = calEnd.getTimeInMillis() - calStart.getTimeInMillis();
        numberOfDays = (int) (diffMs / (1000L * 60 * 60 * 24)) + 1;
        etNoOfDays.setText(numberOfDays > 0 ? String.valueOf(numberOfDays) : "");
    }

    private void onSubmitLeave() {
        if (!isStartSet || !isEndSet) {
            Toast.makeText(this, "Select both dates", Toast.LENGTH_SHORT).show();
            return;
        }

        if (numberOfDays <= 0) {
            Toast.makeText(this, "End date cannot be before start date", Toast.LENGTH_SHORT).show();
            return;
        }

        String comments = getText(etComments);
        if (comments.isEmpty()) {
            etComments.setError("Required");
            return;
        }

        btnSubmit.setEnabled(false);
        validateLeaveWithinSubscription(comments);
    }

    private void validateLeaveWithinSubscription(String comments) {
        customerRepository.fetchCustomer(
                businessId,
                customerId,
                customerDoc -> {
                    SubscriptionWindow window = extractSubscriptionWindow(customerDoc);
                    if (window == null) {
                        completeWithMessage("No active subscription dates found");
                        return;
                    }

                    Date leaveStartDate = calStart.getTime();
                    Date leaveEndDate = calEnd.getTime();
                    if (leaveStartDate.before(window.minStart.toDate()) || leaveEndDate.after(window.maxEnd.toDate())) {
                        String start = DateFormat.format("d MMM, yyyy", window.minStart.toDate()).toString();
                        String end = DateFormat.format("d MMM, yyyy", window.maxEnd.toDate()).toString();
                        completeWithMessage("Leave dates must be between " + start + " and " + end);
                        return;
                    }

                    fetchBusinessPrefixAndApplyLeave(comments);
                },
                exception -> handleActionError("Error checking subscription", exception)
        );
    }

    private SubscriptionWindow extractSubscriptionWindow(DocumentSnapshot customerDoc) {
        List<Timestamp> starts = readTimestampList(customerDoc.get("customer_subscription_start_date"));
        List<Timestamp> ends = readTimestampList(customerDoc.get("customer_subscription_end_date"));
        if (starts.isEmpty() || ends.isEmpty()) {
            return null;
        }

        Timestamp minStart = Collections.min(starts, (first, second) -> first.toDate().compareTo(second.toDate()));
        Timestamp maxEnd = Collections.max(ends, (first, second) -> first.toDate().compareTo(second.toDate()));
        return new SubscriptionWindow(minStart, maxEnd);
    }

    private void fetchBusinessPrefixAndApplyLeave(String comments) {
        businessRepository.fetchBusinessPrefix(
                businessId,
                prefix -> {
                    businessPrefix = prefix != null ? prefix.trim() : "";
                    if (businessPrefix.isEmpty()) {
                        handleActionError("Error fetching business prefix", new IllegalStateException("Business prefix missing"));
                        return;
                    }
                    createLeaveAndApply(comments);
                },
                exception -> handleActionError("Error fetching business prefix", exception)
        );
    }

    private void createLeaveAndApply(String comments) {
        leaveRepository.fetchNextLeaveId(
                businessId,
                businessPrefix,
                leaveId -> {
                    Map<String, Object> leaveData = buildLeavePayload(leaveId);
                    leaveRepository.createLeave(
                            businessId,
                            leaveId,
                            leaveData,
                            () -> addLeaveCommentAndAdjustSubscription(comments),
                            exception -> handleActionError("Leave save error", exception)
                    );
                },
                exception -> handleActionError("Leave ID error", exception)
        );
    }

    private Map<String, Object> buildLeavePayload(String leaveId) {
        Map<String, Object> leaveData = new HashMap<>();
        leaveData.put("start_date", new Timestamp(calStart.getTime()));
        leaveData.put("end_date", new Timestamp(calEnd.getTime()));
        leaveData.put("no_of_days", numberOfDays);
        leaveData.put("leaves_customer_id", customerId);
        leaveData.put("leaves_id", leaveId);
        return leaveData;
    }

    private void addLeaveCommentAndAdjustSubscription(String comments) {
        String creatorEmail = userEmail != null && !userEmail.trim().isEmpty() ? userEmail.trim() : "unknown";
        commentRepository.addComment(
                businessId,
                customerId,
                businessPrefix,
                ENTITY_TYPE_LEAVES,
                comments,
                creatorEmail,
                this::applyLeaveAdjustments,
                exception -> handleActionError("Comment error", exception)
        );
    }

    private void applyLeaveAdjustments() {
        leaveRepository.applyLeaveAdjustments(
                businessId,
                customerId,
                numberOfDays,
                () -> {
                    Toast.makeText(this, "Leave applied successfully", Toast.LENGTH_SHORT).show();
                    finish();
                },
                exception -> handleActionError("Update error", exception)
        );
    }

    private void completeWithMessage(String message) {
        btnSubmit.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void handleActionError(String prefix, Exception exception) {
        btnSubmit.setEnabled(true);
        String details = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "Unknown error";
        Toast.makeText(this, prefix + ": " + details, Toast.LENGTH_LONG).show();
    }

    private List<Timestamp> readTimestampList(Object rawValues) {
        List<Timestamp> values = new ArrayList<>();
        if (!(rawValues instanceof List<?>)) {
            return values;
        }
        for (Object value : (List<?>) rawValues) {
            if (value instanceof Timestamp) {
                values.add((Timestamp) value);
            }
        }
        return values;
    }

    private void setCalendarToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private static final class SubscriptionWindow {
        private final Timestamp minStart;
        private final Timestamp maxEnd;

        private SubscriptionWindow(Timestamp minStart, Timestamp maxEnd) {
            this.minStart = minStart;
            this.maxEnd = maxEnd;
        }
    }
}
