package com.sentri.access_control.utils;

import android.net.Uri;

import com.google.firebase.storage.StorageReference;

import java.util.function.Consumer;

public final class ImageUploadHelper {
    private ImageUploadHelper() {
    }

    public static void uploadImage(Uri sourceUri,
                                   StorageReference destination,
                                   Consumer<String> onSuccess,
                                   Consumer<Exception> onError) {
        if (sourceUri == null) {
            onSuccess.accept("");
            return;
        }

        destination.putFile(sourceUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        throw exception != null ? exception : new RuntimeException("Upload failed");
                    }
                    return destination.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> onSuccess.accept(uri.toString()))
                .addOnFailureListener(e -> {
                    if (onError != null) {
                        onError.accept(e);
                    }
                });
    }
}
