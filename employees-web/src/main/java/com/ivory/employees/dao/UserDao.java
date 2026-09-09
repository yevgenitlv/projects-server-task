package com.ivory.employees.dao;

import com.ivory.employees.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Access to APP_USERS, the credential store behind the LOGIN service. */
public final class UserDao {

    /** A stored credential: the salt and the PBKDF2 hash of the password, both hex-encoded. */
    public record Credentials(String username, String salt, String passwordHash) {
    }

    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public Optional<Credentials> find(String username) {
        String sql = "SELECT USERNAME, SALT, PASSWORD_HASH FROM APP_USERS WHERE USERNAME = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credentials(
                        resultSet.getString("USERNAME"),
                        resultSet.getString("SALT"),
                        resultSet.getString("PASSWORD_HASH")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load user " + username, e);
        }
    }

    /** Inserts the user, or does nothing when it already exists. Returns true when inserted. */
    public boolean insertIfAbsent(Credentials credentials) {
        String sql = """
                INSERT INTO APP_USERS (USERNAME, SALT, PASSWORD_HASH)
                SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM APP_USERS WHERE USERNAME = ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, credentials.username());
            statement.setString(2, credentials.salt());
            statement.setString(3, credentials.passwordHash());
            statement.setString(4, credentials.username());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to store user " + credentials.username(), e);
        }
    }
}
