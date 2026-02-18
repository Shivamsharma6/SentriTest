package com.sentri.access_control.models;

public class DeviceHistoryItem {
    private String user;
    private String action;
    private String timestamp;

    public DeviceHistoryItem(String user, String action, String timestamp) {
        this.user = user;
        this.action = action;
        this.timestamp = timestamp;
    }

    public String getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getTimestamp() {
        return timestamp;
    }
}

