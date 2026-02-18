package com.sentri.access_control;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.sentri.access_control.repositories.BusinessRepository;
import com.sentri.access_control.repositories.FirestoreBusinessRepository;
import com.sentri.access_control.utils.ImageUploadHelper;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AddBusiness extends AppCompatActivity {
    private static final int RC_PICK_OWNER_PHOTO = 1001;
    private static final int RC_CAPTURE_OWNER_PHOTO = 1002;
    private static final int RC_PICK_AADHAR_PHOTO = 1003;
    private static final int RC_CAPTURE_AADHAR_PHOTO = 1004;

    private EditText etBusinessName;
    private EditText etBusinessAddress;
    private EditText etBusinessEmail;
    private EditText etBusinessPhone;
    private EditText etBusinessGst;
    private EditText etBusinessMaxSeats;
    private EditText etSubscriptionEndDays;
    private EditText etNextSequence;
    private EditText etPrefix;
    private EditText etWhatsappBefore;
    private EditText etWhatsappAfter;
    private EditText etOwnerName;
    private EditText etOwnerPhone;
    private EditText etOwnerAadharNumber;
    private EditText etCloseTime;
    private EditText etStatus;

    private Button btnOpenTime;
    private Button btnCreate;
    private TextView tvOpenTime;
    private ImageView ivOwnerPhoto;
    private ImageView ivOwnerAadhar;

    private Uri ownerPhotoUri;
    private Uri ownerAadharUri;
    private Uri tempCapturedUri;

    private String businessId;
    private final Calendar openCal = Calendar.getInstance();
    private final int currentYear = Calendar.getInstance().get(Calendar.YEAR);

    private BusinessRepository businessRepository;
    private StorageReference storageRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_business);

        initializeDependencies();
        bindViews();
        setupActions();
    }

    private void initializeDependencies() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        businessRepository = new FirestoreBusinessRepository(firestore);
        storageRoot = FirebaseStorage.getInstance().getReference().child("businessess");
    }

    private void bindViews() {
        etBusinessName = findViewById(R.id.etBusinessName);
        etBusinessAddress = findViewById(R.id.etBusinessAddress);
        etBusinessEmail = findViewById(R.id.etBusinessEmail);
        etBusinessPhone = findViewById(R.id.etBusinessPhone);
        etBusinessGst = findViewById(R.id.etBusinessGst);
        etBusinessMaxSeats = findViewById(R.id.etBusinessMaxSeats);
        etSubscriptionEndDays = findViewById(R.id.etSubscriptionEndDays);
        etNextSequence = findViewById(R.id.etNextSequence);
        etPrefix = findViewById(R.id.etBusinessPrefix);
        etWhatsappBefore = findViewById(R.id.etWhatsappBefore);
        etWhatsappAfter = findViewById(R.id.etWhatsappAfter);
        etOwnerName = findViewById(R.id.etOwnerName);
        etOwnerPhone = findViewById(R.id.etOwnerPhone);
        etOwnerAadharNumber = findViewById(R.id.etOwnerAadharNumber);
        etCloseTime = findViewById(R.id.etCloseTime);
        etStatus = findViewById(R.id.etStatus);

        btnOpenTime = findViewById(R.id.btnOpenTime);
        tvOpenTime = findViewById(R.id.tvOpenTime);
        btnCreate = findViewById(R.id.btnCreateBusiness);

        ivOwnerPhoto = findViewById(R.id.ivOwnerPhoto);
        ivOwnerAadhar = findViewById(R.id.ivOwnerAadharPhoto);
    }

    private void setupActions() {
        btnOpenTime.setOnClickListener(v -> showOpenTimePicker());
        ivOwnerPhoto.setOnClickListener(v -> showImageSourceDialog(true));
        ivOwnerAadhar.setOnClickListener(v -> showImageSourceDialog(false));
        btnCreate.setOnClickListener(v -> startBusinessCreation());
    }

    private void showOpenTimePicker() {
        int hour = openCal.get(Calendar.HOUR_OF_DAY);
        int minute = openCal.get(Calendar.MINUTE);
        new TimePickerDialog(
                this,
                (TimePicker picker, int selectedHour, int selectedMinute) -> {
                    openCal.set(Calendar.HOUR_OF_DAY, selectedHour);
                    openCal.set(Calendar.MINUTE, selectedMinute);
                    tvOpenTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute));
                },
                hour,
                minute,
                false
        ).show();
    }

    private void showImageSourceDialog(boolean forOwnerPhoto) {
        new AlertDialog.Builder(this)
                .setTitle("Select Photo")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera(forOwnerPhoto);
                    } else {
                        launchGallery(forOwnerPhoto);
                    }
                })
                .show();
    }

    private void launchCamera(boolean forOwnerPhoto) {
        Uri captureUri = createImageUri();
        if (captureUri == null) {
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show();
            return;
        }

        tempCapturedUri = captureUri;
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
        startActivityForResult(cameraIntent, forOwnerPhoto ? RC_CAPTURE_OWNER_PHOTO : RC_CAPTURE_AADHAR_PHOTO);
    }

    private void launchGallery(boolean forOwnerPhoto) {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickIntent, forOwnerPhoto ? RC_PICK_OWNER_PHOTO : RC_PICK_AADHAR_PHOTO);
    }

    private Uri createImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "business_temp_" + System.currentTimeMillis() + ".jpg");
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == RC_PICK_OWNER_PHOTO && data != null) {
            ownerPhotoUri = data.getData();
            if (ownerPhotoUri != null) {
                ivOwnerPhoto.setImageURI(ownerPhotoUri);
            }
            return;
        }

        if (requestCode == RC_CAPTURE_OWNER_PHOTO && tempCapturedUri != null) {
            ownerPhotoUri = tempCapturedUri;
            ivOwnerPhoto.setImageURI(ownerPhotoUri);
            return;
        }

        if (requestCode == RC_PICK_AADHAR_PHOTO && data != null) {
            ownerAadharUri = data.getData();
            if (ownerAadharUri != null) {
                ivOwnerAadhar.setImageURI(ownerAadharUri);
            }
            return;
        }

        if (requestCode == RC_CAPTURE_AADHAR_PHOTO && tempCapturedUri != null) {
            ownerAadharUri = tempCapturedUri;
            ivOwnerAadhar.setImageURI(ownerAadharUri);
        }
    }

    private void startBusinessCreation() {
        if (!validateRequiredInputs()) {
            return;
        }

        setCreatingState(true);
        businessRepository.fetchNextBusinessId(
                currentYear,
                nextBusinessId -> {
                    businessId = nextBusinessId;
                    uploadImagesAndCreateBusiness();
                },
                exception -> onCreateFailed("Error generating ID", exception)
        );
    }

    private void uploadImagesAndCreateBusiness() {
        StorageReference businessStorage = storageRoot.child(businessId);
        ImageUploadHelper.uploadImage(
                ownerPhotoUri,
                businessStorage.child("owner_photo.jpg"),
                ownerPhotoUrl -> ImageUploadHelper.uploadImage(
                        ownerAadharUri,
                        businessStorage.child("owner_aadhar_photo.jpg"),
                        ownerAadharUrl -> createBusinessDocument(ownerPhotoUrl, ownerAadharUrl),
                        exception -> onCreateFailed("Photo upload failed", exception)
                ),
                exception -> onCreateFailed("Photo upload failed", exception)
        );
    }

    private void createBusinessDocument(String ownerPhotoUrl, String ownerAadharUrl) {
        Map<String, Object> businessData = buildBusinessPayload(ownerPhotoUrl, ownerAadharUrl);
        businessRepository.createBusiness(
                businessId,
                businessData,
                () -> businessRepository.initializeBusinessCollections(
                        businessId,
                        this::onBusinessCreated,
                        exception -> onCreateFailed("Failed to initialize business collections", exception)
                ),
                exception -> onCreateFailed("Create failed", exception)
        );
    }

    private Map<String, Object> buildBusinessPayload(String ownerPhotoUrl, String ownerAadharUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("business_id", businessId);
        data.put("business_name", getText(etBusinessName));
        data.put("business_address", getText(etBusinessAddress));
        data.put("business_email", getText(etBusinessEmail));
        data.put("business_phone", getText(etBusinessPhone));
        data.put("business_gst", getText(etBusinessGst));
        data.put("business_max_seats", getText(etBusinessMaxSeats));
        data.put("business_customer_subscription_end_days", getText(etSubscriptionEndDays));
        data.put("business_next_sequence", getText(etNextSequence));
        data.put("business_prefix", getText(etPrefix));
        data.put("business_whatsapp_days_before_expiry", getText(etWhatsappBefore));
        data.put("business_whatsapp_days_after_expiry", getText(etWhatsappAfter));
        data.put("business_owner_name", getText(etOwnerName));
        data.put("business_owner_phone", getText(etOwnerPhone));
        data.put("business_owner_aadhar_number", getText(etOwnerAadharNumber));
        data.put("business_owner_photo", ownerPhotoUrl);
        data.put("business_owner_aadhar_photo", ownerAadharUrl);
        data.put("business_close_time", getText(etCloseTime));
        data.put("business_status", getText(etStatus));
        data.put("business_open_time", new Timestamp(openCal.getTime()));
        data.put("created_at", FieldValue.serverTimestamp());
        data.put("updated_at", FieldValue.serverTimestamp());
        return data;
    }

    private boolean validateRequiredInputs() {
        if (TextUtils.isEmpty(getText(etBusinessName))) {
            etBusinessName.setError("Required");
            etBusinessName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(getText(etPrefix))) {
            etPrefix.setError("Required");
            etPrefix.requestFocus();
            return false;
        }
        return true;
    }

    private void onBusinessCreated() {
        setCreatingState(false);
        Toast.makeText(this, "Business created: " + businessId, Toast.LENGTH_LONG).show();
        finish();
    }

    private void onCreateFailed(String prefix, Exception exception) {
        setCreatingState(false);
        String message = exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "Unknown error";
        Toast.makeText(this, prefix + ": " + message, Toast.LENGTH_LONG).show();
    }

    private void setCreatingState(boolean creating) {
        btnCreate.setEnabled(!creating);
    }

    private String getText(EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
