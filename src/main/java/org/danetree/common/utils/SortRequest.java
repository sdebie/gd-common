package org.danetree.common.utils;

import lombok.Getter;
import lombok.Setter;
import org.danetree.common.enums.SortDirection;

@Getter
@Setter
public class SortRequest
{
    private String field;
    private SortDirection direction = SortDirection.ASC;
}
