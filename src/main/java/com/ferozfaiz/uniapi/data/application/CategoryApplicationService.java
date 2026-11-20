package com.ferozfaiz.uniapi.data.application;

import com.ferozfaiz.uniapi.data.application.command.CreateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.MoveCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.PatchCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.ReorderCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.UpdateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.policy.CategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.data.application.policy.CategoryListPolicy;
import com.ferozfaiz.uniapi.data.application.query.CategoryTree;
import com.ferozfaiz.uniapi.data.application.query.ListCategoriesQuery;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHasChildrenException;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryNotFoundException;
import com.ferozfaiz.uniapi.data.domain.exception.DuplicateCategoryException;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.model.CategoryNode;
import com.ferozfaiz.uniapi.data.domain.repository.CategoryRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.text.Text;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryApplicationService {

  private static final Logger log = LoggerFactory.getLogger(CategoryApplicationService.class);
  private static final String SYSTEM_ACTOR = "system";

  private final CategoryRepository categories;
  private final CategoryHierarchyPolicy hierarchyPolicy;
  private final CategoryListPolicy listPolicy;
  private final Clock clock;

  @Autowired
  public CategoryApplicationService(
    CategoryRepository categories,
    CategoryHierarchyPolicy hierarchyPolicy,
    CategoryListPolicy listPolicy
  ) {
    this(categories, hierarchyPolicy, listPolicy, Clock.systemUTC());
  }

  CategoryApplicationService(
    CategoryRepository categories,
    CategoryHierarchyPolicy hierarchyPolicy,
    CategoryListPolicy listPolicy,
    Clock clock
  ) {
    this.categories = categories;
    this.hierarchyPolicy = hierarchyPolicy;
    this.listPolicy = listPolicy;
    this.clock = clock;
  }

  public Category create(CreateCategoryCommand command) {
    Long parentId = command.parentId();
    if (parentId != null) {
      findById(parentId);
    }

    String slug = normalizeSlug(command.slug());
    ensureSlugIsAvailable(parentId, slug);

    OffsetDateTime now = OffsetDateTime.now(clock);
    Category category = new Category(
      null,
      parentId,
      null,
      slug,
      requireText(command.name(), "name"),
      Text.trimToNull(command.description()),
      command.active() == null || command.active(),
      now,
      now,
      SYSTEM_ACTOR,
      SYSTEM_ACTOR
    );

    Category created =
      parentId == null
        ? categories.saveRoot(category, command.orderTarget())
        : categories.saveChild(category, parentId, command.orderTarget());
    log
      .atInfo()
      .addKeyValue("category.system", "data")
      .addKeyValue("category.operation", "create")
      .addKeyValue("category.id", created.id())
      .addKeyValue("category.parent_id", created.parentId())
      .addKeyValue("category.ranking.result", "assigned")
      .log("data category ranked create completed");
    return created;
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public Category findById(Long id) {
    return categories.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public CategoryNode findNodeById(Long id) {
    return categories.findNodeById(id).orElseThrow(() -> new CategoryNotFoundException(id));
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication)")
  @Transactional(readOnly = true)
  public List<Category> findRoots() {
    return categories.findRoots();
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication)")
  @Transactional(readOnly = true)
  public PageResult<CategoryNode> findAll(ListCategoriesQuery query) {
    listPolicy.enforcePage(query.page());
    listPolicy.enforceSort(query.sort());
    return categories.findAll(query.page(), query.sort());
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #parentId)")
  @Transactional(readOnly = true)
  public List<Category> findChildren(Long parentId) {
    findById(parentId);
    return categories.findChildren(parentId);
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public List<Category> findAncestors(Long id) {
    findById(id);
    return categories.findAncestors(id);
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public List<Category> findDescendants(Long id) {
    findById(id);
    hierarchyPolicy.enforceTreeRead(Math.max(0, categories.countSelfAndDescendants(id) - 1));
    return categories.findDescendants(id);
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication)")
  @Transactional(readOnly = true)
  public List<CategoryTree> findTree() {
    hierarchyPolicy.enforceTreeRead(categories.count());
    return CategoryTreeBuilder.forest(categories.findAllForTree());
  }

  @PreAuthorize("@categoryReadAuthorizationPolicy.canRead(authentication, #id)")
  @Transactional(readOnly = true)
  public CategoryTree findTree(Long id) {
    Category root = findById(id);
    hierarchyPolicy.enforceTreeRead(categories.countSelfAndDescendants(id));
    return CategoryTreeBuilder.tree(root, categories.findDescendants(id));
  }

  public Category update(Long id, UpdateCategoryCommand command) {
    Category existing = findById(id);
    String slug = normalizeSlug(command.slug());
    ensureSlugIsAvailableForCategory(existing.parentId(), slug, existing.id());

    Category updated = new Category(
      existing.id(),
      existing.parentId(),
      existing.sortRank(),
      slug,
      requireText(command.name(), "name"),
      Text.trimToNull(command.description()),
      command.active(),
      existing.createdAt(),
      OffsetDateTime.now(clock),
      existing.createdBy(),
      SYSTEM_ACTOR
    );

    return categories.save(updated);
  }

  public Category patch(Long id, PatchCategoryCommand command) {
    Category existing = findById(id);
    String slug = command.slug() == null ? existing.slug() : normalizeSlug(command.slug());
    ensureSlugIsAvailableForCategory(existing.parentId(), slug, existing.id());

    Category patched = new Category(
      existing.id(),
      existing.parentId(),
      existing.sortRank(),
      slug,
      command.name() == null ? existing.name() : requireText(command.name(), "name"),
      command.description() == null
        ? existing.description()
        : Text.trimToNull(command.description()),
      command.active() == null ? existing.active() : command.active(),
      existing.createdAt(),
      OffsetDateTime.now(clock),
      existing.createdBy(),
      SYSTEM_ACTOR
    );

    return categories.save(patched);
  }

  public Category move(Long id, MoveCategoryCommand command) {
    CategoryMoveEstimate estimate = estimateMove(id, command);
    hierarchyPolicy.enforceMove(estimate, command.mode());

    categories.move(
      id,
      command.parentId(),
      command.mode(),
      hierarchyPolicy.syncStatementTimeout(),
      command.orderTarget()
    );
    log
      .atInfo()
      .addKeyValue("category.system", "data")
      .addKeyValue("category.operation", "move")
      .addKeyValue("category.id", id)
      .addKeyValue("category.parent_id", command.parentId())
      .addKeyValue("category.ranking.result", "assigned")
      .log("data category ranked move completed");
    return findById(id);
  }

  public Category reorder(Long id, ReorderCategoryCommand command) {
    Category existing = findById(id);
    Long destinationParentId =
      command.parentId() == null ? existing.parentId() : command.parentId();
    MoveCategoryCommand moveCommand = new MoveCategoryCommand(
      destinationParentId,
      CategoryMoveMode.LEAF_ONLY,
      command.orderTarget()
    );
    CategoryMoveEstimate estimate = estimateMove(id, moveCommand);
    hierarchyPolicy.enforceMove(estimate, moveCommand.mode());

    categories.move(
      id,
      destinationParentId,
      moveCommand.mode(),
      hierarchyPolicy.syncStatementTimeout(),
      moveCommand.orderTarget()
    );
    log
      .atInfo()
      .addKeyValue("category.system", "data")
      .addKeyValue("category.operation", "reorder")
      .addKeyValue("category.id", id)
      .addKeyValue("category.parent_id", destinationParentId)
      .addKeyValue("category.ranking.result", "assigned")
      .log("data category ranked reorder completed");
    return findById(id);
  }

  @Transactional(readOnly = true)
  public CategoryMoveEstimate estimateMove(Long id, MoveCategoryCommand command) {
    Category existing = findById(id);
    Long newParentId = command.parentId();
    if (newParentId != null) {
      findById(newParentId);
    }
    ensureSlugIsAvailableForCategory(newParentId, existing.slug(), existing.id());

    return categories.estimateMove(id, newParentId);
  }

  public void delete(Long id) {
    findById(id);
    if (categories.hasChildren(id)) {
      throw new CategoryHasChildrenException(id);
    }
    hierarchyPolicy.enforceDelete(categories.estimateDelete(id));
    categories.deleteById(id);
  }

  private void ensureSlugIsAvailable(@Nullable Long parentId, String slug) {
    if (categories.existsByParentIdAndSlug(parentId, slug)) {
      throw DuplicateCategoryException.slugAlreadyExistsInParent();
    }
  }

  private void ensureSlugIsAvailableForCategory(
    @Nullable Long parentId,
    String slug,
    @Nullable Long id
  ) {
    if (id != null && categories.existsByParentIdAndSlugAndIdNot(parentId, slug, id)) {
      throw DuplicateCategoryException.slugAlreadyExistsInParent();
    }
  }

  private static String normalizeSlug(String slug) {
    return requireText(slug, "slug").toLowerCase(Locale.ROOT);
  }

  private static String requireText(String value, String fieldName) {
    String trimmed = Text.trimToNull(value);
    if (trimmed == null) {
      throw InvalidRequestException.requiredField(fieldName);
    }
    return trimmed;
  }
}
