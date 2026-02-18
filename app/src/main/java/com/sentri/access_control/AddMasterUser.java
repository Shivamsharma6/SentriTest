package com.sentri.access_control;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.FirestoreUserRepository;
import com.sentri.access_control.repositories.UserRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddMasterUser extends AppCompatActivity {
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSave;

    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_master_user);

        userRepository = new FirestoreUserRepository(FirebaseFirestore.getInstance());

        etEmail = findViewById(R.id.etMasterEmail);
        etPassword = findViewById(R.id.etMasterPassword);
        btnSave = findViewById(R.id.btnSaveMaster);

        btnSave.setOnClickListener(v -> saveMasterUser());
    }

    private void saveMasterUser() {
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        String grantedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        Map<String, Object> userData = buildMasterUserPayload(email, password, grantedAt);

        btnSave.setEnabled(false);
        userRepository.createUserDocument(
                email,
                userData,
                () -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Master user added: " + email, Toast.LENGTH_SHORT).show();
                    finish();
                },
                exception -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Failed to add: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private Map<String, Object> buildMasterUserPayload(String email, String password, String grantedAt) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("user_email", email);
        userData.put("password", password);
        userData.put("created_at", FieldValue.serverTimestamp());
        userData.put("updated_at", FieldValue.serverTimestamp());
        userData.put("last_login", FieldValue.serverTimestamp());
        userData.put("user_business_access_id", new ArrayList<>());
        userData.put("user_business_access_levels", new ArrayList<>());
        userData.put("user_business_access_name", new ArrayList<>());
        userData.put("user_business_granted", new ArrayList<>());
        userData.put("user_business_status", new ArrayList<>());
        userData.put("user_current_business_id", "");
        userData.put("user_current_business_name", "");
        userData.put("user_current_business_access_level", "");
        userData.put("user_current_business_granted", grantedAt);
        userData.put("user_current_business_status", false);
        return userData;
    }

    private String getText(TextInputEditText editText) {
        CharSequence value = editText.getText();
        return value != null ? value.toString().trim() : "";
    }
}
