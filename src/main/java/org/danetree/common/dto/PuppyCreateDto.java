package org.danetree.common.dto;

import java.util.UUID;

public record PuppyCreateDto(
        String name,
        String gender,
        String geneticLine,
        String description,
        String statusBadge,
        String registrationNumber,
        UUID parentPairId,
        UUID ownerUserId
) {
}
