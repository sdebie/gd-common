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
import org.danetree.common.dto.LitterDto;
import org.danetree.common.dto.LitterUpdateDto;

@ApplicationScoped
public class LitterRepository {

    private static final String FIND_LITTERS_SQL = """
            SELECT id, code, title, birth_month, birth_year, description, parent_pair_id
            FROM litters
            ORDER BY birth_year ASC, birth_month ASC
            """;

    private static final String UPDATE_LITTER_SQL = """
            UPDATE litters
            SET code = ?, title = ?, birth_month = ?, birth_year = ?, description = ?, parent_pair_id = ?
            WHERE id = ?
            """;

    public List<LitterDto> getLitters() {
        List<LitterDto> litters = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(FIND_LITTERS_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                litters.add(new LitterDto(
                        (UUID) resultSet.getObject("id"),
                        resultSet.getString("code"),
                        resultSet.getString("title"),
                        resultSet.getInt("birth_month"),
                        resultSet.getInt("birth_year"),
                        resultSet.getString("description"),
                        (UUID) resultSet.getObject("parent_pair_id")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query litters table", e);
        }
        return litters;
    }

    public boolean updateLitter(LitterUpdateDto input) {
        if (input.id() == null) return false;

        try (Connection connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
             PreparedStatement statement = connection.prepareStatement(UPDATE_LITTER_SQL)) {
            statement.setString(1, input.code());
            statement.setString(2, input.title());
            statement.setObject(3, input.birthMonth());
            statement.setObject(4, input.birthYear());
            statement.setString(5, input.description());
            statement.setObject(6, input.parentPairId());
            statement.setObject(7, input.id());

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update litter", e);
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
