package com.sentri.access_control;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.CardRepository;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.repositories.FirestoreCardRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CardAssignment extends AppCompatActivity {

    private static final String COMMENT_ENTITY_TYPE = "cards";
    private static final String ACTION_CARD_ASSIGNED = "card_assigned";
    private static final String ACTION_CARD_REPLACED = "card_replaced";
    private static final String ACTION_CARD_RETURNED = "card_returned";
    private static final String ACTION_CARD_UNASSIGNED = "card_unassigned";
    private static final String DASH = "-";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    private CardRepository cardRepository;
    private BusinessRepository businessRepository;
    private CommentRepository commentRepository;

    private String businessId;
    private String customerId;
    private boolean isNewFlow;
    private boolean isReplaceFlow;
    private boolean isReturnFlow;
    private String userEmail;
    private String businessPrefix;

    private final List<DocumentSnapshot> cards = new ArrayList<>();
    private CardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_assignment);

        bindViews();
        initializeDependencies();
        readIntentData();
        setupRecycler();

        if (isReturnFlow) {
            handleImmediateReturnFlowAndFinish();
            return;
        }

        if (TextUtils.isEmpty(businessId)) {
            Toast.makeText(this, "Showing all cards (no business id provided)", Toast.LENGTH_SHORT).show();
            loadAllCards();
            return;
        }
        loadCards();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.rv_cards);
        progressBar = findViewById(R.id.progress);
    }

    private void initializeDependencies() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        cardRepository = new FirestoreCardRepository(firestore);
        businessRepository = new FirestoreBusinessRepository(firestore);
        commentRepository = new FirestoreCommentRepository(firestore);
        userEmail = new PrefsManager(this).getUserEmail();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        businessId = intent.getStringExtra("business_id");
        customerId = intent.getStringExtra("customer_id");
        isNewFlow = intent.getBooleanExtra("new", false);
        isReplaceFlow = intent.getBooleanExtra("replace", false);
        isReturnFlow = intent.getBooleanExtra("return", false);
    }

    private void setupRecycler() {
        adapter = new CardAdapter(cards, this::onCardClicked);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
    }

    private void loadAllCards() {
        setLoading(true);
        cardRepository.fetchAllCards(
                docs -> {
                    cards.clear();
                    cards.addAll(docs);
                    adapter.notifyDataSetChanged();
                    setLoading(false);
                },
                exception -> {
                    setLoading(false);
                    showLongToast("Failed to load cards: " + exception.getMessage());
                }
        );
    }

    private void loadCards() {
        setLoading(true);
        boolean onlyUnassigned = isNewFlow || isReplaceFlow || isReturnFlow;
        cardRepository.fetchBusinessCards(
                businessId,
                onlyUnassigned,
                docs -> {
                    cards.clear();
                    cards.addAll(docs);
                    adapter.notifyDataSetChanged();
                    setLoading(false);
                },
                exception -> {
                    setLoading(false);
                    showLongToast("Failed to load cards: " + exception.getMessage());
                }
        );
    }

    private void onCardClicked(int position) {
        if (position < 0 || position >= cards.size()) {
            return;
        }

        DocumentSnapshot cardDoc = cards.get(position);
        String cardId = safeText(cardDoc.getString("card_id"), cardDoc.getId());

        if (!isNewFlow && !isReplaceFlow && !isReturnFlow) {
            showReadonlyDialog(cardDoc);
            return;
        }

        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(customerId)) {
            showLongToast("Missing business/customer details for card assignment");
            return;
        }

        if (isNewFlow) {
            new AlertDialog.Builder(this)
                    .setTitle("Assign Card")
                    .setMessage("Assign this card to the customer?\n\nCard: " + cardId)
                    .setPositiveButton("Assign", (dialog, which) -> assignNewCard(cardDoc, cardId))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (isReplaceFlow) {
            new AlertDialog.Builder(this)
                    .setTitle("Replace Card")
                    .setMessage("Replace the old card with this one?\n\nCard: " + cardId)
                    .setPositiveButton("Replace", (dialog, which) -> replaceCard(cardDoc, cardId))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void assignNewCard(DocumentSnapshot cardDoc, String cardId) {
        setLoading(true);
        cardRepository.assignCardToCustomer(
                businessId,
                customerId,
                cardDoc.getId(),
                cardId,
                () -> addCardComment(
                        ACTION_CARD_ASSIGNED,
                        customerId,
                        () -> {
                            setLoading(false);
                            Toast.makeText(this, "Card assigned successfully", Toast.LENGTH_SHORT).show();
                            navigateToCustomerProfileOrFinish();
                        }
                ),
                this::onActionError
        );
    }

    private void replaceCard(DocumentSnapshot newCardDoc, String newCardId) {
        setLoading(true);
        cardRepository.replaceCardForCustomer(
                businessId,
                customerId,
                newCardDoc.getId(),
                newCardId,
                () -> addCardComment(
                        ACTION_CARD_REPLACED,
                        customerId,
                        () -> {
                            setLoading(false);
                            Toast.makeText(this, "Card replaced successfully", Toast.LENGTH_SHORT).show();
                            navigateToCustomerProfileOrFinish();
                        }
                ),
                this::onActionError
        );
    }

    private void handleImmediateReturnFlowAndFinish() {
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(customerId)) {
            showLongToast("Missing business/customer details for card return");
            finish();
            return;
        }

        setLoading(true);
        cardRepository.returnCardFromCustomer(
                businessId,
                customerId,
                () -> addCardComment(
                        ACTION_CARD_RETURNED,
                        customerId,
                        () -> {
                            setLoading(false);
                            Toast.makeText(this, "Card returned and detached", Toast.LENGTH_SHORT).show();
                            navigateToCustomerProfileOrFinish();
                        }
                ),
                exception -> {
                    setLoading(false);
                    showLongToast(exception.getMessage() != null ? exception.getMessage() : "Failed to return card");
                    finish();
                }
        );
    }

    private void showReadonlyDialog(DocumentSnapshot cardDoc) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_card, null);
        LinearLayout container = dialogView.findViewById(R.id.card_info_container);
        Button btnUnassign = dialogView.findViewById(R.id.btn_unassign);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getDefault());

        Map<String, Object> data = cardDoc.getData();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                String displayValue;
                if (value instanceof Timestamp) {
                    displayValue = dateFormat.format(((Timestamp) value).toDate());
                } else {
                    displayValue = String.valueOf(value);
                }

                TextView fieldText = new TextView(this);
                fieldText.setText(entry.getKey() + " : " + displayValue);
                fieldText.setPadding(8, 8, 8, 8);
                container.addView(fieldText);
            }
        }

        String assignedTo = safeText(cardDoc.getString("card_assigned_to"), "");
        boolean canUnassign = !TextUtils.isEmpty(businessId) && !TextUtils.isEmpty(assignedTo);
        btnUnassign.setVisibility(canUnassign ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Card Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();

        if (canUnassign) {
            btnUnassign.setOnClickListener(v -> unassignCard(cardDoc, assignedTo, dialog));
        }

        dialog.show();
    }

    private void unassignCard(DocumentSnapshot cardDoc, String assignedCustomerId, AlertDialog dialog) {
        setLoading(true);
        cardRepository.unassignCard(
                businessId,
                assignedCustomerId,
                cardDoc.getId(),
                () -> addCardComment(
                        ACTION_CARD_UNASSIGNED,
                        assignedCustomerId,
                        () -> {
                            setLoading(false);
                            Toast.makeText(this, "Card unassigned successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadCards();
                        }
                ),
                this::onActionError
        );
    }

    private void addCardComment(String commentText, String commentCustomerId, Runnable next) {
        if (TextUtils.isEmpty(businessId)) {
            next.run();
            return;
        }

        if (!TextUtils.isEmpty(businessPrefix)) {
            persistCardComment(commentText, commentCustomerId, next);
            return;
        }

        businessRepository.fetchBusinessPrefix(
                businessId,
                prefix -> {
                    businessPrefix = safeText(prefix, businessId);
                    persistCardComment(commentText, commentCustomerId, next);
                },
                exception -> {
                    showLongToast("Comment skipped: " + exception.getMessage());
                    next.run();
                }
        );
    }

    private void persistCardComment(String commentText, String commentCustomerId, Runnable next) {
        String customerForComment = safeText(commentCustomerId, customerId);
        String creator = safeText(userEmail, "unknown");

        commentRepository.addComment(
                businessId,
                customerForComment,
                safeText(businessPrefix, businessId),
                COMMENT_ENTITY_TYPE,
                commentText,
                creator,
                next,
                exception -> {
                    showLongToast("Comment skipped: " + exception.getMessage());
                    next.run();
                }
        );
    }

    private void navigateToCustomerProfileOrFinish() {
        if (TextUtils.isEmpty(businessId) || TextUtils.isEmpty(customerId)) {
            finish();
            return;
        }

        Intent intent = new Intent(this, CustomerProfile.class);
        intent.putExtra("businessDocId", businessId);
        intent.putExtra("customerDocId", customerId);
        startActivity(intent);
        finish();
    }

    private void onActionError(Exception exception) {
        setLoading(false);
        showLongToast("Failed: " + (exception.getMessage() != null ? exception.getMessage() : "Unknown error"));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showLongToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static class CardAdapter extends RecyclerView.Adapter<CardAdapter.VH> {
        interface ItemClick {
            void onClick(int position);
        }

        private final List<DocumentSnapshot> list;
        private final ItemClick click;

        CardAdapter(List<DocumentSnapshot> list, ItemClick click) {
            this.list = list;
            this.click = click;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_assign, parent, false);
            return new VH(view, click);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DocumentSnapshot cardDoc = list.get(position);
            String cardId = cardDoc.getString("card_id");
            holder.tvTitle.setText(cardId != null ? cardId : cardDoc.getId());

            String assignedTo = cardDoc.getString("card_assigned_to");
            Boolean status = cardDoc.getBoolean("card_status");
            holder.tvDetail.setText("Assigned to: "
                    + (assignedTo == null || assignedTo.trim().isEmpty() ? DASH : assignedTo)
                    + " | Status: "
                    + (status == null ? DASH : String.valueOf(status)));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            private final TextView tvTitle;
            private final TextView tvDetail;

            VH(@NonNull View itemView, ItemClick click) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.card_title);
                tvDetail = itemView.findViewById(R.id.card_detail);
                itemView.setOnClickListener(view -> {
                    int adapterPosition = getAdapterPosition();
                    if (click != null && adapterPosition != RecyclerView.NO_POSITION) {
                        click.onClick(adapterPosition);
                    }
                });
            }
        }
    }
}
