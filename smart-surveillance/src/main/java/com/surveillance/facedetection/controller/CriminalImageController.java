package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.entity.Criminal;
import com.surveillance.facedetection.service.CriminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class CriminalImageController {

    @Autowired
    private CriminalService criminalService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @GetMapping("/images/criminal/{id}")
    @ResponseBody
    public ResponseEntity<Resource> getCriminalImage(@PathVariable Long id) {
        Criminal criminal = criminalService.getCriminalById(id);
        Path imagePath = resolveImagePath(criminal.getImagePath());

        if (imagePath == null || !Files.exists(imagePath) || Files.isDirectory(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(imagePath);
        String contentType = "application/octet-stream";
        try {
            String detectedType = Files.probeContentType(imagePath);
            if (detectedType != null) {
                contentType = detectedType;
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private Path resolveImagePath(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }

        Path pathFromDb = Paths.get(dbPath);
        if (pathFromDb.isAbsolute() && Files.exists(pathFromDb)) {
            return pathFromDb.normalize();
        }

        String normalized = dbPath.replace("\\", "/");
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);

        Path uploadBase = Paths.get(uploadDir);
        if (!uploadBase.isAbsolute()) {
            uploadBase = Paths.get(System.getProperty("user.dir")).resolve(uploadBase);
        }
        Path candidate = uploadBase.resolve(fileName).normalize();
        if (Files.exists(candidate)) {
            return candidate;
        }

        Path legacy = Paths.get(System.getProperty("user.dir"))
                .resolve("src/main/resources/static/uploads/criminals")
                .resolve(fileName)
                .normalize();
        return Files.exists(legacy) ? legacy : null;
    }
}
