package com.sentri.access_control;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceDetailsTest {
    @Test
    public void testDeviceDetailsLaunch() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), DeviceDetails.class);
        intent.putExtra("businessDocId", "testBiz");
        intent.putExtra("deviceDocId", "testDevice");
        try (ActivityScenario<DeviceDetails> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.ivBack));
                assertNotNull(activity.findViewById(R.id.tvMac));
                assertNotNull(activity.findViewById(R.id.tvName));
                assertNotNull(activity.findViewById(R.id.tvSsid));
                assertNotNull(activity.findViewById(R.id.tvStatus));
                assertNotNull(activity.findViewById(R.id.tvLastOnline));
                assertNotNull(activity.findViewById(R.id.tvUpdatedAt));
                assertNotNull(activity.findViewById(R.id.tvUpdatedBy));
            });
        }
    }
}
