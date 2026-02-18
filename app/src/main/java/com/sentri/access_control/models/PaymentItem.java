// src/main/java/com/sentri/access_control/models/PaymentItem.java
package com.sentri.access_control.models;

public class PaymentItem {
    private String date;
    private String method;
    private String processedBy;
    private String type;
    private String rate;
    private String amount;
    private boolean isPositive;

    public PaymentItem(String date,
                       String method,
                       String processedBy,
                       String type,
                       String rate,
                       String amount,
                       boolean isPositive) {
        this.date        = date;
        this.method      = method;
        this.processedBy = processedBy;
        this.type        = type;
        this.rate        = rate;
        this.amount      = amount;
        this.isPositive  = isPositive;
    }

    public String getDate()        { return date; }
    public String getMethod()      { return method; }
    public String getProcessedBy() { return processedBy; }
    public String getType()        { return type; }
    public String getRate()        { return rate; }
    public String getAmount()      { return amount; }
    public boolean isPositive()    { return isPositive; }
}
