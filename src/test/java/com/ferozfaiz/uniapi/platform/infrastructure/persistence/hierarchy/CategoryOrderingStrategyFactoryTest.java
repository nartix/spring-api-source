package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CategoryOrderingStrategyFactoryTest {

  @Test
  void selectsExactlyOneStrategyForSystem() {
    UnrankedFallbackOrderingStrategy strategy = new UnrankedFallbackOrderingStrategy(
      UnrankedCategoryFixture.DESCRIPTOR
    );
    CategoryOrderingStrategyFactory factory = new CategoryOrderingStrategyFactory(
      List.of(strategy)
    );

    assertThat(factory.requireStrategy(UnrankedCategoryFixture.SYSTEM_KEY)).isSameAs(strategy);
  }

  @Test
  void rejectsDuplicateStrategiesForSameSystem() {
    UnrankedFallbackOrderingStrategy first = new UnrankedFallbackOrderingStrategy(
      UnrankedCategoryFixture.DESCRIPTOR
    );
    UnrankedFallbackOrderingStrategy second = new UnrankedFallbackOrderingStrategy(
      UnrankedCategoryFixture.DESCRIPTOR
    );

    assertThatThrownBy(() -> new CategoryOrderingStrategyFactory(List.of(first, second)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Duplicate");
  }

  @Test
  void rankOperationsFailFastForDisabledDescriptors() {
    SparseRankOperations operations = new SparseRankOperations(
      mock(NamedParameterJdbcTemplate.class)
    );

    assertThatThrownBy(() ->
      operations.assignRank(
        UnrankedCategoryFixture.DESCRIPTOR,
        10L,
        null,
        SparseOrderTarget.append()
      )
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("disabled");
  }
}
