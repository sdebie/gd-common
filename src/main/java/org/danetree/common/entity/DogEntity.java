package org.danetree.common.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.danetree.common.enums.AvailabilityStatus;
import org.danetree.common.enums.Gender;

public record DogEntity(
        UUID id,
        String name,
        OffsetDateTime dateOfBirth,
        Gender gender,
        String color,
        AvailabilityStatus availabilityStatus,
        UUID litterId) {
}

