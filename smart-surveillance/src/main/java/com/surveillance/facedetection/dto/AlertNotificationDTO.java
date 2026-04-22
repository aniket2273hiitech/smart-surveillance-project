package com.surveillance.facedetection.dto;

public class AlertNotificationDTO {

    private Long   alertId;
    private String criminalName;
    private String crimeType;
    private String location;
    private String timestamp;
    private String confidencePercent;
    private String message;

    public AlertNotificationDTO() {}

    public AlertNotificationDTO(Long alertId, String criminalName, String crimeType,
                                 String location, String timestamp,
                                 String confidencePercent, String message) {
        this.alertId           = alertId;
        this.criminalName      = criminalName;
        this.crimeType         = crimeType;
        this.location          = location;
        this.timestamp         = timestamp;
        this.confidencePercent = confidencePercent;
        this.message           = message;
    }

    public Long   getAlertId()           { return alertId; }
    public String getCriminalName()      { return criminalName; }
    public String getCrimeType()         { return crimeType; }
    public String getLocation()          { return location; }
    public String getTimestamp()         { return timestamp; }
    public String getConfidencePercent() { return confidencePercent; }
    public String getMessage()           { return message; }

    public void setAlertId(Long alertId)                   { this.alertId = alertId; }
    public void setCriminalName(String criminalName)       { this.criminalName = criminalName; }
    public void setCrimeType(String crimeType)             { this.crimeType = crimeType; }
    public void setLocation(String location)               { this.location = location; }
    public void setTimestamp(String timestamp)             { this.timestamp = timestamp; }
    public void setConfidencePercent(String c)             { this.confidencePercent = c; }
    public void setMessage(String message)                 { this.message = message; }
}
