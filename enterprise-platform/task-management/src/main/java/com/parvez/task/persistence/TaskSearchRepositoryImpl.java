package com.parvez.task.persistence;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.parvez.task.service.InvalidTaskQueryException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public class TaskSearchRepositoryImpl implements TaskSearchRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<TaskEntity> search(Specification<TaskEntity> specification, Pageable pageable) {
        long offset = pageable.getOffset();
        if (offset > Integer.MAX_VALUE) {
            throw new InvalidTaskQueryException("Requested page is too large");
        }

        var criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<TaskEntity> query = criteriaBuilder.createQuery(TaskEntity.class);
        Root<TaskEntity> root = query.from(TaskEntity.class);
        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        query.select(root);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(orders(pageable.getSort(), root, criteriaBuilder));

        List<TaskEntity> content = entityManager.createQuery(query)
                .setFirstResult((int) offset)
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(content, pageable, count(specification));
    }

    private long count(Specification<TaskEntity> specification) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
        Root<TaskEntity> root = query.from(TaskEntity.class);
        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        query.select(criteriaBuilder.count(root));
        if (predicate != null) {
            query.where(predicate);
        }
        return entityManager.createQuery(query).getSingleResult();
    }

    private List<Order> orders(Sort sort, Root<TaskEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        List<Order> orders = new ArrayList<>();
        boolean idSpecified = false;
        for (Sort.Order order : sort) {
            orders.add(order.isAscending()
                    ? criteriaBuilder.asc(root.get(order.getProperty()))
                    : criteriaBuilder.desc(root.get(order.getProperty())));
            idSpecified |= "id".equals(order.getProperty());
        }
        if (!idSpecified) {
            orders.add(criteriaBuilder.asc(root.get("id")));
        }
        return orders;
    }
}