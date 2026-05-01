package ru.chelper.dto;

import jakarta.validation.constraints.NotBlank;

public class ResendVerificationCodeRequest {

    @NotBlank
    private String usernameOrEmail;

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
}
