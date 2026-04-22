package com.surveillance.facedetection.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a system user.
 * Roles: ADMIN or POLICE_OFFICER (as defined in the ER diagram)
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @NotBlank
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // One User → Many DetectionLogs (audit trail as per ER diagram)
    @OneToMany(mappedBy = "detectedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetectionLog> detectionLogs;

    public enum Role {
        ADMIN,
        POLICE_OFFICER
    }
}
