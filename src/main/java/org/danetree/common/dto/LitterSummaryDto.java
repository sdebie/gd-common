package org.danetree.common.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LitterSummaryDto(
        UUID id,
        String title,
        LocalDate birthDate,
        int puppyCount,
        String description) {
}

