package org.danetree.common.dto;

import java.util.UUID;

public record LitterUpdateDto(
        UUID id,
        String code,
        String title,
        Integer birthMonth,
        Integer birthYear,
        String description,
        UUID parentPairId
) {
}
