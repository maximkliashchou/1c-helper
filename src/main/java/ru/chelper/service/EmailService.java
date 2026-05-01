package ru.chelper.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Код подтверждения email");
        message.setText("""
                Здравствуйте!

                Ваш код подтверждения email: %s

                Код действует 15 минут. Если вы не регистрировались в 1С — Помощник, просто проигнорируйте это письмо.
                """.formatted(code));
        mailSender.send(message);
    }
}
