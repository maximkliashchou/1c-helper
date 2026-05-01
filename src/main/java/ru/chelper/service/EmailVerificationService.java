package ru.chelper.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.entity.User;
import ru.chelper.repository.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public void createAndSendCode(User user) {
        String code = generateCode();
        Instant now = Instant.now();
        user.setEmailVerified(false);
        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(now.plus(CODE_TTL));
        user.setEmailVerificationSentAt(now);
        user.setUpdatedAt(now);
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    @Transactional
    public void resendCode(String usernameOrEmail) {
        User user = findByUsernameOrEmail(usernameOrEmail);
        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email уже подтвержден");
        }
        Instant now = Instant.now();
        Instant sentAt = user.getEmailVerificationSentAt();
        if (sentAt != null && sentAt.plus(RESEND_DELAY).isAfter(now)) {
            throw new IllegalArgumentException("Повторно отправить код можно через минуту");
        }
        createAndSendCode(user);
        userRepository.save(user);
    }

    @Transactional
    public User verifyCode(String usernameOrEmail, String code) {
        User user = findByUsernameOrEmail(usernameOrEmail);
        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email уже подтвержден");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Введите 6-значный код подтверждения");
        }
        Instant expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Код подтверждения истек. Запросите новый код");
        }
        String codeHash = user.getEmailVerificationCodeHash();
        if (codeHash == null || !passwordEncoder.matches(code, codeHash)) {
            throw new IllegalArgumentException("Неверный код подтверждения");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationSentAt(null);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private User findByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Укажите имя пользователя или email");
        }
        String value = usernameOrEmail.trim();
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(value, value)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
