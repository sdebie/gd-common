package org.danetree.common.dto;

import java.util.UUID;

public record LitterImageDto(
        UUID id,
        String url,
        String caption,
        boolean main
) {
}
