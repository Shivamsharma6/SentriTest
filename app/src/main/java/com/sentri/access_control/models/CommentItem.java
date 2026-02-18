package com.sentri.access_control.models;

public class CommentItem {
    private String text;
    private String date;

    public CommentItem(String text, String date) {
        this.text = text;
        this.date = date;
    }

    public String getText() {
        return text;
    }

    public String getDate() {
        return date;
    }
}

