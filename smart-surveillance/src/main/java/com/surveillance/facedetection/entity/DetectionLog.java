package com.surveillance.facedetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stores every face detection event (both matched and unmatched).
 * ER Diagram fields: id, timestamp, location, match_status
 * References: User (who was logged in) and Criminal (if matched)
 *
 * Relationships:
 *   One Criminal → Many DetectionLogs
 *   One User     → Many DetectionLogs (audit trail)
 */
@Entity
@Table(name = "detection_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private MatchStatus matchStatus;

    /**
     * Confidence score from histogram comparison (0.0 to 1.0).
     * Higher = better match.
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * Path to the captured frame image (screenshot of the detected face).
     */
    @Column(name = "captured_frame_path")
    private String capturedFramePath;

    // Which criminal was matched (null if NO_MATCH)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criminal_id", nullable = true)
    private Criminal matchedCriminal;

    // Which user/officer triggered this detection session
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User detectedBy;

    /** Not stored — explains NO_MATCH on scan (threshold vs ambiguous second place). */
    @Transient
    private String scanRejectionNote;

    @Transient
    private Double secondBestScore;

    @Transient
    private String bestCandidateName;

    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public enum MatchStatus {
        MATCHED,
        NO_MATCH,
        PROCESSING
    }
}
