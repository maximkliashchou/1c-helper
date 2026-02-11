package ru.chelper.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.chelper.dto.UserResponse;
import ru.chelper.security.UserPrincipal;
import ru.chelper.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        UserResponse profile = userService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<UserResponse> getByUsername(@PathVariable String username) {
        UserResponse profile = userService.getProfileByUsername(username);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestBody Map<String, String> body) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            String email = body.get("email");
            String newPassword = body.get("newPassword");
            UserResponse updated = userService.updateProfile(principal.getId(), email, newPassword);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestParam("file") MultipartFile file) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            String path = userService.updateAvatar(principal.getId(), file);
            return ResponseEntity.ok(Map.of("avatarPath", path));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
