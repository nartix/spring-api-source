package com.ferozfaiz.uniapi.catalogue.application;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock.ResourceType;
import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogueReferenceDataApplicationService {

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  private final CatalogueReferenceDataRepository referenceData;
  private final CatalogueSlugWriteLock slugWriteLock;

  public CatalogueReferenceDataApplicationService(
    CatalogueReferenceDataRepository referenceData,
    CatalogueSlugWriteLock slugWriteLock
  ) {
    this.referenceData = referenceData;
    this.slugWriteLock = slugWriteLock;
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadBrand(authentication, null)")
  @Transactional(readOnly = true)
  public PageResult<CatalogueBrand> listBrands(ListReferenceDataQuery query) {
    return referenceData.findBrands(query);
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadBrand(authentication, #id)")
  @Transactional(readOnly = true)
  public CatalogueBrand getBrand(Long id) {
    return referenceData
      .findBrandById(id)
      .orElseThrow(() -> CatalogueReferenceNotFoundException.catalogueBrand(id));
  }

  public CatalogueBrand createOrUpdateBrand(UpsertBrandCommand command) {
    validateRequiredFields(command.name(), command.slug(), "brand");
    validateFieldShapes(command.name(), command.slug(), "brand");
    if (command.id() != null && !referenceData.existsBrandId(command.id())) {
      throw CatalogueReferenceNotFoundException.catalogueBrand(command.id());
    }
    slugWriteLock.lock(ResourceType.BRAND, command.slug());
    if (referenceData.existsBrandSlug(command.slug(), command.id())) {
      throw DuplicateCatalogueSlugException.brand(command.slug());
    }
    return referenceData.saveBrand(
      new CatalogueBrand(command.id(), command.name(), command.slug(), command.active(), null, null)
    );
  }

  public void deleteBrand(Long id) {
    getBrand(id);
    if (referenceData.brandHasProducts(id)) {
      throw CatalogueConflictException.brandDeleteBlocked(id);
    }
    referenceData.deleteBrandById(id);
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadManufacturer(authentication, null)")
  @Transactional(readOnly = true)
  public PageResult<CatalogueManufacturer> listManufacturers(ListReferenceDataQuery query) {
    return referenceData.findManufacturers(query);
  }

  @PreAuthorize("@catalogueReadAuthorizationPolicy.canReadManufacturer(authentication, #id)")
  @Transactional(readOnly = true)
  public CatalogueManufacturer getManufacturer(Long id) {
    return referenceData
      .findManufacturerById(id)
      .orElseThrow(() -> CatalogueReferenceNotFoundException.catalogueManufacturer(id));
  }

  public CatalogueManufacturer createOrUpdateManufacturer(UpsertManufacturerCommand command) {
    validateRequiredFields(command.name(), command.slug(), "manufacturer");
    validateFieldShapes(command.name(), command.slug(), "manufacturer");
    if (command.id() != null && !referenceData.existsManufacturerId(command.id())) {
      throw CatalogueReferenceNotFoundException.catalogueManufacturer(command.id());
    }
    slugWriteLock.lock(ResourceType.MANUFACTURER, command.slug());
    if (referenceData.existsManufacturerSlug(command.slug(), command.id())) {
      throw DuplicateCatalogueSlugException.manufacturer(command.slug());
    }
    return referenceData.saveManufacturer(
      new CatalogueManufacturer(
        command.id(),
        command.name(),
        command.slug(),
        command.active(),
        null,
        null
      )
    );
  }

  public void deleteManufacturer(Long id) {
    getManufacturer(id);
    if (referenceData.manufacturerHasProducts(id)) {
      throw CatalogueConflictException.manufacturerDeleteBlocked(id);
    }
    referenceData.deleteManufacturerById(id);
  }

  private static void validateRequiredFields(String name, String slug, String resource) {
    if (isBlank(name)) {
      throw new CatalogueException(
        "catalogue.application." + resource + ".name.required",
        resource + " name is required"
      );
    }
    if (isBlank(slug)) {
      throw new CatalogueException(
        "catalogue.application." + resource + ".slug.required",
        resource + " slug is required"
      );
    }
  }

  private static void validateFieldShapes(String name, String slug, String resource) {
    validateSize(
      name,
      255,
      "catalogue.api." + resource + ".name.size",
      resource + " name is too long"
    );
    if (!isBlank(slug) && (slug.length() > 180 || !SLUG_PATTERN.matcher(slug).matches())) {
      throw new CatalogueException(
        "catalogue.api.slug.pattern",
        "slug may contain lowercase letters, numbers, and hyphens",
        slug
      );
    }
  }

  private static void validateSize(String value, int max, String key, String message) {
    if (value != null && value.length() > max) {
      throw new CatalogueException(key, message, max);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
