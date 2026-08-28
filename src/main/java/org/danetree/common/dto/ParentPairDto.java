package org.danetree.common.dto;

import java.util.UUID;

public record ParentPairDto(
        UUID id,
        String femaleParentKey,
        String maleParentKey
) {
}
