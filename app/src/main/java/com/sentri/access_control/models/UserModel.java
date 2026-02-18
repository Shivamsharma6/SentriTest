package com.sentri.access_control.models;

public class UserModel {
    private String userId, name, email, phone, photoUrl, accessLevel;

    public UserModel() {}

    public UserModel(String userId, String name, String email, String phone, String photoUrl, String accessLevel) {
        this.userId      = userId;
        this.name        = name;
        this.email       = email;
        this.phone       = phone;
        this.photoUrl    = photoUrl;
        this.accessLevel = accessLevel;
    }

    public String getUserId()      { return userId; }
    public String getName()        { return name; }
    public String getEmail()       { return email; }
    public String getPhone()       { return phone; }
    public String getPhotoUrl()    { return photoUrl; }
    public String getAccessLevel() { return accessLevel; }
}
