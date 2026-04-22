package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.service.AlertService;
import com.surveillance.facedetection.repository.DetectionLogRepository;
import com.surveillance.facedetection.entity.DetectionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Police Officer controller.
 * Accessible only to users with ROLE_POLICE_OFFICER (enforced by SecurityConfig).
 *
 * Features (as per Use Case Diagram):
 *   - View real-time alerts
 *   - Monitor surveillance feed (upload image for detection)
 *   - Access detection reports
 */
@Controller
@RequestMapping("/officer")
@PreAuthorize("hasRole('POLICE_OFFICER')")
public class OfficerController {

    @Autowired
    private AlertService alertService;

    @Autowired
    private DetectionLogRepository detectionLogRepository;

    // ─── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String officerDashboard(Model model) {
        model.addAttribute("pendingAlerts",  alertService.getPendingAlerts());
        model.addAttribute("pendingCount",   alertService.getPendingAlertCount());
        model.addAttribute("recentLogs",
                detectionLogRepository.findByMatchStatusOrderByTimestampDesc(
                        DetectionLog.MatchStatus.MATCHED));
        return "officer/dashboard"; // → templates/officer/dashboard.html
    }

    // ─── Alerts ────────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    public String viewAlerts(Model model) {
        model.addAttribute("alerts",       alertService.getAllAlerts());
        model.addAttribute("pendingCount", alertService.getPendingAlertCount());
        return "officer/alerts"; // → templates/officer/alerts.html
    }

    @PostMapping("/alerts/acknowledge/{id}")
    public String acknowledgeAlert(@PathVariable Long id,
                                    RedirectAttributes redirectAttrs) {
        alertService.acknowledgeAlert(id);
        redirectAttrs.addFlashAttribute("successMessage", "Alert acknowledged.");
        return "redirect:/officer/alerts";
    }

    // ─── Detection Reports ─────────────────────────────────────────────────────

    @GetMapping("/reports")
    public String detectionReports(Model model) {
        model.addAttribute("logs",
                detectionLogRepository.findTop20ByOrderByTimestampDesc());
        model.addAttribute("totalMatches",
                detectionLogRepository.countByMatchStatus(DetectionLog.MatchStatus.MATCHED));
        return "officer/reports"; // → templates/officer/reports.html
    }
}
