package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.security.JwtUtils;
import com.agriinvest.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<User> signup(@RequestBody User user) {
        // Force the Enum usage here if it's missing from the request
        if (user.getRole() == null) {
            user.setRole(User.Role.INVESTOR);
        }
        return ResponseEntity.ok(userService.registerUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        return userService.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(user -> {
                    String token = jwtUtils.generateToken(email);

                    // Build response manually to handle the NULL role edge case
                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    response.put("type", "Bearer");
                    // If role is null, return "GUEST" to prevent the JSON crash
                    response.put("role", user.getRole() != null ? user.getRole().name() : "GUEST");
                    response.put("id", user.getId());
                    response.put("fullName", user.getFullName());
                    response.put("email", user.getEmail());
                    response.put("verified", user.isVerified());

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid Credentials")));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> me(Authentication authentication) {
        return userService.findByEmail(authentication.getName())
                .map(user -> ResponseEntity.ok(Map.of(
                        "id", user.getId(),
                        "fullName", user.getFullName(),
                        "email", user.getEmail(),
                        "role", user.getRole() != null ? user.getRole().name() : "GUEST",
                        "verified", user.isVerified()
                )))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }
}
