package com.sentri.access_control;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.adapters.UserAdapter;
import com.sentri.access_control.models.UserModel;
import com.sentri.access_control.repositories.BusinessUserRepository;
import com.sentri.access_control.repositories.FirestoreBusinessUserRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class UserList extends AppCompatActivity {

    private ImageView ivBack;
    private ImageView ivSearch;
    private EditText etSearch;
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddUser;

    private final List<UserModel> userList = new ArrayList<>();
    private UserAdapter adapter;

    private BusinessUserRepository businessUserRepository;
    private String businessId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        businessId = new PrefsManager(this).getCurrentBizId();
        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        businessUserRepository = new FirestoreBusinessUserRepository(FirebaseFirestore.getInstance());

        bindViews();
        setupRecycler();
        setupActions();
        loadUsersForBusiness();
    }

    private void bindViews() {
        ivBack = findViewById(R.id.ivBack);
        etSearch = findViewById(R.id.etSearch);
        ivSearch = findViewById(R.id.ivSearch);
        recyclerView = findViewById(R.id.recyclerViewUsers);
        fabAddUser = findViewById(R.id.fabAddUser);
    }

    private void setupRecycler() {
        adapter = new UserAdapter(userList, user -> {
            Intent intent = new Intent(UserList.this, UserProfile.class);
            intent.putExtra("businessDocId", businessId);
            intent.putExtra("userId", user.getUserId());
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        ivBack.setOnClickListener(v -> finish());
        ivSearch.setOnClickListener(v -> adapter.filter(etSearch.getText().toString().trim()));
        fabAddUser.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddUser.class);
            intent.putExtra("businessDocId", businessId);
            startActivity(intent);
        });
    }

    private void loadUsersForBusiness() {
        businessUserRepository.fetchBusinessUsers(
                businessId,
                docs -> {
                    userList.clear();
                    for (DocumentSnapshot doc : docs) {
                        userList.add(mapUser(doc));
                    }
                    adapter.updateList(userList);
                },
                exception -> Toast.makeText(this,
                        "Error loading users: " + exception.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private UserModel mapUser(DocumentSnapshot doc) {
        return new UserModel(
                doc.getId(),
                valueOf(doc.getString("name")),
                valueOf(doc.getString("user_email")),
                valueOf(doc.getString("user_contact")),
                valueOf(doc.getString("user_photo")),
                valueOf(doc.getString("user_access_level"))
        );
    }

    private String valueOf(String value) {
        return value != null ? value : "";
    }
}
