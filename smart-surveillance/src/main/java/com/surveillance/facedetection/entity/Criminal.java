package com.surveillance.facedetection.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a criminal record stored by Admin.
 * ER Diagram fields: id, name, image (file path on disk), case_details
 * Linked to DetectionLog: One Criminal → Many Detection Logs
 */
@Entity
@Table(name = "criminals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Criminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "criminal_id")
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Relative path to the criminal's photo stored on disk.
     * Example: uploads/criminals/john_doe.jpg
     */
    @Column(name = "image_path", nullable = false)
    private String imagePath;

    /**
     * Optional extra reference images (comma-separated relative paths).
     * Example: uploads/criminals/a.jpg,uploads/criminals/b.jpg
     */
    @Column(name = "additional_image_paths", columnDefinition = "TEXT")
    private String additionalImagePaths;

    /**
     * Serialized face signature vector (comma-separated doubles).
     * Built from criminal reference face images for faster/reliable matching.
     */
    @Column(name = "face_embedding", columnDefinition = "TEXT")
    private String faceEmbedding;

    @Column(name = "case_details", columnDefinition = "TEXT")
    private String caseDetails;

    @Column(name = "crime_type", length = 100)
    private String crimeType;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // One Criminal → Many DetectionLogs (as per ER diagram relationship)
    @OneToMany(mappedBy = "matchedCriminal", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetectionLog> detectionLogs;

    @PrePersist
    public void prePersist() {
        this.registeredAt = LocalDateTime.now();
    }
}
