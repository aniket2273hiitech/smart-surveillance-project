package com.surveillance.facedetection.controller;

import com.surveillance.facedetection.dto.UserDetailsImpl;
import com.surveillance.facedetection.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Handles login page and role-based dashboard routing.
 */
@Controller
public class AuthController {

    /** Show the login page */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out successfully.");
        }
        return "login"; // → templates/login.html
    }

    /**
     * After login, Spring Security redirects here.
     * We redirect the user to the correct dashboard based on their role.
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User.Role role = userDetails.getUser().getRole();

        return switch (role) {
            case ADMIN          -> "redirect:/admin/dashboard";
            case POLICE_OFFICER -> "redirect:/officer/dashboard";
        };
    }

    /** Root URL redirect to dashboard */
    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }
}
