package org.danetree.common.dto;

import java.util.UUID;

public record LitterDto(
        UUID id,
        String code,
        String title,
        int birthMonth,
        int birthYear,
        String description,
        UUID parentPairId
) {
}
