package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.BreadcrumbItem;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.ParentOptionSlice;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.HierarchyCycleException;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankAssignmentResult;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

public class DescriptorDrivenAdminCategoryJdbcProvider
  implements
    AdminCategoryReadPort,
    AdminCategoryHierarchyReadPort,
    AdminCategoryBasicMutationPort,
    AdminCategoryHierarchyMutationPort,
    AdminCategoryOrderingPort,
    AdminCategoryLifecyclePort
{

  private static final Logger log = LoggerFactory.getLogger(
    DescriptorDrivenAdminCategoryJdbcProvider.class
  );

  private final String systemKey;
  private final CategorySystemDescriptor categorySystem;
  private final ClosureTableDescriptor closure;
  private final AdminCategoryNodeTableDescriptor node;
  private final NamedParameterJdbcTemplate jdbc;
  private final AdminCategoryJdbcSql sql;
  private final ClosureTableOperations closureTables;
  private final SparseRankOperations rankOperations;
  private final TransactionTemplate transactions;
  private final AdminCategoryParentOptionsCursorCodec cursorCodec;

  public DescriptorDrivenAdminCategoryJdbcProvider(
    String systemKey,
    CategorySystemDescriptor categorySystem,
    AdminCategoryNodeTableDescriptor node,
    NamedParameterJdbcTemplate jdbc,
    ClosureTableOperations closureTables,
    SparseRankOperations rankOperations,
    TransactionTemplate transactions,
    AdminCategoryParentOptionsCursorCodec cursorCodec
  ) {
    this.systemKey = Objects.requireNonNull(systemKey, "systemKey");
    this.categorySystem = Objects.requireNonNull(categorySystem, "categorySystem");
    this.closure = categorySystem.closureTableDescriptor();
    this.node = Objects.requireNonNull(node, "node");
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.sql = new AdminCategoryJdbcSql(node, categorySystem);
    this.closureTables = Objects.requireNonNull(closureTables, "closureTables");
    this.rankOperations = Objects.requireNonNull(rankOperations, "rankOperations");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.cursorCodec = Objects.requireNonNull(cursorCodec, "cursorCodec");
  }

  @Override
  public PageResult<CategoryNode> list(AdminCategoryListQuery query) {
    return read("list", null, () -> {
      if (query.parentScope().parentId() != null && !exists(query.parentScope().parentId())) {
        throw AdminCategoryErrors.parentNotFound(query.parentScope().parentId());
      }
      long total = count(sql.count(query));
      List<CategoryNode> content = query(
        sql.list(query),
        AdminCategoryJdbcRowMapper.categoryNode()
      );
      return page(content, query.page().page(), query.page().size(), total, query.sort());
    });
  }

  @Override
  public PageResult<CategoryNode> tree(AdminCategoryTreeQuery query) {
    return read("tree", null, () -> {
      long total = count(sql.countAll());
      List<CategoryNode> content = query(
        sql.tree(query),
        AdminCategoryJdbcRowMapper.categoryNode()
      );
      return page(content, query.page().page(), query.page().size(), total, query.sort());
    });
  }

  @Override
  public Category get(Long id) {
    return read("get", id, () -> getCategory(id));
  }

  private Category getCategory(Long id) {
    try {
      return queryForObject(sql.category(id), AdminCategoryJdbcRowMapper.category());
    } catch (EmptyResultDataAccessException exception) {
      throw AdminCategoryErrors.categoryNotFound(id);
    }
  }

  @Override
  public CategoryNode getNode(Long id) {
    return read("getNode", id, () -> getCategoryNode(id));
  }

  private CategoryNode getCategoryNode(Long id) {
    try {
      return queryForObject(sql.categoryNode(id), AdminCategoryJdbcRowMapper.categoryNode());
    } catch (EmptyResultDataAccessException exception) {
      throw AdminCategoryErrors.categoryNotFound(id);
    }
  }

  @Override
  public List<BreadcrumbItem> breadcrumb(Long id) {
    return read("breadcrumb", id, () -> breadcrumbItems(id));
  }

  private List<BreadcrumbItem> breadcrumbItems(Long id) {
    List<BreadcrumbItem> breadcrumb = query(
      sql.breadcrumb(id),
      AdminCategoryJdbcRowMapper.breadcrumbItem()
    );
    if (breadcrumb.isEmpty()) {
      throw AdminCategoryErrors.categoryNotFound(id);
    }
    return breadcrumb;
  }

  @Override
  public ParentOptionSlice parentOptions(AdminCategoryParentOptionsQuery query) {
    return read("parentOptions", null, () -> {
      AdminCategoryParentOptionsCursorCodec.RequestContext context =
        new AdminCategoryParentOptionsCursorCodec.RequestContext(
          query.q(),
          query.includeInactive(),
          query.excludeSubtreeId(),
          query.includeIds()
        );
      AdminCategoryParentOptionsCursorCodec.Position cursorPosition =
        query.cursor() == null || query.cursor().isBlank()
          ? null
          : cursorCodec.decode(systemKey, context, query.cursor());
      List<AdminCategoryJdbcRowMapper.ParentOptionRow> includedRows = includedParentOptions(query);
      List<AdminCategoryJdbcRowMapper.ParentOptionRow> returnedIncludedRows =
        includedRows.size() > query.limit() ? includedRows.subList(0, query.limit()) : includedRows;
      int ordinaryLimit = query.limit() - returnedIncludedRows.size();
      List<AdminCategoryJdbcRowMapper.ParentOptionRow> ordinaryRows =
        ordinaryLimit <= 0
          ? List.of()
          : query(
              sql.parentOptions(query, cursorPosition, ordinaryLimit + 1),
              AdminCategoryJdbcRowMapper.parentOptionRow()
            );
      boolean hasMore = ordinaryRows.size() > ordinaryLimit;
      List<AdminCategoryJdbcRowMapper.ParentOptionRow> returnedOrdinaryRows = hasMore
        ? ordinaryRows.subList(0, ordinaryLimit)
        : ordinaryRows;
      List<AdminCategoryJdbcRowMapper.ParentOptionRow> pageRows = new ArrayList<>(
        returnedIncludedRows.size() + returnedOrdinaryRows.size()
      );
      pageRows.addAll(returnedIncludedRows);
      pageRows.addAll(returnedOrdinaryRows);
      String nextCursor = hasMore
        ? encodeNextCursor(context, returnedOrdinaryRows.getLast())
        : null;
      return new ParentOptionSlice(toParentOptions(pageRows), hasMore, nextCursor);
    });
  }

  private List<AdminCategoryJdbcRowMapper.ParentOptionRow> includedParentOptions(
    AdminCategoryParentOptionsQuery query
  ) {
    if (query.includeIds().isEmpty()) {
      return List.of();
    }
    List<AdminCategoryJdbcRowMapper.ParentOptionRow> rows = query(
      sql.parentOptionsByIds(query.includeIds(), query.excludeSubtreeId()),
      AdminCategoryJdbcRowMapper.parentOptionRow()
    );
    Map<Long, AdminCategoryJdbcRowMapper.ParentOptionRow> byId = new LinkedHashMap<>();
    for (AdminCategoryJdbcRowMapper.ParentOptionRow row : rows) {
      byId.put(row.option().category().id(), row);
    }
    List<AdminCategoryJdbcRowMapper.ParentOptionRow> ordered = new ArrayList<>();
    for (Long includeId : query.includeIds()) {
      AdminCategoryJdbcRowMapper.ParentOptionRow row = byId.get(includeId);
      if (row != null) {
        ordered.add(row);
      }
    }
    return ordered;
  }

  private List<AdminCategoryModels.ParentOption> toParentOptions(
    List<AdminCategoryJdbcRowMapper.ParentOptionRow> rows
  ) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<Long> parentOptionIds = rows
      .stream()
      .map(row -> row.option().category().id())
      .distinct()
      .toList();
    List<AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow> breadcrumbRows = query(
      sql.parentOptionBreadcrumbsByIds(parentOptionIds),
      AdminCategoryJdbcRowMapper.parentOptionBreadcrumbRow()
    );
    Map<Long, List<BreadcrumbItem>> breadcrumbsByParentOptionId = new LinkedHashMap<>();
    for (AdminCategoryJdbcRowMapper.ParentOptionBreadcrumbRow breadcrumbRow : breadcrumbRows) {
      breadcrumbsByParentOptionId
        .computeIfAbsent(breadcrumbRow.parentOptionId(), ignored -> new ArrayList<>())
        .add(breadcrumbRow.breadcrumbItem());
    }
    return rows
      .stream()
      .map(AdminCategoryJdbcRowMapper.ParentOptionRow::option)
      .map(option -> {
        List<BreadcrumbItem> breadcrumbs = breadcrumbsByParentOptionId.get(option.category().id());
        if (breadcrumbs == null || breadcrumbs.isEmpty()) {
          throw AdminCategoryErrors.categoryNotFound(option.category().id());
        }
        return new AdminCategoryModels.ParentOption(
          option.category(),
          option.depth(),
          option.label(),
          List.copyOf(breadcrumbs),
          option.selectable(),
          option.disabledReason()
        );
      })
      .toList();
  }

  @Override
  public Category create(AdminCategoryCreateCommand command) {
    return mutation("create", null, () ->
      transactions.execute(status -> {
        validateParentExists(command.parentId());
        validatePlacement(command.orderTarget());
        rejectDuplicateSlug(command.slug(), null);
        Long sortRank = allocateRank(command.parentId(), command.orderTarget());
        Long id = insertNode(command, sortRank);
        if (command.parentId() == null) {
          closureTables.createRoot(closure, id);
        } else {
          closureTables.createChild(closure, id, command.parentId());
        }
        logWrite("create", id, "succeeded", null);
        return getCategory(id);
      })
    );
  }

  @Override
  public Category replace(Long id, AdminCategoryUpdateCommand command) {
    return mutation("update", id, () ->
      transactions.execute(status ->
        updateInternal(
          "update",
          id,
          command.parentId(),
          command.name(),
          command.slug(),
          command.active(),
          SparseOrderTarget.append()
        )
      )
    );
  }

  @Override
  public Category patch(Long id, AdminCategoryPatchCommand command) {
    return mutation("patch", id, () ->
      transactions.execute(status -> {
        Category existing = requireCategory(id);
        Long parentId = command.parentIdPresent() ? command.parentId() : existing.parentId();
        String name = command.namePresent() ? command.name() : existing.name();
        String slug = command.slugPresent() ? command.slug() : existing.slug();
        boolean active = command.activePresent()
          ? activeOrRejectNull(command.active())
          : existing.active();
        return updateInternal(
          "patch",
          id,
          parentId,
          name,
          slug,
          active,
          SparseOrderTarget.append()
        );
      })
    );
  }

  @Override
  public void move(Long id, AdminCategoryMoveCommand command) {
    mutationWithoutResult("move", id, () ->
      transactions.executeWithoutResult(status -> {
        requireCategory(id);
        moveInternal(id, command.newParentId(), command.orderTarget());
        logWrite("move", id, "succeeded", null);
      })
    );
  }

  @Override
  public Category reorder(Long id, AdminCategoryReorderCommand command) {
    return mutation("reorder", id, () ->
      transactions.execute(status -> {
        Category existing = requireCategory(id);
        Long parentId = command.parentId() == null ? existing.parentId() : command.parentId();
        moveInternal(id, parentId, command.orderTarget());
        logWrite("reorder", id, "succeeded", null);
        return getCategory(id);
      })
    );
  }

  @Override
  public void activate(Long id) {
    mutationWithoutResult("activate", id, () -> setActive(id, true));
  }

  @Override
  public void deactivate(Long id) {
    mutationWithoutResult("deactivate", id, () -> setActive(id, false));
  }

  @Override
  public void delete(Long id) {
    mutationWithoutResult("delete", id, () ->
      transactions.executeWithoutResult(status -> {
        requireCategory(id);
        if (hasChildren(id)) {
          throw AdminCategoryErrors.deleteBlocked(id);
        }
        closureTables.deleteSubtreePaths(closure, id);
        updateExpected(sql.deleteNode(id), id);
        logWrite("delete", id, "succeeded", null);
      })
    );
  }

  private Category updateInternal(
    String operation,
    Long id,
    Long parentId,
    String name,
    String slug,
    boolean active,
    SparseOrderTarget orderTarget
  ) {
    Category existing = requireCategory(id);
    validateParentMove(id, parentId);
    validatePlacement(orderTarget);
    rejectDuplicateSlug(slug, id);
    Long sortRank = existing.sortRank();
    boolean parentChanged = !Objects.equals(existing.parentId(), parentId);
    if (parentChanged) {
      moveHierarchy(id, parentId);
      sortRank = assignRank(id, parentId, orderTarget).assignedRank();
    }
    try {
      updateExpected(sql.updateNode(id, parentId, sortRank, name, slug, active), id);
    } catch (RuntimeException exception) {
      throw AdminCategoryJdbcExceptionTranslator.duplicateSlug(slug, exception);
    }
    logWrite(operation, id, "succeeded", null);
    return getCategory(id);
  }

  private void moveInternal(Long id, Long parentId, SparseOrderTarget orderTarget) {
    validateParentMove(id, parentId);
    validatePlacement(orderTarget);
    closureTables.acquireHierarchyMoveLock(closure);
    closureTables.lockMoveEndpointsSafely(closure, id, parentId);
    boolean directParentAlreadySet = closureTables.hasDirectParent(closure, id, parentId);
    if (!directParentAlreadySet) {
      moveHierarchy(id, parentId);
    }
    if (
      categorySystem.ranking().rankingEnabled() &&
      (!directParentAlreadySet || orderTarget.hasExplicitPlacement())
    ) {
      assignRank(id, parentId, orderTarget);
    }
    updateExpected(sql.touch(id), id);
  }

  private Long insertNode(AdminCategoryCreateCommand command, Long sortRank) {
    AdminCategoryJdbcSql.Statement statement = sql.insertNode(
      command.parentId(),
      sortRank,
      command.name(),
      command.slug(),
      command.active()
    );
    KeyHolder keyHolder = new GeneratedKeyHolder();
    try {
      jdbc.update(
        statement.sql(),
        new MapSqlParameterSource(statement.parameters()),
        keyHolder,
        new String[] { node.idColumn() }
      );
    } catch (RuntimeException exception) {
      throw AdminCategoryJdbcExceptionTranslator.duplicateSlug(command.slug(), exception);
    }
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Descriptor-backed category insert did not return an id");
    }
    return key.longValue();
  }

  private void moveHierarchy(Long id, Long parentId) {
    try {
      closureTables.moveSubtree(closure, id, parentId);
    } catch (HierarchyCycleException exception) {
      throw AdminCategoryJdbcExceptionTranslator.invalidMove(id, parentId, exception);
    }
  }

  private Long allocateRank(Long parentId, SparseOrderTarget orderTarget) {
    if (!categorySystem.ranking().rankingEnabled()) {
      return null;
    }
    try {
      return rankOperations.allocateRank(categorySystem, parentId, orderTarget);
    } catch (RuntimeException exception) {
      throw AdminCategoryJdbcExceptionTranslator.invalidPlacement(exception);
    }
  }

  private RankAssignmentResult assignRank(Long id, Long parentId, SparseOrderTarget orderTarget) {
    try {
      return rankOperations.assignRank(categorySystem, id, parentId, orderTarget);
    } catch (RuntimeException exception) {
      throw AdminCategoryJdbcExceptionTranslator.invalidPlacement(exception);
    }
  }

  private void setActive(Long id, boolean active) {
    transactions.executeWithoutResult(status -> {
      requireCategory(id);
      updateExpected(sql.updateActive(id, active), id);
      logWrite(active ? "activate" : "deactivate", id, "succeeded", null);
    });
  }

  private Category requireCategory(Long id) {
    return getCategory(id);
  }

  private void validateParentExists(Long parentId) {
    if (parentId != null && !exists(parentId)) {
      throw AdminCategoryErrors.parentNotFound(parentId);
    }
  }

  private void validateParentMove(Long id, Long parentId) {
    if (Objects.equals(id, parentId)) {
      throw AdminCategoryErrors.invalidMove(id, parentId);
    }
    validateParentExists(parentId);
    if (parentId != null && closureTables.isDescendantOf(closure, parentId, id)) {
      throw AdminCategoryErrors.invalidMove(id, parentId);
    }
  }

  private void validatePlacement(SparseOrderTarget orderTarget) {
    Objects.requireNonNull(orderTarget, "orderTarget");
    if (!categorySystem.ranking().placementModes().contains(orderTarget.mode())) {
      throw AdminCategoryErrors.invalidPlacementTarget();
    }
    if (!categorySystem.ranking().rankingEnabled() && orderTarget.hasExplicitPlacement()) {
      throw AdminCategoryErrors.invalidPlacementTarget();
    }
  }

  private void rejectDuplicateSlug(String slug, Long excludingId) {
    if (count(sql.existsSlug(slug, excludingId)) > 0) {
      throw AdminCategoryErrors.duplicateSlug(slug);
    }
  }

  private boolean hasChildren(Long id) {
    return count(sql.hasChildren(id)) > 0;
  }

  private boolean exists(Long id) {
    AdminCategoryJdbcSql.Statement statement = sql.category(id);
    Long count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM (" + statement.sql() + ") existing",
      statement.parameters(),
      Long.class
    );
    return count != null && count > 0;
  }

  private void updateExpected(AdminCategoryJdbcSql.Statement statement, Long id) {
    int updated = jdbc.update(statement.sql(), statement.parameters());
    if (updated == 0) {
      throw AdminCategoryErrors.categoryNotFound(id);
    }
  }

  private long count(AdminCategoryJdbcSql.Statement statement) {
    Long total = jdbc.queryForObject(statement.sql(), statement.parameters(), Long.class);
    return total == null ? 0 : total;
  }

  private <T> List<T> query(
    AdminCategoryJdbcSql.Statement statement,
    org.springframework.jdbc.core.RowMapper<T> rowMapper
  ) {
    return jdbc.query(statement.sql(), statement.parameters(), rowMapper);
  }

  private <T> T queryForObject(
    AdminCategoryJdbcSql.Statement statement,
    org.springframework.jdbc.core.RowMapper<T> rowMapper
  ) {
    return jdbc.queryForObject(statement.sql(), statement.parameters(), rowMapper);
  }

  private String encodeNextCursor(
    AdminCategoryParentOptionsCursorCodec.RequestContext context,
    AdminCategoryJdbcRowMapper.ParentOptionRow row
  ) {
    return cursorCodec.encode(
      systemKey,
      context,
      new AdminCategoryParentOptionsCursorCodec.Position(
        row.searchRank(),
        row.option().category().active(),
        row.normalizedName(),
        row.option().category().id()
      )
    );
  }

  private static boolean activeOrRejectNull(Boolean active) {
    if (active == null) {
      throw new InvalidRequestException(
        "category.api.patch.field.required",
        "active cannot be null",
        "active"
      );
    }
    return active;
  }

  private <T> T read(String operation, Long categoryId, Supplier<T> action) {
    try {
      T result = action.get();
      logRead(operation, categoryId, "succeeded", null);
      return result;
    } catch (RuntimeException exception) {
      logRead(operation, categoryId, "failed", exception);
      throw exception;
    }
  }

  private <T> T mutation(String operation, Long categoryId, Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException exception) {
      logWrite(operation, categoryId, "failed", exception);
      throw exception;
    }
  }

  private void mutationWithoutResult(String operation, Long categoryId, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      logWrite(operation, categoryId, "failed", exception);
      throw exception;
    }
  }

  private void logRead(
    String operation,
    Long categoryId,
    String outcome,
    RuntimeException exception
  ) {
    LoggingEventBuilder event = exception == null ? log.atInfo() : log.atWarn();
    event
      .addKeyValue("category.event", readEvent(operation, exception))
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("operation", operation)
      .addKeyValue("outcome", outcome);
    if (categoryId != null) {
      event.addKeyValue("categoryId", categoryId);
    }
    errorCode(exception).ifPresent(errorCode -> event.addKeyValue("errorCode", errorCode));
    event.log(
      exception == null
        ? "descriptor-backed category read"
        : "descriptor-backed category read failed"
    );
  }

  private void logWrite(
    String operation,
    Long categoryId,
    String outcome,
    RuntimeException exception
  ) {
    LoggingEventBuilder event = exception == null ? log.atInfo() : log.atWarn();
    event
      .addKeyValue("category.event", "descriptor_provider.category." + operation)
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("operation", operation)
      .addKeyValue("outcome", outcome);
    if (categoryId != null) {
      event.addKeyValue("categoryId", categoryId);
    }
    errorCode(exception).ifPresent(errorCode -> event.addKeyValue("errorCode", errorCode));
    event.log("descriptor-backed category mutation");
  }

  private static String readEvent(String operation, RuntimeException exception) {
    if (
      "parentOptions".equals(operation) &&
      exception instanceof AdminCategoryException adminCategoryException &&
      "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID".equals(adminCategoryException.code())
    ) {
      return "descriptor_provider.parent_options.cursor_invalid";
    }
    return "descriptor_provider.category.read." + operation;
  }

  private static java.util.Optional<String> errorCode(RuntimeException exception) {
    if (exception instanceof AdminCategoryException adminCategoryException) {
      return java.util.Optional.of(adminCategoryException.code());
    }
    return java.util.Optional.empty();
  }

  private static <T> PageResult<T> page(
    List<T> content,
    int page,
    int size,
    long total,
    com.ferozfaiz.uniapi.shared.pagination.SortCriteria sort
  ) {
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / (double) size);
    boolean first = page == 0;
    boolean last = totalPages == 0 || page >= totalPages - 1;
    return new PageResult<>(content, page, size, total, totalPages, first, last, sort);
  }
}
