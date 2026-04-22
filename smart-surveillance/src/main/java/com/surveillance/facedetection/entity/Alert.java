package com.surveillance.facedetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an alert generated when a criminal face is matched.
 * ER Diagram fields: AlertID (PK), AlertType
 * Triggered by: DetectionLog (when match_status = MATCHED)
 */
@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_acknowledged", nullable = false)
    private boolean acknowledged = false;

    // Alert is triggered by a detection log event
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private DetectionLog detectionLog;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum AlertType {
        CRIMINAL_DETECTED,
        SYSTEM_ERROR,
        LOW_CONFIDENCE_MATCH
    }
}
