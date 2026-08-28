package org.danetree.common.repository;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import org.danetree.common.utils.FilterRequest;
import org.danetree.common.utils.PageRequest;
import org.danetree.common.utils.PanacheQueryBuilder;

import java.util.List;

public abstract class BaseRepository<T, ID> implements PanacheRepositoryBase<T, ID>
{
    protected abstract Class<T> getEntityClass();

    public List<T> findAll(PageRequest pageRequest, FilterRequest filterRequest)
    {
        PanacheQueryBuilder queryBuilder = PanacheQueryBuilder.from(filterRequest, getEntityClass());
        PanacheQuery<T> query;

        if (queryBuilder.hasQuery() && queryBuilder.hasParams()) {
            // Filtered query with bound parameters (e.g., ILIKE, EQUALS, …)
            query = find(queryBuilder.query(), queryBuilder.sort(), queryBuilder.params());
        } else if (queryBuilder.hasQuery()) {
            // Param-free clauses such as IS NULL / IS NOT NULL
            query = find(queryBuilder.query(), queryBuilder.sort());
        } else {
            // No filter at all — return every row
            query = findAll(queryBuilder.sort());
        }

        query.page(queryBuilder.page(pageRequest));
        return query.list();
    }

    public long count(FilterRequest filterRequest)
    {
        PanacheQueryBuilder queryBuilder = PanacheQueryBuilder.from(filterRequest, getEntityClass());

        if (queryBuilder.hasQuery() && queryBuilder.hasParams()) {
            return count(queryBuilder.query(), queryBuilder.params());
        }

        if (queryBuilder.hasQuery()) {
            // Param-free clauses such as IS NULL / IS NOT NULL
            return count(queryBuilder.query());
        }

        return count();
    }
}
