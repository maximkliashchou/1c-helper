package ru.chelper.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.chelper.dto.UserResponse;
import ru.chelper.entity.User;
import ru.chelper.repository.UserRepository;
import ru.chelper.security.UserPrincipal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final Path uploadDir = Paths.get("uploads", "avatars").toAbsolutePath().normalize();

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            // ignore
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfileByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, String email, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (email != null && !email.isBlank()) {
            String lower = email.trim().toLowerCase();
            if (!lower.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(lower)) {
                throw new IllegalArgumentException("Эта почта уже используется");
            }
            user.setEmail(lower);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) {
                throw new IllegalArgumentException("Пароль должен быть не короче 6 символов");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        user.setUpdatedAt(java.time.Instant.now());
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (ext == null || !java.util.Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Допустимые форматы: jpg, png, gif, webp");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        String filename = UUID.randomUUID() + "." + ext;
        Path target = uploadDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить файл", e);
        }
        if (user.getAvatarPath() != null) {
            Path oldPath = uploadDir.resolve(Paths.get(user.getAvatarPath()).getFileName().toString());
            try {
                Files.deleteIfExists(oldPath);
            } catch (IOException ignored) {
            }
        }
        user.setAvatarPath("/uploads/avatars/" + filename);
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
        return user.getAvatarPath();
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    private static String getExtension(String name) {
        if (name == null) return null;
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1) : null;
    }

    private static UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setAvatarPath(user.getAvatarPath());
        r.setRoles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
