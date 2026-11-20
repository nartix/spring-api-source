package com.ferozfaiz.uniapi.catalogue.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.application.validation.CatalogueCategoryCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogueCategoryApplicationSlugLockTest {

  @Mock
  private CatalogueCategoryRepository categories;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Test
  void locksCategorySlugBeforeDuplicateCheck() {
    when(categories.save(any(), any())).thenReturn(
      new CatalogueCategory(1L, null, "Storage", "storage", true, null, null)
    );
    var service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    service.create(new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true));

    var inOrder = inOrder(slugWriteLock, categories);
    inOrder.verify(slugWriteLock).lock(ResourceType.CATEGORY, "storage");
    inOrder.verify(categories).existsSlug("storage", null);
  }
}
