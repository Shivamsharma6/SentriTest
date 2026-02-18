package com.sentri.access_control;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreShiftRepository;
import com.sentri.access_control.repositories.ShiftRepository;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Field;

public class SeatSelection extends AppCompatActivity {

    // Business window & seats
    private int openHour = 0, closeHour = 24, maxSeats = 10;
    private String businessId, customerId;
    private Date startDate, endDate;
    private long windowStartMs, windowEndMs;

    // Preserve original start/end strings passed into this activity
    private String origStartDateStr = null;
    private String origEndDateStr = null;

    // Views
    private TableLayout table;
    private TableLayout tableUnallocatedShifts;
    private EditText etStart, etEnd;
    private Button btnSubmit;

    // Controls
    private Button btnZoomIn, btnZoomOut;
    private Switch switchUnallocatedSeat;
    private TextView tvSeatDisplay;

    // Sticky scrolls (your custom views)
    private com.sentri.access_control.ui.ObservableHorizontalScrollView hsvHeader, hsvContent;
    private com.sentri.access_control.ui.ObservableScrollView svLeft, svContent;
    private LinearLayout headerRow, leftSeatColumn;

    // Selection state
    private int selectedSeat = -1;
    private int startHourSel = -1;
    private int endHourSel   = -1;

    private boolean syncingH = false, syncingV = false;

    // Zoom state
    private int currentColDp = 80;
    private final int MIN_COL_DP = 36;
    private final int MAX_COL_DP = 220;
    private final int COL_STEP_DP = 12;
    private float baseTextSpHeader = 11f;
    private float baseTextSpCell   = 12f;

    // Preserve state across rebuilds
    private Map<String, CellState> savedCellState = new HashMap<>();

    // Dedicated saved selection fields (persist across rebuild)
    private int savedSelectedSeat = -1;
    private int savedStartHour = -1;
    private int savedEndHour = -1;
    private BusinessRepository businessRepository;
    private ShiftRepository shiftRepository;

    private static class CellState {
        boolean occupied;
        boolean customerShift;
        boolean selected;
        CharSequence text;
        String occupiedCustomerId;
    }

    private static class UnallocatedShiftRow {
        final String customerId;
        final String startTime;
        final String endTime;

        UnallocatedShiftRow(String customerId, String startTime, String endTime) {
            this.customerId = customerId;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private final java.util.List<UnallocatedShiftRow> unallocatedShiftRows = new java.util.ArrayList<>();

    // Format helpers
    private String fmtHour(int h) {
        int hh = ((h % 24) + 24) % 24;
        return String.format(Locale.getDefault(), "%02d:00", hh);
    }

    private String slotLabel(int startHour) {
        return fmtHour(startHour) + "-" + fmtHour(startHour + 1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_selection);

        // Read Intent params (keep originals for re-sending to next Activity)
        businessId = getIntent().getStringExtra("businessDocId");
        customerId = getIntent().getStringExtra("customerDocId");
        origStartDateStr = getIntent().getStringExtra("startDate");
        origEndDateStr   = getIntent().getStringExtra("endDate");
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        businessRepository = new FirestoreBusinessRepository(firestore);
        shiftRepository = new FirestoreShiftRepository(firestore);

        // parse dates (graceful fallback)
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM, yyyy", Locale.getDefault());
            startDate = (origStartDateStr != null) ? sdf.parse(origStartDateStr) : new Date(0);
            endDate   = (origEndDateStr   != null) ? sdf.parse(origEndDateStr)   : new Date(Long.MAX_VALUE);

            Calendar c = Calendar.getInstance();
            c.setTime(startDate);
            c.set(Calendar.HOUR_OF_DAY,  0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            startDate = c.getTime();

            c.setTime(endDate);
            c.set(Calendar.HOUR_OF_DAY,  23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
            endDate = c.getTime();
        } catch (ParseException ex) {
            ex.printStackTrace();
            startDate = new Date(0);
            endDate   = new Date(Long.MAX_VALUE);
        }

        windowStartMs = startDate.getTime();
        windowEndMs   = endDate.getTime();

        // Bind views
        table    = findViewById(R.id.tableLayout);
        tableUnallocatedShifts = findViewById(R.id.tableUnallocatedShifts);
        etStart  = findViewById(R.id.etStartTime);
        etEnd    = findViewById(R.id.etEndTime);
        btnSubmit= findViewById(R.id.btnSubmit);

        hsvHeader = findViewById(R.id.hsvHeader);
        hsvContent= findViewById(R.id.hsvContent);
        svLeft    = findViewById(R.id.svLeft);
        svContent = findViewById(R.id.svContent);
        headerRow = findViewById(R.id.headerRow);
        leftSeatColumn = findViewById(R.id.leftSeatColumn);

        // Controls - if your layout doesn't have these ids, add them per previous instructions
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        switchUnallocatedSeat = findViewById(R.id.switchUnallocatedSeat);
        tvSeatDisplay = findViewById(R.id.tvSeatDisplay);
        setupTimeInputHandlers();
        setManualTimeInputsEnabled(false);

        if (switchUnallocatedSeat != null) switchUnallocatedSeat.setChecked(false);
        if (tvSeatDisplay != null) tvSeatDisplay.setText("Seat: -");

        if (table != null) {
            table.setStretchAllColumns(false);
            table.setShrinkAllColumns(false);
        }

        // sync horizontal scroll (header <-> content)
        if (hsvContent != null) hsvContent.setOnScrollListener((l, t) -> {
            if (syncingH) return;
            syncingH = true; if (hsvHeader != null) hsvHeader.scrollTo(l, 0); syncingH = false;
        });
        if (hsvHeader != null) hsvHeader.setOnScrollListener((l, t) -> {
            if (syncingH) return;
            syncingH = true; if (hsvContent != null) hsvContent.scrollTo(l, svContent.getScrollY()); syncingH = false;
        });

        // sync vertical scroll (left <-> content)
        if (svContent != null) svContent.setOnScrollListener((l, t) -> {
            if (syncingV) return;
            syncingV = true; if (svLeft != null) svLeft.scrollTo(0, t); syncingV = false;
        });
        if (svLeft != null) svLeft.setOnScrollListener((l, t) -> {
            if (syncingV) return;
            syncingV = true; if (svContent != null) svContent.scrollTo(hsvContent.getScrollX(), t); syncingV = false;
        });

        // Build UI
        buildHeaderRow();
        buildSeatRows();

        // Zoom buttons
        if (btnZoomIn != null) btnZoomIn.setOnClickListener(v -> zoomIn());
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> zoomOut());

        // Wire unallocated switch behaviour
        if (switchUnallocatedSeat != null) {
            switchUnallocatedSeat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Enable manual time pick via EditTexts
                    setManualTimeInputsEnabled(true);
                    if (etStart != null) etStart.setText("");
                    if (etEnd != null) etEnd.setText("");
                    startHourSel = -1;
                    endHourSel = -1;
                    if (tvSeatDisplay != null) tvSeatDisplay.setText("unallocated");

                    // clear previous seat selection
                    if (selectedSeat > 0) {
                        clearSelectedSeatHighlights(selectedSeat);
                        selectedSeat = -1;
                    }

                    Toast.makeText(this, "Unallocated mode: tap Start/End to pick hours", Toast.LENGTH_SHORT).show();
                } else {
                    // Clear unallocated state and reset UI
                    setManualTimeInputsEnabled(false);
                    if (etStart != null) etStart.setText("");
                    if (etEnd != null) etEnd.setText("");
                    startHourSel = -1;
                    endHourSel = -1;
                    if (tvSeatDisplay != null) tvSeatDisplay.setText("Seat: -");
                }
            });
        }

