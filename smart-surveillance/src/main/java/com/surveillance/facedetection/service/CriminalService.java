package com.surveillance.facedetection.service;

import com.surveillance.facedetection.entity.Criminal;
import com.surveillance.facedetection.repository.CriminalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class CriminalService {

    @Autowired
    private CriminalRepository criminalRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Saves a new criminal record.
     * The photo is stored on disk under uploadDir.
     * The relative path is saved in the database (imagePath field).
     */
    public Criminal addCriminal(String name, String caseDetails,
                                 String crimeType, MultipartFile photo,
                                 MultipartFile[] referencePhotos) throws IOException {

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Save primary image
        String uniqueFilename = storePhoto(uploadPath, photo);
        List<String> allPaths = new ArrayList<>();
        allPaths.add("uploads/criminals/" + uniqueFilename);

        // Save optional additional reference images
        if (referencePhotos != null) {
            for (MultipartFile referencePhoto : referencePhotos) {
                if (referencePhoto != null && !referencePhoto.isEmpty()) {
                    String refFilename = storePhoto(uploadPath, referencePhoto);
                    allPaths.add("uploads/criminals/" + refFilename);
                }
            }
        }

        // Save criminal record to database with relative path
        Criminal criminal = new Criminal();
        criminal.setName(name);
        criminal.setCaseDetails(caseDetails);
        criminal.setCrimeType(crimeType);
        criminal.setImagePath(allPaths.get(0));
        criminal.setAdditionalImagePaths(allPaths.size() > 1
                ? String.join(",", allPaths.subList(1, allPaths.size()))
                : null);
        criminal.setFaceEmbedding(null);
        criminal.setActive(true);

        return criminalRepository.save(criminal);
    }

    public List<String> getReferenceImagePaths(Criminal criminal) {
        List<String> paths = new ArrayList<>();
        if (criminal.getImagePath() != null && !criminal.getImagePath().isBlank()) {
            paths.add(criminal.getImagePath().trim());
        }

        String extraPaths = criminal.getAdditionalImagePaths();
        if (extraPaths != null && !extraPaths.isBlank()) {
            Arrays.stream(extraPaths.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(paths::add);
        }
        return paths;
    }

    public void save(Criminal criminal) {
        criminalRepository.save(criminal);
    }

    private String storePhoto(Path uploadPath, MultipartFile photo) throws IOException {
        String originalFilename = photo.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String uniqueFilename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(photo.getInputStream(), filePath);
        return uniqueFilename;
    }

    public List<Criminal> getAllActiveCriminals() {
        return criminalRepository.findByActiveTrue();
    }

    public List<Criminal> getAllCriminals() {
        return criminalRepository.findAll();
    }

    public Criminal getCriminalById(Long id) {
        return criminalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Criminal not found: " + id));
    }

    public List<Criminal> searchByName(String name) {
        return criminalRepository.findByNameContainingIgnoreCase(name);
    }

    /** Soft delete - marks criminal as inactive rather than deleting from DB */
    public void deactivateCriminal(Long id) {
        Criminal criminal = getCriminalById(id);
        criminal.setActive(false);
        criminalRepository.save(criminal);
    }

    public void activateCriminal(Long id) {
        Criminal criminal = getCriminalById(id);
        criminal.setActive(true);
        criminal.setFaceEmbedding(null);
        criminalRepository.save(criminal);
    }

    public void deleteCriminal(Long id) {
        Criminal criminal = getCriminalById(id);
        deleteCriminalImages(criminal);
        criminalRepository.delete(criminal);
    }

    private void deleteCriminalImages(Criminal criminal) {
        for (String imagePath : getReferenceImagePaths(criminal)) {
            try {
                String normalized = imagePath.replace("\\", "/");
                String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
                Path baseUploadPath = Paths.get(uploadDir);
                if (!baseUploadPath.isAbsolute()) {
                    baseUploadPath = Paths.get(System.getProperty("user.dir")).resolve(baseUploadPath);
                }
                Files.deleteIfExists(baseUploadPath.resolve(fileName).normalize());
            } catch (Exception ignored) {
                // Keep delete operation resilient even if an image file is already missing.
            }
        }
    }

    public long getTotalCount() {
        return criminalRepository.count();
    }
}
