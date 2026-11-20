package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryTreeQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminListCatalogueCategoriesQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.application.validation.CatalogueCategoryCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryParentOptionLookup;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryTreePolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogueCategoryApplicationService {

  private static final Logger log = LoggerFactory.getLogger(
    CatalogueCategoryApplicationService.class
  );
  private static final String CATEGORY_SYSTEM = "catalogue";

  private final CatalogueCategoryRepository categories;
  private final CatalogueCategoryCommandValidator validator;
  private final CatalogueSlugWriteLock slugWriteLock;
  private final CategorySystemRegistry categorySystems;
  private final AdminCategoryTreePolicy treePolicy;

  @Autowired
  public CatalogueCategoryApplicationService(
    CatalogueCategoryRepository categories,
    CatalogueCategoryCommandValidator validator,
    CatalogueSlugWriteLock slugWriteLock,
    CategorySystemRegistry categorySystems,
    AdminCategoryTreePolicy treePolicy
  ) {
    this.categories = categories;
    this.validator = validator;
    this.slugWriteLock = slugWriteLock;
    this.categorySystems = categorySystems;
    this.treePolicy = treePolicy;
  }

  CatalogueCategoryApplicationService(
    CatalogueCategoryRepository categories,
    CatalogueCategoryCommandValidator validator,
    CatalogueSlugWriteLock slugWriteLock
  ) {
    this.categories = categories;
    this.validator = validator;
    this.slugWriteLock = slugWriteLock;
    this.categorySystems = null;
    this.treePolicy = new AdminCategoryTreePolicy();
  }

  public CatalogueCategory save(CatalogueCategory category) {
    return categories.save(category);
  }

  @Transactional(readOnly = true)
  public PageResult<CatalogueCategory> findAll(AdminListCatalogueCategoriesQuery query) {
    validateListParent(query.parentScope());
    return categories.findAll(query.parentScope(), query.filters(), query.page(), query.sort());
  }

  @Transactional(readOnly = true)
  public PageResult<CatalogueCategoryNode> findAllNodes(AdminListCatalogueCategoriesQuery query) {
    validateListParent(query.parentScope());
    return categories.findAllNodes(
      query.parentScope(),
      query.filters(),
      query.page(),
      query.sort()
    );
  }

  @Transactional(readOnly = true)
  public PageResult<CatalogueCategoryTreeNode> findTree(AdminCatalogueCategoryTreeQuery query) {
    var page = treePolicy.resolvePage(query.page().page(), query.page().size());
    long totalRows = categories.countTreeRows();
    treePolicy.enforceCompleteTree(totalRows, page.size());
    return categories.findTree(page, query.sort());
  }

  public CatalogueCategory create(UpsertCatalogueCategoryCommand command) {
    validateParentExists(command.parentId());
    CatalogueCategory saved = upsert(command, null);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "category.created")
      .addKeyValue("category.system", "catalogue")
      .addKeyValue("category.operation", "create")
      .addKeyValue("category.id", saved.id())
      .addKeyValue("category.parent_id", saved.parentId())
      .addKeyValue("category.ranking.result", "assigned")
      .log("catalogue category created");
    return saved;
  }

  public CatalogueCategory update(UpsertCatalogueCategoryCommand command) {
    if (command.id() == null) {
      throw CatalogueNotFoundException.category(command.slug());
    }
    categories
      .findById(command.id())
      .orElseThrow(() -> CatalogueNotFoundException.category(command.slug()));
    validateParentExists(command.parentId());
    validateParentMove(command.id(), command.parentId());
    CatalogueCategory saved = upsert(command, command.id());
    log
      .atInfo()
      .addKeyValue("catalogue.event", "category.updated")
      .addKeyValue("category.id", saved.id())
      .log("catalogue category updated");
    return saved;
  }

  @Transactional(readOnly = true)
  public CatalogueCategory get(Long id) {
    return categories
      .findById(id)
      .orElseThrow(() -> CatalogueNotFoundException.category(String.valueOf(id)));
  }

  @Transactional(readOnly = true)
  public CatalogueCategoryNode getNode(Long id) {
    return categories
      .findNodeById(id)
      .orElseThrow(() -> CatalogueNotFoundException.category(String.valueOf(id)));
  }

  @Transactional(readOnly = true)
  public List<CatalogueCategoryBreadcrumbItem> findBreadcrumb(Long id) {
    List<CatalogueCategoryBreadcrumbItem> breadcrumb = categories.findBreadcrumbById(id);
    if (breadcrumb.isEmpty()) {
      throw CatalogueNotFoundException.category(String.valueOf(id));
    }
    return breadcrumb;
  }

  @Transactional(readOnly = true)
  public CatalogueCategoryParentOptionSlice findParentOptions(
    AdminCatalogueCategoryParentOptionsQuery query
  ) {
    if (query.excludeSubtreeId() != null) {
      validateParentExists(query.excludeSubtreeId());
    }
    CatalogueCategoryParentOptionSlice slice = categories.findParentOptions(
      new CatalogueCategoryParentOptionLookup(
        query.q(),
        query.numericQuery(),
        query.limit(),
        query.cursor(),
        query.excludeSubtreeId(),
        query.includeIds(),
        query.includeInactive()
      )
    );
    return withDisambiguatedLabels(slice);
  }

  public void move(Long categoryId, Long newParentId) {
    move(categoryId, newParentId, SparseOrderTarget.append());
  }

  public void move(Long categoryId, Long newParentId, SparseOrderTarget orderTarget) {
    get(categoryId);
    validateParentMove(categoryId, newParentId);
    validatePlacement(orderTarget);
    categories.move(categoryId, newParentId, orderTarget);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "category.moved")
      .addKeyValue("category.system", "catalogue")
      .addKeyValue("category.operation", "move")
      .addKeyValue("category.id", categoryId)
      .addKeyValue("category.parent_id", newParentId)
      .addKeyValue("category.ranking.result", "assigned")
      .log("catalogue category moved");
  }

  public CatalogueCategory reorder(Long categoryId, ReorderCatalogueCategoryCommand command) {
    CatalogueCategory existing = get(categoryId);
    Long destinationParentId =
      command.parentId() == null ? existing.parentId() : command.parentId();
    validateParentMove(categoryId, destinationParentId);
    validatePlacement(command.orderTarget());
    categories.move(categoryId, destinationParentId, command.orderTarget());
    log
      .atInfo()
      .addKeyValue("catalogue.event", "category.reordered")
      .addKeyValue("category.system", "catalogue")
      .addKeyValue("category.operation", "reorder")
      .addKeyValue("category.id", categoryId)
      .addKeyValue("category.parent_id", destinationParentId)
      .addKeyValue("category.ranking.result", "assigned")
      .log("catalogue category reordered");
    return get(categoryId);
  }

  private void validateParentMove(Long categoryId, Long newParentId) {
    if (categoryId.equals(newParentId)) {
      throw CatalogueConflictException.invalidCategoryMove(categoryId, newParentId);
    }
    validateParentExists(newParentId);
    if (newParentId != null) {
      boolean moveWouldCreateLoop = categories.isDescendantOf(newParentId, categoryId);
      validator.validateNotMovingIntoDescendant(moveWouldCreateLoop);
    }
  }

  public CatalogueCategory activate(Long id) {
    return setActive(id, true);
  }

  public CatalogueCategory deactivate(Long id) {
    return setActive(id, false);
  }

  public void delete(Long id) {
    get(id);
    if (categories.hasChildren(id) || categories.hasAssignedProducts(id)) {
      throw CatalogueConflictException.categoryDeleteBlocked(id);
    }
    categories.deleteById(id);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "category.deleted")
      .addKeyValue("category.id", id)
      .log("catalogue category deleted");
  }

  private CatalogueCategory upsert(UpsertCatalogueCategoryCommand command, Long excludingId) {
    validator.validateRequiredCategoryFields(command.name(), command.slug());
    validator.validateCategoryFieldShapes(command.name(), command.slug());
    validatePlacement(command.orderTarget());
    slugWriteLock.lock(ResourceType.CATEGORY, command.slug());
    if (categories.existsSlug(command.slug(), excludingId)) {
      throw DuplicateCatalogueSlugException.category(command.slug());
    }
    return categories.save(toCategory(command), command.orderTarget());
  }

  private CatalogueCategory setActive(Long id, boolean active) {
    CatalogueCategory category = categories
      .findById(id)
      .orElseThrow(() -> CatalogueNotFoundException.category(String.valueOf(id)));
    CatalogueCategory saved = categories.save(
      new CatalogueCategory(
        category.id(),
        category.parentId(),
        category.sortRank(),
        category.name(),
        category.slug(),
        active,
        category.createdAt(),
        category.updatedAt()
      )
    );
    log
      .atInfo()
      .addKeyValue("catalogue.event", active ? "category.activated" : "category.deactivated")
      .addKeyValue("category.id", id)
      .log("catalogue category lifecycle changed");
    return saved;
  }

  private void validateParentExists(Long parentId) {
    if (parentId != null && categories.findById(parentId).isEmpty()) {
      throw CatalogueNotFoundException.category(String.valueOf(parentId));
    }
  }

  private void validateListParent(CategoryParentScope parentScope) {
    if (parentScope.mode() == CategoryParentScope.Mode.CHILDREN) {
      validateParentExists(parentScope.parentId());
    }
  }

  private void validatePlacement(SparseOrderTarget orderTarget) {
    if (categorySystems == null || !orderTarget.hasExplicitPlacement()) {
      return;
    }
    var descriptor = categorySystems.requireDescriptor(CATEGORY_SYSTEM);
    if (descriptor.ranking().rankingEnabled()) {
      validator.validateConfiguredPlacementMode(descriptor, orderTarget);
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

  private static CatalogueCategory toCategory(UpsertCatalogueCategoryCommand command) {
    return new CatalogueCategory(
      command.id(),
      command.parentId(),
      null,
      command.name(),
      command.slug(),
      command.active(),
      null,
      null
    );
  }

  private static CatalogueCategoryParentOptionSlice withDisambiguatedLabels(
    CatalogueCategoryParentOptionSlice slice
  ) {
    Map<String, Long> nameCounts = slice
      .data()
      .stream()
      .collect(
        Collectors.groupingBy(
          option -> option.category().name().toLowerCase(Locale.ROOT),
          Collectors.counting()
        )
      );
    List<CatalogueCategoryParentOption> labelledOptions = slice
      .data()
      .stream()
      .map(option -> {
        String key = option.category().name().toLowerCase(Locale.ROOT);
        if (nameCounts.getOrDefault(key, 0L) <= 1) {
          return option.withLabel(option.category().name());
        }
        return option.withLabel(
          option
            .path()
            .stream()
            .map(item -> item.category().name())
            .collect(Collectors.joining(" / "))
        );
      })
      .toList();
    return new CatalogueCategoryParentOptionSlice(
      labelledOptions,
      slice.hasMore(),
      slice.nextCursor()
    );
  }
}
