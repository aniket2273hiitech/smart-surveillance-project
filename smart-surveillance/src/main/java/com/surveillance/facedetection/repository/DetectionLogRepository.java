package com.surveillance.facedetection.repository;

import com.surveillance.facedetection.entity.DetectionLog;
import com.surveillance.facedetection.entity.DetectionLog.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetectionLogRepository extends JpaRepository<DetectionLog, Long> {

    // Get all matched detections (for dashboard alerts)
    List<DetectionLog> findByMatchStatusOrderByTimestampDesc(MatchStatus status);

    // Get recent 20 logs for dashboard display
    List<DetectionLog> findTop20ByOrderByTimestampDesc();

    // Count total matches (for admin dashboard statistics)
    long countByMatchStatus(MatchStatus status);

    // Get logs for a specific criminal
    @Query("SELECT d FROM DetectionLog d WHERE d.matchedCriminal.id = :criminalId ORDER BY d.timestamp DESC")
    List<DetectionLog> findByCriminalId(Long criminalId);
}
