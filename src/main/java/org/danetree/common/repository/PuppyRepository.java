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
import org.danetree.common.dto.DogImageDto;
import org.danetree.common.dto.PuppyCardDto;
import org.danetree.common.dto.PuppyCreateDto;
import org.danetree.common.dto.PuppyUpdateDto;

@ApplicationScoped
public class PuppyRepository {

    private static final String FIND_PUPPIES_SQL = """
            SELECT
                d.id,
                d.name,
                d.gender,
                d.genetic_line,
                d.date_of_birth,
                d.profile_text AS description,
                d.is_deceased,
                d.registration_number,
                d.parent_pair_id,
                d.owner_user_id,
                owner.name AS owner_name
            FROM dogs d
            LEFT JOIN users owner ON owner.id = d.owner_user_id
            WHERE d.role = 'puppy'
            ORDER BY d.id ASC
            """;

    private static final String FIND_DOG_IMAGES_SQL = """
            SELECT id, image_url, caption, COALESCE(taken_at, created_at) AS image_time, is_main
            FROM dog_images
            WHERE dog_id = ?
            ORDER BY created_at DESC
            """;

    private static final String UPDATE_PUPPY_SQL = """
            UPDATE dogs
            SET name = ?, gender = ?, genetic_line = ?, date_of_birth = COALESCE(?, date_of_birth), profile_text = ?, is_deceased = ?, registration_number = ?, parent_pair_id = ?, owner_user_id = ?
            WHERE id = ?
            """;

    private static final String FIND_LITTER_BIRTH_DATE_SQL = """
            SELECT birth_month, birth_year
            FROM litters
            WHERE parent_pair_id = ?
            LIMIT 1
            """;

    private static final String INSERT_PUPPY_SQL = """
            INSERT INTO dogs (name, gender, genetic_line, role, profile_text, is_deceased, registration_number, parent_pair_id, owner_user_id, date_of_birth)
            VALUES (?, ?, ?, 'puppy', ?, FALSE, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UNSET_MAIN_IMAGES_SQL = """
            UPDATE dog_images SET is_main = FALSE WHERE dog_id = ?
            """;

    private static final String INSERT_IMAGE_SQL = """
            INSERT INTO dog_images (dog_id, image_url, caption, is_main) VALUES (?, ?, ?, ?)
            """;

    public List<PuppyCardDto> getPuppies() {
        List<PuppyCardDto> puppies = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(FIND_PUPPIES_SQL);
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
                UUID parentPairId = (UUID) resultSet.getObject("parent_pair_id");
                UUID ownerUserId = (UUID) resultSet.getObject("owner_user_id");
                String ownerName = resultSet.getString("owner_name");

                // Fetch images for this dog
                List<DogImageDto> images = new ArrayList<>();
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
                        images.add(new DogImageDto(imageId, url, caption, timestamp, isMain));
                    }
                }

                puppies.add(new PuppyCardDto(
                        id,
                        name != null ? name.toLowerCase() : "",
                        name,
                        gender,
                        geneticLine,
                        dob,
                        description != null ? description : "",
                        images,
                        isDeceased ? "RIP" : null,
                        registrationNumber,
                        parentPairId,
                        ownerUserId,
                        ownerName
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query dogs table", e);
        }
        return puppies;
    }

    public boolean updatePuppy(PuppyUpdateDto input) {
        if (input.id() == null) return false;
        UUID dogId = input.id();
        
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(UPDATE_PUPPY_SQL)) {
            statement.setString(1, input.name());
            statement.setString(2, input.gender());
            statement.setString(3, input.geneticLine());
            LocalDateTime litterDateOfBirth = findLitterBirthDate(connection, input.parentPairId());
            if (litterDateOfBirth != null) {
                statement.setTimestamp(4, Timestamp.valueOf(litterDateOfBirth));
            } else {
                statement.setNull(4, java.sql.Types.TIMESTAMP);
            }
            statement.setString(5, input.description());
            statement.setBoolean(6, "RIP".equals(input.statusBadge()));
            statement.setString(7, input.registrationNumber());
            statement.setObject(8, input.parentPairId());
            statement.setObject(9, input.ownerUserId());
            statement.setObject(10, dogId);

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update puppy", e);
        }
    }

    public UUID createPuppy(PuppyCreateDto input) {
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(INSERT_PUPPY_SQL)) {
            statement.setString(1, input.name());
            statement.setString(2, input.gender());
            statement.setString(3, input.geneticLine());
            statement.setString(4, input.description());
            statement.setString(5, input.registrationNumber());
            statement.setObject(6, input.parentPairId());
            statement.setObject(7, input.ownerUserId());

            LocalDateTime litterDateOfBirth = findLitterBirthDate(connection, input.parentPairId());
            if (litterDateOfBirth != null) {
                statement.setTimestamp(8, Timestamp.valueOf(litterDateOfBirth));
            } else {
                statement.setNull(8, java.sql.Types.TIMESTAMP);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return (UUID) resultSet.getObject("id");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create puppy", e);
        }
        throw new IllegalStateException("Unable to create puppy: no id returned");
    }

    private LocalDateTime findLitterBirthDate(Connection connection, UUID parentPairId) throws SQLException {
        if (parentPairId == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(FIND_LITTER_BIRTH_DATE_SQL)) {
            statement.setObject(1, parentPairId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int birthMonth = rs.getInt("birth_month");
                    int birthYear = rs.getInt("birth_year");
                    return LocalDateTime.of(birthYear, birthMonth, 1, 0, 0);
                }
            }
        }
        return null;
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
