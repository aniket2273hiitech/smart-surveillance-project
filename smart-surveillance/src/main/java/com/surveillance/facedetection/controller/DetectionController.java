package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.dto.UserDetailsImpl;
import com.surveillance.facedetection.entity.DetectionLog;
import com.surveillance.facedetection.entity.User;
import com.surveillance.facedetection.service.FaceDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles face detection requests.
 * Both ADMIN and POLICE_OFFICER can submit images for detection.
 *
 * Workflow (synopsis Section 8, Steps 2-9):
 *   1. User uploads an image (or frame from webcam)
 *   2. FaceDetectionService processes it (grayscale → Haar → histogram match)
 *   3. Result (MATCHED / NO_MATCH) displayed on result page
 */
@Controller
@RequestMapping("/detection")
public class DetectionController {

    @Autowired
    private FaceDetectionService faceDetectionService;

    /** Show the detection submission page */
    @GetMapping("/scan")
    public String showScanPage() {
        return "detection/scan"; // → templates/detection/scan.html
    }

    /**
     * Accepts image upload and runs the full detection pipeline.
     * Returns the result page showing match status and criminal info if matched.
     */
    @PostMapping("/scan")
    public String runDetection(
            @RequestParam("image")    MultipartFile image,
            @RequestParam(value = "location", defaultValue = "Unknown") String location,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttrs) {

        // Get logged-in user entity from Spring Security
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User loggedInUser = userDetails.getUser();

        try {
            if (image.isEmpty()) {
                redirectAttrs.addFlashAttribute("errorMessage",
                        "Please select an image file to scan.");
                return "redirect:/detection/scan";
            }

            // Run detection pipeline
            DetectionLog result = faceDetectionService.detectAndMatch(
                    image, location, loggedInUser);

            model.addAttribute("result", result);
            model.addAttribute("isMatched",
                    result.getMatchStatus() == DetectionLog.MatchStatus.MATCHED);

            return "detection/result"; // → templates/detection/result.html

        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Detection failed: " + e.getMessage());
            return "redirect:/detection/scan";
        }
    }
}
