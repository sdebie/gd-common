package org.danetree.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PuppyUpdateDto(
    UUID id,
    String name,
    String gender,
    String geneticLine,
    String dogKey,
    String description,
    LocalDateTime dateOfBirth,
    String statusBadge,
    String registrationNumber,
    UUID parentPairId,
    UUID ownerUserId
) {}
