package com.ferozfaiz.uniapi.catalogue.domain.repository;

import com.ferozfaiz.uniapi.catalogue.domain.model.CataloguePrice;

public interface CataloguePriceRepository {
  CataloguePrice recordCurrentPrice(CataloguePrice price);
}
