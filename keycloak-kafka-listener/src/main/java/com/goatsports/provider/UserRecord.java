package com.goatsports.provider;

import java.util.UUID;

public record UserRecord(
        UUID userId,
        UUID keycloakId,
        String email,
        String username,
        String fullName,
        String avatarUrl,
        String password,
        String status,
        String roleId) {
};