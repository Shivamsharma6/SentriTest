package com.sentri.access_control;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.FirestoreUserRepository;
import com.sentri.access_control.repositories.UserRepository;
import com.sentri.access_control.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSignIn;
    private MaterialButton btnSignUp;
    private TextView tvForgot;

    private FirebaseAuth auth;
    private UserRepository userRepository;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PrefsManager prefsManager = new PrefsManager(this);
        if (prefsManager.hasSession()) {
            navigateToHome(
                    new ArrayList<>(prefsManager.getBizIds()),
                    new ArrayList<>(prefsManager.getBizNames()),
                    prefsManager.getCurrentBizId()
            );
            return;
        }

        setContentView(R.layout.activity_main);
        auth = FirebaseAuth.getInstance();
        userRepository = new FirestoreUserRepository(FirebaseFirestore.getInstance());

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Signing in...");
        progressDialog.setCancelable(false);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvForgot = findViewById(R.id.tvForgot);

        btnSignIn.setOnClickListener(v -> attemptLogin());
        btnSignUp.setOnClickListener(v -> startActivity(new Intent(this, SignUp.class)));
        tvForgot.setOnClickListener(v -> startActivity(new Intent(this, ForgotPassword.class)));
    }

    private void attemptLogin() {
        String email = getText(etEmail);
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        progressDialog.dismiss();
                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Authentication failed";
                        Toast.makeText(this, "Sign-in failed: " + message, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (currentUser != null && !currentUser.isEmailVerified()) {
                        progressDialog.dismiss();
                        showUnverifiedDialog(currentUser);
                        auth.signOut();
                        return;
                    }

                    fetchUserDocumentAndContinue(email);
                });
    }

    private void fetchUserDocumentAndContinue(String email) {
        userRepository.fetchUserDocument(
                email,
                this::handleUserDocument,
                e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }

    private void handleUserDocument(DocumentSnapshot doc) {
        progressDialog.dismiss();
        if (!doc.exists()) {
            Toast.makeText(this, "No user record found in database", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> businessIds = readStringList(doc, "user_business_access_id");
        List<String> businessNames = readStringList(doc, "user_business_access_name");
        List<String> accessLevels = readStringList(doc, "user_business_access_levels");
        while (businessNames.size() < businessIds.size()) {
            businessNames.add(businessIds.get(businessNames.size()));
        }
        while (accessLevels.size() < businessIds.size()) {
            accessLevels.add("");
        }

        if (businessIds.isEmpty() || businessNames.isEmpty()) {
            Toast.makeText(this, "No business access assigned", Toast.LENGTH_LONG).show();
            return;
        }

        String currentBiz = doc.getString("user_current_business_id");
        if (currentBiz == null || currentBiz.trim().isEmpty()) {
            currentBiz = businessIds.get(0);
        }

        int selectedBizIndex = Math.max(0, businessIds.indexOf(currentBiz));
        String currentBizName = doc.getString("user_current_business_name");
        if (currentBizName == null || currentBizName.trim().isEmpty()) {
            currentBizName = businessNames.size() > selectedBizIndex
                    ? businessNames.get(selectedBizIndex)
                    : businessNames.get(0);
        }

        String currentAccessLevel = doc.getString("user_current_business_access_level");
        if ((currentAccessLevel == null || currentAccessLevel.trim().isEmpty())
                && accessLevels.size() > selectedBizIndex) {
            currentAccessLevel = accessLevels.get(selectedBizIndex);
        }

        String email = auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null
                ? auth.getCurrentUser().getEmail()
                : getText(etEmail);

        PrefsManager prefsManager = new PrefsManager(this);
        prefsManager.saveSession(
                email,
                currentBiz,
                currentBizName,
                businessIds,
                businessNames,
                accessLevels,
                currentAccessLevel,
                doc.getString("user_photo_url")
        );

        userRepository.updateLastLogin(doc.getId(), null, ignored -> {
        });
        navigateToHome(new ArrayList<>(businessIds), new ArrayList<>(businessNames), currentBiz);
    }

    private List<String> readStringList(DocumentSnapshot doc, String fieldName) {
        Object value = doc.get(fieldName);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private void navigateToHome(ArrayList<String> businessIds, ArrayList<String> businessNames, String currentBiz) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putStringArrayListExtra("EXTRA_BIZ_IDS", businessIds);
        intent.putStringArrayListExtra("EXTRA_BIZ_NAMES", businessNames);
        intent.putExtra("EXTRA_CURRENT_BIZ", currentBiz);
        startActivity(intent);
        finish();
    }

    private void showUnverifiedDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Email not verified")
                .setMessage("Please verify your email before signing in. Resend verification email?")
                .setPositiveButton("Resend", (dialog, which) -> user.sendEmailVerification()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Verification email resent", Toast.LENGTH_LONG).show();
                            } else {
                                String message = task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Failed to resend verification";
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
