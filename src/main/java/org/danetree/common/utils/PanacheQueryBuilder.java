package org.danetree.common.utils;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import org.danetree.common.enums.LogicalOperator;
import org.danetree.common.enums.SortDirection;

import java.lang.reflect.Field;
import java.util.*;

public class PanacheQueryBuilder
{
    private final FilterRequest filterRequest;
    private final Class<?> entityClass;
    private final List<String> whereClauses = new ArrayList<>();
    private final Map<String, Object> paramMap = new LinkedHashMap<>();

    // unique param name counter
    private int seq = 0;
    private String builtQuery;
    private Sort builtSort;
    private Map<String, Object> builtParams;

    public PanacheQueryBuilder(FilterRequest filterRequest)
    {
        this(filterRequest, null);
    }

    public PanacheQueryBuilder(FilterRequest filterRequest, Class<?> entityClass)
    {
        this.filterRequest = filterRequest != null ? filterRequest : new FilterRequest();
        this.entityClass = entityClass;
    }

    public static PanacheQueryBuilder from(FilterRequest filterRequest)
    {
        return new PanacheQueryBuilder(filterRequest, null).build();
    }

    public static PanacheQueryBuilder from(FilterRequest filterRequest, Class<?> entityClass)
    {
        return new PanacheQueryBuilder(filterRequest, entityClass).build();
    }

    protected PanacheQueryBuilder build()
    {
        // 1. Flat top-level filters (AND-ed together)
        if (filterRequest.getFilters() != null) {
            for (Filter filter : filterRequest.getFilters()) {
                String clause = buildFilter(filter);
                if (clause != null) {
                    whereClauses.add(clause);
                }
            }
        }

        // 2. Group filters (each group becomes a bracketed expression)
        if (filterRequest.getFilterGroups() != null) {
            for (FilterGroup filterGroup : filterRequest.getFilterGroups()) {
                String clause = buildGroup(filterGroup);
                if (clause != null && !clause.isBlank()) {
                    whereClauses.add(clause);
                }
            }
        }

        // 3. Combine everything at the top level with AND
        builtQuery = String.join(" AND ", whereClauses);

        // 4. Sort
        builtSort = buildSort(filterRequest.getSort());

        // 5. Parameters
        builtParams = Collections.unmodifiableMap(paramMap);

        return this;
    }


    private Sort buildSort(List<SortRequest> sortRequests)
    {
        if (sortRequests == null || sortRequests.isEmpty()) return null;

        Sort sort = null;

        for (SortRequest sortRequest : sortRequests) {
            if (sortRequest.getField() == null || sortRequest.getField().isBlank()) continue;
            Sort.Direction dir = sortRequest.getDirection() == SortDirection.DESC ? Sort.Direction.Descending : Sort.Direction.Ascending;
            sort = (sort == null) ? Sort.by(sanitize(sortRequest.getField()), dir)
                    : sort.and(sanitize(sortRequest.getField()), dir);
        }
        return sort != null ? sort : Sort.by("id");
    }

    /**
     * Prevent JPQL injection — only alphanumerics, underscores, and dots allowed.
     * Dot notation supports JOIN navigation (e.g. "address.city").
     * Delegates to the shared {@link FieldNameValidator} so the rule has one definition.
     */
    private String sanitize(String field)
    {
        return FieldNameValidator.validate(field);
    }

    private String buildGroup(FilterGroup filterGroup)
    {
        if (filterGroup == null || filterGroup.isEmpty()) return null;

        List<String> parts = new ArrayList<>();

        if (filterGroup.getFilters() != null) {
            for (Filter f : filterGroup.getFilters()) {
                String c = buildFilter(f);
                if (c != null) parts.add(c);
            }
        }

        if (filterGroup.getFilterGroups() != null) {
            for (FilterGroup sub : filterGroup.getFilterGroups()) {
                String c = buildGroup(sub);
                if (c != null && !c.isBlank()) parts.add("(" + c + ")");
            }
        }

        if (parts.isEmpty()) return null;

        String joiner = filterGroup.getOperator() == LogicalOperator.OR ? " OR " : " AND ";
        return parts.size() == 1 ? parts.getFirst() : "(" + String.join(joiner, parts) + ")";
    }

