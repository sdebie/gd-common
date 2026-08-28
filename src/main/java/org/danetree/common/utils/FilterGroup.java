package org.danetree.common.utils;

import lombok.Getter;
import lombok.Setter;
import org.danetree.common.enums.LogicalOperator;

import java.util.List;

@Getter
@Setter
public class FilterGroup
{
    private LogicalOperator operator = LogicalOperator.AND;
    private List<Filter> filters;
    private List<FilterGroup> filterGroups;

    public boolean isEmpty()
    {
        return (filters == null || filters.isEmpty()) && (filterGroups == null || filterGroups.isEmpty());
    }
}
