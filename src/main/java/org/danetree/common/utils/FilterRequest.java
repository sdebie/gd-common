package org.danetree.common.utils;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Unified filter + sort descriptor passed alongside PageRequest.
 * <br/><br/>
 * Supports three things:
 *   1. filters  — flat list of single-field predicates (implicitly AND-ed)
 *   2. groups   — grouped predicates with explicit AND/OR logic (nestable)
 *   3. sort     — one or more sort columns with direction
 * <br/><br/>
 * Flat filters and groups are AND-ed together at the top level.
 * <br/><br/>
 * GraphQL usage:
 * <br/><br/>
 *   filterRequest: {
 *     filters: [
 *       { field: "active", operator: EQ, value: "true" }
 *     ],
 *     groups: [
 *       {
 *         operator: OR,
 *         filters: [
 *           { field: "name",  operator: ILIKE, value: "acme" },
 *           { field: "email", operator: ILIKE, value: "acme" }
 *         ]
 *       }
 *     ],
 *     sort: [{ field: "name", direction: ASC }]
 *   }
 */
@Getter
@Setter
public class FilterRequest
{
    private List<Filter> filters;
    private List<FilterGroup> filterGroups;
    private List<SortRequest> sort;
}
