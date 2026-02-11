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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Пользователь с такой почтой уже зарегистрирован");
        }
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.getRoles().add(User.Role.USER);
        user = userRepository.save(user);
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
