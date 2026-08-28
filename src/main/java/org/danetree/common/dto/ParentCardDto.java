package org.danetree.common.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ParentCardDto(
        UUID id,
        String dogKey,
        String name,
        String gender,
        String geneticLine,
        LocalDateTime dateOfBirth,
        String description,
        List<DogImageDto> images,
        String statusBadge,
        String registrationNumber) {
}
