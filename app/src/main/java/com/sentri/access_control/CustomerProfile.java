package com.sentri.access_control;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.CustomerRepository;
import com.sentri.access_control.repositories.FirestoreCustomerRepository;
import com.sentri.access_control.repositories.FirestoreShiftRepository;
import com.sentri.access_control.repositories.ShiftRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CustomerProfile extends AppCompatActivity {
    private ImageView ivBack;
    private ImageView ivProfile;
    private ImageView ivAadharPhoto;
    private ImageView ivMessage;
    private ImageView ivNotification;
    private ImageView ivCall;

    private TextView tvBusinessName;
    private TextView tvCustomerName;
    private TextView tvStatus;
    private TextView tvCustomerId;
    private TextView tvCardNumbers;
    private TextView tvContactNumber;
    private TextView tvEmergencyContactNumber;
    private TextView tvPaymentLink;
    private TextView tvShiftDetails;
    private LinearLayout layoutShiftList;

    private SwitchCompat switchStatus;
    private Button btnReceivePayment;
    private Button btnAddShiftAction;
    private Button btnLeaves;
    private Button btnLoadHistory;
    private Button btnEditCustomer;

    private CustomerRepository customerRepository;
    private ShiftRepository shiftRepository;

    private String businessDocId;
    private String customerDocId;

    private static final long SHIFT_EXPIRY_SOON_DAYS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_profile);

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        customerRepository = new FirestoreCustomerRepository(firestore);
        shiftRepository = new FirestoreShiftRepository(firestore);

        businessDocId = getIntent().getStringExtra("businessDocId");
        customerDocId = getIntent().getStringExtra("customerDocId");
        if (businessDocId == null || customerDocId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupStaticActions();
        loadCustomerProfile();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        ivProfile = findViewById(R.id.ivProfile);
        ivAadharPhoto = findViewById(R.id.ivAadharPhoto);
        ivMessage = findViewById(R.id.ivMessage);
        ivNotification = findViewById(R.id.ivNotification);
        ivCall = findViewById(R.id.ivCall);

        tvBusinessName = findViewById(R.id.tv_business_name);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvStatus = findViewById(R.id.tvStatus);
        switchStatus = findViewById(R.id.switchStatus);

        tvCustomerId = findViewById(R.id.tvCustomerId);
        tvCardNumbers = findViewById(R.id.tvCardNumbers);
        tvContactNumber = findViewById(R.id.tvContactNumber);
        tvEmergencyContactNumber = findViewById(R.id.tvEmergencyContactNumber);
        tvPaymentLink = findViewById(R.id.tvPaymentLink);
        tvShiftDetails = findViewById(R.id.tvShiftDetails);
        layoutShiftList = findViewById(R.id.layoutShiftList);

        btnReceivePayment = findViewById(R.id.btnReceivePayment);
        btnAddShiftAction = findViewById(R.id.btnAddShiftAction);
        btnLeaves = findViewById(R.id.btnApplyLeave);
        btnLoadHistory = findViewById(R.id.btnHistory);
        btnEditCustomer = findViewById(R.id.btnEditCustomer);
    }

    private void setupStaticActions() {
        ivBack.setOnClickListener(v -> finish());
        ivNotification.setOnClickListener(v -> startActivity(new Intent(this, AppNotifications.class)));

        btnLoadHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerHistory.class);
            intent.putExtra("businessId", businessDocId);
            intent.putExtra("customerId", customerDocId);
            startActivity(intent);
        });

        btnReceivePayment.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerPayment.class);
            intent.putExtra("businessDocId", businessDocId);
            intent.putExtra("customerDocId", customerDocId);
            startActivity(intent);
        });

        btnLeaves.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerLeave.class);
            intent.putExtra("businessDocId", businessDocId);
            intent.putExtra("customerDocId", customerDocId);
            startActivity(intent);
        });

        btnAddShiftAction.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerShift.class);
            intent.putExtra("businessDocId", businessDocId);
            intent.putExtra("customerDocId", customerDocId);
            startActivity(intent);
        });

        btnEditCustomer.setOnClickListener(v -> {
            PrefsManager prefs = new PrefsManager(this);
            String access = prefs.getCurrentBizAccessLevel();
            boolean canEdit = access != null
                    && (access.equalsIgnoreCase("admin") || access.equalsIgnoreCase("manager"));
            if (!canEdit) {
                Toast.makeText(this, "You don't have permission to edit this profile", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AddCustomer.class);
            intent.putExtra("isEdit", true);
            intent.putExtra("businessDocId", businessDocId);
            intent.putExtra("customerDocId", customerDocId);
            startActivity(intent);
        });

        ivMessage.setOnClickListener(v -> {
            String number = tvContactNumber.getText().toString().trim();
            if (number.isEmpty() || "-".equals(number)) {
                Toast.makeText(this, "WhatsApp number not available", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                getPackageManager().getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://api.whatsapp.com/send?phone=" + number)));
            } catch (PackageManager.NameNotFoundException exception) {
                Toast.makeText(this,
                        "WhatsApp is not installed on this device.",
                        Toast.LENGTH_LONG).show();
            }
        });

        ivCall.setOnClickListener(v -> {
            CharSequence[] options = {"Contact Number", "Emergency Contact"};
            new AlertDialog.Builder(this)
                    .setTitle("Call Customer")
                    .setItems(options, (DialogInterface dialog, int which) -> {
                        String dialNumber = (which == 0)
                                ? tvContactNumber.getText().toString()
                                : tvEmergencyContactNumber.getText().toString();
                        if (dialNumber.isEmpty() || "-".equals(dialNumber)) {
                            Toast.makeText(this, "Number not available", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialNumber)));
                    })
                    .show();
        });
    }

    private void loadCustomerProfile() {
        customerRepository.fetchCustomer(
                businessDocId,
                customerDocId,
                doc -> {
                    if (!doc.exists()) {
                        finish();
                        return;
                    }

                    String customerName = doc.getString("customer_name");
                    boolean customerStatus = Boolean.TRUE.equals(doc.getBoolean("customer_status"));
                    tvCustomerName.setText(customerName);
                    tvBusinessName.setText((customerName != null ? customerName : "Customer") + "'s Profile");

                    String profileUrl = doc.getString("customer_photo");
                    if (profileUrl != null && !profileUrl.isEmpty()) {
                        Glide.with(this).load(profileUrl).circleCrop().into(ivProfile);
                    }

                    String aadharUrl = doc.getString("customer_aadhar_photo");
                    if (aadharUrl != null && !aadharUrl.isEmpty()) {
                        Glide.with(this)
                                .load(aadharUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .into(ivAadharPhoto);
                        ivAadharPhoto.setVisibility(View.VISIBLE);
                    } else {
                        ivAadharPhoto.setVisibility(View.GONE);
                    }

                    View.OnClickListener imageClick = v -> {
                        String url = (v.getId() == R.id.ivProfile) ? profileUrl : aadharUrl;
                        if (url == null || url.isEmpty()) {
                            return;
                        }
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(url), "image/*");
                        startActivity(intent);
                    };
                    ivProfile.setOnClickListener(imageClick);
                    ivAadharPhoto.setOnClickListener(imageClick);

                    tvCustomerId.setText("ID - " + safeString(doc.getString("customer_id")));

                    String whatsapp = doc.getString("customer_whatsapp");
                    tvContactNumber.setText(whatsapp != null && !whatsapp.isEmpty() ? whatsapp : "-");

                    String emergency = doc.getString("customer_emergency_contact");
                    tvEmergencyContactNumber.setText(emergency != null && !emergency.isEmpty() ? emergency : "-");

                    String rate = doc.getString("customer_current_payment_rate");
                    if (rate == null || rate.isEmpty()) {
                        rate = "0";
                    }
                    tvPaymentLink.setText("Rs " + rate.trim() + " per month");

                    String accessLevel = new PrefsManager(this).getCurrentBizAccessLevel();
                    boolean canEdit = accessLevel != null
                            && (accessLevel.equalsIgnoreCase("admin") || accessLevel.equalsIgnoreCase("manager"));
                    btnEditCustomer.setVisibility(canEdit ? View.VISIBLE : View.GONE);

                    List<String> currentShiftIds = readStringList(doc.get("customer_current_shift_id"));
                    List<Timestamp> subscriptionEndDates = readTimestampList(doc.get("customer_subscription_end_date"));

                    boolean isSubscriptionExpired = isSubscriptionExpired(subscriptionEndDates);
                    if (isSubscriptionExpired) {
                        switchStatus.setChecked(false);
                        switchStatus.setEnabled(false);
                        Toast.makeText(this,
                                "Subscription expired. Please renew first.",
                                Toast.LENGTH_LONG).show();
                    } else if (currentShiftIds.isEmpty()) {
                        switchStatus.setChecked(false);
                        switchStatus.setEnabled(false);
                        Toast.makeText(this,
                                "No shifts assigned. Please add a shift first.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        switchStatus.setEnabled(true);
                    }

                    if (!customerStatus) {
                        btnReceivePayment.setVisibility(View.GONE);
                        btnLeaves.setVisibility(View.GONE);
                    } else {
                        btnReceivePayment.setVisibility(View.VISIBLE);
                        btnLeaves.setVisibility(View.VISIBLE);
                    }

                    switchStatus.setOnCheckedChangeListener(null);
                    switchStatus.setChecked(customerStatus);
                    updateStatusText(customerStatus);

                    List<String> shiftIdsForToggle = new ArrayList<>(currentShiftIds);
                    switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            updateStatusText(true);
                            return;
                        }

                        new AlertDialog.Builder(this)
                                .setTitle("Deactivate Customer?")
                                .setMessage("This will remove all subscriptions and shifts assigned to the customer. Continue?")
                                .setPositiveButton("Yes", (dialog, which) -> deactivateCustomerAndShifts(shiftIdsForToggle))
                                .setNegativeButton("No", (dialog, which) -> switchStatus.setChecked(true))
                                .setCancelable(false)
                                .show();
                    });

                    setupClickableStyles();
                    setupCardActions(doc, customerStatus);
                    loadShiftDetails(currentShiftIds);
                },
                e -> Toast.makeText(this,
                        "Error loading profile: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private void setupCardActions(DocumentSnapshot doc, boolean customerStatus) {
        String currentCardId = doc.getString("customer_current_card_id");
        if (!Objects.equals(currentCardId, "")) {
            tvCardNumbers.setText("Card Id: " + currentCardId);
        }

        if (currentCardId == null || currentCardId.trim().isEmpty()) {
            tvCardNumbers.setOnClickListener(v -> {
                if (!customerStatus) {
                    Toast.makeText(this, "Activate Customer first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, CardAssignment.class);
                intent.putExtra("business_id", businessDocId);
                intent.putExtra("customer_id", customerDocId);
                intent.putExtra("new", true);
                startActivity(intent);
            });
            return;
        }

        tvCardNumbers.setOnClickListener(v -> {
            String cardIdForIntent = currentCardId;
            new AlertDialog.Builder(this)
                    .setTitle("Card options")
                    .setMessage("Return or change the card?\n\nCard: " + cardIdForIntent)
                    .setPositiveButton("Return Card", (dialog, which) -> {
                        Intent intent = new Intent(this, CardAssignment.class);
                        intent.putExtra("return", true);
                        intent.putExtra("business_id", businessDocId);
                        intent.putExtra("customer_id", customerDocId);
                        intent.putExtra("card_id", cardIdForIntent);
                        startActivity(intent);
                    })
                    .setNeutralButton("Replace", (dialog, which) -> {
                        Intent intent = new Intent(this, CardAssignment.class);
                        intent.putExtra("replace", true);
                        intent.putExtra("business_id", businessDocId);
                        intent.putExtra("customer_id", customerDocId);
                        intent.putExtra("card_id", cardIdForIntent);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupClickableStyles() {
        if (tvCardNumbers != null) {
            tvCardNumbers.setTextColor(getResources().getColor(R.color.blue));
            tvCardNumbers.setPaintFlags(tvCardNumbers.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }

        if (tvContactNumber != null) {
            tvContactNumber.setTextColor(getResources().getColor(R.color.blue));
            tvContactNumber.setPaintFlags(tvContactNumber.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
        if (tvEmergencyContactNumber != null) {
            tvEmergencyContactNumber.setTextColor(getResources().getColor(R.color.blue));
            tvEmergencyContactNumber.setPaintFlags(tvEmergencyContactNumber.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    private void deactivateCustomerAndShifts(List<String> shiftIds) {
        customerRepository.deactivateCustomer(
                businessDocId,
                customerDocId,
                () -> shiftRepository.markShiftsInactive(
                        businessDocId,
                        shiftIds,
                        () -> {
                            Toast.makeText(this,
                                    "Customer deactivated and assigned shifts marked inactive",
                                    Toast.LENGTH_SHORT).show();
                            refreshActivityUI();
                        },
                        e -> {
                            Toast.makeText(this,
                                    "Customer deactivated but failed to update shifts: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            refreshActivityUI();
                        }
                ),
                e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    switchStatus.setChecked(true);
                }
        );
    }

    private void loadShiftDetails(List<String> shiftIds) {
        if (shiftIds == null || shiftIds.isEmpty()) {
            renderNoShiftState("No shifts assigned");
            return;
        }

        shiftRepository.fetchShiftsByIds(
                businessDocId,
                shiftIds,
                shiftDocs -> {
                    if (shiftDocs == null || shiftDocs.isEmpty()) {
                        renderNoShiftState("No shifts assigned");
                        return;
                    }

                    List<DocumentSnapshot> docs = new ArrayList<>(shiftDocs);
                    docs.sort(new Comparator<DocumentSnapshot>() {
                        @Override
                        public int compare(DocumentSnapshot a, DocumentSnapshot b) {
                            Timestamp ta = a.getTimestamp("shift_start_time");
                            Timestamp tb = b.getTimestamp("shift_start_time");
                            if (ta == null && tb == null) {
                                return 0;
                            }
                            if (ta == null) {
                                return 1;
                            }
                            if (tb == null) {
                                return -1;
                            }
                            return tb.compareTo(ta);
                        }
                    });

                    showShiftWarnings(docs);
                    renderShiftCards(docs);
                },
                e -> renderNoShiftState("Error loading shifts: " + e.getMessage())
        );
    }

    private void showShiftWarnings(List<DocumentSnapshot> docs) {
        if (hasExpiringShifts(docs)) {
            Toast.makeText(this, "One or more current shifts are about to expire", Toast.LENGTH_LONG).show();
        }
        if (hasOverlappingShifts(docs)) {
            Toast.makeText(this, "Warning: overlapping active shift date ranges found", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasExpiringShifts(List<DocumentSnapshot> docs) {
        long now = System.currentTimeMillis();
        long threshold = now + TimeUnit.DAYS.toMillis(SHIFT_EXPIRY_SOON_DAYS);
        for (DocumentSnapshot doc : docs) {
            Timestamp endTs = doc.getTimestamp("shift_end_time");
            if (endTs == null) {
                continue;
            }
            long endMs = endTs.toDate().getTime();
            if (endMs >= now && endMs <= threshold) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOverlappingShifts(List<DocumentSnapshot> docs) {
        for (int i = 0; i < docs.size(); i++) {
            Timestamp aStart = docs.get(i).getTimestamp("shift_start_time");
            Timestamp aEnd = docs.get(i).getTimestamp("shift_end_time");
            if (aStart == null || aEnd == null) {
                continue;
            }
            long aStartMs = aStart.toDate().getTime();
            long aEndMs = aEnd.toDate().getTime();
            for (int j = i + 1; j < docs.size(); j++) {
                Timestamp bStart = docs.get(j).getTimestamp("shift_start_time");
                Timestamp bEnd = docs.get(j).getTimestamp("shift_end_time");
                if (bStart == null || bEnd == null) {
                    continue;
                }
                long bStartMs = bStart.toDate().getTime();
                long bEndMs = bEnd.toDate().getTime();
                if (aStartMs <= bEndMs && bStartMs <= aEndMs) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderNoShiftState(String message) {
        tvShiftDetails.setVisibility(View.VISIBLE);
        tvShiftDetails.setText(message);
        if (layoutShiftList != null) {
            layoutShiftList.removeAllViews();
        }
    }

    private void renderShiftCards(List<DocumentSnapshot> docs) {
        if (layoutShiftList == null) {
            return;
        }

        tvShiftDetails.setVisibility(View.GONE);
        layoutShiftList.removeAllViews();

        for (int i = 0; i < docs.size(); i++) {
            DocumentSnapshot shiftDoc = docs.get(i);
            String shiftId = shiftDoc.getId();
            String seat = safeString(shiftDoc.getString("shift_seat"));
            Timestamp startTs = shiftDoc.getTimestamp("shift_start_time");
            Timestamp endTs = shiftDoc.getTimestamp("shift_end_time");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));

            TextView details = new TextView(this);
            details.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            details.setTextColor(getResources().getColor(R.color.blue));
            details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            details.setText(buildShiftText(seat, startTs, endTs));

            ImageButton deleteBtn = new ImageButton(this);
            deleteBtn.setImageResource(R.drawable.ic_delete_red);
            deleteBtn.setBackgroundColor(Color.TRANSPARENT);
            deleteBtn.setContentDescription("Delete shift mapping");
            deleteBtn.setOnClickListener(v -> showDeleteShiftConfirmation(shiftId, seat, startTs, endTs));

            row.addView(details);
            row.addView(deleteBtn);
            layoutShiftList.addView(row);

            if (i < docs.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                ));
                divider.setBackgroundColor(Color.parseColor("#44FFFFFF"));
                layoutShiftList.addView(divider);
            }
        }
    }

    private void showDeleteShiftConfirmation(String shiftId, String seat, Timestamp startTs, Timestamp endTs) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Shift")
                .setMessage("Remove this shift?\n\n" + buildShiftText(seat, startTs, endTs))
                .setPositiveButton("Delete", (dialog, which) -> removeShift(shiftId, seat, startTs, endTs))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String buildShiftText(String seat, Timestamp startTs, Timestamp endTs) {
        String startDate = startTs != null
                ? DateFormat.format("d MMM, yyyy", startTs.toDate()).toString()
                : "-";
        String endDate = endTs != null
                ? DateFormat.format("d MMM, yyyy", endTs.toDate()).toString()
                : "-";
        String startTime = startTs != null
                ? DateFormat.format("hh:mm a", startTs.toDate()).toString()
                : "-";
        String endTime = endTs != null
                ? DateFormat.format("hh:mm a", endTs.toDate()).toString()
                : "-";
        long dayCount = calculateDayCount(startTs, endTs);

        return "Date: " + startDate + " - " + endDate
                + "\nTiming: " + startTime + " - " + endTime
                + "\nSeat: " + seat + ", Days: " + dayCount;
    }

    private long calculateDayCount(Timestamp startTs, Timestamp endTs) {
        if (startTs == null || endTs == null) {
            return 0L;
        }
        long diff = endTs.toDate().getTime() - startTs.toDate().getTime();
        if (diff <= 0L) {
            return 0L;
        }
        return diff / (24L * 60L * 60L * 1000L);
    }

    private void removeShift(String shiftId, String seat, Timestamp startTs, Timestamp endTs) {
        customerRepository.removeCustomerShiftAssignment(
                businessDocId,
                customerDocId,
                shiftId,
                seat,
                startTs,
                endTs,
                () -> shiftRepository.markShiftInactive(
                        businessDocId,
                        shiftId,
                        () -> {
                            Toast.makeText(this, "Shift removed", Toast.LENGTH_SHORT).show();
                            ensureCustomerStatusAfterShiftDelete();
                        },
                        e -> Toast.makeText(this,
                                "Shift update failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                ),
                e -> Toast.makeText(this,
                        "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void ensureCustomerStatusAfterShiftDelete() {
        customerRepository.fetchCustomer(
                businessDocId,
                customerDocId,
                snapshot -> {
                    List<String> remainingShifts = readStringList(snapshot.get("customer_current_shift_id"));
                    if (remainingShifts.isEmpty()) {
                        customerRepository.deactivateCustomer(
                                businessDocId,
                                customerDocId,
                                this::refreshActivityUI,
                                e -> Toast.makeText(this,
                                        "Failed to deactivate customer: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show()
                        );
                    } else {
                        refreshActivityUI();
                    }
                },
                e -> Toast.makeText(this,
                        "Failed to refresh customer state: " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private boolean isSubscriptionExpired(List<Timestamp> endDates) {
        Timestamp latest = null;
        for (Timestamp timestamp : endDates) {
            if (latest == null || (timestamp != null && timestamp.toDate().after(latest.toDate()))) {
                latest = timestamp;
            }
        }
        return latest != null && latest.toDate().before(new Date());
    }

    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<String> output = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item != null) {
                output.add(String.valueOf(item));
            }
        }
        return output;
    }

    private List<Timestamp> readTimestampList(Object raw) {
        if (!(raw instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Timestamp> output = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof Timestamp) {
                output.add((Timestamp) item);
            }
        }
        return output;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private void refreshActivityUI() {
        switchStatus.setChecked(false);
        switchStatus.setEnabled(false);
        Intent intent = getIntent();
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void updateStatusText(boolean active) {
        if (active) {
            tvStatus.setText("Active");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            tvStatus.setText("Inactive");
            tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"));
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, CustomerList.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