    protected String buildFilter(Filter filter)
    {
        if (filter == null || filter.getKey() == null || filter.getKey().isBlank()) {
            return null;
        }

        String field = sanitize(filter.getKey());
        String p = "p" + seq++;

        // Resolve the exact enum class for this field via reflection on the entity class.
        // Falls back to the legacy ProductStatusEn heuristic when no entity class is provided.
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> enumType = resolveEnumType(field);

        return switch (filter.getOperator()) {
            case EQUALS -> {
                bind(p, enumType != null ? coerceToEnum(filter.getValue(), enumType) : coerce(filter.getValue()));
                yield field + " = :" + p;
            }
            case NOT_EQUALS -> {
                bind(p, enumType != null ? coerceToEnum(filter.getValue(), enumType) : coerce(filter.getValue()));
                yield field + " != :" + p;
            }
            case GREATER_THAN -> {
                bind(p, coerce(filter.getValue()));
                yield field + " > :" + p;
            }
            case GREATER_THAN_OR_EQUALS -> {
                bind(p, coerce(filter.getValue()));
                yield field + " >= :" + p;
            }
            case LESS_THAN -> {
                bind(p, coerce(filter.getValue()));
                yield field + " < :" + p;
            }
            case LESS_THAN_OR_EQUALS -> {
                bind(p, coerce(filter.getValue()));
                yield field + " <= :" + p;
            }
            case IN -> {
                bind(p, enumType != null ? coerceToEnumList(filter.getValues(), enumType) : coerceList(filter.getValues()));
                yield field + " IN (:" + p + ")";
            }
            case NOT_IN -> {
                bind(p, enumType != null ? coerceToEnumList(filter.getValues(), enumType) : coerceList(filter.getValues()));
                yield field + " NOT IN (:" + p + ")";
            }
            case LIKE -> {
                bind(p, "%" + filter.getValue() + "%");
                yield field + " LIKE :" + p;
            }
            case ILIKE -> {
                bind(p, "%" + filter.getValue().toLowerCase() + "%");
                yield "LOWER(" + field + ") LIKE :" + p;
            }
            case NOT_LIKE -> {
                bind(p, "%" + filter.getValue() + "%");
                yield field + " NOT LIKE :" + p;
            }
            case IS_NULL -> field + " IS NULL";
            case IS_NOT_NULL -> field + " IS NOT NULL";
            default -> throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void bind(String key, Object value)
    {
        paramMap.put(key, value);
    }

    /**
     * Resolves the enum class for a given JPQL field name by inspecting the entity class
     * via reflection. Dot-notation fields (e.g. "address.city") walk the chain.
     * Returns null if the field is not an enum or the entity class is unknown.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Class<? extends Enum> resolveEnumType(String fieldName)
    {
        if (entityClass == null) return null;
        try {
            String[] parts = fieldName.split("\\.");
            // If the first segment is not a Java field on the entity it is a JPQL alias
            // (e.g. "p" in "p.status"). Skip it so the remaining path resolves correctly.
            int start = (parts.length > 1 && findField(entityClass, parts[0]) == null) ? 1 : 0;
            Class<?> current = entityClass;
            for (int i = start; i < parts.length; i++) {
                Field f = findField(current, parts[i]);
                if (f == null) return null;
                current = f.getType();
            }
            return current.isEnum() ? (Class<? extends Enum>) current : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String name)
    {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object coerceToEnum(String value, Class<? extends Enum> enumClass)
    {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    private List<Object> coerceToEnumList(List<String> values, Class<? extends Enum> enumClass)
    {
        if (values == null) return Collections.emptyList();
        List<Object> out = new ArrayList<>();
        for (String v : values) out.add(coerceToEnum(v, enumClass));
        return out;
    }

    /**
     * Legacy fallback used when no entity class is provided.
     * Only handles ProductStatusEn — kept for backward compatibility with callers
     * that have not yet passed an entity class.
     */
//    protected Object coerceEnum(String value)
//    {
//        if (value == null) return null;
//        try {
//            return ProductStatusEn.valueOf(value);
//        } catch (IllegalArgumentException ignored) {
//            return value;
//        }
//    }

    /**
     * Best-effort coercion from String to a more specific type.
     * Override in a subclass if you need entity-aware type coercion.
     */
    protected Object coerce(String value)
    {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return Boolean.parseBoolean(value);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    protected List<Object> coerceList(List<String> values)
    {
        if (values == null) return Collections.emptyList();
        List<Object> out = new ArrayList<>();
        for (String v : values) out.add(coerce(v));
        return out;
    }

    public boolean hasQuery()
    {
        return builtQuery != null && !builtQuery.isBlank();
    }

    /**
     * The JPQL where-clause string, or empty string if no filters were set.
     */
    public String query()
    {
        return builtQuery;
    }

    /**
     * The Panache Sort descriptor. Defaults to "id ASC" if no sort was set.
     */
    public Sort sort()
    {
        return builtSort;
    }

    /**
     * Named parameters as a plain Map — pass directly to Panache's
     * find(query, sort, params) overload that accepts Map<String, Object>.
     * Only call this when hasParams() is true.
     */
    public Map<String, Object> params()
    {
        return builtParams;
    }

    /**
     * True when filters produced bound parameters (i.e. not IS_NULL / IS_NOT_NULL only).
     */
    public boolean hasParams()
    {
        return !builtParams.isEmpty();
    }

    /**
     * Converts a PageRequest to a Panache Page.
     */
    public Page page(PageRequest pageRequest)
    {
        PageRequest p = pageRequest != null ? pageRequest : new PageRequest();
        return Page.of(p.getPageIndex(), p.getPageSize());
    }
}
