package org.danetree.common.repository;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.danetree.common.dto.ParentCardDto;
import org.danetree.common.dto.ParentUpdateDto;

import static java.sql.Timestamp.*;

@ApplicationScoped
@Slf4j
public class ParentRepository {

    private static final String FIND_PARENTS_SQL = """
            SELECT
                d.id,
                d.name,
                d.gender,
                d.genetic_line,
                d.date_of_birth,
                d.profile_text AS description,
                d.is_deceased,
                d.registration_number
            FROM dogs d
            WHERE d.role = 'parent'
            ORDER BY d.id ASC
            """;

    private static final String FIND_DOG_IMAGES_SQL = """
            SELECT id, image_url, caption, COALESCE(taken_at, created_at) AS image_time, is_main
            FROM dog_images
            WHERE dog_id = ?
            ORDER BY created_at DESC
            """;

    private static final String UPDATE_PARENT_SQL = """
            UPDATE dogs
            SET name = ?, gender = ?, genetic_line = ?, date_of_birth = ?, profile_text = ?, is_deceased = ?, registration_number = ?
            WHERE id = ?
            """;

    private static final String UNSET_MAIN_IMAGES_SQL = """
            UPDATE dog_images SET is_main = FALSE WHERE dog_id = ?
            """;

    private static final String INSERT_IMAGE_SQL = """
            INSERT INTO dog_images (dog_id, image_url, caption, is_main) VALUES (?, ?, ?, ?)
            """;

    private static final String UNSET_MAIN_FOR_IMAGE_OWNER_SQL = """
            UPDATE dog_images SET is_main = FALSE
            WHERE dog_id = (SELECT dog_id FROM dog_images WHERE id = ?)
            """;

    private static final String SET_MAIN_IMAGE_SQL = """
            UPDATE dog_images SET is_main = TRUE WHERE id = ?
            """;

    private static final String FIND_IMAGE_URL_SQL = """
            SELECT image_url FROM dog_images WHERE id = ?
            """;

    private static final String DELETE_IMAGE_SQL = """
            DELETE FROM dog_images WHERE id = ?
            """;

    private static final String UPDATE_IMAGE_CAPTION_SQL = """
            UPDATE dog_images SET caption = ? WHERE id = ?
            """;

    public List<ParentCardDto> getParents() {
        List<ParentCardDto> parents = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(FIND_PARENTS_SQL);
             PreparedStatement imagesStatement = connection.prepareStatement(FIND_DOG_IMAGES_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                UUID id = (UUID) resultSet.getObject("id");
                String name = resultSet.getString("name");
                String gender = resultSet.getString("gender");
                String geneticLine = resultSet.getString("genetic_line");
                
                java.sql.Timestamp dobTimestamp = resultSet.getTimestamp("date_of_birth");
                LocalDateTime dob = null;
                if (dobTimestamp != null) {
                    dob = dobTimestamp.toLocalDateTime();
                }
                
                String description = resultSet.getString("description");
                boolean isDeceased = resultSet.getBoolean("is_deceased");
                String registrationNumber = resultSet.getString("registration_number");

                // Fetch images for this dog
                List<org.danetree.common.dto.DogImageDto> images = new ArrayList<>();
                imagesStatement.setObject(1, id);
                try (ResultSet imagesRs = imagesStatement.executeQuery()) {
                    while (imagesRs.next()) {
                        UUID imageId = (UUID) imagesRs.getObject("id");
                        String url = imagesRs.getString("image_url");
                        String caption = imagesRs.getString("caption");
                        boolean isMain = imagesRs.getBoolean("is_main");
                        OffsetDateTime timestamp = null;
                        java.sql.Timestamp imageTime = imagesRs.getTimestamp("image_time");
                        if (imageTime != null) {
                            timestamp = OffsetDateTime.ofInstant(imageTime.toInstant(), ZoneId.of("UTC"));
                        }
                        images.add(new org.danetree.common.dto.DogImageDto(imageId, url, caption, timestamp, isMain));
                    }
                }

                parents.add(new ParentCardDto(
                        id,
                        name != null ? name.toLowerCase() : "",
                        name,
                        gender,
                        geneticLine,
                        dob,
                        description != null ? description : "",
                        images,
                        isDeceased ? "RIP" : null,
                        registrationNumber
                ));
            }
        } catch (SQLException e) {
            log.error("Unable to query dogs table", e);
            throw new IllegalStateException("Unable to query dogs table", e);
        }
        return parents;
    }

    public boolean updateParent(ParentUpdateDto input) {
        if (input.id() == null) return false;
        UUID dogId = input.id();
        
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(UPDATE_PARENT_SQL)) {
            statement.setString(1, input.name());
            statement.setString(2, input.gender());
            statement.setString(3, input.geneticLine());
            if (input.dateOfBirth() != null) {
                statement.setTimestamp(4, Timestamp.valueOf(input.dateOfBirth()));
            } else {
                statement.setNull(4, java.sql.Types.TIMESTAMP);
            }
            statement.setString(5, input.description());
            statement.setBoolean(6, "RIP".equals(input.statusBadge()));
            statement.setString(7, input.registrationNumber());
            statement.setObject(8, dogId);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update parent", e);
        }
    }

    public void saveImage(UUID dogId, String imageUrl, boolean isMain, String caption) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword())) {
            connection.setAutoCommit(false);
            try {
                if (isMain) {
                    try (PreparedStatement unsetMain = connection.prepareStatement(UNSET_MAIN_IMAGES_SQL)) {
                        unsetMain.setObject(1, dogId);
                        unsetMain.executeUpdate();
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(INSERT_IMAGE_SQL)) {
                    insert.setObject(1, dogId);
                    insert.setString(2, imageUrl);
                    insert.setString(3, caption);
                    insert.setBoolean(4, isMain);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save image", e);
        }
    }

    public boolean setMainImage(UUID imageId) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword())) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement unsetMain = connection.prepareStatement(UNSET_MAIN_FOR_IMAGE_OWNER_SQL)) {
                    unsetMain.setObject(1, imageId);
                    unsetMain.executeUpdate();
                }
                boolean updated;
                try (PreparedStatement setMain = connection.prepareStatement(SET_MAIN_IMAGE_SQL)) {
                    setMain.setObject(1, imageId);
                    updated = setMain.executeUpdate() > 0;
                }
                connection.commit();
                return updated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to set main image", e);
        }
    }

    public String deleteImage(UUID imageId) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword())) {
            String imageUrl = null;
            try (PreparedStatement find = connection.prepareStatement(FIND_IMAGE_URL_SQL)) {
                find.setObject(1, imageId);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        imageUrl = rs.getString("image_url");
                    }
                }
            }
            if (imageUrl == null) {
                return null;
            }
            try (PreparedStatement delete = connection.prepareStatement(DELETE_IMAGE_SQL)) {
                delete.setObject(1, imageId);
                delete.executeUpdate();
            }
            return imageUrl;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to delete image", e);
        }
    }

    public boolean updateImageCaption(UUID imageId, String caption) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(UPDATE_IMAGE_CAPTION_SQL)) {
            statement.setString(1, caption);
            statement.setObject(2, imageId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update image caption", e);
        }
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

