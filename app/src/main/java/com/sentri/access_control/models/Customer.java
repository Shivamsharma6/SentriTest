package com.sentri.access_control.models;

public class Customer {
    private String customer_name;
    private String customer_id;
    private String customer_current_card_id;
    private boolean customer_status;
    private String customer_photo;        // URL to profile photo
    private String customer_aadhar_photo; // URL to Aadhar image

    /** No-arg constructor required by Firestore */
    public Customer() { }

    public Customer(String name,
                    String id,
                    String cardId,
                    boolean status,
                    String photo,
                    String aadharPhoto) {
        this.customer_name             = name;
        this.customer_id               = id;
        this.customer_current_card_id  = cardId;
        this.customer_status           = status;
        this.customer_photo            = photo;
        this.customer_aadhar_photo     = aadharPhoto;
    }

    public String  getCustomerName()            { return customer_name; }
    public String  getCustomerId()              { return customer_id; }
    public String  getCustomerCurrentCardId()   { return customer_current_card_id; }
    public boolean isCustomerStatus()           { return customer_status; }
    public String  getCustomerPhoto()           { return customer_photo; }
    public String  getCustomerAadharPhoto()     { return customer_aadhar_photo; }

    public void setCustomerPhoto(String photo)          { this.customer_photo = photo; }
    public void setCustomerAadharPhoto(String aadhar)   { this.customer_aadhar_photo = aadhar; }
}