        // Fetch business configuration & shifts
        if (businessId != null) {
            businessRepository.fetchBusinessConfig(
                    businessId,
                    config -> {
                        openHour = config.getOpenHour();
                        closeHour = config.getCloseHour();
                        maxSeats = config.getMaxSeats();

                        buildHeaderRow();
                        buildSeatRows();

                        loadShiftsWithCustomerFirst();
                    },
                    e -> {
                        Toast.makeText(SeatSelection.this, "Error loading business: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
            );
        }

        // Submit behaviour
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                boolean unallocatedMode = (switchUnallocatedSeat != null && switchUnallocatedSeat.isChecked());
                String seatValue;
                int finalSelectedSeat;

                if (unallocatedMode) {
                    seatValue = "unallocated";
                    finalSelectedSeat = -1;

                    // parse start/end hours from etStart/etEnd
                    int parsedStart = -1, parsedEnd = -1;
                    try {
                        String s = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                        if (!s.isEmpty()) parsedStart = Integer.parseInt(s.split(":")[0]);
                    } catch (Exception ignored) {}
                    try {
                        String s = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
                        if (!s.isEmpty()) parsedEnd = Integer.parseInt(s.split(":")[0]);
                    } catch (Exception ignored) {}

                    if (parsedStart < 0 || parsedEnd < 0) {
                        new AlertDialog.Builder(this)
                                .setMessage("Please pick both Start and End hours for unallocated seat.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    // time check: start < end (same day)
                    if (!(parsedStart < parsedEnd)) {
                        new AlertDialog.Builder(this)
                                .setTitle("Invalid times")
                                .setMessage("Start time must be strictly earlier than End time for unallocated seats.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    // Compute actual millis anchored to startDate (startDate is midnight of origStartDateStr)
                    Calendar cs = Calendar.getInstance();
                    cs.setTime(startDate);
                    cs.set(Calendar.HOUR_OF_DAY, parsedStart);
                    cs.set(Calendar.MINUTE, 0);
                    cs.set(Calendar.SECOND, 0);
                    cs.set(Calendar.MILLISECOND, 0);
                    long newStartMs = cs.getTimeInMillis();

                    Calendar ce = Calendar.getInstance();
                    ce.setTime(startDate);
                    ce.set(Calendar.HOUR_OF_DAY, parsedEnd);
                    ce.set(Calendar.MINUTE, 0);
                    ce.set(Calendar.SECOND, 0);
                    ce.set(Calendar.MILLISECOND, 0);
                    long newEndMs = ce.getTimeInMillis();

                    // Make final copies for lambda capture
                    final int fParsedStart = parsedStart;
                    final int fParsedEnd = parsedEnd;
                    final long fNewStartMs = newStartMs;
                    final long fNewEndMs = newEndMs;

                    // Now check overlap with existing unallocated shifts (async). On success -> show confirm
                    checkUnallocatedConflict(fNewStartMs, fNewEndMs, conflict -> {
                        if (conflict) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Time overlap")
                                    .setMessage("An existing unallocated booking overlaps this time range. Please choose another range.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }
                        // No conflict -> show confirmation & proceed
                        String msg = "Seat: unallocated\nStart: " + String.format("%02d:00", fParsedStart) +
                                "  End: " + String.format("%02d:00", fParsedEnd) + "\nProceed?";
                        new AlertDialog.Builder(this)
                                .setTitle("Confirm Unallocated")
                                .setMessage(msg)
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    // Start CustomerShift with extras
                                    Intent i = new Intent(SeatSelection.this, CustomerShift.class);
                                    i.putExtra("businessDocId", businessId);
                                    i.putExtra("customerDocId", customerId);

                                    i.putExtra("allocatedSeatString", "unallocated");
                                    i.putExtra("selectedSeat", -1);

                                    i.putExtra("startTime", fParsedStart);
                                    i.putExtra("endTime", fParsedEnd);
                                    i.putExtra("calledFromSeatSelection", true);

                                    i.putExtra("startDate", origStartDateStr);
                                    i.putExtra("endDate", origEndDateStr);

                                    startActivity(i);
                                    finish();
                                })
                                .setNegativeButton("No", null)
                                .show();
                    });

                } else {
                    // Allocated seat path (unchanged)
                    if (selectedSeat > 0) {
                        seatValue = String.valueOf(selectedSeat);
                        finalSelectedSeat = selectedSeat;
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("Please select a seat by tapping a row (or turn on Unallocated).")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    if (startHourSel < 0 || endHourSel < 0) {
                        new AlertDialog.Builder(this)
                                .setMessage("Please select both start and end times for the allocated seat.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    // confirmation and proceed
                    String msg = "Seat: " + seatValue + "\nStart: " + String.format("%02d:00", startHourSel) +
                            "  End: " + String.format("%02d:00", endHourSel) + "\nProceed?";
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Seat Selection")
                            .setMessage(msg)
                            .setPositiveButton("Yes", (dialogInterface, which) -> {
                                Intent i = new Intent(SeatSelection.this, CustomerShift.class);
                                i.putExtra("businessDocId", businessId);
                                i.putExtra("customerDocId", customerId);

                                i.putExtra("allocatedSeatString", seatValue);
                                i.putExtra("selectedSeat", finalSelectedSeat);

                                i.putExtra("startTime", startHourSel);
                                i.putExtra("endTime", endHourSel);
                                i.putExtra("calledFromSeatSelection", true);

                                i.putExtra("startDate", origStartDateStr);
                                i.putExtra("endDate", origEndDateStr);

                                startActivity(i);
                                finish();
                            })
                            .setNegativeButton("No", null)
                            .show();
                }
            });
        }

    }

    private void setupTimeInputHandlers() {
        if (etStart != null) {
            etStart.setOnClickListener(v -> onTimeInputTapped(true));
            etStart.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onTimeInputTapped(true);
                }
                return true;
            });
        }

        if (etEnd != null) {
            etEnd.setOnClickListener(v -> onTimeInputTapped(false));
            etEnd.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onTimeInputTapped(false);
                }
                return true;
            });
        }
    }

    private void onTimeInputTapped(boolean isStartField) {
        if (switchUnallocatedSeat != null && switchUnallocatedSeat.isChecked()) {
            openHourPicker(isStartField);
            return;
        }
        Toast.makeText(
                this,
                isStartField
                        ? "Tap a seat cell to set Start time (or enable Unallocated)"
                        : "Tap a seat cell to set End time (or enable Unallocated)",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setManualTimeInputsEnabled(boolean enabled) {
        configureTimeInput(etStart, enabled);
        configureTimeInput(etEnd, enabled);
    }

    private void configureTimeInput(EditText editText, boolean enabled) {
        if (editText == null) {
            return;
        }
        editText.setEnabled(enabled);
        editText.setFocusable(false);
        editText.setFocusableInTouchMode(false);
        editText.setClickable(true);
        editText.setLongClickable(false);
    }

    /**
     * Query Firestore for unallocated shifts in this business and check overlap.
     *
     * Order of checks:
     *  1) Filter shifts to those that have shift_seat == "unallocated" and shift_status == true (server-side).
     *  2) From those, first look for shifts that belong to this customer (we try common field names).
     *     If any same-customer unallocated shifts are found, check them all for overlap with new range.
     *     If any overlap -> conflict = true.
     *  3) If no same-customer unallocated shifts are found -> allow (conflict = false).
     *
     * Note: if you'd rather also block if ANY unallocated shift (other customers) overlaps, tell me and
     * I will check the full set instead of only the same-customer subset.
     */
    private void checkUnallocatedConflict(long newStartMs, long newEndMs, java.util.function.Consumer<Boolean> callback) {
        if (businessId == null) {
            callback.accept(false);
            return;
        }

        shiftRepository.fetchActiveUnallocatedShifts(
                businessId,
                documents -> {
                    // Collect docs that belong to this customerId (robust to several possible field names)
                    java.util.List<DocumentSnapshot> sameCustomerDocs = new java.util.ArrayList<>();

                    for (DocumentSnapshot doc : documents) {
                        // try several candidate fields that might hold the customer id in your schema
                        String docCustomerId = null;
                        if (docCustomerId == null && doc.contains("shift_customer_id")) docCustomerId = doc.getString("shift_customer_id");

                        // If we have a customerId and it matches the current customer, add to sameCustomerDocs
                        if (docCustomerId != null && customerId != null && docCustomerId.equals(customerId)) {
                            sameCustomerDocs.add(doc);
                        }
                    }

                    // If there are same-customer unallocated shifts, check overlap against all of them
                    if (!sameCustomerDocs.isEmpty()) {
                        boolean conflict = false;
                        for (DocumentSnapshot doc : sameCustomerDocs) {
                            Timestamp tsStart = doc.getTimestamp("shift_start_time");
                            Timestamp tsEnd   = doc.getTimestamp("shift_end_time");
                            if (tsStart == null || tsEnd == null) continue;
                            long s = tsStart.toDate().getTime();
                            long e = tsEnd.toDate().getTime();
                            // overlap check: existing_end > new_start && existing_start < new_end
                            if (e > newStartMs && s < newEndMs) {
                                conflict = true;
                                break;
                            }
                        }
                        callback.accept(conflict);
                        return;
                    }

                    // No same-customer unallocated shifts found -> allow (no conflict).
                    callback.accept(false);
                },
                e -> {
                    Log.e("SeatSel", "Error fetching unallocated shifts for conflict check", e);
                    // Conservatively block on failure.
                    callback.accept(true);
                }
        );
    }


    // ---------------------------
    // Load shifts (customer-first)
    // ---------------------------
    private void loadShiftsWithCustomerFirst() {
        if (businessId == null) return;

        if (customerId != null) {
            java.util.concurrent.atomic.AtomicReference<List<String>> currentShiftIdsRef =
                    new java.util.concurrent.atomic.AtomicReference<>(null);

            shiftRepository.fetchCustomerShiftIds(
                    businessId,
                    customerId,
                    shiftIds -> {
                        if (shiftIds != null && !shiftIds.isEmpty()) {
                            currentShiftIdsRef.set(new java.util.ArrayList<>(shiftIds));
                        }
                        shiftRepository.fetchAllShifts(
                                businessId,
                                querySnapshot -> iterateAndMarkShifts(querySnapshot, currentShiftIdsRef.get()),
                                e -> Log.e("SeatSel", "Error loading shifts after customer fetch", e)
                        );
                    },
                    e -> {
                        Log.e("SeatSel", "Error loading customer doc for current shift id", e);
                        shiftRepository.fetchAllShifts(
                                businessId,
                                querySnapshot -> iterateAndMarkShifts(querySnapshot, null),
                                e2 -> Log.e("SeatSel", "Error loading shifts (fallback)", e2)
                        );
                    }
            );
        } else {
            shiftRepository.fetchAllShifts(
                    businessId,
                    querySnapshot -> iterateAndMarkShifts(querySnapshot, null),
                    e -> Log.e("SeatSel", "Error loading shifts (no customerId)", e)
            );
        }
    }

    private void iterateAndMarkShifts(QuerySnapshot querySnapshot, List<String> currentShiftIds) {
        unallocatedShiftRows.clear();
        for (DocumentSnapshot shiftDoc : querySnapshot.getDocuments()) {
            Boolean status = shiftDoc.getBoolean("shift_status");
            if (!Boolean.TRUE.equals(status)) continue;

            Timestamp tsStart = shiftDoc.getTimestamp("shift_start_time");
            Timestamp tsEnd   = shiftDoc.getTimestamp("shift_end_time");
            String seatStr    = shiftDoc.getString("shift_seat");
            if (tsStart == null || tsEnd == null || seatStr == null) continue;

            long startMs = tsStart.toDate().getTime();
            long endMs   = tsEnd.toDate().getTime();
            if (!(endMs > windowStartMs && startMs < windowEndMs)) continue;
            String shiftCustomerId = shiftDoc.getString("shift_customer_id");

            int seatNum;
            try {
                seatNum = Integer.parseInt(seatStr);
            } catch (NumberFormatException e) {
                if ("unallocated".equalsIgnoreCase(seatStr)) {
                    String startLabel = DateFormat.format("d MMM yyyy, HH:mm", tsStart.toDate()).toString();
                    String endLabel = DateFormat.format("d MMM yyyy, HH:mm", tsEnd.toDate()).toString();
                    String customerLabel = (shiftCustomerId == null || shiftCustomerId.trim().isEmpty()) ? "-" : shiftCustomerId;
                    unallocatedShiftRows.add(new UnallocatedShiftRow(customerLabel, startLabel, endLabel));
                }
                continue;
            }

            Calendar cal = Calendar.getInstance();
            cal.setTime(tsStart.toDate());
            int startH = cal.get(Calendar.HOUR_OF_DAY);
            cal.setTime(tsEnd.toDate());
            int endH   = cal.get(Calendar.HOUR_OF_DAY);

            boolean isCurrentCustomerShift = currentShiftIds != null && currentShiftIds.contains(shiftDoc.getId());

            if (startH <= endH) {
                for (int h = startH; h < endH; h++) {
                    if (isCurrentCustomerShift) markCustomerShift(seatNum, h);
                    else markOccupied(seatNum, h, shiftCustomerId);
                }
            } else {
                for (int h = startH; h < closeHour; h++) {
                    if (isCurrentCustomerShift) markCustomerShift(seatNum, h);
                    else markOccupied(seatNum, h, shiftCustomerId);
                }
                for (int h = openHour; h < endH; h++) {
                    if (isCurrentCustomerShift) markCustomerShift(seatNum, h);
                    else markOccupied(seatNum, h, shiftCustomerId);
                }
            }
        }
        renderUnallocatedRows();
    }

    // ---------------------------
    // Header + Rows builders
    // ---------------------------
    private void buildHeaderRow() {
        if (headerRow != null) headerRow.removeAllViews();

        final int w = colPx();
        float scaledSp = scaledHeaderTextSp();

        for (int h = openHour; h < closeHour; h++) {
            TextView tv = new TextView(this);
            tv.setText(slotLabel(h));
            tv.setSingleLine(true);
            tv.setEllipsize(null);
            tv.setGravity(Gravity.CENTER);
            int pad = dpToPx(8 + (currentColDp - 96) / 12);
            tv.setPadding(pad, pad, pad, pad);
            tv.setBackgroundResource(R.drawable.cell_border);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp);

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);

            headerRow.addView(tv);
        }
    }

    private void buildSeatRows() {
        if (leftSeatColumn != null) leftSeatColumn.removeAllViews();
        if (table != null) table.removeAllViews();

        final int w = colPx();
        float scaledSpCell = scaledCellTextSp();
        int cellPad = dpToPx(8 + (currentColDp - 96) / 12);

        for (int seat = 1; seat <= maxSeats; seat++) {
            // left seat label column
            if (leftSeatColumn != null) {
                TextView seatTv = new TextView(this);
                seatTv.setText(String.valueOf(seat));
                seatTv.setGravity(Gravity.CENTER);
                seatTv.setPadding(cellPad / 2, cellPad, cellPad / 2, cellPad);
                seatTv.setBackgroundResource(R.drawable.cell_border);
                seatTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSpCell);
                leftSeatColumn.addView(seatTv);
            }

            // build the row for this seat
            TableRow row = new TableRow(this);
            row.setLayoutParams(new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int h = openHour; h < closeHour; h++) {
                TextView cell = new TextView(this);
                String tag = seat + ":" + h;
                cell.setTag(tag);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(cellPad, cellPad, cellPad, cellPad);
                cell.setBackgroundResource(R.drawable.cell_border);
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSpCell);
                cell.setText("");

                // clickable (always interactive)
                cell.setOnClickListener(this::onCellTapped);

                TableRow.LayoutParams lp = new TableRow.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
                cell.setLayoutParams(lp);

                row.addView(cell);
            }

            if (table != null) table.addView(row);
        }
    }

    // ---------------------------
    // Zoom utilities
    // ---------------------------
    private void zoomIn() {
        if (currentColDp + COL_STEP_DP > MAX_COL_DP) currentColDp = MAX_COL_DP;
        else currentColDp += COL_STEP_DP;
        rebuildGridPreservingState();
    }

    private void zoomOut() {
        if (currentColDp - COL_STEP_DP < MIN_COL_DP) currentColDp = MIN_COL_DP;
        else currentColDp -= COL_STEP_DP;
        rebuildGridPreservingState();
    }

    private int colPx() {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, currentColDp, getResources().getDisplayMetrics());
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float scaledHeaderTextSp() {
        float delta = (currentColDp - 96) / 24f;
        return Math.max(10f, baseTextSpHeader + delta * 2f);
    }

    private float scaledCellTextSp() {
        float delta = (currentColDp - 96) / 24f;
        return Math.max(10f, baseTextSpCell + delta * 2.0f);
    }

    /**
     * Capture current UI cell state into savedCellState.
     * Also capture the current selected seat/range into savedSelectedSeat/savedStartHour/savedEndHour.
     */
    private void captureCurrentCellState() {
        savedCellState.clear();
        savedSelectedSeat = -1;
        savedStartHour = -1;
        savedEndHour = -1;

        for (int seat = 1; seat <= maxSeats; seat++) {
            int localMin = Integer.MAX_VALUE;
            int localMax = Integer.MIN_VALUE;
            boolean seatHasSel = false;

            for (int h = openHour; h < closeHour; h++) {
                View v = findCellView(seat, h);
                if (v == null) continue;
                CellState cs = new CellState();
                cs.occupied = Boolean.TRUE.equals(v.getTag(R.id.occupied));
                cs.customerShift = Boolean.TRUE.equals(v.getTag(R.id.customer_shift));
                cs.selected = Boolean.TRUE.equals(v.getTag(R.id.selected));
                if (v instanceof TextView) cs.text = ((TextView)v).getText();
                Object occupiedCustomerId = v.getTag(R.id.occupied_customer_id);
                cs.occupiedCustomerId = occupiedCustomerId != null ? String.valueOf(occupiedCustomerId) : null;
                savedCellState.put(seat + ":" + h, cs);

                if (cs.selected) {
                    seatHasSel = true;
                    if (h < localMin) localMin = h;
                    if (h > localMax) localMax = h;
                }
            }

            if (seatHasSel && savedSelectedSeat == -1) {
                savedSelectedSeat = seat;
                savedStartHour = (localMin == Integer.MAX_VALUE) ? -1 : localMin;
                savedEndHour = (localMax == Integer.MIN_VALUE) ? -1 : (localMax + 1);
            }
        }
    }

    /**
     * Rebuild grid and re-applies savedCellState.
     * Also restores selectedSeat/startHourSel/endHourSel from the dedicated saved fields.
     */
    private void rebuildGridPreservingState() {
        captureCurrentCellState();

        // rebuild
        buildHeaderRow();
        buildSeatRows();

        // reapply saved state
        for (Map.Entry<String, CellState> e : savedCellState.entrySet()) {
            String key = e.getKey();
            String[] parts = key.split(":");
            if (parts.length != 2) continue;
            int seat = Integer.parseInt(parts[0]);
            int hour = Integer.parseInt(parts[1]);
            CellState cs = e.getValue();
            View v = findCellView(seat, hour);
            if (v == null) continue;
            if (cs.customerShift) {
                v.setBackgroundResource(R.drawable.cell_customer_shift);
                v.setTag(R.id.customer_shift, true);
                v.setTag(R.id.occupied, null);
                v.setTag(R.id.selected, null);
                v.setTag(R.id.occupied_customer_id, null);
                v.setOnClickListener(null);
            } else if (cs.occupied) {
                v.setBackgroundResource(R.drawable.cell_occupied);
                v.setTag(R.id.occupied, true);
                v.setTag(R.id.occupied_customer_id, cs.occupiedCustomerId);
                if (cs.occupiedCustomerId != null && !cs.occupiedCustomerId.trim().isEmpty()) {
                    v.setOnClickListener(view -> openCustomerProfile(cs.occupiedCustomerId));
                } else {
                    v.setOnClickListener(null);
                }
            } else if (cs.selected) {
                v.setBackgroundResource(R.drawable.cell_selected);
                v.setTag(R.id.selected, true);
                v.setTag(R.id.occupied_customer_id, null);
            } else {
                v.setBackgroundResource(R.drawable.cell_border);
                v.setTag(R.id.selected, null);
                v.setTag(R.id.occupied_customer_id, null);
                v.setOnClickListener(this::onCellTapped);
            }
            if (v instanceof TextView && cs.text != null) ((TextView)v).setText(cs.text);
        }

        // Restore selection variables from dedicated saved fields (if present).
        if (savedSelectedSeat > 0) {
            selectedSeat = savedSelectedSeat;
            startHourSel = savedStartHour;
            endHourSel   = savedEndHour;
            if (startHourSel >= 0 && etStart != null) etStart.setText(String.format("%02d:00", startHourSel));
            if (endHourSel >= 0 && etEnd != null) etEnd.setText(String.format("%02d:00", endHourSel));
            if (tvSeatDisplay != null) tvSeatDisplay.setText("Seat: " + selectedSeat);
        }
    }

    // ---------------------------
    // Selection logic
    // ---------------------------
    private void onCellTapped(View v) {
        String rawTag = (String) v.getTag();
        if (rawTag == null) return;
        String[] p = rawTag.split(":");
        if (p.length != 2) return;
        int seatFromTag = Integer.parseInt(p[0]);
        int hr = Integer.parseInt(p[1]);

        boolean unallocatedMode = (switchUnallocatedSeat != null && switchUnallocatedSeat.isChecked());

        if (unallocatedMode) {
            // If both start and end already selected, reset first (so this tap becomes new start)
            if (startHourSel >= 0 && endHourSel > 0) {
                startHourSel = -1;
                endHourSel = -1;
                if (etStart != null) etStart.setText("");
                if (etEnd != null) etEnd.setText("");
                // proceed to treat this tap as first tap (start)
            }

            // Unallocated: tapping sets start then end (no highlight)
            if (startHourSel < 0) {
                startHourSel = hr;
                if (etStart != null) etStart.setText(String.format("%02d:00", hr));
                if (etEnd != null) etEnd.setText("");
                return;
            } else {
                int attemptedEndHour = hr + 1;
                endHourSel = attemptedEndHour;
                if (etEnd != null) etEnd.setText(String.format("%02d:00", endHourSel));
                return;
            }
        }

        // Allocated mode: seat = tapped row
        View tappedCell = findCellView(seatFromTag, hr);
        Boolean isCustShiftTap = (tappedCell != null) ? (Boolean) tappedCell.getTag(R.id.customer_shift) : null;
        if (Boolean.TRUE.equals(isCustShiftTap)) {
            Toast.makeText(this, "This slot belongs to the current customer's shift and cannot be selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tvSeatDisplay != null) tvSeatDisplay.setText("Seat: " + seatFromTag);

        // If selecting a different seat, clear previous
        if (selectedSeat != seatFromTag) {
            if (selectedSeat > 0) clearSelectedSeatHighlights(selectedSeat);

            selectedSeat = seatFromTag;
            startHourSel = hr;
            endHourSel = -1;
            if (etStart != null) etStart.setText(String.format("%02d:00", hr));
            if (etEnd != null) etEnd.setText("");
            clearHighlights();
            highlightCell(seatFromTag, hr);
            return;
        }

        // second tap -> set end, with conflict check
        int attemptedEndHour = hr + 1;
        boolean conflict = hasConflictInRange(seatFromTag, startHourSel, hr);
        if (conflict) {
            new AlertDialog.Builder(this)
                    .setTitle("Time slot unavailable")
                    .setMessage("Continuous time slots not available - already occupied in between.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        endHourSel = attemptedEndHour;
        if (etEnd != null) etEnd.setText(String.format("%02d:00", endHourSel));
        clearHighlights();

        if (startHourSel <= hr) {
            for (int h = startHourSel; h <= hr; h++) highlightCell(seatFromTag, h);
        } else {
            for (int h = startHourSel; h < closeHour; h++) highlightCell(seatFromTag, h);
            for (int h = openHour; h <= hr; h++) highlightCell(seatFromTag, h);
        }
    }

    private boolean hasConflictInRange(int seat, int startH, int endHinclusive) {
        if (seat <= 0) return false;
        java.util.function.IntPredicate checkHour = (h) -> {
            View cell = findCellView(seat, h);
            if (cell == null) return false;
            Boolean isOcc = (Boolean) cell.getTag(R.id.occupied);
            Boolean isCust = (Boolean) cell.getTag(R.id.customer_shift);
            return (Boolean.TRUE.equals(isOcc) && !Boolean.TRUE.equals(isCust));
        };
        if (startH <= endHinclusive) {
            for (int h = startH; h <= endHinclusive; h++) if (checkHour.test(h)) return true;
        } else {
            for (int h = startH; h < closeHour; h++) if (checkHour.test(h)) return true;
            for (int h = openHour; h <= endHinclusive; h++) if (checkHour.test(h)) return true;
        }
        return false;
    }

    private void clearSelectedSeatHighlights(int seat) {
        if (seat <= 0) return;
        for (int h = openHour; h < closeHour; h++) {
            View cell = findCellView(seat, h);
            if (cell == null) continue;
            Boolean isOcc = (Boolean) cell.getTag(R.id.occupied);
            if (Boolean.TRUE.equals(isOcc)) continue;
            Boolean isCustShift = (Boolean) cell.getTag(R.id.customer_shift);
            if (Boolean.TRUE.equals(isCustShift)) continue;
            Boolean isSel = (Boolean) cell.getTag(R.id.selected);
            if (Boolean.TRUE.equals(isSel)) {
                cell.setBackgroundResource(R.drawable.cell_border);
                cell.setTag(R.id.selected, null);
                cell.setOnClickListener(this::onCellTapped);
            }
        }
        if (selectedSeat == seat) {
            selectedSeat = -1;
            startHourSel = -1;
            endHourSel   = -1;
            if (etStart != null) etStart.setText("");
            if (etEnd != null) etEnd.setText("");
        }
    }

    private View findCellView(int seat, int hour) {
        int rowIndex = seat - 1;
        int colIndex = hour - openHour;
        if (rowIndex < 0 || rowIndex >= table.getChildCount()) return null;
        TableRow row = (TableRow) table.getChildAt(rowIndex);
        if (colIndex < 0 || colIndex >= row.getChildCount()) return null;
        return row.getChildAt(colIndex);
    }

    private void highlightCell(int seat, int hour) {
        View cell = findCellView(seat, hour);
        if (cell == null) return;
        Boolean isCustShift = (Boolean) cell.getTag(R.id.customer_shift);
        if (Boolean.TRUE.equals(isCustShift)) return;
        Boolean isOcc = (Boolean) cell.getTag(R.id.occupied);
        if (Boolean.TRUE.equals(isOcc)) return;
        cell.setBackgroundResource(R.drawable.cell_selected);
        cell.setTag(R.id.selected, true);
    }

    private void markCustomerShift(int seat, int hour) {
        View cell = findCellView(seat, hour);
        if (cell == null) return;
        cell.setBackgroundResource(R.drawable.cell_customer_shift);
        if (cell instanceof TextView) {
            ((TextView) cell).setText("");
        }
        cell.setTag(R.id.customer_shift, true);
        cell.setTag(R.id.occupied, null);
        cell.setTag(R.id.occupied_customer_id, null);
        cell.setTag(R.id.selected, null);
        cell.setOnClickListener(null);
    }

    private void markOccupied(int seat, int hour, String occupiedCustomerId) {
        View cell = findCellView(seat, hour);
        if (cell == null) return;
        Boolean isCustShift = (Boolean) cell.getTag(R.id.customer_shift);
        if (Boolean.TRUE.equals(isCustShift)) return;
        Boolean isSel = (Boolean) cell.getTag(R.id.selected);
        if (Boolean.TRUE.equals(isSel)) return;
        cell.setBackgroundResource(R.drawable.cell_occupied);
        cell.setTag(R.id.occupied, true);
        cell.setTag(R.id.occupied_customer_id, occupiedCustomerId);
        if (cell instanceof TextView) {
            TextView tv = (TextView) cell;
            tv.setText(occupiedCustomerId != null ? occupiedCustomerId : "");
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(9f, scaledCellTextSp() - 1f));
            tv.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        }
        if (occupiedCustomerId != null && !occupiedCustomerId.trim().isEmpty()) {
            cell.setOnClickListener(v -> openCustomerProfile(occupiedCustomerId));
        } else {
            cell.setOnClickListener(null);
        }
    }

    private void clearHighlights() {
        for (int i = 0; i < table.getChildCount(); i++) {
            TableRow row = (TableRow) table.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                View cell = row.getChildAt(j);
                Boolean isOcc = (Boolean) cell.getTag(R.id.occupied);
                if (Boolean.TRUE.equals(isOcc)) continue;
                Boolean isCustShift = (Boolean) cell.getTag(R.id.customer_shift);
                if (Boolean.TRUE.equals(isCustShift)) continue;
                cell.setTag(R.id.selected, null);
                cell.setTag(R.id.occupied_customer_id, null);
                cell.setBackgroundResource(R.drawable.cell_border);
                if (cell instanceof TextView) {
                    ((TextView) cell).setText("");
                }
                cell.setOnClickListener(this::onCellTapped);
            }
        }
    }

    private void openCustomerProfile(String targetCustomerId) {
        if (businessId == null || targetCustomerId == null || targetCustomerId.trim().isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, CustomerProfile.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", targetCustomerId.trim());
        startActivity(intent);
    }

    private void renderUnallocatedRows() {
        if (tableUnallocatedShifts == null) {
            return;
        }
        tableUnallocatedShifts.removeAllViews();

        TableRow header = new TableRow(this);
        header.addView(buildUnallocatedCell("customer_id", true));
        header.addView(buildUnallocatedCell("start_time", true));
        header.addView(buildUnallocatedCell("end_time", true));
        tableUnallocatedShifts.addView(header);

        if (unallocatedShiftRows.isEmpty()) {
            TableRow empty = new TableRow(this);
            TextView only = buildUnallocatedCell("No unallocated shifts", false);
            empty.addView(only);
            tableUnallocatedShifts.addView(empty);
            return;
        }

        for (UnallocatedShiftRow row : unallocatedShiftRows) {
            TableRow tr = new TableRow(this);
            tr.addView(buildUnallocatedCell(row.customerId, false));
            tr.addView(buildUnallocatedCell(row.startTime, false));
            tr.addView(buildUnallocatedCell(row.endTime, false));
            tableUnallocatedShifts.addView(tr);
        }
    }

    private TextView buildUnallocatedCell(String text, boolean header) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setBackgroundResource(R.drawable.cell_border);
        tv.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 12f : 11f);
        tv.setTypeface(null, header ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    /**
     * Open Android TimePickerDialog in 24-hr mode. Only hour selection is effective; minute forced to 00.
     * If isStart==true sets etStart/startHourSel else etEnd/endHourSel.
     */
    private void openHourPicker(boolean isStart) {
        // Choose a sensible default hour
        int defaultHour = isStart ? Math.max(openHour, (startHourSel >= 0 ? startHourSel : openHour))
                : Math.max(openHour + 1, (endHourSel > 0 ? endHourSel : openHour + 1));

        TimePickerDialog.OnTimeSetListener listener = (view, hourOfDay, minute) -> {
            int hour = hourOfDay;
            if (hour < openHour || hour > closeHour) {
                Toast.makeText(this,
                        "Time must be within business hours",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (isStart) {
                startHourSel = hour;
                if (etStart != null) etStart.setText(String.format(Locale.getDefault(), "%02d:00", hour));
                // if previously had an end, clear it so user can reselect end after changing start
                if (endHourSel > 0) {
                    endHourSel = -1;
                    if (etEnd != null) etEnd.setText("");
                }
            } else {
                endHourSel = hour;
                if (etEnd != null) etEnd.setText(String.format(Locale.getDefault(), "%02d:00", hour));
            }
        };

        // Use the default TimePickerDialog constructor (no problematic style).
        // Minutes will be shown in the picker but we ignore them - we always set minutes to 00.
        TimePickerDialog tpd = new TimePickerDialog(this, listener, defaultHour, 0, true);
        tpd.setTitle(isStart ? "Select Start Hour (24h)" : "Select End Hour (24h)");
        tpd.setOnShowListener(dialog -> enforceHourOnlyPicker(tpd));

        // Ensure show() is called on UI thread
        runOnUiThread(tpd::show);
    }

    private void enforceHourOnlyPicker(TimePickerDialog tpd) {
        try {
            int minuteId = getResources().getIdentifier("minute", "id", "android");
            View minuteView = tpd.findViewById(minuteId);
            if (minuteView != null) {
                minuteView.setVisibility(View.GONE);
                minuteView.setEnabled(false);
            }

            Field pickerField = TimePickerDialog.class.getDeclaredField("mTimePicker");
            pickerField.setAccessible(true);
            Object pickerObj = pickerField.get(tpd);
            if (pickerObj instanceof android.widget.TimePicker) {
                android.widget.TimePicker picker = (android.widget.TimePicker) pickerObj;
                picker.setIs24HourView(true);
                picker.setMinute(0);
            }
        } catch (Exception ignored) {
            // Fallback is still safe: minute value is ignored and stored as 00.
        }
    }

}
