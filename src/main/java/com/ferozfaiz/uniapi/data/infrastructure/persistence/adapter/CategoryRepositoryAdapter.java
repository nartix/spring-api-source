package com.ferozfaiz.uniapi.data.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyCycleException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyPolicyViolationException;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryDeleteEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.data.domain.repository.CategoryRepository;
import com.ferozfaiz.uniapi.data.infrastructure.persistence.entity.CategoryEntity;
import com.ferozfaiz.uniapi.data.infrastructure.persistence.mapper.DataPersistenceMapper;
import com.ferozfaiz.uniapi.data.infrastructure.persistence.repository.CategoryJpaRepository;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DeleteSubtreeEstimate;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.HierarchyCycleException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.HierarchyMoveMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.MoveSubtreeEstimate;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankAssignmentResult;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankRebalanceLimitExceededException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CategoryRepositoryAdapter implements CategoryRepository {

  private static final ClosureTableDescriptor CATEGORY_HIERARCHY = ClosureTableDescriptor.of(
    "data_category",
    "data_category_closure"
  );
  private static final String CATEGORY_SYSTEM = "data";
  private static final Map<String, String> SORT_PROPERTIES = Map.of(
    "id",
    "category_depth.id",
    "parentId",
    "category_depth.parent_id",
    "depth",
    "category_depth.depth",
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

  private final CategoryJpaRepository categories;
  private final ClosureTableOperations closureTables;
  private final NamedParameterJdbcTemplate jdbc;
  private final CategorySystemDescriptor categorySystem;
  private final SparseRankOperations rankOperations;

  public CategoryRepositoryAdapter(
    CategoryJpaRepository categories,
    ClosureTableOperations closureTables,
    NamedParameterJdbcTemplate jdbc,
    CategorySystemRegistry categorySystems,
    SparseRankOperations rankOperations
  ) {
    this.categories = categories;
    this.closureTables = closureTables;
    this.jdbc = jdbc;
    this.categorySystem = categorySystems.requireDescriptor(CATEGORY_SYSTEM);
    this.rankOperations = rankOperations;
  }

  @Override
  @Transactional
  public Category save(Category category) {
    return toDomain(categories.save(DataPersistenceMapper.toEntity(category)));
  }

  @Override
  @Transactional
  public Category saveRoot(Category category, SparseOrderTarget orderTarget) {
    long sortRank = allocateRank(null, orderTarget);
    Category saved = saveAndFlush(
      new Category(
        category.id(),
        null,
        sortRank,
        category.slug(),
        category.name(),
        category.description(),
        category.active(),
        category.createdAt(),
        category.updatedAt(),
        category.createdBy(),
        category.updatedBy()
      )
    );
    closureTables.createRoot(CATEGORY_HIERARCHY, Objects.requireNonNull(saved.id()));
    return saved;
  }

  @Override
  @Transactional
  public Category saveChild(Category category, Long parentId, SparseOrderTarget orderTarget) {
    long sortRank = allocateRank(parentId, orderTarget);
    Category saved = saveAndFlush(
      new Category(
        category.id(),
        parentId,
        sortRank,
        category.slug(),
        category.name(),
        category.description(),
        category.active(),
        category.createdAt(),
        category.updatedAt(),
        category.createdBy(),
        category.updatedBy()
      )
    );
    closureTables.createChild(CATEGORY_HIERARCHY, Objects.requireNonNull(saved.id()), parentId);
    return saved;
  }

  @Override
  public Optional<Category> findById(Long id) {
    return Objects.requireNonNull(categories.findById(id).map(DataPersistenceMapper::toDomain));
  }

  @Override
  public Optional<CategoryNode> findNodeById(Long id) {
    List<CategoryNode> nodes = jdbc.query(
      """
      WITH category_depth AS (
        SELECT
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by,
          COALESCE(MAX(path.depth), 0) AS depth
        FROM data_category category
        JOIN data_category_closure path ON path.descendant_id = category.id
        WHERE category.id = :id
        GROUP BY
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by
      )
      SELECT
        id,
        parent_id,
        slug,
        name,
        sort_rank,
        description,
        is_active,
        created_at,
        updated_at,
        created_by,
        updated_by,
        depth
      FROM category_depth
      """,
      Map.of("id", id),
      (resultSet, rowNumber) -> toCategoryNode(resultSet)
    );
    return nodes.stream().findFirst();
  }

  @Override
  public List<Category> findRoots() {
    return categories
      .findByParentIdIsNullOrderBySortRankAscIdAsc()
      .stream()
      .map(DataPersistenceMapper::toDomain)
      .toList();
  }

  @Override
  public List<Category> findChildren(Long parentId) {
    return categories
      .findByParentIdOrderBySortRankAscIdAsc(parentId)
      .stream()
      .map(DataPersistenceMapper::toDomain)
      .toList();
  }

  @Override
  public List<Category> findAncestors(Long id) {
    return findAllByOrderedIds(closureTables.findAncestorIds(CATEGORY_HIERARCHY, id));
  }

  @Override
  public List<Category> findDescendants(Long id) {
    return findAllByOrderedIds(closureTables.findDescendantIds(CATEGORY_HIERARCHY, id));
  }

  @Override
  public List<Category> findAllForTree() {
    return categories
      .findAllByOrderByParentIdAscSortRankAscIdAsc()
      .stream()
      .map(DataPersistenceMapper::toDomain)
      .toList();
  }

  @Override
  public List<Category> findSelfAndDescendants(Long id) {
    List<Long> ids = new java.util.ArrayList<>();
    ids.add(id);
    ids.addAll(closureTables.findDescendantIds(CATEGORY_HIERARCHY, id));
    return findAllByOrderedIds(ids);
  }

  @Override
  public PageResult<CategoryNode> findAll(PageCriteria page, SortCriteria sort) {
    return isTreeSort(sort) ? findAllByTreeOrder(page, sort) : findAllByFlatOrder(page, sort);
  }

  private PageResult<CategoryNode> findAllByFlatOrder(PageCriteria page, SortCriteria sort) {
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("limit", page.size())
      .addValue("offset", (long) page.page() * page.size());

    List<CategoryNode> content = jdbc.query(
      """
      WITH category_depth AS (
        SELECT
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by,
          COALESCE(MAX(path.depth), 0) AS depth
        FROM data_category category
        JOIN data_category_closure path ON path.descendant_id = category.id
        GROUP BY
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by
      )
      SELECT
        id,
        parent_id,
        slug,
        name,
        sort_rank,
        description,
        is_active,
        created_at,
        updated_at,
        created_by,
        updated_by,
        depth
      FROM category_depth
      ORDER BY %s
      LIMIT :limit OFFSET :offset
      """.formatted(toOrderBy(sort)),
      parameters,
      (resultSet, rowNumber) -> toCategoryNode(resultSet)
    );

    return toPageResult(content, page, sort);
  }

  private PageResult<CategoryNode> findAllByTreeOrder(PageCriteria page, SortCriteria sort) {
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("limit", page.size())
      .addValue("offset", (long) page.page() * page.size());

    List<CategoryNode> content = jdbc.query(
      """
      WITH RECURSIVE category_depth AS (
        SELECT
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by,
          COALESCE(MAX(path.depth), 0) AS depth
        FROM data_category category
        JOIN data_category_closure path ON path.descendant_id = category.id
        GROUP BY
          category.id,
          category.parent_id,
          category.slug,
          category.name,
          category.sort_rank,
          category.description,
          category.is_active,
          category.created_at,
          category.updated_at,
          category.created_by,
          category.updated_by
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
          ARRAY[ranked_categories.sibling_rank] AS sort_path
        FROM ranked_categories
        WHERE ranked_categories.parent_id IS NULL
        UNION ALL
        SELECT
          child.*,
          parent.sort_path || child.sibling_rank
        FROM ranked_categories child
        JOIN ordered_tree parent ON child.parent_id = parent.id
      )
      SELECT
        id,
        parent_id,
        slug,
        name,
        sort_rank,
        description,
        is_active,
        created_at,
        updated_at,
        created_by,
        updated_by,
        depth
      FROM ordered_tree
      ORDER BY sort_path ASC
      LIMIT :limit OFFSET :offset
      """.formatted(toTreeSiblingOrderBy(sort)),
      parameters,
      (resultSet, rowNumber) -> toCategoryNode(resultSet)
    );

    return toPageResult(content, page, sort);
  }

  private PageResult<CategoryNode> toPageResult(
    List<CategoryNode> content,
    PageCriteria page,
    SortCriteria sort
  ) {
    long totalElements = count();
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / page.size());

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

  private static CategoryNode toCategoryNode(java.sql.ResultSet resultSet)
    throws java.sql.SQLException {
    return new CategoryNode(
      new Category(
        resultSet.getLong("id"),
        resultSet.getObject("parent_id", Long.class),
        resultSet.getLong("sort_rank"),
        resultSet.getString("slug"),
        resultSet.getString("name"),
        resultSet.getString("description"),
        resultSet.getBoolean("is_active"),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
        resultSet.getString("created_by"),
        resultSet.getString("updated_by")
      ),
      resultSet.getInt("depth")
    );
  }

  @Override
  public boolean existsByParentIdAndSlug(@Nullable Long parentId, String slug) {
    return parentId == null
      ? categories.existsByParentIdIsNullAndSlug(slug)
      : categories.existsByParentIdAndSlug(parentId, slug);
  }

  @Override
  public boolean existsByParentIdAndSlugAndIdNot(@Nullable Long parentId, String slug, Long id) {
    return parentId == null
      ? categories.existsByParentIdIsNullAndSlugAndIdNot(slug, id)
      : categories.existsByParentIdAndSlugAndIdNot(parentId, slug, id);
  }

  @Override
  public long count() {
    return Objects.requireNonNullElse(
      jdbc.queryForObject("SELECT COUNT(*) FROM data_category", Map.of(), Long.class),
      0L
    );
  }

  @Override
  public long countSelfAndDescendants(Long id) {
    return closureTables.countSelfAndDescendants(CATEGORY_HIERARCHY, id);
  }

  @Override
  public CategoryMoveEstimate estimateMove(Long id, @Nullable Long parentId) {
    return toCategoryMoveEstimate(closureTables.estimateMove(CATEGORY_HIERARCHY, id, parentId));
  }

  @Override
  public CategoryDeleteEstimate estimateDelete(Long id) {
    return toCategoryDeleteEstimate(closureTables.estimateDelete(CATEGORY_HIERARCHY, id));
  }

  @Override
  public boolean hasChildren(Long id) {
    return categories.existsByParentId(id);
  }

  @Override
  @Transactional
  public void move(
    Long id,
    @Nullable Long parentId,
    CategoryMoveMode mode,
    Duration statementTimeout,
    SparseOrderTarget orderTarget
  ) {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(statementTimeout, "statementTimeout");
    Objects.requireNonNull(orderTarget, "orderTarget");

    try {
      closureTables.setLocalStatementTimeout(statementTimeout);
      closureTables.acquireHierarchyMoveLock(CATEGORY_HIERARCHY);
      closureTables.lockMoveEndpointsSafely(CATEGORY_HIERARCHY, id, parentId);
      boolean directParentAlreadySet = closureTables.hasDirectParent(
        CATEGORY_HIERARCHY,
        id,
        parentId
      );
      if (directParentAlreadySet && !orderTarget.hasExplicitPlacement()) {
        return;
      }
      if (!directParentAlreadySet) {
        closureTables.moveSubtree(CATEGORY_HIERARCHY, id, parentId, toHierarchyMoveMode(mode));
      }
      RankAssignmentResult rank = assignRank(id, parentId, orderTarget);
      categories.findById(id).ifPresent(category -> {
        category.setParentId(parentId);
        category.setSortRank(rank.assignedRank());
      });
    } catch (RuntimeException exception) {
      if (!causedByHierarchyCycle(exception)) {
        throw exception;
      }
      throw new CategoryHierarchyCycleException();
    }
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    categories.deleteById(id);
  }

  private static CategoryMoveEstimate toCategoryMoveEstimate(MoveSubtreeEstimate estimate) {
    return new CategoryMoveEstimate(
      estimate.subtreeSize(),
      estimate.oldAncestorCount(),
      estimate.newAncestorCount(),
      estimate.estimatedDeleteRows(),
      estimate.estimatedInsertRows(),
      estimate.estimatedTotalRows(),
      estimate.subtreeMaxDepth(),
      estimate.newParentDepth(),
      estimate.resultingMaxDepth(),
      estimate.parentChangeRequired()
    );
  }

  private static CategoryDeleteEstimate toCategoryDeleteEstimate(DeleteSubtreeEstimate estimate) {
    return new CategoryDeleteEstimate(estimate.subtreeSize(), estimate.estimatedClosureRows());
  }

  private static HierarchyMoveMode toHierarchyMoveMode(CategoryMoveMode mode) {
    return switch (mode) {
      case LEAF_ONLY -> HierarchyMoveMode.LEAF_ONLY;
      case SUBTREE -> HierarchyMoveMode.SUBTREE;
    };
  }

  private Category saveAndFlush(Category category) {
    return toDomain(categories.saveAndFlush(DataPersistenceMapper.toEntity(category)));
  }

  private long allocateRank(@Nullable Long parentId, SparseOrderTarget orderTarget) {
    try {
      return rankOperations.allocateRank(categorySystem, parentId, orderTarget);
    } catch (RankRebalanceLimitExceededException exception) {
      throw CategoryHierarchyPolicyViolationException.rankRebalanceLimitExceeded(
        exception.affectedRows(),
        exception.maxRows()
      );
    }
  }

  private RankAssignmentResult assignRank(
    Long categoryId,
    @Nullable Long parentId,
    SparseOrderTarget orderTarget
  ) {
    try {
      return rankOperations.assignRank(categorySystem, categoryId, parentId, orderTarget);
    } catch (RankRebalanceLimitExceededException exception) {
      throw CategoryHierarchyPolicyViolationException.rankRebalanceLimitExceeded(
        exception.affectedRows(),
        exception.maxRows()
      );
    }
  }

  private static Category toDomain(CategoryEntity entity) {
    return Objects.requireNonNull(DataPersistenceMapper.toDomain(Objects.requireNonNull(entity)));
  }

  private static boolean causedByHierarchyCycle(Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof HierarchyCycleException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String toOrderBy(SortCriteria sort) {
    List<String> orderClauses = new java.util.ArrayList<>(
      sort
        .orders()
        .stream()
        .map(order -> {
          String property = SORT_PROPERTIES.get(order.property());
          if (property == null) {
            throw new IllegalArgumentException(
              "Unsupported category sort property: " + order.property()
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
      orderClauses.add(SORT_PROPERTIES.get("id") + " ASC");
    }
    return String.join(", ", orderClauses);
  }

  private static boolean isTreeSort(SortCriteria sort) {
    return !sort.orders().isEmpty() && "tree".equals(sort.orders().getFirst().property());
  }

  private static String toTreeSiblingOrderBy(SortCriteria sort) {
    List<String> orderClauses = new java.util.ArrayList<>(
      sort
        .orders()
        .stream()
        .skip(1)
        .map(order -> {
          String property = SORT_PROPERTIES.get(order.property());
          if (property == null) {
            throw new IllegalArgumentException(
              "Unsupported category tree sibling sort property: " + order.property()
            );
          }
          return property + " " + order.direction().name();
        })
        .toList()
    );

    if (orderClauses.isEmpty()) {
      orderClauses.add(SORT_PROPERTIES.get("sortRank") + " ASC");
    }
    if (
      sort
        .orders()
        .stream()
        .skip(1)
        .noneMatch(order -> "id".equals(order.property()))
    ) {
      orderClauses.add(SORT_PROPERTIES.get("id") + " ASC");
    }
    return String.join(", ", orderClauses);
  }

  private List<Category> findAllByOrderedIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }

    Map<Long, Integer> positions = ids
      .stream()
      .collect(Collectors.toMap(Function.identity(), ids::indexOf));

    return categories
      .findAllById(ids)
      .stream()
      .map(DataPersistenceMapper::toDomain)
      .sorted(
        Comparator.comparingInt(category ->
          positions.getOrDefault(category.id(), Integer.MAX_VALUE)
        )
      )
      .toList();
  }
}
