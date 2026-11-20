package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueReferenceDataRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueBrandEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity.CatalogueManufacturerEntity;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.mapper.CataloguePersistenceMapper;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueBrandJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueManufacturerJpaRepository;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.repository.CatalogueProductJpaRepository;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CatalogueReferenceDataRepositoryAdapter implements CatalogueReferenceDataRepository {

  private static final Map<String, String> SORT_PROPERTIES = Map.of(
    "name",
    "name",
    "slug",
    "slug",
    "createdAt",
    "createdAt",
    "updatedAt",
    "updatedAt",
    "id",
    "id"
  );

  private final CatalogueBrandJpaRepository brands;
  private final CatalogueManufacturerJpaRepository manufacturers;
  private final CatalogueProductJpaRepository products;

  public CatalogueReferenceDataRepositoryAdapter(
    CatalogueBrandJpaRepository brands,
    CatalogueManufacturerJpaRepository manufacturers,
    CatalogueProductJpaRepository products
  ) {
    this.brands = brands;
    this.manufacturers = manufacturers;
    this.products = products;
  }

  @Override
  @Transactional
  public CatalogueBrand saveBrand(CatalogueBrand brand) {
    CatalogueBrandEntity entity =
      brand.id() == null ? CataloguePersistenceMapper.toEntity(brand) : updateExistingBrand(brand);
    return CataloguePersistenceMapper.toDomain(brands.saveAndFlush(entity));
  }

  @Override
  @Transactional
  public CatalogueManufacturer saveManufacturer(CatalogueManufacturer manufacturer) {
    CatalogueManufacturerEntity entity =
      manufacturer.id() == null
        ? CataloguePersistenceMapper.toEntity(manufacturer)
        : updateExistingManufacturer(manufacturer);
    return CataloguePersistenceMapper.toDomain(manufacturers.saveAndFlush(entity));
  }

  @Override
  public Optional<CatalogueBrand> findBrandById(Long id) {
    return brands.findById(id).map(CataloguePersistenceMapper::toDomain);
  }

  @Override
  public Optional<CatalogueManufacturer> findManufacturerById(Long id) {
    return manufacturers.findById(id).map(CataloguePersistenceMapper::toDomain);
  }

  @Override
  public PageResult<CatalogueBrand> findBrands(ListReferenceDataQuery query) {
    Page<CatalogueBrandEntity> page = brands.findAll(
      referenceDataSpec(query),
      pageable(query.page().page(), query.page().size(), query.sort())
    );
    return pageResult(page.map(CataloguePersistenceMapper::toDomain), query);
  }

  @Override
  public PageResult<CatalogueManufacturer> findManufacturers(ListReferenceDataQuery query) {
    Page<CatalogueManufacturerEntity> page = manufacturers.findAll(
      referenceDataSpec(query),
      pageable(query.page().page(), query.page().size(), query.sort())
    );
    return pageResult(page.map(CataloguePersistenceMapper::toDomain), query);
  }

  @Override
  @Transactional
  public void deleteBrandById(Long id) {
    brands.deleteById(id);
  }

  @Override
  @Transactional
  public void deleteManufacturerById(Long id) {
    manufacturers.deleteById(id);
  }

  @Override
  public boolean existsBrandSlug(String slug, Long excludingId) {
    return excludingId == null
      ? brands.existsBySlug(slug)
      : brands.existsBySlugAndIdNot(slug, excludingId);
  }

  @Override
  public boolean existsManufacturerSlug(String slug, Long excludingId) {
    return excludingId == null
      ? manufacturers.existsBySlug(slug)
      : manufacturers.existsBySlugAndIdNot(slug, excludingId);
  }

  @Override
  public boolean existsBrandId(Long id) {
    return brands.existsById(id);
  }

  @Override
  public boolean existsManufacturerId(Long id) {
    return manufacturers.existsById(id);
  }

  @Override
  public boolean brandHasProducts(Long id) {
    return products.existsByBrandId(id);
  }

  @Override
  public boolean manufacturerHasProducts(Long id) {
    return products.existsByManufacturerId(id);
  }

  private CatalogueBrandEntity updateExistingBrand(CatalogueBrand brand) {
    CatalogueBrandEntity entity = brands
      .findById(brand.id())
      .orElseThrow(() -> CatalogueReferenceNotFoundException.catalogueBrand(brand.id()));
    entity.setName(brand.name());
    entity.setSlug(brand.slug());
    entity.setActive(brand.active());
    return entity;
  }

  private CatalogueManufacturerEntity updateExistingManufacturer(
    CatalogueManufacturer manufacturer
  ) {
    CatalogueManufacturerEntity entity = manufacturers
      .findById(manufacturer.id())
      .orElseThrow(() ->
        CatalogueReferenceNotFoundException.catalogueManufacturer(manufacturer.id())
      );
    entity.setName(manufacturer.name());
    entity.setSlug(manufacturer.slug());
    entity.setActive(manufacturer.active());
    return entity;
  }

  private static <T> Specification<T> referenceDataSpec(ListReferenceDataQuery query) {
    return (root, criteriaQuery, criteriaBuilder) -> {
      var predicates = new ArrayList<Predicate>();
      if (query.search() != null) {
        String pattern = "%" + query.search().toLowerCase(Locale.ROOT) + "%";
        predicates.add(
          criteriaBuilder.or(
            criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
            criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), pattern)
          )
        );
      }
      if (query.active() != null) {
        predicates.add(criteriaBuilder.equal(root.get("active"), query.active()));
      }
      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Pageable pageable(int page, int size, SortCriteria sort) {
    return PageRequest.of(page, size, toJpaSort(sort));
  }

  private static Sort toJpaSort(SortCriteria sort) {
    return Sort.by(
      sort
        .orders()
        .stream()
        .map(order -> {
          String property = SORT_PROPERTIES.get(order.property());
          if (property == null) {
            throw new IllegalArgumentException("unsupported reference data sort property");
          }
          return new Sort.Order(
            order.direction().name().equals("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
            property
          );
        })
        .toList()
    );
  }

  private static <T> PageResult<T> pageResult(Page<T> page, ListReferenceDataQuery query) {
    return new PageResult<>(
      page.getContent(),
      page.getNumber(),
      page.getSize(),
      page.getTotalElements(),
      page.getTotalPages(),
      page.isFirst(),
      page.isLast(),
      query.sort()
    );
  }
}
