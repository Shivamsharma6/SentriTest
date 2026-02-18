package com.sentri.access_control;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sentri.access_control.repositories.FirestoreUserRepository;
import com.sentri.access_control.repositories.UserRepository;
import com.sentri.access_control.utils.PrefsManager;

public class UserDetails extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private ImageView ivUserPhoto;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvUserPhone;

    private UserRepository userRepository;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        toolbar = findViewById(R.id.toolbar);
        ivUserPhoto = findViewById(R.id.ivUserPhoto);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvUserPhone = findViewById(R.id.tvUserPhone);

        toolbar.setNavigationOnClickListener(v -> finish());

        userRepository = new FirestoreUserRepository(FirebaseFirestore.getInstance());
        userEmail = new PrefsManager(this).getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadUserDetails();
    }

    private void loadUserDetails() {
        userRepository.fetchUserDocument(
                userEmail,
                doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String email = valueOf(doc.getString("user_email"));
                    String phone = valueOf(doc.getString("user_contact"));
                    String photoUrl = valueOf(doc.getString("user_photo_url"));
                    if (photoUrl.isEmpty()) {
                        photoUrl = valueOf(doc.getString("photo_url"));
                    }

                    tvUserName.setText(userEmail);
                    tvUserEmail.setText(email.isEmpty() ? "-" : email);
                    tvUserPhone.setText(phone.isEmpty() ? "-" : phone);

                    if (!photoUrl.isEmpty()) {
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(ivUserPhoto);
                    } else {
                        ivUserPhoto.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                },
                exception -> Toast.makeText(this,
                        "Failed to load user details: " + exception.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    private String valueOf(String value) {
        return value != null ? value.trim() : "";
    }
}
