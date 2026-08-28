package org.danetree.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DogImageDto(
        UUID id,
        String url,
        String caption,
        OffsetDateTime timestamp,
        boolean isMain
) {}
