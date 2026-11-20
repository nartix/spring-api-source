package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogueCategoryJpaLifecycleIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Test
  void categoryLifecycleUsesJpaForCreateUpdateFindActiveSlugAndSlugChecks() {
    var created = categoryService.create(
      new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true)
    );

    assertThat(categories.findById(created.id())).get().extracting("slug").isEqualTo("storage");
    assertThat(categories.findActiveBySlug("storage")).isPresent();
    assertThat(categories.existsSlug("storage", null)).isTrue();
    assertThat(categories.existsSlug("storage", created.id())).isFalse();

    var updated = categoryService.update(
      new UpsertCatalogueCategoryCommand(created.id(), null, "Storage Updated", "storage", true)
    );

    assertThat(updated.name()).isEqualTo("Storage Updated");
    categoryService.deactivate(created.id());
    assertThat(categories.findActiveBySlug("storage")).isEmpty();
  }

  @Test
  void categoryDeleteGuardsUseJpaChecks() {
    Long parent = insertCategory("Storage", "storage", null);
    insertClosure(parent, parent, 0);
    Long child = insertCategory("NVMe SSDs", "nvme-ssds", parent);
    insertClosure(child, child, 0);
    insertClosure(parent, child, 1);

    assertThat(categories.hasChildren(parent)).isTrue();
    assertThatThrownBy(() -> categoryService.delete(parent)).isInstanceOf(
      CatalogueConflictException.class
    );

    Long assignedCategory = insertCategory("CPUs", "cpus", null);
    insertClosure(assignedCategory, assignedCategory, 0);
    Long brandId = insertBrand("AMD", "amd");
    Long manufacturerId = insertManufacturer("AMD", "amd-manufacturer");
    Long productId = insertProduct(
      "Ryzen 7 7800X3D",
      "ryzen-7-7800x3d",
      ProductFamily.CPU,
      brandId,
      manufacturerId,
      true
    );
    jdbc.update(
      "INSERT INTO catalogue_product_category (product_id, category_id) VALUES (?, ?)",
      productId,
      assignedCategory
    );

    assertThat(categories.hasAssignedProducts(assignedCategory)).isTrue();
    assertThatThrownBy(() -> categoryService.delete(assignedCategory)).isInstanceOf(
      CatalogueConflictException.class
    );
  }
}
