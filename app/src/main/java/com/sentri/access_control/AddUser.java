package com.sentri.access_control;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.sentri.access_control.repositories.BusinessUserRepository;
import com.sentri.access_control.repositories.FirestoreBusinessUserRepository;
import com.sentri.access_control.repositories.FirestoreUserRepository;
import com.sentri.access_control.repositories.UserRepository;
import com.sentri.access_control.utils.ImageUploadHelper;
import com.sentri.access_control.utils.PrefsManager;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddUser extends AppCompatActivity {
    private static final int PICK_PROFILE = 2001;
    private static final int CAPTURE_PROFILE = 2002;
    private static final int PICK_AADHAR = 2003;
    private static final int CAPTURE_AADHAR = 2004;

    private ImageView ivProfile;
    private ImageView ivAadhar;
    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private EditText etCurrentCard;
    private Spinner spAccess;
    private Button btnSave;

    private Uri profileUri;
    private Uri aadharUri;
    private Uri tempImageUri;
    private String profileUrl = "";
    private String aadharUrl = "";

    private ProgressDialog progressDialog;
    private BusinessUserRepository businessUserRepository;
    private UserRepository userRepository;

    private String businessId;
    private String businessName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_user);

        initializeDependencies();
        bindViews();
        setupScreenState();
    }

    private void initializeDependencies() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        businessUserRepository = new FirestoreBusinessUserRepository(firestore);
        userRepository = new FirestoreUserRepository(firestore);

        PrefsManager prefsManager = new PrefsManager(this);
        businessId = prefsManager.getCurrentBizId();
        businessName = prefsManager.getCurrentBizName();
        if (businessId == null || businessId.trim().isEmpty()) {
            businessId = getIntent().getStringExtra("businessDocId");
        }
    }

    private void bindViews() {
        ivProfile = findViewById(R.id.ivAddProfilePhoto);
        ivAadhar = findViewById(R.id.ivAddAadharPhoto);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etCurrentCard = findViewById(R.id.etCurrentCard);
        spAccess = findViewById(R.id.spinnerAccess);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupScreenState() {
        if (businessId == null || businessId.trim().isEmpty()) {
            Toast.makeText(this, "No business selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Saving user...");
        progressDialog.setCancelable(false);

        ArrayAdapter<String> accessAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"admin", "manager"}
        );
        accessAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAccess.setAdapter(accessAdapter);

        ivProfile.setOnClickListener(v -> showImageDialog(PICK_PROFILE, CAPTURE_PROFILE));
        ivAadhar.setOnClickListener(v -> showImageDialog(PICK_AADHAR, CAPTURE_AADHAR));
        btnSave.setOnClickListener(v -> validateAndSave());

        setupKeyboardScrollBehavior();
    }

    private void setupKeyboardScrollBehavior() {
        ScrollView scrollView = findViewById(R.id.scrollView);
        List<EditText> fields = Arrays.asList(etCurrentCard, etPhone);
        for (EditText field : fields) {
            field.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) {
                    return;
                }
                scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, view.getBottom()), 200);
            });
        }
    }

    private void showImageDialog(int pickCode, int cameraCode) {
        new AlertDialog.Builder(this)
                .setTitle("Select Image")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        tempImageUri = createImageUri();
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempImageUri);
                        startActivityForResult(cameraIntent, cameraCode);
                    } else {
                        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        pickIntent.setType("image/*");
                        startActivityForResult(Intent.createChooser(pickIntent, "Select Image"), pickCode);
                    }
                })
                .show();
    }

    private Uri createImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == CAPTURE_AADHAR) {
            aadharUri = tempImageUri;
            ivAadhar.setImageURI(aadharUri);
            return;
        }
        if (requestCode == PICK_AADHAR && data != null) {
            aadharUri = data.getData();
            ivAadhar.setImageURI(aadharUri);
            return;
        }
        if (requestCode == CAPTURE_PROFILE) {
            profileUri = tempImageUri;
            ivProfile.setImageURI(profileUri);
            return;
        }
        if (requestCode == PICK_PROFILE && data != null) {
            profileUri = data.getData();
            ivProfile.setImageURI(profileUri);
        }
    }

    private void validateAndSave() {
        String name = getText(etName);
        String email = getText(etEmail);
        String phone = getText(etPhone);

        if (name.isEmpty()) {
            etName.setError("Required");
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Required");
            return;
        }
        if (phone.isEmpty()) {
            etPhone.setError("Required");
            return;
        }

        btnSave.setEnabled(false);
        progressDialog.show();
        uploadUserImagesAndSave(email);
    }

    private void uploadUserImagesAndSave(String email) {
        String storageKey = email.replace("@", "_").replace(".", "_");
        StorageReference userStorage = FirebaseStorage.getInstance()
                .getReference("business_users")
                .child(businessId)
                .child(storageKey);

        ImageUploadHelper.uploadImage(
                aadharUri,
                userStorage.child("aadhar.jpg"),
                uploadedAadhar -> {
                    aadharUrl = uploadedAadhar;
                    ImageUploadHelper.uploadImage(
                            profileUri,
                            userStorage.child("photo.jpg"),
                            uploadedProfile -> {
                                profileUrl = uploadedProfile;
                                saveBusinessUserAndSyncRoot(email);
                            },
                            this::onSaveFailed
                    );
                },
                this::onSaveFailed
        );
    }

    private void saveBusinessUserAndSyncRoot(String email) {
        String accessLevel = String.valueOf(spAccess.getSelectedItem());
        Map<String, Object> businessUserPayload = buildBusinessUserPayload(email, accessLevel);

        businessUserRepository.saveBusinessUser(
                businessId,
                email,
                businessUserPayload,
                () -> upsertRootUser(email, accessLevel),
                this::onSaveFailed
        );
    }

    private Map<String, Object> buildBusinessUserPayload(String email, String accessLevel) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", getText(etName));
        payload.put("user_email", email);
        payload.put("user_contact", getText(etPhone));
        payload.put("user_current_card", getText(etCurrentCard));
        payload.put("user_access_level", accessLevel);
        payload.put("user_status", "true");
        payload.put("user_granted_at", FieldValue.serverTimestamp());
        payload.put("created_at", FieldValue.serverTimestamp());
        payload.put("updated_at", FieldValue.serverTimestamp());
        payload.put("user_aadhar_photo", aadharUrl);
        payload.put("user_photo", profileUrl);
        return payload;
    }

    private void upsertRootUser(String email, String accessLevel) {
        String grantedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String resolvedBusinessName = businessName != null ? businessName : "";

        Map<String, Object> rootPayload = new HashMap<>();
        rootPayload.put("password", accessLevel);
        rootPayload.put("user_email", email);
        rootPayload.put("user_current_business_access_level", accessLevel);
        rootPayload.put("user_current_business_id", businessId);
        rootPayload.put("user_current_business_name", resolvedBusinessName);
        rootPayload.put("user_current_business_granted", grantedAt);
        rootPayload.put("user_current_business_status", true);
        rootPayload.put("updated_at", FieldValue.serverTimestamp());
        rootPayload.put("created_at", FieldValue.serverTimestamp());

        userRepository.mergeUserDocument(
                email,
                rootPayload,
                () -> userRepository.appendBusinessAccess(
                        email,
                        businessId,
                        resolvedBusinessName,
                        accessLevel,
                        grantedAt,
                        true,
                        this::onUserSaved,
                        this::onSaveFailed
                ),
                this::onSaveFailed
        );
    }

    private void onUserSaved() {
        progressDialog.dismiss();
        btnSave.setEnabled(true);
        Toast.makeText(this, "User added and synced", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onSaveFailed(Exception exception) {
        progressDialog.dismiss();
        btnSave.setEnabled(true);
        String message = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "Unknown error";
        Toast.makeText(this, "Save failed: " + message, Toast.LENGTH_LONG).show();
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
