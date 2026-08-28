package org.danetree.common.entity;

import java.time.LocalDate;
import java.util.UUID;

public record LitterEntity(
        UUID id,
        String title,
        LocalDate birthDate,
        UUID sireId,
        UUID damId,
        String description) {
}

