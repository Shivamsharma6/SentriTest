package com.sentri.access_control;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.sentri.access_control.adapters.CustomerAdapter;
import com.sentri.access_control.models.Customer;
import com.sentri.access_control.repositories.CustomerRepository;
import com.sentri.access_control.repositories.FirestoreCustomerRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class CustomerList extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CustomerAdapter adapter;
    private final List<Customer> customerList = new ArrayList<>();

    private String businessDocId;
    private Chip chipAll;
    private Chip chipActive;
    private Chip chipInactive;
    private EditText etSearch;

    private CustomerRepository customerRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_list);

        setupToolbar();
        setupState();
        setupList();
        setupActions();
        loadCustomers();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupState() {
        businessDocId = new PrefsManager(this).getCurrentBizId();
        customerRepository = new FirestoreCustomerRepository(FirebaseFirestore.getInstance());

        chipAll = findViewById(R.id.chipAll);
        chipActive = findViewById(R.id.chipActive);
        chipInactive = findViewById(R.id.chipInactive);
        etSearch = findViewById(R.id.etSearch);
    }

    private void setupList() {
        recyclerView = findViewById(R.id.recyclerViewCustomers);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomerAdapter(customerList);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(customer -> {
            Intent intent = new Intent(CustomerList.this, CustomerProfile.class);
            intent.putExtra("businessDocId", businessDocId);
            intent.putExtra("customerDocId", customer.getCustomerId());
            startActivity(intent);
        });
    }

    private void setupActions() {
        FloatingActionButton fab = findViewById(R.id.fabAddCustomer);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(CustomerList.this, AddCustomer.class);
            intent.putExtra("businessDocId", businessDocId);
            startActivity(intent);
        });

        chipAll.setOnCheckedChangeListener((chip, checked) -> reloadAndFilter());
        chipActive.setOnCheckedChangeListener((chip, checked) -> reloadAndFilter());
        chipInactive.setOnCheckedChangeListener((chip, checked) -> reloadAndFilter());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void reloadAndFilter() {
        loadCustomers();
    }

    private void loadCustomers() {
        if (businessDocId == null || businessDocId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected", Toast.LENGTH_LONG).show();
            return;
        }

        customerRepository.fetchCustomers(
                businessDocId,
                this::onCustomersLoaded,
                e -> Toast.makeText(this, "Error loading customers: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    private void onCustomersLoaded(QuerySnapshot snapshots) {
        boolean showAll = chipAll.isChecked();
        boolean showActive = chipActive.isChecked();
        boolean showInactive = chipInactive.isChecked();

        List<Customer> filtered = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Customer customer = toCustomer(doc);
            if (!showAll) {
                if (showActive && !customer.isCustomerStatus()) {
                    continue;
                }
                if (showInactive && customer.isCustomerStatus()) {
                    continue;
                }
            }
            filtered.add(customer);
        }

        adapter.updateList(filtered);
        adapter.filter(etSearch.getText().toString());
    }

    private Customer toCustomer(DocumentSnapshot doc) {
        String name = safeString(doc.getString("customer_name"));
        String id = safeString(doc.getString("customer_id"));
        String cardId = safeString(doc.getString("customer_current_card_id"));
        boolean status = Boolean.TRUE.equals(doc.getBoolean("customer_status"));
        String photoUrl = safeString(doc.getString("customer_photo"));
        String aadharUrl = safeString(doc.getString("customer_aadhar_photo"));

        return new Customer(name, id, cardId, status, photoUrl, aadharUrl);
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}
