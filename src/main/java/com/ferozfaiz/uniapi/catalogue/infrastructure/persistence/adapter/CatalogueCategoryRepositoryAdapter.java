package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryPlacementTargetException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryRankingException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryParentOptionLookup;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryClosureEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueCategoryEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.mapper.CataloguePersistenceMapper;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueCategoryClosureJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueCategoryJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductCategoryJpaRepository;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankAssignmentResult;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankRebalanceLimitExceededException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CatalogueCategoryRepositoryAdapter implements CatalogueCategoryRepository {

  private static final ClosureTableDescriptor CATALOGUE_CATEGORY_HIERARCHY =
    ClosureTableDescriptor.of("catalogue_category", "catalogue_category_closure");
  private static final String CATALOGUE_CATEGORY_SYSTEM = "catalogue";
  private static final Map<String, String> TREE_SORT_PROPERTIES = Map.of(
    "id",
    "category_depth.id",
    "slug",
    "category_depth.slug",
    "name",
    "category_depth.name",
    "sortRank",
    "category_depth.sort_rank",
    "active",
    "category_depth.is_active",
    "createdAt",
    "category_depth.created_at",
    "updatedAt",
    "category_depth.updated_at"
  );
  private static final String DEFAULT_RANKED_TREE_SQL = """
    WITH RECURSIVE ordered_tree AS (
      SELECT
        category.id,
        category.parent_id,
        category.sort_rank,
        category.name,
        category.slug,
        category.is_active,
        category.created_at,
        category.updated_at,
        0 AS depth,
        ARRAY[category.sort_rank, category.id]::BIGINT[] AS sort_path
      FROM catalogue_category category
      WHERE category.parent_id IS NULL

      UNION ALL

      SELECT
        child.id,
        child.parent_id,
        child.sort_rank,
        child.name,
        child.slug,
        child.is_active,
        child.created_at,
        child.updated_at,
        parent.depth + 1 AS depth,
        parent.sort_path || ARRAY[child.sort_rank, child.id]::BIGINT[] AS sort_path
      FROM catalogue_category child
      JOIN ordered_tree parent ON child.parent_id = parent.id
    ),
    paged_tree AS (
      SELECT
        id,
        parent_id,
        sort_rank,
        name,
        slug,
        is_active,
        created_at,
        updated_at,
        depth,
        sort_path
      FROM ordered_tree
      ORDER BY sort_path ASC
      LIMIT :limit OFFSET :offset
    )
    SELECT
      paged_tree.id,
      paged_tree.parent_id,
      paged_tree.sort_rank,
      paged_tree.name,
      paged_tree.slug,
      paged_tree.is_active,
      paged_tree.created_at,
      paged_tree.updated_at,
      paged_tree.depth,
      EXISTS (
        SELECT 1
        FROM catalogue_category direct_child
        WHERE direct_child.parent_id = paged_tree.id
      ) AS has_children
    FROM paged_tree
    ORDER BY paged_tree.sort_path ASC
    """;
  private static final String GENERIC_SORTABLE_TREE_SQL_TEMPLATE = """
    WITH RECURSIVE category_depth AS (
      SELECT
        category.id,
        category.parent_id,
        category.sort_rank,
        category.name,
        category.slug,
        category.is_active,
        category.created_at,
        category.updated_at
      FROM catalogue_category category
    ),
    ranked_categories AS (
      SELECT
        category_depth.*,
        ROW_NUMBER() OVER (
          PARTITION BY category_depth.parent_id
          ORDER BY %s
        ) AS sibling_rank
      FROM category_depth
    ),
    ordered_tree AS (
      SELECT
        ranked_categories.*,
        0 AS depth,
        ARRAY[ranked_categories.sibling_rank] AS sort_path
      FROM ranked_categories
      WHERE ranked_categories.parent_id IS NULL

      UNION ALL

      SELECT
        child.*,
        parent.depth + 1 AS depth,
        parent.sort_path || child.sibling_rank
      FROM ranked_categories child
      JOIN ordered_tree parent ON child.parent_id = parent.id
    ),
    paged_tree AS (
      SELECT
        id,
        parent_id,
        sort_rank,
        name,
        slug,
        is_active,
        created_at,
        updated_at,
        depth,
        sort_path
      FROM ordered_tree
      ORDER BY sort_path ASC
      LIMIT :limit OFFSET :offset
    )
    SELECT
      paged_tree.id,
      paged_tree.parent_id,
      paged_tree.sort_rank,
      paged_tree.name,
      paged_tree.slug,
      paged_tree.is_active,
      paged_tree.created_at,
      paged_tree.updated_at,
      paged_tree.depth,
      EXISTS (
        SELECT 1
        FROM catalogue_category direct_child
        WHERE direct_child.parent_id = paged_tree.id
      ) AS has_children
    FROM paged_tree
    ORDER BY paged_tree.sort_path ASC
    """;
  private static final String BREADCRUMB_SQL = """
    WITH breadcrumb AS (
      SELECT
        category.id,
        category.parent_id,
        category.sort_rank,
        category.name,
        category.slug,
        category.is_active,
        category.created_at,
        category.updated_at,
        path.depth AS distance_to_target,
        MAX(path.depth) OVER () AS target_depth
      FROM catalogue_category_closure path
      JOIN catalogue_category category ON category.id = path.ancestor_id
      WHERE path.descendant_id = :categoryId
    )
    SELECT
      id,
      parent_id,
      sort_rank,
      name,
      slug,
      is_active,
      created_at,
      updated_at,
      target_depth - distance_to_target AS depth
    FROM breadcrumb
    ORDER BY distance_to_target DESC
    """;
  private static final String INCLUDED_PARENT_OPTIONS_SQL = """
    SELECT
      category.id,
      category.parent_id,
      category.sort_rank,
      category.name,
      category.slug,
      category.is_active,
      category.created_at,
      category.updated_at,
      0 AS search_rank
    FROM catalogue_category category
    WHERE category.id IN (:includeIds)
    """;
  private static final String PARENT_OPTION_PATH_SQL = """
    WITH path_rows AS (
      SELECT
        path.descendant_id,
        ancestor.id,
        ancestor.parent_id,
        ancestor.sort_rank,
        ancestor.name,
        ancestor.slug,
        ancestor.is_active,
        ancestor.created_at,
        ancestor.updated_at,
        path.depth AS distance_to_target,
        MAX(path.depth) OVER (PARTITION BY path.descendant_id) AS target_depth
      FROM catalogue_category_closure path
      JOIN catalogue_category ancestor ON ancestor.id = path.ancestor_id
      WHERE path.descendant_id IN (:categoryIds)
    )
    SELECT
      descendant_id,
      id,
      parent_id,
      sort_rank,
      name,
      slug,
      is_active,
      created_at,
      updated_at,
      target_depth - distance_to_target AS depth
    FROM path_rows
    ORDER BY descendant_id ASC, distance_to_target DESC
    """;

  private final CatalogueCategoryJpaRepository categories;
  private final CatalogueCategoryClosureJpaRepository closurePaths;
  private final CatalogueProductCategoryJpaRepository productCategories;
  private final ClosureTableOperations closureTables;
  private final NamedParameterJdbcTemplate jdbc;
  private final CategorySystemDescriptor categorySystem;
  private final SparseRankOperations rankOperations;

  public CatalogueCategoryRepositoryAdapter(
    CatalogueCategoryJpaRepository categories,
    CatalogueCategoryClosureJpaRepository closurePaths,
    CatalogueProductCategoryJpaRepository productCategories,
    ClosureTableOperations closureTables,
    NamedParameterJdbcTemplate jdbc,
    CategorySystemRegistry categorySystems,
    SparseRankOperations rankOperations
  ) {
    this.categories = categories;
    this.closurePaths = closurePaths;
    this.productCategories = productCategories;
    this.closureTables = closureTables;
    this.jdbc = jdbc;
    this.categorySystem = categorySystems.requireDescriptor(CATALOGUE_CATEGORY_SYSTEM);
    this.rankOperations = rankOperations;
  }

  @Override
  @Transactional
  public CatalogueCategory save(CatalogueCategory category, SparseOrderTarget orderTarget) {
    Objects.requireNonNull(orderTarget, "orderTarget");
    validatePlacement(orderTarget);
    Long id = category.id();
    if (id == null) {
      Long sortRank = rankingEnabled() ? allocateRank(category.parentId(), orderTarget) : null;
      CatalogueCategoryEntity saved = categories.saveAndFlush(
        CataloguePersistenceMapper.toEntity(withSortRank(category, sortRank))
      );
      id = saved.getId();
      if (category.parentId() == null) {
        closureTables.createRoot(CATALOGUE_CATEGORY_HIERARCHY, id);
      } else {
        closureTables.createChild(CATALOGUE_CATEGORY_HIERARCHY, id, category.parentId());
      }
    } else {
      CatalogueCategoryEntity entity = categories
        .findById(id)
        .orElseThrow(() -> CatalogueNotFoundException.category(category.slug()));
      Long currentParentId = entity.getParentId();
      Long sortRank = entity.getSortRank();
      boolean parentChanged = !Objects.equals(currentParentId, category.parentId());
      if (rankingEnabled() && (parentChanged || orderTarget.hasExplicitPlacement())) {
        if (parentChanged) {
          closureTables.moveSubtree(CATALOGUE_CATEGORY_HIERARCHY, id, category.parentId());
        }
        RankAssignmentResult rank = assignRank(id, category.parentId(), orderTarget);
        sortRank = rank.assignedRank();
      } else if (parentChanged) {
        closureTables.moveSubtree(CATALOGUE_CATEGORY_HIERARCHY, id, category.parentId());
      }
      entity.setParentId(category.parentId());
      entity.setSortRank(sortRank);
      entity.setName(category.name());
      entity.setSlug(category.slug());
      entity.setActive(category.active());
      categories.saveAndFlush(entity);
    }
    Long savedId = id;
    return findById(savedId).orElseThrow(() ->
      CatalogueNotFoundException.category(category.slug())
    );
  }

  @Override
  public PageResult<CatalogueCategory> findAll(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters,
    PageCriteria page,
    SortCriteria sort
  ) {
    var pageable = PageRequest.of(page.page(), page.size(), toJpaSort(sort));
    Page<CatalogueCategoryEntity> result = categories.findAll(
      categorySpecification(parentScope, filters),
      pageable
    );
    return toPageResult(result, sort);
  }

  @Override
  public PageResult<CatalogueCategoryNode> findAllNodes(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters,
    PageCriteria page,
    SortCriteria sort
  ) {
    PageResult<CatalogueCategory> result = findAll(parentScope, filters, page, sort);
    List<Long> categoryIds = result.content().stream().map(CatalogueCategory::id).toList();
    Map<Long, Integer> depths = depthsByCategoryId(categoryIds);
    Set<Long> parentIdsWithChildren = categoryIds.isEmpty()
      ? Set.of()
      : Set.copyOf(categories.findParentIdsWithChildren(categoryIds));
    return result.map(category ->
      new CatalogueCategoryNode(
        category,
        depths.getOrDefault(category.id(), 0),
        parentIdsWithChildren.contains(category.id())
      )
    );
  }

  @Override
  public PageResult<CatalogueCategoryTreeNode> findTree(PageCriteria page, SortCriteria sort) {
    long totalElements = countTreeRows();
    String sql = treeSql(sort);
    List<CatalogueCategoryTreeNode> content = jdbc.query(
      sql,
      Map.of("limit", page.size(), "offset", (long) page.page() * page.size()),
      CatalogueCategoryRepositoryAdapter::toCategoryTreeNode
    );
    int totalPages = totalPages(totalElements, page.size());
    return new PageResult<>(
      content,
      page.page(),
      page.size(),
      totalElements,
      totalPages,
      page.page() == 0,
      totalPages == 0 || page.page() >= totalPages - 1,
      sort
    );
  }

  @Override
  public Optional<CatalogueCategory> findById(Long id) {
    return categories.findById(id).map(CataloguePersistenceMapper::toDomain);
  }

  @Override
  public Optional<CatalogueCategoryNode> findNodeById(Long id) {
    return findById(id).map(category ->
      new CatalogueCategoryNode(category, depthOf(id), hasChildren(id))
    );
  }

  @Override
  public List<CatalogueCategoryBreadcrumbItem> findBreadcrumbById(Long categoryId) {
    return jdbc.query(
      BREADCRUMB_SQL,
      Map.of("categoryId", Objects.requireNonNull(categoryId, "categoryId")),
      CatalogueCategoryRepositoryAdapter::toCategoryBreadcrumbItem
    );
  }

  @Override
  public CatalogueCategoryParentOptionSlice findParentOptions(
    CatalogueCategoryParentOptionLookup lookup
  ) {
    Objects.requireNonNull(lookup, "lookup");
    List<ParentOptionCandidate> includedCandidates = findIncludedParentOptionCandidates(lookup);
    List<ParentOptionCandidate> returnedIncludedCandidates =
      includedCandidates.size() > lookup.limit()
        ? includedCandidates.subList(0, lookup.limit())
        : includedCandidates;
    int ordinaryLimit = lookup.limit() - returnedIncludedCandidates.size();
    List<ParentOptionCandidate> ordinaryCandidates =
      ordinaryLimit <= 0
        ? List.of()
        : findOrdinaryParentOptionCandidates(
            lookup,
            returnedIncludedCandidates
              .stream()
              .map(candidate -> candidate.category().id())
              .toList(),
            ordinaryLimit + 1
          );
    boolean hasMore = ordinaryCandidates.size() > ordinaryLimit;
    List<ParentOptionCandidate> returnedOrdinaryCandidates = hasMore
      ? ordinaryCandidates.subList(0, ordinaryLimit)
      : ordinaryCandidates;
    List<ParentOptionCandidate> returnedCandidates = new ArrayList<>(
      returnedIncludedCandidates.size() + returnedOrdinaryCandidates.size()
    );
    returnedCandidates.addAll(returnedIncludedCandidates);
    returnedCandidates.addAll(returnedOrdinaryCandidates);

    Map<Long, List<CatalogueCategoryBreadcrumbItem>> pathsByCategoryId = findParentOptionPaths(
      returnedCandidates
        .stream()
        .map(candidate -> candidate.category().id())
        .toList()
    );
    List<CatalogueCategoryParentOption> options = returnedCandidates
      .stream()
      .map(candidate -> toParentOption(candidate, pathsByCategoryId))
      .toList();
    String nextCursor =
      hasMore && !returnedOrdinaryCandidates.isEmpty()
        ? toNextCursor(lookup, returnedOrdinaryCandidates.getLast())
        : null;
    return new CatalogueCategoryParentOptionSlice(options, nextCursor != null, nextCursor);
  }

  @Override
  public Optional<CatalogueCategory> findActiveBySlug(String slug) {
    return categories.findBySlugAndActiveTrue(slug).map(CataloguePersistenceMapper::toDomain);
  }

  @Override
  public List<Long> findSelfAndDescendantIds(String slug) {
    return findActiveBySlug(slug)
      .map(category -> {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(category.id());
        ids.addAll(closureTables.findDescendantIds(CATALOGUE_CATEGORY_HIERARCHY, category.id()));
        return List.copyOf(ids);
      })
      .orElse(List.of());
  }

  @Override
  public boolean existsSlug(String slug, Long excludingId) {
    return excludingId == null
      ? categories.existsBySlug(slug)
      : categories.existsBySlugAndIdNot(slug, excludingId);
  }

  @Override
  public boolean hasChildren(Long categoryId) {
    return categories.existsByParentId(categoryId);
  }

  @Override
  public boolean hasAssignedProducts(Long categoryId) {
    return productCategories.existsByCategoryId(categoryId);
  }

  @Override
  public boolean isDescendantOf(Long descendantId, Long ancestorId) {
    return closureTables.isDescendantOf(CATALOGUE_CATEGORY_HIERARCHY, descendantId, ancestorId);
  }

  @Override
  @Transactional
  public void move(Long categoryId, Long newParentId, SparseOrderTarget orderTarget) {
    Objects.requireNonNull(orderTarget, "orderTarget");
    validatePlacement(orderTarget);
    closureTables.acquireHierarchyMoveLock(CATALOGUE_CATEGORY_HIERARCHY);
    closureTables.lockMoveEndpointsSafely(CATALOGUE_CATEGORY_HIERARCHY, categoryId, newParentId);
    boolean directParentAlreadySet = closureTables.hasDirectParent(
      CATALOGUE_CATEGORY_HIERARCHY,
      categoryId,
      newParentId
    );
    if (directParentAlreadySet && !orderTarget.hasExplicitPlacement()) {
      return;
    }
    if (!directParentAlreadySet) {
      closureTables.moveSubtree(CATALOGUE_CATEGORY_HIERARCHY, categoryId, newParentId);
    }
    categories.findById(categoryId).ifPresent(category -> {
      category.setParentId(newParentId);
      if (rankingEnabled()) {
        RankAssignmentResult rank = assignRank(categoryId, newParentId, orderTarget);
        category.setSortRank(rank.assignedRank());
      }
    });
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    categories.deleteById(id);
    categories.flush();
  }

  private static Sort toJpaSort(SortCriteria sort) {
    return Sort.by(
      sort
        .orders()
        .stream()
        .map(order ->
          new Sort.Order(
            order.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC,
            order.property()
          )
        )
        .toList()
    );
  }

  private static PageResult<CatalogueCategory> toPageResult(
    Page<CatalogueCategoryEntity> result,
    SortCriteria sort
  ) {
    return new PageResult<>(
      result.getContent().stream().map(CataloguePersistenceMapper::toDomain).toList(),
      result.getNumber(),
      result.getSize(),
      result.getTotalElements(),
      result.getTotalPages(),
      result.isFirst(),
      result.isLast(),
      sort
    );
  }

  private static Specification<CatalogueCategoryEntity> categorySpecification(
    CategoryParentScope parentScope,
    AdminCatalogueCategoryFilters filters
  ) {
    Objects.requireNonNull(parentScope, "parentScope");
    Objects.requireNonNull(filters, "filters");
    Specification<CatalogueCategoryEntity> specification = parentScopeSpecification(parentScope);
    if (filters.active() != null) {
      specification = specification.and((root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("active"), filters.active())
      );
    }
    if (filters.slug() != null) {
      specification = specification.and((root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("slug"), filters.slug())
      );
    }
    if (filters.q() != null) {
      String containsPattern = containsPattern(filters.q());
      specification = specification.and((root, query, criteriaBuilder) ->
        criteriaBuilder.or(
          criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), containsPattern, '\\'),
          criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), containsPattern, '\\')
        )
      );
    }
    return specification;
  }

  private static Specification<CatalogueCategoryEntity> parentScopeSpecification(
    CategoryParentScope parentScope
  ) {
    return (root, query, criteriaBuilder) ->
      switch (parentScope.mode()) {
        case ALL -> criteriaBuilder.conjunction();
        case ROOTS -> criteriaBuilder.isNull(root.get("parentId"));
        case CHILDREN -> criteriaBuilder.equal(root.get("parentId"), parentScope.parentId());
      };
  }

  private static String containsPattern(String value) {
    return "%" + escapeLikeLiteral(value.toLowerCase(Locale.ROOT)) + "%";
  }

  private static String prefixPattern(String value) {
    return escapeLikeLiteral(value.toLowerCase(Locale.ROOT)) + "%";
  }

  private static String escapeLikeLiteral(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static CatalogueCategory withSortRank(CatalogueCategory category, Long sortRank) {
    return new CatalogueCategory(
      category.id(),
      category.parentId(),
      sortRank,
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  private Map<Long, Integer> depthsByCategoryId(List<Long> categoryIds) {
    if (categoryIds.isEmpty()) {
      return Map.of();
    }
    return closurePaths
      .findByDescendantIdIn(categoryIds)
      .stream()
      .collect(
        Collectors.toMap(
          CatalogueCategoryClosureEntity::getDescendantId,
          CatalogueCategoryClosureEntity::getDepth,
          Integer::max
        )
      );
  }

  private int depthOf(Long categoryId) {
    return closurePaths
      .findByDescendantId(categoryId)
      .stream()
      .mapToInt(CatalogueCategoryClosureEntity::getDepth)
      .max()
      .orElse(0);
  }

  @Override
  public long countTreeRows() {
    Long total = jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_category",
      Map.of(),
      Long.class
    );
    return total == null ? 0L : total;
  }

  private static int totalPages(long totalElements, int size) {
    if (totalElements == 0) {
      return 0;
    }
    return (int) Math.ceil((double) totalElements / size);
  }

  private static CatalogueCategoryTreeNode toCategoryTreeNode(ResultSet resultSet, int rowNumber)
    throws SQLException {
    return new CatalogueCategoryTreeNode(
      new CatalogueCategory(
        resultSet.getLong("id"),
        resultSet.getObject("parent_id", Long.class),
        resultSet.getObject("sort_rank", Long.class),
        resultSet.getString("name"),
        resultSet.getString("slug"),
        resultSet.getBoolean("is_active"),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
      ),
      resultSet.getInt("depth"),
      resultSet.getBoolean("has_children")
    );
  }

  private static CatalogueCategoryBreadcrumbItem toCategoryBreadcrumbItem(
    ResultSet resultSet,
    int rowNumber
  ) throws SQLException {
    return new CatalogueCategoryBreadcrumbItem(
      new CatalogueCategory(
        resultSet.getLong("id"),
        resultSet.getObject("parent_id", Long.class),
        resultSet.getObject("sort_rank", Long.class),
        resultSet.getString("name"),
        resultSet.getString("slug"),
        resultSet.getBoolean("is_active"),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
      ),
      resultSet.getInt("depth")
    );
  }

  private List<ParentOptionCandidate> findIncludedParentOptionCandidates(
    CatalogueCategoryParentOptionLookup lookup
  ) {
    if (lookup.includeIds().isEmpty()) {
      return List.of();
    }
    Map<String, Object> params = new HashMap<>();
    params.put("includeIds", lookup.includeIds());
    if (lookup.excludeSubtreeId() != null) {
      params.put("excludeSubtreeId", lookup.excludeSubtreeId());
    }
    List<ParentOptionCandidate> rows = jdbc.query(
      includedParentOptionsSql(lookup),
      params,
      CatalogueCategoryRepositoryAdapter::toParentOptionCandidate
    );
    Map<Long, ParentOptionCandidate> byId = rows
      .stream()
      .collect(Collectors.toMap(candidate -> candidate.category().id(), candidate -> candidate));
    return lookup.includeIds().stream().map(byId::get).filter(Objects::nonNull).toList();
  }

  private List<ParentOptionCandidate> findOrdinaryParentOptionCandidates(
    CatalogueCategoryParentOptionLookup lookup,
    List<Long> excludedIds,
    int fetchLimit
  ) {
    String sql = ordinaryParentOptionsSql(lookup, excludedIds);
    Map<String, Object> params = ordinaryParentOptionsParams(lookup, excludedIds, fetchLimit);
    return jdbc.query(sql, params, CatalogueCategoryRepositoryAdapter::toParentOptionCandidate);
  }

  private static String ordinaryParentOptionsSql(
    CatalogueCategoryParentOptionLookup lookup,
    List<Long> excludedIds
  ) {
    StringBuilder sql = new StringBuilder(
      """
      WITH ordinary AS (
        SELECT
          category.id,
          category.parent_id,
          category.sort_rank,
          category.name,
          category.slug,
          category.is_active,
          category.created_at,
          category.updated_at,
          lower(category.name) AS normalized_name,
          %s AS search_rank
        FROM catalogue_category category
        WHERE (:includeInactive = true OR category.is_active = true)
      """.formatted(searchRankExpression(lookup))
    );
    appendExcludeSubtreePredicate(sql, lookup);
    if (!excludedIds.isEmpty()) {
      sql.append("    AND category.id NOT IN (:excludedIds)\n");
    }
    if (lookup.q() != null) {
      sql.append("    AND (\n");
      if (lookup.numericQuery() != null) {
        sql.append("      category.id = :numericQuery\n      OR ");
      } else {
        sql.append("      ");
      }
      sql.append(
        """
        lower(category.name) LIKE :containsPattern ESCAPE '\\'
          OR lower(category.slug) LIKE :containsPattern ESCAPE '\\'
        )
        """
      );
    }
    sql.append("  )\nSELECT *\nFROM ordinary\nWHERE 1 = 1\n");
    if (lookup.cursor() != null) {
      sql.append(cursorPredicate(lookup));
    }
    sql.append(orderBy(lookup));
    sql.append("LIMIT :limit\n");
    return sql.toString();
  }

  private static String searchRankExpression(CatalogueCategoryParentOptionLookup lookup) {
    if (lookup.q() == null) {
      return "0";
    }
    StringBuilder expression = new StringBuilder(
      """
      CASE
      """
    );
    if (lookup.numericQuery() != null) {
      expression.append("  WHEN category.id = :numericQuery THEN 0\n");
    }
    expression.append(
      """
          WHEN lower(category.slug) = :normalizedQ THEN 1
          WHEN lower(category.slug) LIKE :prefixPattern ESCAPE '\\' THEN 2
          WHEN lower(category.name) LIKE :prefixPattern ESCAPE '\\' THEN 3
          ELSE 4
      END
      """
    );
    return expression.toString();
  }

  private static String includedParentOptionsSql(CatalogueCategoryParentOptionLookup lookup) {
    StringBuilder sql = new StringBuilder(INCLUDED_PARENT_OPTIONS_SQL);
    appendExcludeSubtreePredicate(sql, lookup);
    return sql.toString();
  }

  private static void appendExcludeSubtreePredicate(
    StringBuilder sql,
    CatalogueCategoryParentOptionLookup lookup
  ) {
    if (lookup.excludeSubtreeId() == null) {
      return;
    }
    sql.append(
      """
          AND NOT EXISTS (
            SELECT 1
            FROM catalogue_category_closure excluded_path
            WHERE excluded_path.ancestor_id = :excludeSubtreeId
              AND excluded_path.descendant_id = category.id
          )
      """
    );
  }

  private static String cursorPredicate(CatalogueCategoryParentOptionLookup lookup) {
    if (lookup.q() == null) {
      return """
        AND (
          (:cursorActive = true AND is_active = false)
          OR (
            is_active = :cursorActive
            AND (
              normalized_name > :cursorName
              OR (normalized_name = :cursorName AND id > :cursorId)
            )
          )
        )
      """;
    }
    return """
    AND (
      search_rank > :cursorRank
      OR (
        search_rank = :cursorRank
        AND (
          (:cursorActive = true AND is_active = false)
          OR (
            is_active = :cursorActive
            AND (
              normalized_name > :cursorName
              OR (normalized_name = :cursorName AND id > :cursorId)
            )
          )
        )
      )
    )
    """;
  }

  private static String orderBy(CatalogueCategoryParentOptionLookup lookup) {
    if (lookup.q() == null) {
      return """
        ORDER BY is_active DESC, normalized_name ASC, id ASC
      """;
    }
    return """
    ORDER BY search_rank ASC, is_active DESC, normalized_name ASC, id ASC
    """;
  }

  private static Map<String, Object> ordinaryParentOptionsParams(
    CatalogueCategoryParentOptionLookup lookup,
    List<Long> excludedIds,
    int fetchLimit
  ) {
    Map<String, Object> params = new HashMap<>();
    params.put("includeInactive", lookup.includeInactive());
    params.put("limit", fetchLimit);
    if (lookup.excludeSubtreeId() != null) {
      params.put("excludeSubtreeId", lookup.excludeSubtreeId());
    }
    if (!excludedIds.isEmpty()) {
      params.put("excludedIds", excludedIds);
    }
    if (lookup.q() != null) {
      params.put("normalizedQ", lookup.q().toLowerCase(Locale.ROOT));
      params.put("containsPattern", containsPattern(lookup.q()));
      params.put("prefixPattern", prefixPattern(lookup.q()));
      if (lookup.numericQuery() != null) {
        params.put("numericQuery", lookup.numericQuery());
      }
    }
    if (lookup.cursor() != null) {
      params.put("cursorRank", lookup.cursor().rank());
      params.put("cursorActive", lookup.cursor().active());
      params.put("cursorName", lookup.cursor().name());
      params.put("cursorId", lookup.cursor().id());
    }
    return params;
  }

  private Map<Long, List<CatalogueCategoryBreadcrumbItem>> findParentOptionPaths(
    List<Long> categoryIds
  ) {
    if (categoryIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<CatalogueCategoryBreadcrumbItem>> paths = new LinkedHashMap<>();
    jdbc.query(PARENT_OPTION_PATH_SQL, Map.of("categoryIds", categoryIds), resultSet -> {
      Long descendantId = resultSet.getLong("descendant_id");
      paths
        .computeIfAbsent(descendantId, ignored -> new ArrayList<>())
        .add(toCategoryBreadcrumbItem(resultSet, 0));
    });
    return paths;
  }

  private static CatalogueCategoryParentOption toParentOption(
    ParentOptionCandidate candidate,
    Map<Long, List<CatalogueCategoryBreadcrumbItem>> pathsByCategoryId
  ) {
    List<CatalogueCategoryBreadcrumbItem> path = pathsByCategoryId.getOrDefault(
      candidate.category().id(),
      List.of()
    );
    int depth = path.isEmpty() ? 0 : path.getLast().depth();
    return CatalogueCategoryParentOption.from(candidate.category(), depth, path);
  }

  private static String toNextCursor(
    CatalogueCategoryParentOptionLookup lookup,
    ParentOptionCandidate candidate
  ) {
    return AdminCatalogueCategoryParentOptionsCursor.encode(
      lookup.mode(),
      lookup.q(),
      lookup.includeInactive(),
      lookup.excludeSubtreeId(),
      lookup.q() == null ? null : candidate.searchRank(),
      candidate.category().active(),
      candidate.category().name().toLowerCase(Locale.ROOT),
      candidate.category().id()
    );
  }

  private static ParentOptionCandidate toParentOptionCandidate(ResultSet resultSet, int rowNumber)
    throws SQLException {
    return new ParentOptionCandidate(
      new CatalogueCategory(
        resultSet.getLong("id"),
        resultSet.getObject("parent_id", Long.class),
        resultSet.getObject("sort_rank", Long.class),
        resultSet.getString("name"),
        resultSet.getString("slug"),
        resultSet.getBoolean("is_active"),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
      ),
      resultSet.getInt("search_rank")
    );
  }

  private record ParentOptionCandidate(CatalogueCategory category, int searchRank) {}

  private static String treeSql(SortCriteria sort) {
    if (usesDefaultRankedTreeOrder(sort)) {
      return DEFAULT_RANKED_TREE_SQL;
    }
    return GENERIC_SORTABLE_TREE_SQL_TEMPLATE.formatted(toTreeSiblingOrderBy(sort));
  }

  private static boolean usesDefaultRankedTreeOrder(SortCriteria sort) {
    List<SortOrder> orders = sort.orders();
    if (orders.size() == 1) {
      return isAscendingOrder(orders.get(0), "sortRank");
    }
    return (
      orders.size() == 2 &&
      isAscendingOrder(orders.get(0), "sortRank") &&
      isAscendingOrder(orders.get(1), "id")
    );
  }

  private static boolean isAscendingOrder(SortOrder order, String property) {
    return property.equals(order.property()) && order.direction() == SortDirection.ASC;
  }

  private static String toTreeSiblingOrderBy(SortCriteria sort) {
    List<String> orderClauses = new java.util.ArrayList<>(
      sort
        .orders()
        .stream()
        .map(order -> {
          String property = TREE_SORT_PROPERTIES.get(order.property());
          if (property == null) {
            throw new IllegalArgumentException(
              "Unsupported catalogue category tree sort property: " + order.property()
            );
          }
          return property + " " + order.direction().name();
        })
        .toList()
    );

    if (
      sort
        .orders()
        .stream()
        .noneMatch(order -> "id".equals(order.property()))
    ) {
      orderClauses.add(TREE_SORT_PROPERTIES.get("id") + " ASC");
    }
    return String.join(", ", orderClauses);
  }

  private long allocateRank(Long parentId, SparseOrderTarget orderTarget) {
    try {
      return rankOperations.allocateRank(categorySystem, parentId, orderTarget);
    } catch (RankRebalanceLimitExceededException exception) {
      throw CatalogueCategoryRankingException.rebalanceLimitExceeded(
        exception.affectedRows(),
        exception.maxRows()
      );
    } catch (IllegalArgumentException exception) {
      throw placementTargetExceptionOrRethrow(exception);
    }
  }

  private RankAssignmentResult assignRank(
    Long categoryId,
    Long parentId,
    SparseOrderTarget orderTarget
  ) {
    try {
      return rankOperations.assignRank(categorySystem, categoryId, parentId, orderTarget);
    } catch (RankRebalanceLimitExceededException exception) {
      throw CatalogueCategoryRankingException.rebalanceLimitExceeded(
        exception.affectedRows(),
        exception.maxRows()
      );
    } catch (IllegalArgumentException exception) {
      throw placementTargetExceptionOrRethrow(exception);
    }
  }

  private static RuntimeException placementTargetExceptionOrRethrow(
    IllegalArgumentException exception
  ) {
    String message = exception.getMessage();
    if (
      message != null && (message.contains("Placement target") || message.contains("targetIndex"))
    ) {
      return CatalogueCategoryPlacementTargetException.invalidTarget();
    }
    return exception;
  }

  private boolean rankingEnabled() {
    return categorySystem.ranking().rankingEnabled();
  }

  private void validatePlacement(SparseOrderTarget orderTarget) {
    if (rankingEnabled() || !orderTarget.hasExplicitPlacement()) {
      return;
    }
    throw switch (orderTarget.mode()) {
      case BEFORE_ID -> rankingDisabled("before-id", "beforeId is not supported");
      case AFTER_ID -> rankingDisabled("after-id", "afterId is not supported");
      case TARGET_INDEX -> rankingDisabled("target-index", "targetIndex is not supported");
      case APPEND -> new IllegalStateException("append placement cannot be explicit");
    };
  }

  private static InvalidRequestException rankingDisabled(String fieldKey, String defaultMessage) {
    return new InvalidRequestException(
      "catalogue.api.category.ranking.disabled." + fieldKey,
      defaultMessage + " when ranking is disabled"
    );
  }
}
