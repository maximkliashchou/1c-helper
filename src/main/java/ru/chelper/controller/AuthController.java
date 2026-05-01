package ru.chelper.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.*;
import ru.chelper.dto.AuthResponse;
import ru.chelper.dto.LoginRequest;
import ru.chelper.dto.RegisterPendingResponse;
import ru.chelper.dto.RegisterRequest;
import ru.chelper.dto.ResendVerificationCodeRequest;
import ru.chelper.dto.VerifyEmailRequest;
import ru.chelper.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterPendingResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        try {
            AuthResponse response = authService.verifyEmail(request.getUsernameOrEmail(), request.getCode());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification-code")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendVerificationCodeRequest request) {
        try {
            authService.resendVerificationCode(request.getUsernameOrEmail());
            return ResponseEntity.ok(Map.of("message", "Код подтверждения отправлен повторно"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Подтвердите email перед входом"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Неверный логин или пароль"));
        }
    }
}
