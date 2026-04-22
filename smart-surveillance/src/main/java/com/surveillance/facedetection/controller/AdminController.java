package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.entity.User;
import com.surveillance.facedetection.service.AlertService;
import com.surveillance.facedetection.service.CriminalService;
import com.surveillance.facedetection.service.FaceDetectionService;
import com.surveillance.facedetection.service.UserService;
import com.surveillance.facedetection.repository.DetectionLogRepository;
import com.surveillance.facedetection.entity.DetectionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import com.surveillance.facedetection.dto.UserDetailsImpl;


/**
 * Admin-only controller.
 * Accessible only to users with ROLE_ADMIN (enforced by SecurityConfig).
 *
 * Features:
 *   - View admin dashboard with statistics
 *   - Add / deactivate criminal records
 *   - Manage users (register police officers)
 *   - View all detection logs
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private FaceDetectionService faceDetectionService;

    @Autowired
    private CriminalService criminalService;

    @Autowired
    private UserService userService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private DetectionLogRepository detectionLogRepository;

    // ─── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("totalCriminals",   criminalService.getTotalCount());
        model.addAttribute("totalMatches",
                detectionLogRepository.countByMatchStatus(DetectionLog.MatchStatus.MATCHED));
        model.addAttribute("pendingAlerts",    alertService.getPendingAlertCount());
        model.addAttribute("recentLogs",       detectionLogRepository.findTop20ByOrderByTimestampDesc());
        return "admin/dashboard"; // → templates/admin/dashboard.html
    }

    // ─── Criminal Management ───────────────────────────────────────────────────

    @GetMapping("/criminals")
    public String listCriminals(
            @RequestParam(value = "search", required = false) String search,
            Model model) {

        if (search != null && !search.isBlank()) {
            model.addAttribute("criminals", criminalService.searchByName(search));
            model.addAttribute("search", search);
        } else {
            model.addAttribute("criminals", criminalService.getAllCriminals());
        }
        return "admin/criminals"; // → templates/admin/criminals.html
    }

    @GetMapping("/criminals/add")
    public String showAddCriminalForm() {
        return "admin/add-criminal"; // → templates/admin/add-criminal.html
    }

    @PostMapping("/criminals/add")
    public String addCriminal(
            @RequestParam("name")        String name,
            @RequestParam("caseDetails") String caseDetails,
            @RequestParam("crimeType")   String crimeType,
            @RequestParam("photo")       MultipartFile photo,
            @RequestParam(value = "referencePhotos", required = false) MultipartFile[] referencePhotos,
            RedirectAttributes redirectAttrs) {

        try {
            criminalService.addCriminal(name, caseDetails, crimeType, photo, referencePhotos);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Criminal record added successfully: " + name);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Failed to add criminal record: " + e.getMessage());
        }
        return "redirect:/admin/criminals";
    }
    @PostMapping("/criminals/deactivate/{id}")
    public String deactivateCriminal(@PathVariable Long id,
                                      RedirectAttributes redirectAttrs) {
        criminalService.deactivateCriminal(id);
        redirectAttrs.addFlashAttribute("successMessage", "Criminal record deactivated.");
        return "redirect:/admin/criminals";
    }

    @PostMapping("/criminals/activate/{id}")
    public String activateCriminal(@PathVariable Long id,
                                   RedirectAttributes redirectAttrs) {
        criminalService.activateCriminal(id);
        redirectAttrs.addFlashAttribute("successMessage", "Criminal record activated.");
        return "redirect:/admin/criminals";
    }

    @PostMapping("/criminals/delete/{id}")
    public String deleteCriminal(@PathVariable Long id,
                                 RedirectAttributes redirectAttrs) {
        try {
            criminalService.deleteCriminal(id);
            redirectAttrs.addFlashAttribute("successMessage", "Criminal record deleted.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Unable to delete record. It may still be linked with detection logs.");
        }
        return "redirect:/admin/criminals";
    }

    // ─── User Management ───────────────────────────────────────────────────────

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "admin/users"; // → templates/admin/users.html
    }

    @GetMapping("/users/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("roles", User.Role.values());
        return "admin/add-user"; // → templates/admin/add-user.html
    }

    @PostMapping("/users/add")
    public String addUser(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("role")     String role,
            RedirectAttributes redirectAttrs) {

        try {
            userService.registerUser(username, password, User.Role.valueOf(role));
            redirectAttrs.addFlashAttribute("successMessage",
                    "User registered successfully: " + username);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Failed to register user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/toggle/{id}")
    public String toggleUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttrs) {

        // Get currently logged in admin
        UserDetailsImpl currentUser = (UserDetailsImpl) authentication.getPrincipal();

        // Prevent admin from deactivating themselves
        if (currentUser.getUser().getId().equals(id)) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "You cannot deactivate your own account.");
            return "redirect:/admin/users";
        }

        // Prevent deactivating the last active admin
        User targetUser = userService.getUserById(id);
        if (targetUser.getRole() == User.Role.ADMIN && targetUser.isEnabled()) {
            long activeAdminCount = userService.getAllUsers()
                    .stream()
                    .filter(u -> u.getRole() == User.Role.ADMIN && u.isEnabled())
                    .count();
            if (activeAdminCount <= 1) {
                redirectAttrs.addFlashAttribute("errorMessage",
                        "Cannot deactivate the last active Admin.");
                return "redirect:/admin/users";
            }
        }

        userService.toggleUserStatus(id);
        redirectAttrs.addFlashAttribute("successMessage", "User status updated.");
        return "redirect:/admin/users";
    }

    // ─── Detection Logs ────────────────────────────────────────────────────────

    @GetMapping("/logs")
    public String detectionLogs(Model model) {
        model.addAttribute("logs", detectionLogRepository.findTop20ByOrderByTimestampDesc());
        return "admin/logs"; // → templates/admin/logs.html
    }
}
