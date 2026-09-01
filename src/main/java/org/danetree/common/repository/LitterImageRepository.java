package org.danetree.common.repository;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@ApplicationScoped
public class LitterImageRepository {

    private static final String SAVE_LITTER_IMAGE_SQL = """
            INSERT INTO litter_images (id, litter_id, image_url, caption, is_main)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SET_MAIN_IMAGE_SQL = """
            UPDATE litter_images
            SET is_main = (id = ?)
            WHERE litter_id = (SELECT litter_id FROM litter_images WHERE id = ?)
            """;

    private static final String UPDATE_CAPTION_SQL = """
            UPDATE litter_images
            SET caption = ?
            WHERE id = ?
            """;

    private static final String DELETE_LITTER_IMAGE_SQL = """
            DELETE FROM litter_images
            WHERE id = ?
            RETURNING image_url, litter_id
            """;

    private static final String GET_IMAGE_URL_SQL = """
            SELECT image_url FROM litter_images WHERE id = ?
            """;

    public void saveLitterImage(UUID litterId, String imageUrl, boolean isMain, String caption) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(SAVE_LITTER_IMAGE_SQL)) {

            UUID imageId = UUID.randomUUID();
            statement.setObject(1, imageId);
            statement.setObject(2, litterId);
            statement.setString(3, imageUrl);
            statement.setString(4, caption);
            statement.setBoolean(5, isMain);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save litter image", e);
        }
    }

    public boolean setMainImage(UUID imageId) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(SET_MAIN_IMAGE_SQL)) {

            statement.setObject(1, imageId);
            statement.setObject(2, imageId);

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to set main image", e);
        }
    }

    public boolean updateCaption(UUID imageId, String caption) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(UPDATE_CAPTION_SQL)) {

            statement.setString(1, caption);
            statement.setObject(2, imageId);

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update image caption", e);
        }
    }

    public String deleteLitterImage(UUID imageId) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(DELETE_LITTER_IMAGE_SQL)) {

            statement.setObject(1, imageId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("image_url");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to delete litter image", e);
        }
        return null;
    }

    private static String databaseUrl() {
        String explicitUrl = System.getenv("DB_URL");
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String name = System.getenv().getOrDefault("DB_NAME", "danetree");
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    private static String databaseUser() {
        return System.getenv().getOrDefault("DB_USER", "postgres");
    }

    private static String databasePassword() {
        return System.getenv().getOrDefault("DB_PASSWORD", "postgres");
    }
}
