package com.sentri.access_control.models;

public class NotificationItem {
    private String message;
    private String date;

    public NotificationItem(String message, String date) {
        this.message = message;
        this.date = date;
    }

    public String getMessage() {
        return message;
    }

    public String getDate() {
        return date;
    }
}
