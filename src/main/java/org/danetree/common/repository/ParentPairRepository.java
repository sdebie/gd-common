package org.danetree.common.repository;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.danetree.common.dto.ParentPairCreateDto;
import org.danetree.common.dto.ParentPairDto;
import org.danetree.common.dto.ParentPairUpdateDto;

@ApplicationScoped
public class ParentPairRepository {

    private static final String FIND_PARENT_PAIRS_SQL = """
            SELECT
                pp.id,
                LOWER(female.name) AS female_parent_key,
                LOWER(male.name) AS male_parent_key
            FROM parent_pairs pp
            JOIN dogs female ON female.id = pp.female_parent_id
            JOIN dogs male ON male.id = pp.male_parent_id
            ORDER BY pp.created_at ASC, pp.id ASC
            """;

    private static final String FIND_DOG_BY_KEY_SQL = """
            SELECT id FROM dogs
            WHERE LOWER(name) = LOWER(?) OR id::text = ?
            LIMIT 1
            """;

    private static final String UPDATE_PARENT_PAIR_SQL = """
            UPDATE parent_pairs
            SET female_parent_id = COALESCE(?, female_parent_id),
                male_parent_id = COALESCE(?, male_parent_id)
            WHERE id = ?
            """;

    private static final String INSERT_PARENT_PAIR_SQL = """
            INSERT INTO parent_pairs (female_parent_id, male_parent_id)
            VALUES (?, ?)
            """;

    public List<ParentPairDto> getParentPairs() {
        List<ParentPairDto> pairs = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(FIND_PARENT_PAIRS_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                UUID id = (UUID) resultSet.getObject("id");
                String femaleParentKey = resultSet.getString("female_parent_key");
                String maleParentKey = resultSet.getString("male_parent_key");

                pairs.add(new ParentPairDto(
                        id,
                        femaleParentKey != null ? femaleParentKey : "",
                        maleParentKey != null ? maleParentKey : ""
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query parent_pairs table", e);
        }
        return pairs;
    }

    public boolean updateParentPair(ParentPairUpdateDto input) {
        if (input.id() == null) {
            return false;
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword())) {
            UUID femaleId = null;
            if (input.femaleParentKey() != null && !input.femaleParentKey().isBlank()) {
                femaleId = findDogIdByKey(connection, input.femaleParentKey());
            }

            UUID maleId = null;
            if (input.maleParentKey() != null && !input.maleParentKey().isBlank()) {
                maleId = findDogIdByKey(connection, input.maleParentKey());
            }

            try (PreparedStatement statement = connection.prepareStatement(UPDATE_PARENT_PAIR_SQL)) {
                statement.setObject(1, femaleId);
                statement.setObject(2, maleId);
                statement.setObject(3, input.id());

                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update parent pair", e);
        }
    }

    public boolean createParentPair(ParentPairCreateDto input) {
        if (input == null || input.femaleParentKey() == null || input.maleParentKey() == null) {
            return false;
        }

        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword())) {
            UUID femaleId = findDogIdByKey(connection, input.femaleParentKey());
            UUID maleId = findDogIdByKey(connection, input.maleParentKey());

            if (femaleId == null || maleId == null) {
                return false;
            }

            try (PreparedStatement statement = connection.prepareStatement(INSERT_PARENT_PAIR_SQL)) {
                statement.setObject(1, femaleId);
                statement.setObject(2, maleId);

                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create parent pair", e);
        }
    }

    private UUID findDogIdByKey(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_DOG_BY_KEY_SQL)) {
            statement.setString(1, key.trim());
            statement.setString(2, key.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
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
