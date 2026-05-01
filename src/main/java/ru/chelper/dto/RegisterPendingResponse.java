package ru.chelper.dto;

public class RegisterPendingResponse {

    private String username;
    private String email;
    private boolean verificationRequired;
    private String message;

    public RegisterPendingResponse() {
    }

    public RegisterPendingResponse(String username, String email, boolean verificationRequired, String message) {
        this.username = username;
        this.email = email;
        this.verificationRequired = verificationRequired;
        this.message = message;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
