package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertProductCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.application.validation.ProductCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateProductIdentifierException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductRepository;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);

  private final CatalogueProductRepository products;
  private final CatalogueReferenceDataRepository referenceData;
  private final ProductCommandValidator validator;
  private final CatalogueSlugWriteLock slugWriteLock;

  public ProductApplicationService(
    CatalogueProductRepository products,
    CatalogueReferenceDataRepository referenceData,
    ProductCommandValidator validator,
    CatalogueSlugWriteLock slugWriteLock
  ) {
    this.products = products;
    this.referenceData = referenceData;
    this.validator = validator;
    this.slugWriteLock = slugWriteLock;
  }

  public CatalogueProduct save(CatalogueProduct product) {
    return products.save(product);
  }

  public CatalogueProduct create(UpsertProductCommand command) {
    CatalogueProduct saved = upsert(command, null);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "product.created")
      .addKeyValue("product.id", saved.id())
      .log("catalogue product created");
    return saved;
  }

  public CatalogueProduct update(UpsertProductCommand command) {
    if (command.id() == null) {
      throw CatalogueNotFoundException.product(null);
    }
    products
      .findById(command.id())
      .orElseThrow(() -> CatalogueNotFoundException.product(command.id()));
    CatalogueProduct saved = upsert(command, command.id());
    log
      .atInfo()
      .addKeyValue("catalogue.event", "product.updated")
      .addKeyValue("product.id", saved.id())
      .log("catalogue product updated");
    return saved;
  }

  @Transactional(readOnly = true)
  public CatalogueProduct get(Long id) {
    return products.findById(id).orElseThrow(() -> CatalogueNotFoundException.product(id));
  }

  public CatalogueProduct activate(Long id) {
    return setActive(id, true);
  }

  public CatalogueProduct deactivate(Long id) {
    return setActive(id, false);
  }

  public void assignCategory(Long productId, Long categoryId) {
    ensureProductExists(productId);
    ensureCategoryExists(categoryId);
    products.assignCategory(productId, categoryId);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "product.category.assigned")
      .addKeyValue("product.id", productId)
      .addKeyValue("category.id", categoryId)
      .log("catalogue product category assigned");
  }

  public void removeCategory(Long productId, Long categoryId) {
    ensureProductExists(productId);
    ensureCategoryExists(categoryId);
    products.removeCategory(productId, categoryId);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "product.category.removed")
      .addKeyValue("product.id", productId)
      .addKeyValue("category.id", categoryId)
      .log("catalogue product category removed");
  }

  public void delete(Long id) {
    ensureProductExists(id);
    if (
      products.hasMerchantOffers(id) ||
      products.hasCurrentPrices(id) ||
      products.hasPriceHistory(id)
    ) {
      throw CatalogueConflictException.productDeleteBlocked(id);
    }
    products.deleteById(id);
    log
      .atInfo()
      .addKeyValue("catalogue.event", "product.deleted")
      .addKeyValue("product.id", id)
      .log("catalogue product deleted");
  }

  private CatalogueProduct upsert(UpsertProductCommand command, Long excludingId) {
    validator.validateRequiredProductFields(
      command.title(),
      command.slug(),
      command.family(),
      command.brandId(),
      command.manufacturerId()
    );
    validator.validateProductFieldShapes(
      command.title(),
      command.slug(),
      command.mainImageUrl(),
      command.shortDescription()
    );
    validator.validateProductIdentifiers(command.identifiers());
    slugWriteLock.lock(ResourceType.PRODUCT, command.slug());
    if (products.existsSlug(command.slug(), excludingId)) {
      throw DuplicateCatalogueSlugException.product(command.slug());
    }
    validateIdentifiersAvailable(command, excludingId);
    validateReferencesExist(command.brandId(), command.manufacturerId());
    return products.save(toProduct(command));
  }

  private void validateIdentifiersAvailable(UpsertProductCommand command, Long excludingId) {
    for (var identifier : command.identifiers()) {
      if (products.existsIdentifier(identifier.canonicalGtin14(), excludingId)) {
        throw new DuplicateProductIdentifierException(identifier.canonicalGtin14());
      }
    }
  }

  private CatalogueProduct setActive(Long id, boolean active) {
    CatalogueProduct product = products
      .findById(id)
      .orElseThrow(() -> CatalogueNotFoundException.product(id));
    CatalogueProduct saved = products.save(
      new CatalogueProduct(
        product.id(),
        product.title(),
        product.slug(),
        product.family(),
        product.brand(),
        product.manufacturer(),
        product.mainImageUrl(),
        product.shortDescription(),
        active,
        product.identifiers(),
        product.createdAt(),
        product.updatedAt()
      )
    );
    log
      .atInfo()
      .addKeyValue("catalogue.event", active ? "product.activated" : "product.deactivated")
      .addKeyValue("product.id", id)
      .log("catalogue product lifecycle changed");
    return saved;
  }

  private void ensureProductExists(Long productId) {
    products.findById(productId).orElseThrow(() -> CatalogueNotFoundException.product(productId));
  }

  private void ensureCategoryExists(Long categoryId) {
    if (!products.categoryExists(categoryId)) {
      throw CatalogueNotFoundException.category(String.valueOf(categoryId));
    }
  }

  private void validateReferencesExist(Long brandId, Long manufacturerId) {
    if (!referenceData.existsBrandId(brandId)) {
      throw CatalogueReferenceNotFoundException.brand(brandId);
    }
    if (!referenceData.existsManufacturerId(manufacturerId)) {
      throw CatalogueReferenceNotFoundException.manufacturer(manufacturerId);
    }
  }

  private static CatalogueProduct toProduct(UpsertProductCommand command) {
    return new CatalogueProduct(
      command.id(),
      command.title(),
      command.slug(),
      command.family(),
      new CatalogueBrand(command.brandId(), null, null, true, null, null),
      new CatalogueManufacturer(command.manufacturerId(), null, null, true, null, null),
      command.mainImageUrl(),
      command.shortDescription(),
      command.active(),
      command.identifiers(),
      null,
      null
    );
  }
}
