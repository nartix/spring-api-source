package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.util.Optional;

public interface CatalogueReferenceDataRepository {
  CatalogueBrand saveBrand(CatalogueBrand brand);

  CatalogueManufacturer saveManufacturer(CatalogueManufacturer manufacturer);

  Optional<CatalogueBrand> findBrandById(Long id);

  Optional<CatalogueManufacturer> findManufacturerById(Long id);

  PageResult<CatalogueBrand> findBrands(ListReferenceDataQuery query);

  PageResult<CatalogueManufacturer> findManufacturers(ListReferenceDataQuery query);

  void deleteBrandById(Long id);

  void deleteManufacturerById(Long id);

  boolean existsBrandSlug(String slug, Long excludingId);

  boolean existsManufacturerSlug(String slug, Long excludingId);

  boolean existsBrandId(Long id);

  boolean existsManufacturerId(Long id);

  boolean brandHasProducts(Long id);

  boolean manufacturerHasProducts(Long id);
}
