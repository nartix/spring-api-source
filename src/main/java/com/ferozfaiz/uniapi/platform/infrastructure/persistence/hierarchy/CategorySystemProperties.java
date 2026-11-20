package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "uniapi")
public class CategorySystemProperties {

  private Map<String, SystemProperties> categorySystems = new LinkedHashMap<>();

  public Map<String, SystemProperties> getCategorySystems() {
    return Map.copyOf(categorySystems);
  }

  public void setCategorySystems(Map<String, SystemProperties> categorySystems) {
    this.categorySystems =
      categorySystems == null ? new LinkedHashMap<>() : new LinkedHashMap<>(categorySystems);
  }

  public static class SystemProperties {

    private String owningModule;
    private String nodeTable;
    private String closureTable;
    private String nodeIdColumn = "id";
    private String parentIdColumn = "parent_id";
    private String ancestorColumn = "ancestor_id";
    private String descendantColumn = "descendant_id";
    private String depthColumn = "depth";
    private RankingProperties ranking = new RankingProperties();
    private List<FallbackOrderProperties> fallbackOrder = new ArrayList<>();

    public String getOwningModule() {
      return owningModule;
    }

    public void setOwningModule(String owningModule) {
      this.owningModule = owningModule;
    }

    public String getNodeTable() {
      return nodeTable;
    }

    public void setNodeTable(String nodeTable) {
      this.nodeTable = nodeTable;
    }

    public String getClosureTable() {
      return closureTable;
    }

    public void setClosureTable(String closureTable) {
      this.closureTable = closureTable;
    }

    public String getNodeIdColumn() {
      return nodeIdColumn;
    }

    public void setNodeIdColumn(String nodeIdColumn) {
      this.nodeIdColumn = nodeIdColumn;
    }

    public String getParentIdColumn() {
      return parentIdColumn;
    }

    public void setParentIdColumn(String parentIdColumn) {
      this.parentIdColumn = parentIdColumn;
    }

    public String getAncestorColumn() {
      return ancestorColumn;
    }

    public void setAncestorColumn(String ancestorColumn) {
      this.ancestorColumn = ancestorColumn;
    }

    public String getDescendantColumn() {
      return descendantColumn;
    }

    public void setDescendantColumn(String descendantColumn) {
      this.descendantColumn = descendantColumn;
    }

    public String getDepthColumn() {
      return depthColumn;
    }

    public void setDepthColumn(String depthColumn) {
      this.depthColumn = depthColumn;
    }

    public RankingProperties getRanking() {
      return ranking;
    }

    public void setRanking(RankingProperties ranking) {
      this.ranking = ranking == null ? new RankingProperties() : ranking;
    }

    public List<FallbackOrderProperties> getFallbackOrder() {
      return List.copyOf(fallbackOrder);
    }

    public void setFallbackOrder(List<FallbackOrderProperties> fallbackOrder) {
      this.fallbackOrder =
        fallbackOrder == null ? new ArrayList<>() : new ArrayList<>(fallbackOrder);
    }
  }

  public static class RankingProperties {

    private boolean enabled;
    private String columnName = "sort_rank";
    private long startingRank = 1_000_000_000L;
    private long rankStep = 1_000_000_000L;
    private long maxSyncRebalanceRows = 1_000L;
    private DisabledRankingRequestPolicy disabledRequestPolicy =
      DisabledRankingRequestPolicy.REJECT;
    private List<PlacementMode> placementModes = new ArrayList<>(
      EnumSet.allOf(PlacementMode.class)
    );
    private boolean sortRankExposed = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      if (!enabled) {
        this.placementModes = new ArrayList<>(List.of(PlacementMode.APPEND));
        this.sortRankExposed = false;
      }
    }

    public String getColumnName() {
      return columnName;
    }

    public void setColumnName(String columnName) {
      this.columnName = columnName;
    }

    public long getStartingRank() {
      return startingRank;
    }

    public void setStartingRank(long startingRank) {
      this.startingRank = startingRank;
    }

    public long getRankStep() {
      return rankStep;
    }

    public void setRankStep(long rankStep) {
      this.rankStep = rankStep;
    }

    public long getMaxSyncRebalanceRows() {
      return maxSyncRebalanceRows;
    }

    public void setMaxSyncRebalanceRows(long maxSyncRebalanceRows) {
      this.maxSyncRebalanceRows = maxSyncRebalanceRows;
    }

    public DisabledRankingRequestPolicy getDisabledRequestPolicy() {
      return disabledRequestPolicy;
    }

    public void setDisabledRequestPolicy(DisabledRankingRequestPolicy disabledRequestPolicy) {
      this.disabledRequestPolicy =
        disabledRequestPolicy == null ? DisabledRankingRequestPolicy.REJECT : disabledRequestPolicy;
    }

    public List<PlacementMode> getPlacementModes() {
      return List.copyOf(placementModes);
    }

    public void setPlacementModes(List<PlacementMode> placementModes) {
      this.placementModes =
        placementModes == null ? new ArrayList<>() : new ArrayList<>(placementModes);
    }

    public boolean isSortRankExposed() {
      return sortRankExposed;
    }

    public void setSortRankExposed(boolean sortRankExposed) {
      this.sortRankExposed = sortRankExposed;
    }
  }

  public static class FallbackOrderProperties {

    private String property;
    private String columnName;
    private FallbackOrderDescriptor.Direction direction = FallbackOrderDescriptor.Direction.ASC;

    public String getProperty() {
      return property;
    }

    public void setProperty(String property) {
      this.property = property;
    }

    public String getColumnName() {
      return columnName;
    }

    public void setColumnName(String columnName) {
      this.columnName = columnName;
    }

    public FallbackOrderDescriptor.Direction getDirection() {
      return direction;
    }

    public void setDirection(FallbackOrderDescriptor.Direction direction) {
      this.direction = direction == null ? FallbackOrderDescriptor.Direction.ASC : direction;
    }
  }
}
