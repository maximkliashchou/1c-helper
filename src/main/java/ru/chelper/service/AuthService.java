package ru.chelper.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.dto.AuthResponse;
import ru.chelper.dto.LoginRequest;
import ru.chelper.dto.RegisterRequest;
import ru.chelper.dto.RegisterPendingResponse;
import ru.chelper.entity.User;
import ru.chelper.repository.UserRepository;
import ru.chelper.security.JwtUtil;
import ru.chelper.security.UserPrincipal;

import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public RegisterPendingResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Пользователь с такой почтой уже зарегистрирован");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.getRoles().add(User.Role.USER);
        user = userRepository.save(user);
        emailVerificationService.createAndSendCode(user);
        return new RegisterPendingResponse(
                user.getUsername(),
                user.getEmail(),
                true,
                "Код подтверждения отправлен на email"
        );
    }

    @Transactional
    public AuthResponse verifyEmail(String usernameOrEmail, String code) {
        User user = emailVerificationService.verifyCode(usernameOrEmail, code);
        return createAuthResponse(user);
    }

    public void resendVerificationCode(String usernameOrEmail) {
        emailVerificationService.resendCode(usernameOrEmail);
    }

    private AuthResponse createAuthResponse(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                UserPrincipal.from(user), null, UserPrincipal.from(user).getAuthorities());
        String token = jwtUtil.generateToken(auth);
        return new AuthResponse(token, user.getUsername(), user.getEmail(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtUtil.generateToken(auth);
        return new AuthResponse(token, principal.getUsername(), principal.getEmail(),
                principal.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .collect(Collectors.toSet()));
    }
}
