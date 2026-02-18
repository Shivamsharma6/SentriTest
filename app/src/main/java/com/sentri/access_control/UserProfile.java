package com.sentri.access_control;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.sentri.access_control.repositories.BusinessUserRepository;
import com.sentri.access_control.repositories.FirestoreBusinessUserRepository;
import com.sentri.access_control.utils.ImageUploadHelper;
import com.sentri.access_control.utils.PrefsManager;

import java.util.HashMap;
import java.util.Map;

public class UserProfile extends AppCompatActivity {
    private static final int REQ_PROFILE_PHOTO = 1001;
    private static final int REQ_AADHAR_PHOTO = 1002;

    private MaterialToolbar toolbar;
    private ImageView ivProfilePhoto;
    private ImageView ivAadharPhoto;
    private TextView tvEmail;
    private TextView tvGrantedAt;
    private TextView tvCreatedAt;
    private TextView tvUpdatedAt;
    private EditText etContact;
    private EditText etCurrentCard;
    private Spinner spinnerAccess;
    private Spinner spinnerStatus;
    private Button btnEditSave;

    private BusinessUserRepository businessUserRepository;
    private String businessId;
    private String userId;
    private String userAccessLevel;

    private boolean isEditing = false;
    private Uri newProfileUri;
    private Uri newAadharUri;
    private String currentProfileUrl = "";
    private String currentAadharUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        businessUserRepository = new FirestoreBusinessUserRepository(FirebaseFirestore.getInstance());
        businessId = getIntent().getStringExtra("businessDocId");
        userId = getIntent().getStringExtra("userId");
        if (businessId == null || userId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userAccessLevel = new PrefsManager(this).getCurrentBizAccessLevel();

        bindViews();
        setupSpinners();
        setupActions();
        loadUser();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        ivAadharPhoto = findViewById(R.id.ivAadharPhoto);
        tvEmail = findViewById(R.id.tvEmail);
        etContact = findViewById(R.id.etContact);
        etCurrentCard = findViewById(R.id.etCurrentCard);
        spinnerAccess = findViewById(R.id.spinnerAccess);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        tvGrantedAt = findViewById(R.id.tvGrantedAt);
        tvCreatedAt = findViewById(R.id.tvCreatedAt);
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt);
        btnEditSave = findViewById(R.id.btnEditSave);
    }

    private void setupSpinners() {
        ArrayAdapter<String> accessAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"admin", "manager"}
        );
        accessAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccess.setAdapter(accessAdapter);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Active", "Inactive"}
        );
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);
    }

    private void setupActions() {
        toolbar.setNavigationOnClickListener(v -> finish());

        // Actual visibility and permission for editing are decided after user
        // document is loaded in loadUser(), where we know the target's access level.
        btnEditSave.setOnClickListener(v -> toggleEditSave());

        View.OnClickListener photoClickListener = v -> {
            if (isEditing) {
                pickImage(v.getId() == R.id.ivProfilePhoto ? REQ_PROFILE_PHOTO : REQ_AADHAR_PHOTO);
                return;
            }
            String url = v.getId() == R.id.ivProfilePhoto ? currentProfileUrl : currentAadharUrl;
            openPhotoInViewer(url);
        };
        ivProfilePhoto.setOnClickListener(photoClickListener);
        ivAadharPhoto.setOnClickListener(photoClickListener);
    }

    private void loadUser() {
        businessUserRepository.fetchBusinessUser(
                businessId,
                userId,
                doc -> {
                    if (!doc.exists()) {
                        finish();
                        return;
                    }

                    currentProfileUrl = valueOf(doc.getString("user_photo"));
                    currentAadharUrl = valueOf(doc.getString("user_aadhar_photo"));

                    if (!currentProfileUrl.isEmpty()) {
                        Glide.with(this).load(currentProfileUrl).circleCrop().into(ivProfilePhoto);
                    } else {
                        ivProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                    if (!currentAadharUrl.isEmpty()) {
                        Glide.with(this).load(currentAadharUrl).circleCrop().into(ivAadharPhoto);
                    } else {
                        ivAadharPhoto.setImageResource(R.drawable.ic_avatar_placeholder);
                    }

                    tvEmail.setText(valueOf(doc.getString("user_email")));
                    etContact.setText(valueOf(doc.getString("user_contact")));
                    etCurrentCard.setText(valueOf(doc.getString("user_current_card")));

                    String accessLevel = valueOf(doc.getString("user_access_level"));
                    spinnerAccess.setSelection("admin".equalsIgnoreCase(accessLevel) ? 0 : 1);

                    String statusValue = valueOf(doc.getString("user_status"));
                    spinnerStatus.setSelection("true".equalsIgnoreCase(statusValue) ? 0 : 1);

                    tvGrantedAt.setText(formatTimestamp(doc.getTimestamp("user_granted_at")));
                    tvCreatedAt.setText(formatTimestamp(doc.getTimestamp("created_at")));
                    tvUpdatedAt.setText(formatTimestamp(doc.getTimestamp("updated_at")));

                    boolean canEdit = canCurrentUserEditTarget(userAccessLevel, accessLevel);
                    btnEditSave.setVisibility(canEdit ? View.VISIBLE : View.GONE);
                    setFieldsEditable(false);
                    btnEditSave.setText("Edit");
                    isEditing = false;
                },
                exception -> Toast.makeText(this, "Failed to load profile: " + exception.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    /**
     * Permission rules:
     * - Admin (owner) can edit any user.
     * - Manager can edit managers (and any lower-level role such as "user").
     * - Others cannot edit.
     */
    private boolean canCurrentUserEditTarget(String currentAccess, String targetAccess) {
        String current = currentAccess != null ? currentAccess.toLowerCase() : "";
        String target = targetAccess != null ? targetAccess.toLowerCase() : "";

        if ("admin".equals(current)) {
            return true;
        }
        if ("manager".equals(current)) {
            return "manager".equals(target) || "user".equals(target);
        }
        return false;
    }

    private void toggleEditSave() {
        if (!isEditing) {
            setFieldsEditable(true);
            btnEditSave.setText("Save");
            isEditing = true;
            return;
        }
        saveChanges();
    }

    private void setFieldsEditable(boolean editable) {
        etContact.setEnabled(editable);
        etCurrentCard.setEnabled(editable);
        spinnerAccess.setEnabled(editable);
        spinnerStatus.setEnabled(editable);
    }

    private void saveChanges() {
        String email = tvEmail.getText() != null ? tvEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            Toast.makeText(this, "Missing user email", Toast.LENGTH_SHORT).show();
            return;
        }

        btnEditSave.setEnabled(false);
        uploadUpdatedImagesIfAny(email, this::updateUserDocument);
    }

    private void uploadUpdatedImagesIfAny(String email, Runnable onDone) {
        StorageReference userStorage = FirebaseStorage.getInstance()
                .getReference("business_users")
                .child(businessId)
                .child(email.replace("@", "_").replace(".", "_"));

        if (newProfileUri == null && newAadharUri == null) {
            onDone.run();
            return;
        }

        if (newProfileUri != null) {
            ImageUploadHelper.uploadImage(
                    newProfileUri,
                    userStorage.child("photo.jpg"),
                    uploadedUrl -> {
                        currentProfileUrl = uploadedUrl;
                        uploadAadharIfNeeded(userStorage, onDone);
                    },
                    this::onUpdateFailed
            );
            return;
        }

        uploadAadharIfNeeded(userStorage, onDone);
    }

    private void uploadAadharIfNeeded(StorageReference userStorage, Runnable onDone) {
        if (newAadharUri == null) {
            onDone.run();
            return;
        }

        ImageUploadHelper.uploadImage(
                newAadharUri,
                userStorage.child("aadhar.jpg"),
                uploadedUrl -> {
                    currentAadharUrl = uploadedUrl;
                    onDone.run();
                },
                this::onUpdateFailed
        );
    }

    private void updateUserDocument() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("user_contact", getText(etContact));
        updates.put("user_current_card", getText(etCurrentCard));
        updates.put("user_access_level", String.valueOf(spinnerAccess.getSelectedItem()));
        updates.put("user_status", spinnerStatus.getSelectedItem().equals("Active") ? "true" : "false");
        updates.put("updated_at", FieldValue.serverTimestamp());

        if (!currentProfileUrl.isEmpty()) {
            updates.put("user_photo", currentProfileUrl);
        }
        if (!currentAadharUrl.isEmpty()) {
            updates.put("user_aadhar_photo", currentAadharUrl);
        }

        businessUserRepository.updateBusinessUser(
                businessId,
                userId,
                updates,
                () -> {
                    btnEditSave.setEnabled(true);
                    newProfileUri = null;
                    newAadharUri = null;
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    loadUser();
                },
                this::onUpdateFailed
        );
    }

    private void onUpdateFailed(Exception exception) {
        btnEditSave.setEnabled(true);
        Toast.makeText(this, "Error: " + exception.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void pickImage(int requestCode) {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickIntent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        Uri selectedUri = data.getData();
        if (selectedUri == null) {
            return;
        }

        if (requestCode == REQ_PROFILE_PHOTO) {
            newProfileUri = selectedUri;
            ivProfilePhoto.setImageURI(selectedUri);
            return;
        }

        if (requestCode == REQ_AADHAR_PHOTO) {
            newAadharUri = selectedUri;
            ivAadharPhoto.setImageURI(selectedUri);
        }
    }

    private void openPhotoInViewer(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(Uri.parse(url), "image/*");
        startActivity(viewIntent);
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DateFormat.format("d MMM, yyyy hh:mm a", timestamp.toDate()).toString();
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String valueOf(String value) {
        return value != null ? value : "";
    }
}
