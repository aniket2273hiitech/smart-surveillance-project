package com.surveillance.facedetection.service;

import com.surveillance.facedetection.dto.AlertNotificationDTO;
import com.surveillance.facedetection.entity.Alert;
import com.surveillance.facedetection.entity.Criminal;
import com.surveillance.facedetection.entity.DetectionLog;
import com.surveillance.facedetection.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    // Injects the WebSocket message sender
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Creates a CRIMINAL_DETECTED alert when face match is confirmed.
     * Called by FaceDetectionService after a successful match.
     */
    public Alert createAlert(DetectionLog log, Criminal criminal) {
        Alert alert = new Alert();
        alert.setAlertType(Alert.AlertType.CRIMINAL_DETECTED);
        alert.setDetectionLog(log);
        alert.setAcknowledged(false);
        alert.setMessage(
                "⚠️ CRIMINAL DETECTED: " + criminal.getName() +
                " | Crime: " + criminal.getCrimeType() +
                " | Location: " + log.getLocation() +
                " | Confidence: " + String.format("%.1f%%", log.getConfidenceScore() * 100)
        );
        Alert saved = alertRepository.save(alert);

        // ── Push real-time notification to all subscribed browsers ──────────
        AlertNotificationDTO notification = new AlertNotificationDTO(
                saved.getId(),
                criminal.getName(),
                criminal.getCrimeType(),
                log.getLocation(),
                saved.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")),
                String.format("%.1f%%", log.getConfidenceScore() * 100),
                saved.getMessage()
        );
        // Every browser subscribed to /topic/alerts receives this instantly
        messagingTemplate.convertAndSend("/topic/alerts", notification);

        return saved;
    }

    /** Get all unacknowledged alerts - shown on officer dashboard */
    public List<Alert> getPendingAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    /** Get all alerts */
    public List<Alert> getAllAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Officer acknowledges an alert after taking action */
    public void acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
        alert.setAcknowledged(true);
        alertRepository.save(alert);
    }

    /** Count for navbar badge */
    public long getPendingAlertCount() {
        return alertRepository.countByAcknowledgedFalse();
    }
}
