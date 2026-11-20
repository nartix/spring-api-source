package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.application.port.AdminPermissionListingRepository;
import com.ferozfaiz.uniapi.iam.application.query.AdminListPermissionsQuery;
import com.ferozfaiz.uniapi.iam.domain.model.Permission;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity.PermissionEntity;
import com.ferozfaiz.uniapi.iam.infrastructure.persistence.mapper.IamPersistenceMapper;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AdminPermissionListingQueryAdapter implements AdminPermissionListingRepository {

  private static final Map<String, String> SORT_PROPERTIES = Map.of(
    "id",
    "id",
    "code",
    "code",
    "name",
    "name",
    "resource",
    "resource",
    "action",
    "action",
    "active",
    "active",
    "createdAt",
    "createdAt",
    "updatedAt",
    "updatedAt"
  );

  private final EntityManager entityManager;

  public AdminPermissionListingQueryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public PageResult<Permission> findAll(AdminListPermissionsQuery query) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<PermissionEntity> criteria = builder.createQuery(PermissionEntity.class);
    Root<PermissionEntity> root = criteria.from(PermissionEntity.class);
    criteria.select(root);
    criteria.where(predicates(query, root, builder));
    criteria.orderBy(orders(query.sort(), root, builder));

    TypedQuery<PermissionEntity> typedQuery = entityManager.createQuery(criteria);
    typedQuery.setFirstResult(query.page().page() * query.page().size());
    typedQuery.setMaxResults(query.page().size());

    List<Permission> content = typedQuery
      .getResultList()
      .stream()
      .map(IamPersistenceMapper::toDomain)
      .toList();
    long total = count(query, builder);
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.page().size());
    boolean first = query.page().page() == 0;
    boolean last = totalPages == 0 || query.page().page() >= totalPages - 1;

    return new PageResult<>(
      content,
      query.page().page(),
      query.page().size(),
      total,
      totalPages,
      first,
      last,
      query.sort()
    );
  }

  private long count(AdminListPermissionsQuery query, CriteriaBuilder builder) {
    CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
    Root<PermissionEntity> root = criteria.from(PermissionEntity.class);
    criteria.select(builder.count(root));
    criteria.where(predicates(query, root, builder));
    Long count = entityManager.createQuery(criteria).getSingleResult();
    return count == null ? 0 : count;
  }

  private static Predicate[] predicates(
    AdminListPermissionsQuery query,
    Root<PermissionEntity> root,
    CriteriaBuilder builder
  ) {
    List<Predicate> predicates = new ArrayList<>();
    if (query.active() != null) {
      predicates.add(builder.equal(root.get("active"), query.active()));
    }
    if (query.resource() != null) {
      predicates.add(
        builder.equal(builder.lower(root.get("resource")), normalized(query.resource()))
      );
    }
    if (query.action() != null) {
      predicates.add(builder.equal(builder.lower(root.get("action")), normalized(query.action())));
    }
    if (query.q() != null) {
      String value = likeValue(query.q());
      predicates.add(
        builder.or(
          builder.like(builder.lower(root.get("code")), value),
          builder.like(builder.lower(root.get("name")), value),
          builder.like(builder.lower(nullableString(root, "description", builder)), value),
          builder.like(builder.lower(root.get("resource")), value),
          builder.like(builder.lower(root.get("action")), value)
        )
      );
    }
    return predicates.toArray(Predicate[]::new);
  }

  private static Expression<String> nullableString(
    Root<PermissionEntity> root,
    String property,
    CriteriaBuilder builder
  ) {
    return builder.coalesce(root.get(property), "");
  }

  private static List<Order> orders(
    SortCriteria sort,
    Root<PermissionEntity> root,
    CriteriaBuilder builder
  ) {
    return sort
      .orders()
      .stream()
      .map(order -> order(order, root, builder))
      .toList();
  }

  private static Order order(
    SortOrder order,
    Root<PermissionEntity> root,
    CriteriaBuilder builder
  ) {
    String property = SORT_PROPERTIES.get(order.property());
    if (property == null) {
      throw new InvalidRequestException(
        "iam.api.admin-access-options.search.sort.allowed",
        "invalid sort",
        String.join(", ", SORT_PROPERTIES.keySet())
      );
    }
    return order.direction() == SortDirection.DESC
      ? builder.desc(root.get(property))
      : builder.asc(root.get(property));
  }

  private static String likeValue(String value) {
    return "%" + normalized(value) + "%";
  }

  private static String normalized(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
