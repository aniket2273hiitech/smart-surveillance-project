package com.surveillance.facedetection.repository;

import com.surveillance.facedetection.entity.Criminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CriminalRepository extends JpaRepository<Criminal, Long> {

    // Fetch only active criminal records for face matching
    List<Criminal> findByActiveTrue();

    // Search by name (admin search feature)
    List<Criminal> findByNameContainingIgnoreCase(String name);
}
