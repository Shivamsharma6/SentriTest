package com.sentri.access_control;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.FirestoreUserRepository;
import com.sentri.access_control.repositories.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SignUp extends AppCompatActivity {

    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnSignUp;
    private TextView tvHaveAccount;

    private FirebaseAuth auth;
    private UserRepository userRepository;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvHaveAccount = findViewById(R.id.tvHaveAccount);

        auth = FirebaseAuth.getInstance();
        userRepository = new FirestoreUserRepository(FirebaseFirestore.getInstance());

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating account...");
        progressDialog.setCancelable(false);

        btnSignUp.setOnClickListener(view -> attemptSignUp());
        tvHaveAccount.setOnClickListener(view -> finish());
    }

    private void attemptSignUp() {
        String name = getText(etName);
        String email = getText(etEmail);
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        if (name.isEmpty()) {
            etName.setError("Required");
            etName.requestFocus();
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Valid email required");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        progressDialog.show();
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        progressDialog.dismiss();
                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (currentUser == null) {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Registration succeeded, but user is null.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    currentUser.updateProfile(profileUpdates)
                            .addOnCompleteListener(profileTask -> createRootUserRecord(currentUser));
                });
    }

    private void createRootUserRecord(FirebaseUser firebaseUser) {
        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail().trim() : "";
        if (email.isEmpty()) {
            progressDialog.dismiss();
            Toast.makeText(this, "Cannot create user record: email is empty.", Toast.LENGTH_LONG).show();
            sendEmailVerificationAndFinish(firebaseUser);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_email", email);
        payload.put("created_at", FieldValue.serverTimestamp());
        payload.put("updated_at", FieldValue.serverTimestamp());
        payload.put("last_login", null);
        payload.put("user_business_access_id", new ArrayList<String>());
        payload.put("user_business_access_levels", new ArrayList<String>());
        payload.put("user_business_access_name", new ArrayList<String>());
        payload.put("user_business_granted", new ArrayList<String>());
        payload.put("user_business_status", new ArrayList<Object>());
        payload.put("user_current_business_access_level", "");
        payload.put("user_current_business_granted", "");
        payload.put("user_current_business_id", "");
        payload.put("user_current_business_name", "");
        payload.put("user_current_business_status", false);

        userRepository.createUserDocument(
                email,
                payload,
                () -> {
                    progressDialog.dismiss();
                    sendEmailVerificationAndFinish(firebaseUser);
                },
                exception -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Warning: user save failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    sendEmailVerificationAndFinish(firebaseUser);
                }
        );
    }

    private void sendEmailVerificationAndFinish(FirebaseUser user) {
        user.sendEmailVerification().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Toast.makeText(SignUp.this, "Verification email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                } else {
                    String message = task.getException() != null
                            ? task.getException().getMessage()
                            : "Failed to send verification email.";
                    Toast.makeText(SignUp.this, "Warning: " + message, Toast.LENGTH_LONG).show();
                }

                Intent intent = new Intent(SignUp.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
