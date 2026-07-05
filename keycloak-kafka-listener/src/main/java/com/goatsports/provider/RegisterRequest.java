package com.goatsports.provider;

public record RegisterRequest(String username, String fullName, String email, String password, String confirmPassword) {
    public String toJson() {
        return String.format(
                "{\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
                escapeJson(username), escapeJson(fullName), escapeJson(email), escapeJson(password), escapeJson(confirmPassword)
        );
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}