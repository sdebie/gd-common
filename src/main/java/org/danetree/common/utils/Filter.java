package org.danetree.common.utils;

import lombok.Getter;
import lombok.Setter;
import org.danetree.common.entity.FilterOperator;

import java.util.List;

@Getter
@Setter
public class Filter
{
    private String key;
    private FilterOperator operator;
    private String value;
    private List<String> values;

    public Filter()
    {
    }

    public Filter(String key, FilterOperator operator, String value)
    {
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

    public Filter(String key, FilterOperator operator, List<String> values)
    {
        this.key = key;
        this.operator = operator;
        this.values = values;
    }
}
