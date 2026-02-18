package com.sentri.access_control;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
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
import com.sentri.access_control.adapters.CommentsAdapter;
import com.sentri.access_control.models.CommentItem;
import com.sentri.access_control.repositories.CommentRepository;
import com.sentri.access_control.repositories.FirestoreCommentRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class CustomerComments extends AppCompatActivity {

    private static final int MAX_COMMENTS = 100;

    private EditText etComment;
    private TextView tvCharCount;
    private CommentsAdapter adapter;

    private String businessId;
    private String customerId;
    private CommentRepository commentRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_comments);

        businessId = new PrefsManager(this).getCurrentBizId();
        customerId = getIntent().getStringExtra("customerId");

        if (businessId == null || businessId.trim().isEmpty() || customerId == null || customerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing business/customer details", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        commentRepository = new FirestoreCommentRepository(FirebaseFirestore.getInstance());

        etComment = findViewById(R.id.etComment);
        tvCharCount = findViewById(R.id.tvCharCount);
        RecyclerView recyclerComments = findViewById(R.id.recyclerComments);
        ImageView backBtn = findViewById(R.id.ivBack);

        etComment.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                tvCharCount.setText(editable.length() + "/100");
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        adapter = new CommentsAdapter(new ArrayList<>());
        recyclerComments.setLayoutManager(new LinearLayoutManager(this));
        recyclerComments.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());

        loadComments();
    }

    private void loadComments() {
        commentRepository.fetchCommentsByCustomer(
                businessId,
                customerId,
                MAX_COMMENTS,
                snapshot -> {
                    List<CommentItem> commentItems = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String text = safeString(doc.getString("comment_text"));
                        String createdBy = safeString(doc.getString("created_by"));
                        Timestamp createdAt = doc.getTimestamp("created_at");

                        String date = createdAt != null
                                ? DateFormat.format("d MMM, yyyy hh:mm a", createdAt.toDate()).toString()
                                : "-";

                        String body = createdBy.isEmpty() ? text : text + " (" + createdBy + ")";
                        commentItems.add(new CommentItem(body, date));
                    }
                    adapter.updateList(commentItems);
                },
                e -> Toast.makeText(this, "Failed to load comments: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private String safeString(String value) {
        return value != null ? value.trim() : "";
    }
}
