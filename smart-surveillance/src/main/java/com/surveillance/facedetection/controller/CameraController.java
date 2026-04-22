package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.dto.UserDetailsImpl;
import com.surveillance.facedetection.service.CameraStreamService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/camera")
public class CameraController {

    @Autowired
    private CameraStreamService cameraStreamService;

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getCameraStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", cameraStreamService.isRunning());
        status.put("lastResult", cameraStreamService.getLastDetectionResult());
        status.put("lastCriminalName", cameraStreamService.getLastCriminalName());
        status.put("lastConfidence", cameraStreamService.getLastConfidence());
        status.put("detectionInProgress", cameraStreamService.isDetectionInProgress());
        status.put("lastStatusUpdatedAt", cameraStreamService.getLastStatusUpdatedAt());
        status.put("lastMatchEventId", cameraStreamService.getLastMatchEventId());
        status.put("detectionEveryNFrames", cameraStreamService.getDetectionEveryNFrames());
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        return status;
    }
    @GetMapping("/monitor")
    public String monitorPage(Model model) {
        model.addAttribute("cameraRunning", cameraStreamService.isRunning());
        return "camera/monitor";
    }

    @PostMapping("/start")
    public String startCamera(Authentication auth,
                              RedirectAttributes redirectAttrs) {
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        try {
            cameraStreamService.startCamera(userDetails.getUsername());
            redirectAttrs.addFlashAttribute("successMessage",
                    "Camera monitoring started.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Failed to start camera: " + e.getMessage());
        }
        return "redirect:/camera/monitor";
    }

    @PostMapping("/stop")
    public String stopCamera(RedirectAttributes redirectAttrs) {
        cameraStreamService.stopCamera();
        redirectAttrs.addFlashAttribute("successMessage",
                "Camera monitoring stopped.");
        return "redirect:/camera/monitor";
    }

    /**
     * MJPEG stream endpoint.
     * Browser connects to /camera/stream and receives continuous JPEG frames.
     * Used by <img src="/camera/stream"> in monitor.html
     */
    @GetMapping(value = "/stream",
            produces = "multipart/x-mixed-replace;boundary=frame")
    public void streamCamera(HttpServletResponse response) throws IOException {
        response.setContentType("multipart/x-mixed-replace;boundary=frame");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");

        OutputStream outputStream = response.getOutputStream();

        while (cameraStreamService.isRunning()) {
            byte[] frameBytes = cameraStreamService.getLatestFrame();

            if (frameBytes != null && frameBytes.length > 0) {
                try {
                    // Write MJPEG frame boundary
                    String header = "--frame\r\n"
                            + "Content-Type: image/jpeg\r\n"
                            + "Content-Length: " + frameBytes.length + "\r\n\r\n";
                    outputStream.write(header.getBytes());
                    outputStream.write(frameBytes);
                    outputStream.write("\r\n".getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    // Client disconnected
                    break;
                }
            }

            try {
                Thread.sleep(100); // ~10 FPS
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}