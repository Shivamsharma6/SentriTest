package com.sentri.access_control;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sentri.access_control.adapters.PaymentAdapter;
import com.sentri.access_control.models.PaymentItem;
import com.sentri.access_control.repositories.CustomerRepository;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;
import com.sentri.access_control.repositories.FirestoreCustomerRepository;
import com.sentri.access_control.repositories.FirestorePaymentRepository;
import com.sentri.access_control.repositories.FirestoreShiftRepository;
import com.sentri.access_control.repositories.PaymentRepository;
import com.sentri.access_control.repositories.ShiftRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class CustomerPayment extends AppCompatActivity {

    private ImageView ivBack;
    private Switch switchSplit;
    private RadioButton rbCash;
    private RadioButton rbCard;
    private RadioButton rbUpi;
    private EditText etCashAmount;
    private EditText etCardAmount;
    private EditText etUpiAmount;
    private Spinner spinnerType;
    private EditText etDescription;
    private EditText etRate;
    private Button btnAddCash;
    private Button btnAddCard;
    private Button btnAddUpi;
    private Button btnSubmit;
    private RecyclerView rvHistory;
    private Switch switchRenew;
    private TextView renewText;
    private TextView tvRenewStartDate;
    private TextView tvRenewEndDate;

    private CommentRepository commentRepository;
    private CustomerRepository customerRepository;
    private ShiftRepository shiftRepository;
    private PaymentRepository paymentRepository;

    private String businessId;
    private String customerId;
    private String businessPrefix;
    private String userEmail;
    private String shiftSeat;
    private String shiftComments;

    private long shiftStartMs;
    private long shiftEndMs;

    private boolean calledFromShift;

    // Renewal subscription date range (start/end of new subscription).
    private long renewStartMs = 0L;
    private long renewEndMs = 0L;
    private long latestShiftEndMs = 0L;

    // Keeps split amount insertion order for stable method string generation.
    private final Map<String, String> splitAmounts = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_payment);

        bindViews();
        initState();
        setupUi();
        setupHistory();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        switchSplit = findViewById(R.id.switchSplit);
        rbCash = findViewById(R.id.rbCash);
        rbCard = findViewById(R.id.rbCard);
        rbUpi = findViewById(R.id.rbUpi);
        etCashAmount = findViewById(R.id.etCashAmount);
        etCardAmount = findViewById(R.id.etCardAmount);
        etUpiAmount = findViewById(R.id.etUpiAmount);
        btnAddCash = findViewById(R.id.btnAddCash);
        btnAddCard = findViewById(R.id.btnAddCard);
        btnAddUpi = findViewById(R.id.btnAddUpi);
        spinnerType = findViewById(R.id.spinnerPaymentType);
        etDescription = findViewById(R.id.etDescription);
        etRate = findViewById(R.id.etRate);
        btnSubmit = findViewById(R.id.btnSubmit);
        rvHistory = findViewById(R.id.recyclerPaymentHistory);
        switchRenew = findViewById(R.id.switchRenew);
        renewText = findViewById(R.id.renewText);
        tvRenewStartDate = findViewById(R.id.tvRenewStartDate);
        tvRenewEndDate = findViewById(R.id.tvRenewEndDate);
    }

    private void initState() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        commentRepository = new FirestoreCommentRepository(firestore);
        customerRepository = new FirestoreCustomerRepository(firestore);
        shiftRepository = new FirestoreShiftRepository(firestore);
        paymentRepository = new FirestorePaymentRepository(firestore);

        Intent intent = getIntent();
        businessId = intent.getStringExtra("businessDocId");
        customerId = intent.getStringExtra("customerDocId");
        shiftStartMs = intent.getLongExtra("shiftStartMs", 0L);
        shiftEndMs = intent.getLongExtra("shiftEndMs", 0L);
        shiftSeat = intent.getStringExtra("shiftSeat");
        shiftComments = intent.getStringExtra("shiftComments");
        calledFromShift = intent.hasExtra("shiftStartMs");

        if (businessId == null || customerId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userEmail = new PrefsManager(this).getUserEmail();
    }

    private void setupUi() {
        ivBack.setOnClickListener(v -> finish());

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Credit", "Debit"}
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        switchSplit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            splitAmounts.clear();
            resetSplitInputs();
            updateAllRowsUI();
        });

        rbCash.setOnCheckedChangeListener((button, checked) -> updateRowUI("Cash", checked));
        rbCard.setOnCheckedChangeListener((button, checked) -> updateRowUI("Card", checked));
        rbUpi.setOnCheckedChangeListener((button, checked) -> updateRowUI("UPI", checked));

        btnAddCash.setOnClickListener(v -> addSplitEntry("Cash", rbCash, etCashAmount, btnAddCash));
        btnAddCard.setOnClickListener(v -> addSplitEntry("Card", rbCard, etCardAmount, btnAddCard));
        btnAddUpi.setOnClickListener(v -> addSplitEntry("UPI", rbUpi, etUpiAmount, btnAddUpi));

        switchRenew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (calledFromShift && isChecked) {
                Toast.makeText(this, "No active subscription found. Please add one.", Toast.LENGTH_LONG).show();
                switchRenew.setChecked(false);
                switchRenew.setVisibility(View.GONE);
                renewText.setVisibility(View.GONE);
                return;
            }

            if (isChecked) {
                loadLatestShiftEndAndPickRenewDates();
            } else {
                resetRenewDates();
            }
        });

        btnSubmit.setOnClickListener(v -> onSubmit());
        updateAllRowsUI();
    }

    private void setupHistory() {
        List<PaymentItem> historyList = new ArrayList<>();
        PaymentAdapter adapter = new PaymentAdapter(historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
        loadPaymentHistory(adapter);
    }

    private void onSubmit() {
        String rateText = getText(etRate);
        Double rateValue = parsePositiveDouble(rateText);
        if (rateValue == null) {
            etRate.setError("Enter a valid rate");
            return;
        }

        PaymentInput paymentInput;
        try {
            paymentInput = getPaymentInput();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (switchRenew.isChecked()) {
            processRenewSubscription(rateText, rateValue, paymentInput);
            return;
        }

        String description = getText(etDescription);
        if (description.isEmpty()) {
            etDescription.setError("Required");
            return;
        }

        String paymentType = spinnerType.getSelectedItem().toString();

        btnSubmit.setEnabled(false);
        paymentRepository.fetchBusinessPrefix(
                businessId,
                prefix -> {
                    businessPrefix = prefix != null ? prefix.trim() : "";
                    if (businessPrefix.isEmpty()) {
                        handleActionError(new IllegalStateException("Business prefix missing"));
                        return;
                    }
                    addCommentIfAny("payment", description,
                            () -> createPaymentRecord(paymentInput, paymentType, description, rateText));
                },
                this::handleActionError
        );
    }

    private void processRenewSubscription(String rateText, double rateValue, PaymentInput paymentInput) {
        String description = getText(etDescription);
        if (description.isEmpty()) {
            etDescription.setError("Required");
            return;
        }

        if (renewStartMs <= 0L || renewEndMs <= 0L) {
            Toast.makeText(this, "Please select subscription start and end dates for renewal", Toast.LENGTH_LONG).show();
            return;
        }
        if (renewEndMs < renewStartMs) {
            Toast.makeText(this, "End date cannot be before start date", Toast.LENGTH_LONG).show();
            return;
        }

        String paymentType = spinnerType.getSelectedItem().toString();
        btnSubmit.setEnabled(false);

        paymentRepository.fetchBusinessPrefix(
                businessId,
                prefix -> {
                    businessPrefix = prefix != null ? prefix.trim() : "";
                    if (businessPrefix.isEmpty()) {
                        handleActionError(new IllegalStateException("Business prefix missing"));
                        return;
                    }
                    addCommentIfAny("payment", description,
                            () -> createRenewPaymentAndExtendShifts(rateText, rateValue, paymentInput, paymentType, description));
                },
                this::handleActionError
        );
    }

    private void createRenewPaymentAndExtendShifts(String rateText,
                                                   double rateValue,
                                                   PaymentInput paymentInput,
                                                   String paymentType,
                                                   String description) {
        paymentRepository.fetchNextPaymentId(
                businessId,
                businessPrefix,
                paymentId -> {
                    Map<String, Object> payment = buildPaymentMap(paymentId, paymentInput, paymentType, description, rateText);
                    paymentRepository.createPayment(
                            businessId,
                            paymentId,
                            payment,
                            () -> extendSubscriptionAfterRenew(rateText, rateValue, paymentInput.amountValue),
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private void extendSubscriptionAfterRenew(String rateText, double rateValue, double paidAmount) {
        if (renewStartMs <= 0L || renewEndMs <= 0L || renewEndMs < renewStartMs) {
            Toast.makeText(this, "Invalid subscription dates for renewal", Toast.LENGTH_LONG).show();
            btnSubmit.setEnabled(true);
            return;
        }

        final Date renewStartDate = new Date(renewStartMs);
        final Date renewEndDate = new Date(renewEndMs);

        customerRepository.fetchCustomer(
                businessId,
                customerId,
                customerDoc -> {
                    List<String> shiftIds = readStringList(customerDoc.get("customer_current_shift_id"));
                    if (shiftIds.isEmpty()) {
                        // No active shifts; just update payment-related fields.
                        updateCustomerAfterPaymentOnly(rateText);
                        return;
                    }

                    shiftRepository.fetchShiftsByIds(
                            businessId,
                            shiftIds,
                            shiftDocs -> {
                                if (shiftDocs == null || shiftDocs.isEmpty()) {
                                    updateCustomerAfterPaymentOnly(rateText);
                                    return;
                                }
                                List<DocumentSnapshot> activeShiftDocs = new ArrayList<>();
                                List<String> oldShiftIds = new ArrayList<>();
                                for (DocumentSnapshot shiftDoc : shiftDocs) {
                                    String seat = shiftDoc.getString("shift_seat");
                                    Timestamp startTs = shiftDoc.getTimestamp("shift_start_time");
                                    Timestamp endTs = shiftDoc.getTimestamp("shift_end_time");
                                    if (seat == null || startTs == null || endTs == null) {
                                        continue;
                                    }
                                    oldShiftIds.add(shiftDoc.getId());
                                    activeShiftDocs.add(shiftDoc);
                                }

                                if (activeShiftDocs.isEmpty()) {
                                    updateCustomerAfterPaymentOnly(rateText);
                                    return;
                                }

                                List<String> newShiftIds = new ArrayList<>();
                                List<String> newSeats = new ArrayList<>();
                                List<Timestamp> newShiftStarts = new ArrayList<>();
                                List<Timestamp> newShiftEnds = new ArrayList<>();

                                createRenewedShiftsSequentially(
                                        activeShiftDocs,
                                        0,
                                        renewStartDate,
                                        renewEndDate,
                                        rateText,
                                        newShiftIds,
                                        newSeats,
                                        newShiftStarts,
                                        newShiftEnds,
                                        () -> deactivateOldAndReplaceCurrentAssignments(
                                                oldShiftIds,
                                                newShiftIds,
                                                newSeats,
                                                newShiftStarts,
                                                newShiftEnds,
                                                rateText
                                        )
                                );
                            },
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private void createRenewedShiftsSequentially(List<DocumentSnapshot> activeShiftDocs,
                                                 int index,
                                                 Date renewStartDate,
                                                 Date renewEndDate,
                                                 String rateText,
                                                 List<String> newShiftIds,
                                                 List<String> newSeats,
                                                 List<Timestamp> newShiftStarts,
                                                 List<Timestamp> newShiftEnds,
                                                 Runnable onComplete) {
        if (index >= activeShiftDocs.size()) {
            onComplete.run();
            return;
        }

        DocumentSnapshot shiftDoc = activeShiftDocs.get(index);
        String seat = shiftDoc.getString("shift_seat");
        Timestamp originalStartTs = shiftDoc.getTimestamp("shift_start_time");
        Timestamp originalEndTs = shiftDoc.getTimestamp("shift_end_time");
        if (seat == null || originalStartTs == null || originalEndTs == null) {
            createRenewedShiftsSequentially(
                    activeShiftDocs,
                    index + 1,
                    renewStartDate,
                    renewEndDate,
                    rateText,
                    newShiftIds,
                    newSeats,
                    newShiftStarts,
                    newShiftEnds,
                    onComplete
            );
            return;
        }

        Timestamp newStartTs = combineDateWithOriginalTime(renewStartDate, originalStartTs);
        Timestamp newEndTs = combineDateWithOriginalTime(renewEndDate, originalEndTs);

        shiftRepository.fetchNextShiftId(
                businessId,
                businessPrefix,
                newShiftId -> {
                    Map<String, Object> shift = new HashMap<>();
                    shift.put("created_at", FieldValue.serverTimestamp());
                    shift.put("created_by", userEmail);
                    shift.put("shift_business_id", businessId);
                    shift.put("shift_customer_id", customerId);
                    shift.put("shift_start_time", newStartTs);
                    shift.put("shift_end_time", newEndTs);
                    shift.put("shift_seat", seat);
                    shift.put("shift_payment_rate", rateText);
                    shift.put("shift_status", true);
                    shift.put("shift_id", newShiftId);

                    shiftRepository.createShift(
                            businessId,
                            newShiftId,
                            shift,
                            () -> {
                                newShiftIds.add(newShiftId);
                                newSeats.add(seat);
                                newShiftStarts.add(newStartTs);
                                newShiftEnds.add(newEndTs);
                                createRenewedShiftsSequentially(
                                        activeShiftDocs,
                                        index + 1,
                                        renewStartDate,
                                        renewEndDate,
                                        rateText,
                                        newShiftIds,
                                        newSeats,
                                        newShiftStarts,
                                        newShiftEnds,
                                        onComplete
                                );
                            },
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private Timestamp combineDateWithOriginalTime(Date baseDate, Timestamp originalTs) {
        Calendar newCal = Calendar.getInstance();
        newCal.setTime(baseDate);
        Calendar originalCal = Calendar.getInstance();
        originalCal.setTime(originalTs.toDate());
        newCal.set(Calendar.HOUR_OF_DAY, originalCal.get(Calendar.HOUR_OF_DAY));
        newCal.set(Calendar.MINUTE, originalCal.get(Calendar.MINUTE));
        newCal.set(Calendar.SECOND, originalCal.get(Calendar.SECOND));
        newCal.set(Calendar.MILLISECOND, originalCal.get(Calendar.MILLISECOND));
        return new Timestamp(newCal.getTime());
    }

    private void deactivateOldAndReplaceCurrentAssignments(List<String> oldShiftIds,
                                                           List<String> newShiftIds,
                                                           List<String> newSeats,
                                                           List<Timestamp> newShiftStarts,
                                                           List<Timestamp> newShiftEnds,
                                                           String rateText) {
        shiftRepository.markShiftsInactive(
                businessId,
                oldShiftIds,
                () -> {
                    String lastDate = DateFormat.format("d MMM, yyyy hh:mm a", new Date()).toString();
                    customerRepository.replaceCurrentShiftAssignments(
                            businessId,
                            customerId,
                            newShiftIds,
                            newSeats,
                            newShiftStarts,
                            newShiftEnds,
                            rateText,
                            lastDate,
                            () -> {
                                Toast.makeText(this, "Subscription renewed successfully", Toast.LENGTH_SHORT).show();
                                navigateToCustomerProfile();
                            },
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private void resetRenewDates() {
        renewStartMs = 0L;
        renewEndMs = 0L;
        tvRenewStartDate.setText("");
        tvRenewEndDate.setText("");
        tvRenewStartDate.setVisibility(View.GONE);
        tvRenewEndDate.setVisibility(View.GONE);
    }

    private void loadLatestShiftEndAndPickRenewDates() {
        resolveLatestActiveShiftEnd(
                latestEndMs -> {
                    latestShiftEndMs = latestEndMs;
                    showRenewDatePickers();
                },
                this::handleActionError
        );
    }

    private void resolveLatestActiveShiftEnd(Consumer<Long> onSuccess, Consumer<Exception> onError) {
        customerRepository.fetchCustomer(
                businessId,
                customerId,
                customerDoc -> {
                    List<String> shiftIds = readStringList(customerDoc.get("customer_current_shift_id"));
                    if (shiftIds.isEmpty()) {
                        onSuccess.accept(0L);
                        return;
                    }

                    shiftRepository.fetchShiftsByIds(
                            businessId,
                            shiftIds,
                            shiftDocs -> {
                                long latestEndMs = 0L;
                                for (DocumentSnapshot shiftDoc : shiftDocs) {
                                    Timestamp endTs = shiftDoc.getTimestamp("shift_end_time");
                                    if (endTs != null) {
                                        latestEndMs = Math.max(latestEndMs, endTs.toDate().getTime());
                                    }
                                }
                                onSuccess.accept(latestEndMs);
                            },
                            onError
                    );
                },
                onError
        );
    }

    private void showRenewDatePickers() {
        Calendar initial = Calendar.getInstance();
        if (latestShiftEndMs > 0L) {
            initial.setTimeInMillis(startOfDay(latestShiftEndMs));
        }
        long minStartMs = computeMinRenewStartMs();
        DatePickerDialog startDialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar startCal = Calendar.getInstance();
                    startCal.set(Calendar.YEAR, year);
                    startCal.set(Calendar.MONTH, month);
                    startCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    startCal.set(Calendar.HOUR_OF_DAY, 0);
                    startCal.set(Calendar.MINUTE, 0);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    renewStartMs = startCal.getTimeInMillis();
                    showRenewEndDatePicker(startCal);
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        );
        if (minStartMs > 0L) {
            startDialog.getDatePicker().setMinDate(minStartMs);
        }
        startDialog.show();
    }

    private void showRenewEndDatePicker(Calendar startCal) {
        Calendar endCal = (Calendar) startCal.clone();
        DatePickerDialog endDialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    endCal.set(Calendar.YEAR, year);
                    endCal.set(Calendar.MONTH, month);
                    endCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                    endCal.set(Calendar.SECOND, 59);
                    endCal.set(Calendar.MILLISECOND, 999);
                    renewEndMs = endCal.getTimeInMillis();
                    updateRenewDateLabels();
                },
                endCal.get(Calendar.YEAR),
                endCal.get(Calendar.MONTH),
                endCal.get(Calendar.DAY_OF_MONTH)
        );
        endDialog.getDatePicker().setMinDate(startCal.getTimeInMillis());
        endDialog.show();
    }

    private long computeMinRenewStartMs() {
        return 0L;
    }

    private void updateRenewDateLabels() {
        String startText = DateFormat.format("d MMM, yyyy", new Date(renewStartMs)).toString();
        String endText = DateFormat.format("d MMM, yyyy", new Date(renewEndMs)).toString();
        tvRenewStartDate.setText("Start Date: " + startText);
        tvRenewEndDate.setText("End Date: " + endText);
        tvRenewStartDate.setVisibility(View.VISIBLE);
        tvRenewEndDate.setVisibility(View.VISIBLE);
    }

    private void updateRowUI(String method, boolean checked) {
        boolean split = switchSplit.isChecked();

        EditText amountInput;
        Button addButton;
        if ("Cash".equals(method)) {
            amountInput = etCashAmount;
            addButton = btnAddCash;
            if (!split && checked) {
                rbCard.setChecked(false);
                rbUpi.setChecked(false);
            }
        } else if ("Card".equals(method)) {
            amountInput = etCardAmount;
            addButton = btnAddCard;
            if (!split && checked) {
                rbCash.setChecked(false);
                rbUpi.setChecked(false);
            }
        } else {
            amountInput = etUpiAmount;
            addButton = btnAddUpi;
            if (!split && checked) {
                rbCash.setChecked(false);
                rbCard.setChecked(false);
            }
        }

        if (checked) {
            amountInput.setVisibility(View.VISIBLE);
            addButton.setVisibility(split ? View.VISIBLE : View.GONE);
        } else {
            amountInput.setVisibility(View.GONE);
            addButton.setVisibility(View.GONE);
        }
    }

    private void updateAllRowsUI() {
        updateRowUI("Cash", rbCash.isChecked());
        updateRowUI("Card", rbCard.isChecked());
        updateRowUI("UPI", rbUpi.isChecked());
    }

    private void resetSplitInputs() {
        etCashAmount.setEnabled(true);
        etCardAmount.setEnabled(true);
        etUpiAmount.setEnabled(true);

        rbCash.setEnabled(true);
        rbCard.setEnabled(true);
        rbUpi.setEnabled(true);

        etCashAmount.setText("");
        etCardAmount.setText("");
        etUpiAmount.setText("");
    }

    private void addSplitEntry(String method, RadioButton radioButton, EditText amountInput, Button addButton) {
        String value = getText(amountInput);
        Double amount = parsePositiveDouble(value);
        if (amount == null) {
            amountInput.setError("Enter a valid amount");
            return;
        }

        splitAmounts.put(method, String.format(Locale.US, "%.2f", amount));
        amountInput.setEnabled(false);
        radioButton.setEnabled(false);
        addButton.setVisibility(View.GONE);
    }

    private static class PaymentInput {
        final String method;
        final String amountText;
        final double amountValue;

        PaymentInput(String method, String amountText, double amountValue) {
            this.method = method;
            this.amountText = amountText;
            this.amountValue = amountValue;
        }
    }

    private PaymentInput getPaymentInput() {
        if (switchSplit.isChecked()) {
            if (splitAmounts.isEmpty()) {
                throw new IllegalArgumentException("Add at least one split entry");
            }

            double sum = 0.0;
            StringBuilder methodBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : splitAmounts.entrySet()) {
                if (methodBuilder.length() > 0) {
                    methodBuilder.append(",");
                }
                methodBuilder.append(entry.getKey()).append(":").append(entry.getValue());
                sum += Double.parseDouble(entry.getValue());
            }

            String amountText = String.format(Locale.US, "%.2f", sum);
            return new PaymentInput(methodBuilder.toString(), amountText, sum);
        }

        String method;
        String value;
        if (rbCash.isChecked()) {
            method = "Cash";
            value = getText(etCashAmount);
        } else if (rbCard.isChecked()) {
            method = "Card";
            value = getText(etCardAmount);
        } else if (rbUpi.isChecked()) {
            method = "UPI";
            value = getText(etUpiAmount);
        } else {
            throw new IllegalArgumentException("Select a payment method");
        }

        Double amount = parsePositiveDouble(value);
        if (amount == null) {
            throw new IllegalArgumentException("Enter an amount");
        }
        return new PaymentInput(method, String.format(Locale.US, "%.2f", amount), amount);
    }

    private void loadPaymentHistory(PaymentAdapter adapter) {
        paymentRepository.fetchCustomerPayments(
                businessId,
                customerId,
                snapshot -> {
                    List<PaymentItem> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Timestamp timestamp = doc.getTimestamp("created_at");
                        String date = timestamp != null
                                ? DateFormat.format("d MMM, yyyy hh:mm a", timestamp.toDate()).toString()
                                : "-";

                        String method = doc.getString("payment_method");
                        String processedBy = doc.getString("payment_processed_by");
                        String paymentType = doc.getString("payment_type");
                        String rate = doc.getString("payment_rate");
                        String amount = doc.getString("payment_amount");
                        boolean isCredit = paymentType == null || paymentType.trim().isEmpty()
                                || "Credit".equalsIgnoreCase(paymentType);

                        items.add(new PaymentItem(date, method, processedBy, paymentType, rate, amount, isCredit));
                    }
                    adapter.updateList(items);
                },
                this::handleActionError
        );
    }

    private void createPaymentRecord(PaymentInput paymentInput,
                                     String paymentType,
                                     String description,
                                     String rateText) {
        paymentRepository.fetchNextPaymentId(
                businessId,
                businessPrefix,
                paymentId -> {
                    Map<String, Object> payment = buildPaymentMap(paymentId, paymentInput, paymentType, description, rateText);
                    paymentRepository.createPayment(
                            businessId,
                            paymentId,
                            payment,
                            () -> {
                                if (calledFromShift) {
                                    createShiftRecord(rateText);
                                } else {
                                    updateCustomerAfterPaymentOnly(rateText);
                                }
                            },
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private Map<String, Object> buildPaymentMap(String paymentId,
                                                PaymentInput paymentInput,
                                                String paymentType,
                                                String description,
                                                String rateText) {
        Map<String, Object> payment = new HashMap<>();
        payment.put("created_at", FieldValue.serverTimestamp());
        payment.put("payment_business_id", businessId);
        payment.put("payment_customer_id", customerId);
        payment.put("payment_id", paymentId);
        payment.put("payment_method", paymentInput.method);
        payment.put("payment_amount", paymentInput.amountText);
        payment.put("payment_rate", rateText);
        payment.put("payment_type", paymentType);
        payment.put("payment_description", description);
        payment.put("payment_processed_by", userEmail);
        return payment;
    }

    private void createShiftRecord(String rateText) {
        shiftRepository.fetchNextShiftId(
                businessId,
                businessPrefix,
                shiftId -> {
                    Map<String, Object> shift = new HashMap<>();
                    shift.put("created_at", FieldValue.serverTimestamp());
                    shift.put("created_by", userEmail);
                    shift.put("shift_business_id", businessId);
                    shift.put("shift_customer_id", customerId);
                    shift.put("shift_start_time", new Timestamp(new Date(shiftStartMs)));
                    shift.put("shift_end_time", new Timestamp(new Date(shiftEndMs)));
                    shift.put("shift_seat", shiftSeat);
                    shift.put("shift_payment_rate", rateText);
                    shift.put("shift_status", true);
                    shift.put("shift_id", shiftId);

                    shiftRepository.createShift(
                            businessId,
                            shiftId,
                            shift,
                            () -> {
                                String lastDate = DateFormat.format("d MMM, yyyy hh:mm a", new Date()).toString();
                                customerRepository.appendShiftAssignment(
                                        businessId,
                                        customerId,
                                        shiftId,
                                        shiftSeat,
                                        new Timestamp(new Date(shiftStartMs)),
                                        new Timestamp(new Date(shiftEndMs)),
                                        rateText,
                                        lastDate,
                                        () -> addCommentIfAny("shift", shiftComments, this::navigateToCustomerProfile),
                                        this::handleActionError
                                );
                            },
                            this::handleActionError
                    );
                },
                this::handleActionError
        );
    }

    private void updateCustomerAfterPaymentOnly(String rateText) {
        String lastDate = DateFormat.format("d MMM, yyyy hh:mm a", new Date()).toString();
        customerRepository.updateCustomerAfterPayment(
                businessId,
                customerId,
                rateText,
                lastDate,
                () -> {
                    Toast.makeText(this, "Payment recorded and customer updated", Toast.LENGTH_SHORT).show();
                    navigateToCustomerProfile();
                },
                this::handleActionError
        );
    }

    private void addCommentIfAny(String entityType, String text, Runnable next) {
        String commentText = text != null ? text.trim() : "";
        if (commentText.isEmpty()) {
            next.run();
            return;
        }

        commentRepository.addComment(
                businessId,
                customerId,
                businessPrefix,
                entityType,
                commentText,
                userEmail != null ? userEmail : "unknown",
                next,
                this::handleActionError
        );
    }

    private List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (!(value instanceof List<?>)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private List<Timestamp> readTimestampList(Object value) {
        List<Timestamp> result = new ArrayList<>();
        if (!(value instanceof List<?>)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            if (item instanceof Timestamp) {
                result.add((Timestamp) item);
            }
        }
        return result;
    }

    private void navigateToCustomerProfile() {
        Intent intent = new Intent(this, CustomerProfile.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", customerId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void handleActionError(Exception exception) {
        btnSubmit.setEnabled(true);
        String message = exception.getMessage() != null ? exception.getMessage() : "Unknown error";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private Double parsePositiveDouble(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(rawValue.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private long startOfDay(long valueMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(valueMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

}
