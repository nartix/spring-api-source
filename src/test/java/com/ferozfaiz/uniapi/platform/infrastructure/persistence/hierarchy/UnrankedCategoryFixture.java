package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.List;
import java.util.Optional;

final class UnrankedCategoryFixture {

  static final String SYSTEM_KEY = "unranked-test-fixture";
  static final String NODE_TABLE = "test_unranked_category";
  static final String CLOSURE_TABLE = "test_unranked_category_closure";

  static final ClosureTableDescriptor CLOSURE = ClosureTableDescriptor.of(
    NODE_TABLE,
    CLOSURE_TABLE
  );

  static final CategorySystemDescriptor DESCRIPTOR = new CategorySystemDescriptor(
    SYSTEM_KEY,
    "test",
    CLOSURE,
    RankingCapabilityDescriptor.unranked(DisabledRankingRequestPolicy.REJECT),
    new FallbackOrderDescriptor(
      List.of(
        new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
        new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
      )
    ),
    Optional.empty()
  );

  private UnrankedCategoryFixture() {}
}
