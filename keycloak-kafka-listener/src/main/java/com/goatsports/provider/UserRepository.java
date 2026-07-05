package com.goatsports.provider;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class UserRepository {

    private final HikariDataSource dataSource;

    public UserRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UserRecord> findByUsernameOrEmail(String value) {
        String sql = """
                    SELECT user_id, keycloak_id, email, username, full_name, avatar_url, password, status, role_id
                    FROM users
                    WHERE username = ? OR email = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, value);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot query user", e);
        }
    }

    public Optional<UserRecord> findByKeycloakId(String id) {
        String sql = """
                    SELECT user_id, keycloak_id, email, username, full_name, avatar_url, password, status, role_id
                    FROM users
                    WHERE keycloak_id = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(id));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot query user by keycloak_id", e);
        }
    }

    private UserRecord map(ResultSet rs) throws SQLException {
        return new UserRecord(
                rs.getObject("user_id", UUID.class),
                rs.getObject("keycloak_id", UUID.class),
                rs.getString("email"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("avatar_url"),
                rs.getString("password"),
                rs.getString("status"),
                rs.getString("role_id"));
    }
}
