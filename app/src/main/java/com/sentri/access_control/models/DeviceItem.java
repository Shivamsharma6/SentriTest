package com.sentri.access_control.models;

import com.google.firebase.Timestamp;

public class DeviceItem {
    private String id;           // Firestore doc id (e.g., MAC)
    private String mac;          // device_mac (fallback: id)
    private String name;         // device_name
    private String currentSsid;  // current_ssid
    private boolean online;      // device_status
    private Timestamp lastSeen;  // last_seen

    public DeviceItem() { }

    public DeviceItem(String id, String mac, String name, String currentSsid,
                      boolean online, Timestamp lastSeen) {
        this.id = id;
        this.mac = mac;
        this.name = name;
        this.currentSsid = currentSsid;
        this.online = online;
        this.lastSeen = lastSeen;
    }

    public String getId() { return id; }
    public String getMac() { return mac; }
    public String getName() { return name; }
    public String getCurrentSsid() { return currentSsid; }
    public boolean isOnline() { return online; }
    public Timestamp getLastSeen() { return lastSeen; }
}
