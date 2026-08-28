package org.danetree.common.dto;

import java.util.UUID;

public record ParentPairUpdateDto(
        UUID id,
        String femaleParentKey,
        String maleParentKey
) {
}
