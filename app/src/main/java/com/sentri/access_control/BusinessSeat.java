package com.sentri.access_control;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreShiftRepository;
import com.sentri.access_control.repositories.ShiftRepository;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class BusinessSeat extends AppCompatActivity {

    // Business config (defaults)
    private int openHour = 0, closeHour = 24, maxSeats = 10;
    private String businessId = null;

    // Date window selected by user
    private Date startDate, endDate;
    private long windowStartMs = Long.MIN_VALUE, windowEndMs = Long.MAX_VALUE;

    // Views
    private TableLayout table;
    private TableLayout tableUnallocatedShifts;
    private com.sentri.access_control.ui.ObservableHorizontalScrollView hsvHeader, hsvContent;
    private com.sentri.access_control.ui.ObservableScrollView svLeft, svContent;
    private LinearLayout headerRow, leftSeatColumn;
    private TextView tvCorner;

    // Programmatic floating buttons
    private Button floatZoomIn, floatZoomOut;

    // Zoom state same as SeatSelection
    private int currentColDp = 80;
    private final int MIN_COL_DP = 36;
    private final int MAX_COL_DP = 220;
    private final int COL_STEP_DP = 12;
    private float baseTextSpHeader = 11f;
    private float baseTextSpCell   = 12f;

    // Saved visual state across rebuilds: key -> CellVisual
    // key is "seat:hour" for numeric seats.
    private final Map<String, CellVisual> savedVisuals = new HashMap<>();
    private BusinessRepository businessRepository;
    private ShiftRepository shiftRepository;

    private static class CellVisual {
        boolean occupied;
        boolean customerShift;
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

    private final java.util.List<UnallocatedShiftRow> unallocatedRows = new java.util.ArrayList<>();

    private interface HourPainter {
        void paint(int hour);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_seat);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        businessRepository = new FirestoreBusinessRepository(firestore);
        shiftRepository = new FirestoreShiftRepository(firestore);

        // businessId expected to be passed via intent (optional)
        businessId = getIntent().getStringExtra("businessDocId");

        // bind views
        table    = findViewById(R.id.tableLayout);
        tableUnallocatedShifts = findViewById(R.id.tableUnallocatedShifts);
        hsvHeader = findViewById(R.id.hsvHeader);
        hsvContent= findViewById(R.id.hsvContent);
        svLeft    = findViewById(R.id.svLeft);
        svContent = findViewById(R.id.svContent);
        headerRow = findViewById(R.id.headerRow);
        leftSeatColumn = findViewById(R.id.leftSeatColumn);
        tvCorner = findViewById(R.id.tvCorner);

        if (tvCorner != null) tvCorner.setText("Seat\nTime");

        // table flags
        if (table != null) {
            table.setStretchAllColumns(false);
            table.setShrinkAllColumns(false);
        }

        // create floating buttons once the content view is laid out
        final View contentRoot = findViewById(android.R.id.content);
        contentRoot.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                contentRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                createFloatingZoomButtons();
            }
        });

        // wire scroll sync (null-safe)
        wireScrollSync();

        // Fetch business configuration first (if available), then render all active shifts directly.
        if (businessId != null) {
            businessRepository.fetchBusinessConfig(
                    businessId,
                    config -> {
                        openHour = config.getOpenHour();
                        closeHour = config.getCloseHour();
                        maxSeats = config.getMaxSeats();
                        buildHeaderRow();
                        buildSeatRows();
                        loadAndRenderShifts();
                    },
                    e -> {
                        Toast.makeText(this, "Could not load business config, using defaults.", Toast.LENGTH_SHORT).show();
                        buildHeaderRow();
                        buildSeatRows();
                        loadAndRenderShifts();
                    }
            );
        } else {
            buildHeaderRow();
            buildSeatRows();
            loadAndRenderShifts();
        }
    }

    // -------------------------
    // Date picking and loading
    // -------------------------
    private void promptForDateWindowAndLoad() {
        final Calendar c = Calendar.getInstance();

        DatePickerDialog dpStart = new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar s = Calendar.getInstance();
            s.set(Calendar.YEAR, y); s.set(Calendar.MONTH, m); s.set(Calendar.DAY_OF_MONTH, d);
            s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
            startDate = s.getTime();

            DatePickerDialog dpEnd = new DatePickerDialog(this, (view2, y2, m2, d2) -> {
                Calendar e = Calendar.getInstance();
                e.set(Calendar.YEAR, y2); e.set(Calendar.MONTH, m2); e.set(Calendar.DAY_OF_MONTH, d2);
                e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59); e.set(Calendar.MILLISECOND, 999);
                endDate = e.getTime();

                windowStartMs = startDate.getTime();
                windowEndMs   = endDate.getTime();

                // initial build and render
                buildHeaderRow();
                buildSeatRows(); // builds numeric rows + final unallocated row

                loadAndRenderShifts();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dpEnd.setTitle("Select End Date");
            dpEnd.show();

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpStart.setTitle("Select Start Date");
        dpStart.show();
    }

    // -------------------------
    // Load shifts and render
    // -------------------------
    private void loadAndRenderShifts() {
        if (businessId == null) {
            Toast.makeText(this, "No business specified - cannot load shifts.", Toast.LENGTH_LONG).show();
            return;
        }
        unallocatedRows.clear();

        shiftRepository.fetchActiveShifts(
                businessId,
                querySnapshot -> {
                    for (DocumentSnapshot shiftDoc : querySnapshot.getDocuments()) {
                        Timestamp tsStart = shiftDoc.getTimestamp("shift_start_time");
                        Timestamp tsEnd   = shiftDoc.getTimestamp("shift_end_time");
                        String seatStr    = shiftDoc.getString("shift_seat");
                        String shiftCustomerId = shiftDoc.getString("shift_customer_id");
                        if (tsStart == null || tsEnd == null || seatStr == null) continue;

                        long startMs = tsStart.toDate().getTime();
                        long endMs   = tsEnd.toDate().getTime();

                        // check overlap with chosen window
                        if (!(endMs > windowStartMs && startMs < windowEndMs)) continue;

                        int seatNum;
                        try {
                            seatNum = Integer.parseInt(seatStr);
                            paintSeatRange(seatNum, tsStart, tsEnd, shiftCustomerId);
                        } catch (NumberFormatException nf) {
                            String normalizedSeat = seatStr.trim().toLowerCase(Locale.US);
                            if (normalizedSeat.contains("unallocated")) {
                                String startLabel = DateFormat.format("d MMM yyyy, HH:mm", tsStart.toDate()).toString();
                                String endLabel = DateFormat.format("d MMM yyyy, HH:mm", tsEnd.toDate()).toString();
                                String customerLabel = (shiftCustomerId == null || shiftCustomerId.trim().isEmpty()) ? "-" : shiftCustomerId;
                                unallocatedRows.add(new UnallocatedShiftRow(customerLabel, startLabel, endLabel));
                            }
                        }
                    }
                    renderUnallocatedRows();
                },
                e -> Toast.makeText(this, "Error loading shifts: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void paintSeatRange(int seatNum, Timestamp tsStart, Timestamp tsEnd, String shiftCustomerId) {
        int startH = extractHour(tsStart);
        int endH = extractHour(tsEnd);
        if (startH <= endH) {
            for (int h = startH; h < endH; h++) {
                markOccupied(seatNum, h, false, shiftCustomerId, null);
            }
        } else {
            for (int h = startH; h < closeHour; h++) {
                markOccupied(seatNum, h, false, shiftCustomerId, null);
            }
            for (int h = openHour; h < endH; h++) {
                markOccupied(seatNum, h, false, shiftCustomerId, null);
            }
        }
        // Show customer id only once per highlighted block with range-aware fitted text.
        int blockLength;
        if (startH <= endH) {
            blockLength = Math.max(1, endH - startH);
        } else {
            blockLength = Math.max(1, (closeHour - startH) + (endH - openHour));
        }
        markOccupied(seatNum, startH, true, shiftCustomerId, shiftCustomerId);
    }

    private int extractHour(Timestamp timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(timestamp.toDate());
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    private void paintHourRange(int startH, int endH, HourPainter painter) {
        if (startH <= endH) {
            for (int h = startH; h < endH; h++) {
                painter.paint(h);
            }
            return;
        }

        for (int h = startH; h < closeHour; h++) {
            painter.paint(h);
        }
        for (int h = openHour; h < endH; h++) {
            painter.paint(h);
        }
    }

    // ---------------------------
    // Grid building (header + rows + unallocated row)
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
            tv.setTypeface(null, Typeface.BOLD);
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

        // numeric seat rows
        for (int seat = 1; seat <= maxSeats; seat++) {
            if (leftSeatColumn != null) {
                TextView seatTv = new TextView(this);
                seatTv.setText(String.valueOf(seat));
                seatTv.setGravity(Gravity.CENTER);
                seatTv.setPadding(cellPad / 2, cellPad, cellPad / 2, cellPad);
                seatTv.setBackgroundResource(R.drawable.cell_border);
                seatTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSpCell);
                leftSeatColumn.addView(seatTv);
            }

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
                // view-only => no click handlers
                cell.setOnClickListener(null);
                TableRow.LayoutParams lp = new TableRow.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
                cell.setLayoutParams(lp);
                row.addView(cell);
            }

            if (table != null) table.addView(row);
        }

    }

    // ---------------------------
    // Capture & reapply visuals (persistence across rebuilds)
    // ---------------------------
    private void captureCellStates() {
        savedVisuals.clear();
        if (table == null) return;
        for (int r = 0; r < table.getChildCount(); r++) {
            TableRow row = (TableRow) table.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View cell = row.getChildAt(c);
                Object rawTag = cell.getTag();
                String key = (rawTag != null) ? rawTag.toString() : ("r" + r + "c" + c);
                CellVisual cv = new CellVisual();
                cv.occupied = Boolean.TRUE.equals(cell.getTag(R.id.occupied));
                cv.customerShift = Boolean.TRUE.equals(cell.getTag(R.id.customer_shift));
                if (cell instanceof TextView) cv.text = ((TextView) cell).getText();
                Object occupiedCustomerId = cell.getTag(R.id.occupied_customer_id);
                cv.occupiedCustomerId = occupiedCustomerId != null ? String.valueOf(occupiedCustomerId) : null;
                savedVisuals.put(key, cv);
            }
        }
    }

    private void applySavedCellStates() {
        if (table == null || savedVisuals.isEmpty()) return;
        for (Map.Entry<String, CellVisual> e : savedVisuals.entrySet()) {
            String key = e.getKey();
            String[] parts = key.split(":");
            if (parts.length == 2) {
                String left = parts[0], right = parts[1];
                int hour;
                try {
                    hour = Integer.parseInt(right);
                } catch (NumberFormatException ex) {
                    continue;
                }
                CellVisual cv = e.getValue();
                int seat;
                try {
                    seat = Integer.parseInt(left);
                } catch (NumberFormatException ex) {
                    continue;
                }
                View v = findCellView(seat, hour);
                if (v == null) continue;
                if (cv.customerShift) {
                    v.setBackgroundResource(R.drawable.cell_customer_shift);
                    v.setTag(R.id.customer_shift, true);
                    v.setTag(R.id.occupied, null);
                    v.setTag(R.id.occupied_customer_id, null);
                    v.setOnClickListener(null);
                } else if (cv.occupied) {
                    v.setBackgroundResource(R.drawable.cell_occupied);
                    v.setTag(R.id.occupied, true);
                    v.setTag(R.id.occupied_customer_id, cv.occupiedCustomerId);
                    if (cv.occupiedCustomerId != null && !cv.occupiedCustomerId.trim().isEmpty()
                            && cv.text != null && cv.text.length() > 0) {
                        v.setOnClickListener(view -> openCustomerProfile(cv.occupiedCustomerId));
                    } else {
                        v.setOnClickListener(null);
                    }
                } else {
                    v.setBackgroundResource(R.drawable.cell_border);
                    v.setTag(R.id.occupied, null);
                    v.setTag(R.id.occupied_customer_id, null);
                    v.setOnClickListener(null);
                }
                if (v instanceof TextView && cv.text != null) ((TextView) v).setText(cv.text);
            }
        }
    }

    // Rebuild grid but preserve painted cells (used for zoom)
    private void rebuildGridPreservingState() {
        captureCellStates();
        buildHeaderRow();
        buildSeatRows();
        applySavedCellStates();
    }

    // ---------------------------
    // Paint helpers (view-only)
    // ---------------------------
    private void markOccupied(int seat, int hour, boolean showLabel, String shiftCustomerId, String labelText) {
        View cell = findCellView(seat, hour);
        if (cell == null) return;
        // set same tag as other occupied seat cells
        cell.setBackgroundResource(R.drawable.cell_occupied);
        cell.setTag(R.id.occupied, true);
        cell.setTag(R.id.occupied_customer_id, shiftCustomerId);
        if (cell instanceof TextView) {
            TextView tv = (TextView) cell;
            if (showLabel && shiftCustomerId != null && !shiftCustomerId.trim().isEmpty()) {
                tv.setText(labelText != null ? labelText : shiftCustomerId);
                tv.setTextColor(Color.WHITE);
                tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                tv.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(9f, scaledCellTextSp() - 1f));
                cell.setOnClickListener(v -> openCustomerProfile(shiftCustomerId));
            } else {
                tv.setText("");
                cell.setOnClickListener(null);
            }
        } else {
            cell.setOnClickListener(null);
        }
    }

    // ---------------------------
    // Utilities (layout + scroll)
    // ---------------------------
    private void wireScrollSync() {
        // horizontal sync (header <-> content)
        if (hsvContent != null) hsvContent.setOnScrollListener((l, t) -> {
            try {
                if (hsvHeader != null) hsvHeader.scrollTo(l, 0);
            } catch (Exception ignored) {}
        });
        if (hsvHeader != null) hsvHeader.setOnScrollListener((l, t) -> {
            try {
                if (hsvContent != null) hsvContent.scrollTo(l, 0);
            } catch (Exception ignored) {}
        });

        // vertical sync (left <-> content)
        if (svContent != null) svContent.setOnScrollListener((l, t) -> {
            try {
                if (svLeft != null) svLeft.scrollTo(0, t);
            } catch (Exception ignored) {}
        });
        if (svLeft != null) svLeft.setOnScrollListener((l, t) -> {
            try {
                if (svContent != null) svContent.scrollTo(0, t);
            } catch (Exception ignored) {}
        });
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

    private String fmtHour(int h) {
        int hh = ((h % 24) + 24) % 24;
        return String.format(Locale.getDefault(), "%02d:00", hh);
    }

    private String slotLabel(int startHour) {
        return fmtHour(startHour) + "-" + fmtHour(startHour + 1);
    }

    private View findCellView(int seat, int hour) {
        if (table == null) return null;
        int rowIndex = seat - 1;
        if (rowIndex < 0 || rowIndex >= table.getChildCount()) return null;
        TableRow row = (TableRow) table.getChildAt(rowIndex);
        int colIndex = hour - openHour;
        if (colIndex < 0 || colIndex >= row.getChildCount()) return null;
        return row.getChildAt(colIndex);
    }

    // ---------------------------
    // Floating buttons (programmatic)
    // ---------------------------
    private void createFloatingZoomButtons() {
        // Add two small buttons to the activity content overlay (FrameLayout)
        FrameLayout content = (FrameLayout) findViewById(android.R.id.content);
        if (content == null) return;

        // floating container to hold both buttons stacked vertically
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams contLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        int margin = dpToPx(16);
        contLp.setMargins(margin, margin, margin, margin + dpToPx(48)); // lift slightly above bottom
        container.setLayoutParams(contLp);

        // zoom in
        floatZoomIn = createFloatingButton(this, "+");
        FrameLayout.LayoutParams p1 = new FrameLayout.LayoutParams(
                dpToPx(48), dpToPx(48), Gravity.END | Gravity.BOTTOM);
        p1.setMargins(0,0,0, dpToPx(56));
        floatZoomIn.setLayoutParams(p1);
        floatZoomIn.setOnClickListener(v -> {
            zoomInPreserve();
        });

        // zoom out
        floatZoomOut = createFloatingButton(this, "-");
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(
                dpToPx(48), dpToPx(48), Gravity.END | Gravity.BOTTOM);
        p2.setMargins(0,0,0, 0);
        floatZoomOut.setLayoutParams(p2);
        floatZoomOut.setOnClickListener(v -> {
            zoomOutPreserve();
        });

        container.addView(floatZoomIn);
        container.addView(floatZoomOut);
        content.addView(container);
    }

    private Button createFloatingButton(Context ctx, String text) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        b.setElevation(dpToPx(6));
        b.setBackgroundResource(android.R.drawable.btn_default_small);
        int p = dpToPx(6);
        b.setPadding(p, p, p, p);
        return b;
    }

    // ---------------------------
    // Zoom helpers that preserve state
    // ---------------------------
    private void zoomInPreserve() {
        if (currentColDp + COL_STEP_DP > MAX_COL_DP) currentColDp = MAX_COL_DP;
        else currentColDp += COL_STEP_DP;
        rebuildGridPreservingState();
    }

    private void zoomOutPreserve() {
        if (currentColDp - COL_STEP_DP < MIN_COL_DP) currentColDp = MIN_COL_DP;
        else currentColDp -= COL_STEP_DP;
        rebuildGridPreservingState();
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
        header.addView(buildUnallocatedCell("Customer", true));
        header.addView(buildUnallocatedCell("Start Time", true));
        header.addView(buildUnallocatedCell("End Time", true));
        tableUnallocatedShifts.addView(header);

        if (unallocatedRows.isEmpty()) {
            return;
        }

        for (UnallocatedShiftRow unallocatedRow : unallocatedRows) {
            TableRow row = new TableRow(this);
            row.addView(buildUnallocatedCell(unallocatedRow.customerId, false));
            row.addView(buildUnallocatedCell(unallocatedRow.startTime, false));
            row.addView(buildUnallocatedCell(unallocatedRow.endTime, false));
            tableUnallocatedShifts.addView(row);
        }
    }

    private TextView buildUnallocatedCell(String text, boolean header) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setBackgroundResource(R.drawable.cell_border);
        tv.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 12f : 11f);
        tv.setTypeface(null, header ? Typeface.BOLD : Typeface.NORMAL);
        return tv;
    }
}
