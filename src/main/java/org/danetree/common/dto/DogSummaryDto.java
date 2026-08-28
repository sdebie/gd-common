package org.danetree.common.dto;

import java.util.UUID;

import org.danetree.common.enums.AvailabilityStatus;

public record DogSummaryDto(
        UUID id,
        String name,
        String gender,
        AvailabilityStatus availabilityStatus,
        String profileImageUrl) {
}

