package com.ferozfaiz.uniapi.catalogue.application.provider;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryTreeQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminListCatalogueCategoriesQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryPlacementTargetException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CatalogueAdminCategoryProvider
  implements
    AdminCategoryReadPort,
    AdminCategoryHierarchyReadPort,
    AdminCategoryBasicMutationPort,
    AdminCategoryHierarchyMutationPort,
    AdminCategoryOrderingPort,
    AdminCategoryLifecyclePort
{

  private final CatalogueCategoryApplicationService categories;
  private final CatalogueCategoryRepository categoryRepository;

  @Autowired
  public CatalogueAdminCategoryProvider(
    CatalogueCategoryApplicationService categories,
    CatalogueCategoryRepository categoryRepository
  ) {
    this.categories = Objects.requireNonNull(categories, "categories must not be null");
    this.categoryRepository = Objects.requireNonNull(
      categoryRepository,
      "categoryRepository must not be null"
    );
  }

  public CatalogueAdminCategoryProvider(CatalogueCategoryApplicationService categories) {
    this.categories = Objects.requireNonNull(categories, "categories must not be null");
    this.categoryRepository = null;
  }

  @Override
  public PageResult<CategoryNode> list(AdminCategoryListQuery query) {
    try {
      return categories
        .findAllNodes(toCatalogueQuery(query))
        .map(CatalogueAdminCategoryProvider::toNode);
    } catch (CatalogueNotFoundException ex) {
      throw mapNotFound(query, ex);
    }
  }

  @Override
  public PageResult<CategoryNode> tree(AdminCategoryTreeQuery query) {
    if (categoryRepository == null) {
      return categories
        .findTree(toCatalogueQuery(query))
        .map(CatalogueAdminCategoryProvider::toNode);
    }
    return categoryRepository
      .findTree(query.page(), query.sort())
      .map(CatalogueAdminCategoryProvider::toNode);
  }

  @Override
  public AdminCategoryModels.Category get(Long id) {
    try {
      return toCategory(categories.get(id));
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    }
  }

  @Override
  public CategoryNode getNode(Long id) {
    try {
      return toNode(categories.getNode(id));
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    }
  }

  @Override
  public List<AdminCategoryModels.BreadcrumbItem> breadcrumb(Long id) {
    try {
      return categories
        .findBreadcrumb(id)
        .stream()
        .map(CatalogueAdminCategoryProvider::toBreadcrumbItem)
        .toList();
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    }
  }

  @Override
  public AdminCategoryModels.ParentOptionSlice parentOptions(
    AdminCategoryParentOptionsQuery query
  ) {
    try {
      return toParentOptionSlice(categories.findParentOptions(toCatalogueQuery(query)));
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    } catch (InvalidRequestException ex) {
      if (ex.messageKey().contains("parent-options.cursor")) {
        throw AdminCategoryErrors.parentOptionsCursorInvalid();
      }
      throw ex;
    }
  }

  @Override
  public AdminCategoryModels.Category create(AdminCategoryCreateCommand command) {
    try {
      return toCategory(categories.create(toCatalogueCommand(command)));
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.parentNotFound(firstLongMessageArg(ex));
    } catch (DuplicateCatalogueSlugException ex) {
      throw AdminCategoryErrors.duplicateSlug(firstMessageArg(ex));
    } catch (InvalidRequestException ex) {
      throw mapInvalidRequest(ex);
    }
  }

  @Override
  public AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command) {
    try {
      return toCategory(categories.update(toCatalogueCommand(id, command)));
    } catch (CatalogueNotFoundException ex) {
      throw mapCategoryOrParentNotFound(command.parentId(), ex);
    } catch (DuplicateCatalogueSlugException ex) {
      throw AdminCategoryErrors.duplicateSlug(firstMessageArg(ex));
    } catch (CatalogueConflictException ex) {
      throw mapConflict(id, command.parentId(), ex);
    } catch (InvalidRequestException ex) {
      throw mapInvalidRequest(ex);
    }
  }

  @Override
  public AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command) {
    try {
      CatalogueCategory existing = categories.get(id);
      return toCategory(categories.update(toCatalogueCommand(existing, command)));
    } catch (CatalogueNotFoundException ex) {
      throw mapCategoryOrParentNotFound(command.parentId(), ex);
    } catch (DuplicateCatalogueSlugException ex) {
      throw AdminCategoryErrors.duplicateSlug(firstMessageArg(ex));
    } catch (CatalogueConflictException ex) {
      throw mapConflict(id, command.parentId(), ex);
    } catch (InvalidRequestException ex) {
      throw mapInvalidRequest(ex);
    }
  }

  @Override
  public void move(Long id, AdminCategoryMoveCommand command) {
    try {
      categories.move(id, command.newParentId(), command.orderTarget());
    } catch (CatalogueNotFoundException ex) {
      throw mapCategoryOrParentNotFound(command.newParentId(), ex);
    } catch (CatalogueConflictException ex) {
      throw mapConflict(id, command.newParentId(), ex);
    } catch (InvalidRequestException ex) {
      throw mapInvalidRequest(ex);
    }
  }

  @Override
  public AdminCategoryModels.Category reorder(Long id, AdminCategoryReorderCommand command) {
    try {
      return toCategory(
        categories.reorder(
          id,
          new ReorderCatalogueCategoryCommand(command.parentId(), command.orderTarget())
        )
      );
    } catch (CatalogueNotFoundException ex) {
      throw mapCategoryOrParentNotFound(command.parentId(), ex);
    } catch (CatalogueConflictException ex) {
      throw mapConflict(id, command.parentId(), ex);
    } catch (CatalogueCategoryPlacementTargetException ex) {
      throw AdminCategoryErrors.invalidPlacementTarget();
    } catch (InvalidRequestException ex) {
      throw mapInvalidRequest(ex);
    }
  }

  @Override
  public void activate(Long id) {
    try {
      categories.activate(id);
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    }
  }

  @Override
  public void deactivate(Long id) {
    try {
      categories.deactivate(id);
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    }
  }

  @Override
  public void delete(Long id) {
    try {
      categories.delete(id);
    } catch (CatalogueNotFoundException ex) {
      throw AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
    } catch (CatalogueConflictException ex) {
      throw AdminCategoryErrors.deleteBlocked(id);
    }
  }

  private static AdminListCatalogueCategoriesQuery toCatalogueQuery(AdminCategoryListQuery query) {
    return new AdminListCatalogueCategoriesQuery(
      toParentScope(query.parentScope()),
      toFilters(query.filters()),
      query.page(),
      query.sort()
    );
  }

  private static AdminCatalogueCategoryTreeQuery toCatalogueQuery(AdminCategoryTreeQuery query) {
    return new AdminCatalogueCategoryTreeQuery(query.page(), query.sort());
  }

  private static AdminCatalogueCategoryParentOptionsQuery toCatalogueQuery(
    AdminCategoryParentOptionsQuery query
  ) {
    return AdminCatalogueCategoryParentOptionsQuery.from(
      query.q(),
      query.limit(),
      query.cursor(),
      query.includeIds().stream().map(String::valueOf).toList(),
      query.excludeSubtreeId() == null ? null : String.valueOf(query.excludeSubtreeId()),
      String.valueOf(query.includeInactive())
    );
  }

  private static UpsertCatalogueCategoryCommand toCatalogueCommand(
    AdminCategoryCreateCommand command
  ) {
    return new UpsertCatalogueCategoryCommand(
      null,
      command.parentId(),
      command.name(),
      command.slug(),
      command.active(),
      command.orderTarget()
    );
  }

  private static UpsertCatalogueCategoryCommand toCatalogueCommand(
    Long id,
    AdminCategoryUpdateCommand command
  ) {
    return new UpsertCatalogueCategoryCommand(
      id,
      command.parentId(),
      command.name(),
      command.slug(),
      command.active()
    );
  }

  private static UpsertCatalogueCategoryCommand toCatalogueCommand(
    CatalogueCategory existing,
    AdminCategoryPatchCommand command
  ) {
    return new UpsertCatalogueCategoryCommand(
      existing.id(),
      command.parentIdPresent() ? command.parentId() : existing.parentId(),
      command.namePresent() ? command.name() : existing.name(),
      command.slugPresent() ? command.slug() : existing.slug(),
      command.activePresent() ? activeOrRejectNull(command.active()) : existing.active()
    );
  }

  private static CategoryParentScope toParentScope(AdminCategoryParentScope parentScope) {
    return switch (parentScope.mode()) {
      case ALL -> CategoryParentScope.all();
      case ROOTS -> CategoryParentScope.roots();
      case CHILDREN -> CategoryParentScope.children(parentScope.parentId());
    };
  }

  private static AdminCatalogueCategoryFilters toFilters(AdminCategoryFilters filters) {
    return new AdminCatalogueCategoryFilters(filters.active(), filters.q(), filters.slug());
  }

  private static CategoryNode toNode(CatalogueCategoryNode node) {
    return new CategoryNode(toCategory(node.category()), node.depth(), node.hasChildren());
  }

  private static CategoryNode toNode(CatalogueCategoryTreeNode node) {
    return new CategoryNode(toCategory(node.category()), node.depth(), node.hasChildren());
  }

  private static AdminCategoryModels.Category toCategory(CatalogueCategory category) {
    return new AdminCategoryModels.Category(
      category.id(),
      category.parentId(),
      category.sortRank(),
      category.name(),
      category.slug(),
      category.active(),
      category.createdAt(),
      category.updatedAt()
    );
  }

  private static AdminCategoryModels.BreadcrumbItem toBreadcrumbItem(
    CatalogueCategoryBreadcrumbItem item
  ) {
    return new AdminCategoryModels.BreadcrumbItem(toCategory(item.category()), item.depth());
  }

  private static AdminCategoryModels.ParentOptionSlice toParentOptionSlice(
    CatalogueCategoryParentOptionSlice slice
  ) {
    return new AdminCategoryModels.ParentOptionSlice(
      slice.data().stream().map(CatalogueAdminCategoryProvider::toParentOption).toList(),
      slice.hasMore(),
      slice.nextCursor()
    );
  }

  private static AdminCategoryModels.ParentOption toParentOption(
    CatalogueCategoryParentOption option
  ) {
    return new AdminCategoryModels.ParentOption(
      toCategory(option.category()),
      option.depth(),
      option.label(),
      option.path().stream().map(CatalogueAdminCategoryProvider::toBreadcrumbItem).toList(),
      option.selectable(),
      option.disabledReason() == null ? null : option.disabledReason().name()
    );
  }

  private static AdminCategoryException mapNotFound(
    AdminCategoryListQuery query,
    CatalogueNotFoundException ex
  ) {
    if (query.parentScope().mode() == AdminCategoryParentScope.Mode.CHILDREN) {
      return AdminCategoryErrors.parentNotFound(query.parentScope().parentId());
    }
    return AdminCategoryErrors.categoryNotFound(firstLongMessageArg(ex));
  }

  private static Long firstLongMessageArg(CatalogueNotFoundException ex) {
    Object[] args = ex.messageArgs();
    if (args.length == 0 || args[0] == null) {
      return null;
    }
    if (args[0] instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.valueOf(args[0].toString());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String firstMessageArg(DuplicateCatalogueSlugException ex) {
    Object[] args = ex.messageArgs();
    return args.length == 0 || args[0] == null ? null : args[0].toString();
  }

  private static AdminCategoryException mapCategoryOrParentNotFound(
    Long requestedParentId,
    CatalogueNotFoundException ex
  ) {
    Long missingId = firstLongMessageArg(ex);
    if (requestedParentId != null && Objects.equals(requestedParentId, missingId)) {
      return AdminCategoryErrors.parentNotFound(missingId);
    }
    return AdminCategoryErrors.categoryNotFound(missingId);
  }

  private static AdminCategoryException mapConflict(
    Long id,
    Long parentId,
    CatalogueConflictException ex
  ) {
    if ("CATEGORY_MOVE_REJECTED".equals(ex.code())) {
      return AdminCategoryErrors.invalidMove(id, parentId);
    }
    if ("CATEGORY_DELETE_REJECTED".equals(ex.code())) {
      return AdminCategoryErrors.deleteBlocked(id);
    }
    return AdminCategoryErrors.invalidPlacementTarget();
  }

  private static RuntimeException mapInvalidRequest(InvalidRequestException ex) {
    if (ex.messageKey().contains("placement.target")) {
      return AdminCategoryErrors.invalidPlacementTarget();
    }
    return ex;
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
}
