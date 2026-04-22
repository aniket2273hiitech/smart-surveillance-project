package com.surveillance.facedetection.repository;

import com.surveillance.facedetection.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Get unacknowledged alerts (shown prominently on officer dashboard)
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    // All alerts newest first
    List<Alert> findAllByOrderByCreatedAtDesc();

    // Count pending alerts for navbar badge
    long countByAcknowledgedFalse();
}
