package com.sentri.access_control.repositories;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface DeviceRepository {
    void fetchDevices(String businessId, Consumer<List<DocumentSnapshot>> onSuccess, Consumer<Exception> onError);

    void fetchDevice(String businessId, String deviceId, Consumer<DocumentSnapshot> onSuccess, Consumer<Exception> onError);

    void updateDevice(String businessId,
                      String deviceId,
                      Map<String, Object> updates,
                      Runnable onSuccess,
                      Consumer<Exception> onError);
}
