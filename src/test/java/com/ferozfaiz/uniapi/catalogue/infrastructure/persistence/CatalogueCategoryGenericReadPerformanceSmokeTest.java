package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class CatalogueCategoryGenericReadPerformanceSmokeTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  private static final int REQUESTS_PER_SHAPE = 20;
  private static final long ONE_SECOND_NANOS = Duration.ofSeconds(1).toNanos();

  @Autowired
  private MockMvc mvc;

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Test
  void genericCatalogueReadShapesStayWithinSmokeTarget() throws Exception {
    int datasetSize = seedCatalogueDataset();

    SmokeSummary list = measure(
      "list",
      "/api/v1/admin/category-systems/catalogue/categories?page=0&size=20&sort=updatedAt,desc&sort=id,asc"
    );
    SmokeSummary tree = measure("tree", "/api/v1/admin/category-systems/catalogue/categories/tree");
    SmokeSummary parentOptions = measure(
      "parent-options",
      "/api/v1/admin/category-systems/catalogue/categories/parent-options?limit=25"
    );

    System.out.println(
      "generic-category-read-smoke dataset=seeded-testcontainers-catalogue datasetSize=" +
        datasetSize +
        " configuredTreeLimit=10000 " +
        list +
        " " +
        tree +
        " " +
        parentOptions
    );

    assertThat(list.underOneSecond()).isGreaterThanOrEqualTo(19);
    assertThat(tree.underOneSecond()).isGreaterThanOrEqualTo(19);
    assertThat(parentOptions.underOneSecond()).isGreaterThanOrEqualTo(19);
  }

  private SmokeSummary measure(String name, String path) throws Exception {
    List<Long> timings = new ArrayList<>();
    for (int index = 0; index < REQUESTS_PER_SHAPE; index++) {
      long started = System.nanoTime();
      mvc.perform(get(path).with(readJwt())).andExpect(status().isOk());
      timings.add(System.nanoTime() - started);
    }
    return SmokeSummary.from(name, timings);
  }

  private int seedCatalogueDataset() {
    var root = createCategory("Components", "components", null);
    var storage = createCategory("Storage", "storage", root.id());
    createCategory("NVMe SSDs", "nvme-ssds", storage.id());
    createCategory("SATA SSDs", "sata-ssds", storage.id());
    var graphics = createCategory("Graphics", "graphics", root.id());
    createCategory("GPUs", "gpus", graphics.id());
    createCategory("External GPUs", "external-gpus", graphics.id());
    createCategory("Memory", "memory", root.id());
    createCategory("Power Supplies", "power-supplies", root.id());
    createCategory("Cases", "cases", root.id());
    createCategory("Cooling", "cooling", root.id());
    createCategory("Peripherals", "peripherals", null);
    return 12;
  }

  private CatalogueCategory createCategory(String name, String slug, Long parentId) {
    return categoryService.create(
      new UpsertCatalogueCategoryCommand(null, parentId, name, slug, true)
    );
  }

  private static RequestPostProcessor readJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:read"));
  }

  private record SmokeSummary(
    String name,
    long minMs,
    long p95Ms,
    long maxMs,
    long underOneSecond
  ) {
    static SmokeSummary from(String name, List<Long> timings) {
      List<Long> sorted = timings.stream().sorted(Comparator.naturalOrder()).toList();
      long underOneSecond = timings
        .stream()
        .filter(value -> value < ONE_SECOND_NANOS)
        .count();
      return new SmokeSummary(
        name,
        Duration.ofNanos(sorted.getFirst()).toMillis(),
        Duration.ofNanos(sorted.get(18)).toMillis(),
        Duration.ofNanos(sorted.getLast()).toMillis(),
        underOneSecond
      );
    }

    @Override
    public String toString() {
      return (
        name +
        "[minMs=" +
        minMs +
        ",p95Ms=" +
        p95Ms +
        ",maxMs=" +
        maxMs +
        ",under1s=" +
        underOneSecond +
        "/20]"
      );
    }
  }
}
