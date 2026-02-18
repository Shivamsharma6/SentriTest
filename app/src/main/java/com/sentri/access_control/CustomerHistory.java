package com.sentri.access_control;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.repositories.FirestoreHistoryRepository;
import com.sentri.access_control.repositories.HistoryRepository;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CustomerHistory extends AppCompatActivity {

    private String businessId;
    private String customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_history);

        businessId = getIntent().getStringExtra("businessId");
        customerId = getIntent().getStringExtra("customerId");

        if (businessId == null || customerId == null) {
            Toast.makeText(this, "Missing businessId or customerId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ViewPager viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        HistoryPagerAdapter adapter =
                new HistoryPagerAdapter(getSupportFragmentManager(), businessId, customerId);
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    private static class HistoryPagerAdapter extends FragmentPagerAdapter {
        private final String businessId;
        private final String customerId;
        private final String[] titles = {"Payments", "Shifts", "Cards", "Access Logs", "Comments"};

        public HistoryPagerAdapter(@NonNull androidx.fragment.app.FragmentManager fragmentManager,
                                   String businessId,
                                   String customerId) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.businessId = businessId;
            this.customerId = customerId;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return HistoryListFragment.newInstance(
                            businessId, customerId, "payments", "payment_customer_id");
                case 1:
                    return HistoryListFragment.newInstance(
                            businessId, customerId, "customer_shifts", "shift_customer_id");
                case 2:
                    return HistoryListFragment.newInstance(
                            businessId, customerId, "cards", "card_assigned_to");
                case 3:
                    return HistoryListFragment.newInstance(
                            businessId, customerId, "access_logs", "log_entity_id");
                case 4:
                    return CommentsParentFragment.newInstance(businessId, customerId);
                default:
                    return new Fragment();
            }
        }

        @Override
        public int getCount() {
            return titles.length;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    public static class HistoryListFragment extends Fragment {
        private static final int PAGE_SIZE = 5;

        private String businessId;
        private String customerId;
        private String collection;
        private String filterField;

        private HistoryRepository historyRepository;

        private LinearLayout listContainer;
        private Button btnLoadMore;
        private ProgressBar progressBar;

        private DocumentSnapshot lastDoc;
        private boolean loading;
        private boolean hasLoadedOnce;
        private View root;

        public static HistoryListFragment newInstance(String businessId,
                                                      String customerId,
                                                      String collection,
                                                      String filterField) {
            HistoryListFragment fragment = new HistoryListFragment();
            Bundle args = new Bundle();
            args.putString("businessId", businessId);
            args.putString("customerId", customerId);
            args.putString("collection", collection);
            args.putString("filterField", filterField);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            if (root == null) {
                root = inflater.inflate(R.layout.fragment_history_list, container, false);
                listContainer = root.findViewById(R.id.listContainer);
                btnLoadMore = root.findViewById(R.id.btnLoadMore);
                progressBar = root.findViewById(R.id.progressBar);
                historyRepository = new FirestoreHistoryRepository(FirebaseFirestore.getInstance());

                if (getArguments() != null) {
                    businessId = getArguments().getString("businessId");
                    customerId = getArguments().getString("customerId");
                    collection = getArguments().getString("collection");
                    filterField = getArguments().getString("filterField");
                }

                btnLoadMore.setOnClickListener(v -> loadData());
            }

            if (!hasLoadedOnce) {
                loadData();
            }
            return root;
        }

        private void loadData() {
            if (loading) {
                return;
            }
            loading = true;
            progressBar.setVisibility(View.VISIBLE);
            btnLoadMore.setVisibility(View.GONE);

            if (!hasLoadedOnce) {
                listContainer.removeAllViews();
            }

            if (businessId == null || customerId == null || collection == null || filterField == null) {
                addEmptyMessage("Missing businessId/customerId");
                loading = false;
                progressBar.setVisibility(View.GONE);
                return;
            }

            historyRepository.fetchPagedRecords(
                    businessId,
                    collection,
                    filterField,
                    customerId,
                    resolveOrderField(collection),
                    lastDoc,
                    PAGE_SIZE,
                    this::onHistoryLoaded,
                    this::onHistoryLoadFailed
            );
        }

        private String resolveOrderField(String collection) {
            switch (collection) {
                case "access_logs":
                    return "log_timestamp";
                case "customer_shifts":
                case "payments":
                case "comments":
                    return "created_at";
                default:
                    return "updated_at";
            }
        }

        private void onHistoryLoaded(QuerySnapshot snapshot) {
            List<DocumentSnapshot> docs = snapshot.getDocuments();
            if (!docs.isEmpty()) {
                if (!hasLoadedOnce) {
                    listContainer.removeAllViews();
                }
                for (DocumentSnapshot doc : docs) {
                    addRecordView(doc);
                }
                lastDoc = docs.get(docs.size() - 1);
                btnLoadMore.setVisibility(docs.size() < PAGE_SIZE ? View.GONE : View.VISIBLE);
            } else {
                if (!hasLoadedOnce) {
                    addEmptyMessage("No data available");
                }
                btnLoadMore.setVisibility(View.GONE);
            }

            hasLoadedOnce = true;
            loading = false;
            progressBar.setVisibility(View.GONE);
        }

        private void onHistoryLoadFailed(Exception exception) {
            loading = false;
            progressBar.setVisibility(View.GONE);
            if (!hasLoadedOnce) {
                addEmptyMessage("Failed to load: " + exception.getMessage());
            }
            Toast.makeText(getContext(), "Failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }

        private void addEmptyMessage(String text) {
            listContainer.removeAllViews();
            TextView tv = new TextView(getContext());
            tv.setPadding(16, 12, 16, 12);
            tv.setText(text);
            listContainer.addView(tv);
        }

        private void addRecordView(DocumentSnapshot doc) {
            TextView tv = new TextView(getContext());
            tv.setPadding(24, 16, 24, 16);
            tv.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

            StringBuilder builder = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getDefault());

            Map<String, Object> data = doc.getData();
            if (data == null) {
                tv.setText("");
                listContainer.addView(tv);
                return;
            }

            String collection = this.collection != null ? this.collection : "";

            if ("payments".equals(collection)) {
                Object createdAt = data.get("created_at");
                String dateText = createdAt instanceof com.google.firebase.Timestamp
                        ? dateFormat.format(((com.google.firebase.Timestamp) createdAt).toDate())
                        : "";
                String amount = String.valueOf(data.get("payment_amount"));
                String rate = String.valueOf(data.get("payment_rate"));
                String method = String.valueOf(data.get("payment_method"));
                String type = String.valueOf(data.get("payment_type"));

                builder.append("Date: ").append(dateText).append("\n");
                builder.append("Amount: ").append(amount).append("\n");
                builder.append("Rate: ").append(rate).append("\n");
                builder.append("Method: ").append(method).append("\n");
                if (type != null && !"null".equalsIgnoreCase(type)) {
                    builder.append("Type: ").append(type).append("\n");
                }
            } else if ("customer_shifts".equals(collection)) {
                Object startTs = data.get("shift_start_time");
                Object endTs = data.get("shift_end_time");
                String startDate = startTs instanceof com.google.firebase.Timestamp
                        ? dateFormat.format(((com.google.firebase.Timestamp) startTs).toDate())
                        : "";
                String endDate = endTs instanceof com.google.firebase.Timestamp
                        ? dateFormat.format(((com.google.firebase.Timestamp) endTs).toDate())
                        : "";

                String seat = String.valueOf(data.get("shift_seat"));
                String rate = String.valueOf(data.get("shift_payment_rate"));

                builder.append("From: ").append(startDate).append("\n");
                builder.append("To: ").append(endDate).append("\n");
                builder.append("Seat: ").append(seat).append("\n");
                builder.append("Rate: ").append(rate).append("\n");
            } else if ("cards".equals(collection)) {
                String cardId = String.valueOf(data.get("card_id"));
                String status = String.valueOf(data.get("card_status"));
                Object assignedAt = data.get("created_at");
                String dateText = assignedAt instanceof com.google.firebase.Timestamp
                        ? dateFormat.format(((com.google.firebase.Timestamp) assignedAt).toDate())
                        : "";

                builder.append("Card ID: ").append(cardId).append("\n");
                builder.append("Status: ").append(status).append("\n");
                builder.append("Date: ").append(dateText).append("\n");
            } else if ("access_logs".equals(collection)) {
                Object ts = data.get("log_timestamp");
                String dateText = ts instanceof com.google.firebase.Timestamp
                        ? dateFormat.format(((com.google.firebase.Timestamp) ts).toDate())
                        : "";
                String direction = String.valueOf(data.get("log_direction"));
                String source = String.valueOf(data.get("log_source"));

                builder.append("Date: ").append(dateText).append("\n");
                builder.append("Direction: ").append(direction).append("\n");
                builder.append("Source: ").append(source).append("\n");
            } else {
                // fallback: keep readable but avoid raw internal keys when possible
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String key = entry.getKey();
                    if ("updated_at".equals(key)
                            || "created_by".equals(key)
                            || key.endsWith("_id")
                            || key.endsWith("_business_id")
                            || key.endsWith("_customer_id")) {
                        continue;
                    }
                    Object value = entry.getValue();
                    String displayValue;
                    if (value instanceof com.google.firebase.Timestamp) {
                        displayValue = dateFormat.format(((com.google.firebase.Timestamp) value).toDate());
                    } else {
                        displayValue = String.valueOf(value);
                    }
                    builder.append(displayFriendlyLabel(key)).append(": ").append(displayValue).append("\n");
                }
            }

            tv.setText(builder.toString().trim());
            listContainer.addView(tv);
        }

        private String displayFriendlyLabel(String rawKey) {
            if (rawKey == null || rawKey.isEmpty()) {
                return "";
            }
            // simple transform: snake_case / lowerCamelCase -> Title Case words
            String key = rawKey.replace("_", " ");
            StringBuilder b = new StringBuilder();
            boolean newWord = true;
            for (int i = 0; i < key.length(); i++) {
                char ch = key.charAt(i);
                if (Character.isWhitespace(ch)) {
                    newWord = true;
                    b.append(' ');
                } else {
                    if (newWord) {
                        b.append(Character.toUpperCase(ch));
                        newWord = false;
                    } else {
                        b.append(ch);
                    }
                }
            }
            return b.toString();
        }
    }

    public static class CommentsParentFragment extends Fragment {
        private static final String ARG_BUSINESS_ID = "businessId";
        private static final String ARG_CUSTOMER_ID = "customerId";

        public static CommentsParentFragment newInstance(String businessId, String customerId) {
            CommentsParentFragment fragment = new CommentsParentFragment();
            Bundle args = new Bundle();
            args.putString(ARG_BUSINESS_ID, businessId);
            args.putString(ARG_CUSTOMER_ID, customerId);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.fragment_comments_parent, container, false);
            String businessId = getArguments() != null ? getArguments().getString(ARG_BUSINESS_ID) : null;
            String customerId = getArguments() != null ? getArguments().getString(ARG_CUSTOMER_ID) : null;

            ViewPager viewPager = root.findViewById(R.id.commentsViewPager);
            TabLayout tabLayout = root.findViewById(R.id.commentsTabLayout);

            CommentsPagerAdapter adapter =
                    new CommentsPagerAdapter(getChildFragmentManager(), businessId, customerId);
            viewPager.setAdapter(adapter);
            tabLayout.setupWithViewPager(viewPager);
            return root;
        }
    }

    private static class CommentsPagerAdapter extends FragmentPagerAdapter {
        private final String businessId;
        private final String customerId;
        private final String[] titles = {"All", "Cards", "Shift", "Payment", "Customer"};

        public CommentsPagerAdapter(@NonNull androidx.fragment.app.FragmentManager fragmentManager,
                                    String businessId,
                                    String customerId) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.businessId = businessId;
            this.customerId = customerId;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            String type = null;
            switch (position) {
                case 1:
                    type = "cards";
                    break;
                case 2:
                    type = "shift";
                    break;
                case 3:
                    type = "payment";
                    break;
                case 4:
                    type = "customer";
                    break;
            }
            return CommentListFragment.newInstance(businessId, customerId, type);
        }

        @Override
        public int getCount() {
            return titles.length;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    public static class CommentListFragment extends Fragment {
        private static final int PAGE_SIZE = 5;

        private String businessId;
        private String customerId;
        private String entityType;

        private HistoryRepository historyRepository;

        private LinearLayout listContainer;
        private Button btnLoadMore;
        private ProgressBar progressBar;

        private DocumentSnapshot lastDoc;
        private boolean loading;
        private boolean hasLoadedOnce;
        private View root;

        public static CommentListFragment newInstance(String businessId,
                                                      String customerId,
                                                      String entityType) {
            CommentListFragment fragment = new CommentListFragment();
            Bundle args = new Bundle();
            args.putString("businessId", businessId);
            args.putString("customerId", customerId);
            args.putString("entityType", entityType);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            if (root == null) {
                root = inflater.inflate(R.layout.fragment_history_list, container, false);
                listContainer = root.findViewById(R.id.listContainer);
                btnLoadMore = root.findViewById(R.id.btnLoadMore);
                progressBar = root.findViewById(R.id.progressBar);
                historyRepository = new FirestoreHistoryRepository(FirebaseFirestore.getInstance());

                if (getArguments() != null) {
                    businessId = getArguments().getString("businessId");
                    customerId = getArguments().getString("customerId");
                    entityType = getArguments().getString("entityType");
                }

                btnLoadMore.setOnClickListener(v -> loadData());
            }

            if (!hasLoadedOnce) {
                loadData();
            }
            return root;
        }

        private void loadData() {
            if (loading) {
                return;
            }

            loading = true;
            progressBar.setVisibility(View.VISIBLE);
            btnLoadMore.setVisibility(View.GONE);
            if (!hasLoadedOnce) {
                listContainer.removeAllViews();
            }

            if (businessId == null || customerId == null) {
                addEmptyMessage("Missing businessId/customerId");
                loading = false;
                progressBar.setVisibility(View.GONE);
                return;
            }

            historyRepository.fetchPagedComments(
                    businessId,
                    customerId,
                    entityType,
                    lastDoc,
                    PAGE_SIZE,
                    this::onCommentsLoaded,
                    this::onCommentsLoadFailed
            );
        }

        private void onCommentsLoaded(QuerySnapshot snapshot) {
            List<DocumentSnapshot> docs = snapshot.getDocuments();
            if (!docs.isEmpty()) {
                if (!hasLoadedOnce) {
                    listContainer.removeAllViews();
                }
                for (DocumentSnapshot doc : docs) {
                    addCommentView(doc);
                }
                lastDoc = docs.get(docs.size() - 1);
                btnLoadMore.setVisibility(docs.size() < PAGE_SIZE ? View.GONE : View.VISIBLE);
            } else {
                if (!hasLoadedOnce) {
                    addEmptyMessage("No data available");
                }
                btnLoadMore.setVisibility(View.GONE);
            }

            hasLoadedOnce = true;
            loading = false;
            progressBar.setVisibility(View.GONE);
        }

        private void onCommentsLoadFailed(Exception exception) {
            loading = false;
            progressBar.setVisibility(View.GONE);
            if (!hasLoadedOnce) {
                addEmptyMessage("Failed: " + exception.getMessage());
            }
            Toast.makeText(getContext(), "Failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        }

        private void addEmptyMessage(String text) {
            listContainer.removeAllViews();
            TextView tv = new TextView(getContext());
            tv.setPadding(16, 12, 16, 12);
            tv.setText(text);
            listContainer.addView(tv);
        }

        private void addCommentView(DocumentSnapshot doc) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getDefault());

            String dateText = "";
            if (doc.contains("created_at") && doc.get("created_at") instanceof com.google.firebase.Timestamp) {
                dateText = dateFormat.format(((com.google.firebase.Timestamp) doc.get("created_at")).toDate());
            }

            TextView tv = new TextView(getContext());
            tv.setPadding(16, 8, 16, 8);
            tv.setText("- " + doc.getString("comment_text") + " (" + dateText + ")");
            listContainer.addView(tv);
        }
    }
}
