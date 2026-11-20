package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.category.admin.jdbc")
public class AdminCategoryJdbcProviderProperties {

  private List<String> enabledSystems = new ArrayList<>();
  private Map<String, SystemProperties> systems = new LinkedHashMap<>();

  public List<String> getEnabledSystems() {
    return List.copyOf(enabledSystems);
  }

  public void setEnabledSystems(List<String> enabledSystems) {
    this.enabledSystems =
      enabledSystems == null ? new ArrayList<>() : new ArrayList<>(enabledSystems);
  }

  public Map<String, SystemProperties> getSystems() {
    return Map.copyOf(systems);
  }

  public void setSystems(Map<String, SystemProperties> systems) {
    this.systems = systems == null ? new LinkedHashMap<>() : new LinkedHashMap<>(systems);
  }

  public List<String> enabledSystemKeys() {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String configuredKey : enabledSystems) {
      String systemKey = requireText(configuredKey, "enabled system");
      if (!normalized.add(systemKey)) {
        throw new IllegalArgumentException(
          "Duplicate descriptor-backed category system: " + systemKey
        );
      }
    }
    return List.copyOf(normalized);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    String text = value.trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return text;
  }

  public enum SlugUniqueness {
    GLOBAL,
    SYSTEM_SCOPED,
  }

  public enum IdGeneration {
    DATABASE_GENERATED,
    APPLICATION_ASSIGNED,
  }

  public enum TimestampStrategy {
    DATABASE_MANAGED,
    APPLICATION_MANAGED,
  }

  public static class SystemProperties {

    private String nameColumn = "name";
    private String slugColumn = "slug";
    private String activeColumn = "active";
    private String createdAtColumn = "created_at";
    private String updatedAtColumn = "updated_at";
    private SlugUniqueness slugUniqueness = SlugUniqueness.GLOBAL;
    private IdGeneration idGeneration = IdGeneration.DATABASE_GENERATED;
    private TimestampProperties timestamps = new TimestampProperties();

    public String getNameColumn() {
      return nameColumn;
    }

    public void setNameColumn(String nameColumn) {
      this.nameColumn = nameColumn;
    }

    public String getSlugColumn() {
      return slugColumn;
    }

    public void setSlugColumn(String slugColumn) {
      this.slugColumn = slugColumn;
    }

    public String getActiveColumn() {
      return activeColumn;
    }

    public void setActiveColumn(String activeColumn) {
      this.activeColumn = activeColumn;
    }

    public String getCreatedAtColumn() {
      return createdAtColumn;
    }

    public void setCreatedAtColumn(String createdAtColumn) {
      this.createdAtColumn = createdAtColumn;
    }

    public String getUpdatedAtColumn() {
      return updatedAtColumn;
    }

    public void setUpdatedAtColumn(String updatedAtColumn) {
      this.updatedAtColumn = updatedAtColumn;
    }

    public SlugUniqueness getSlugUniqueness() {
      return slugUniqueness;
    }

    public void setSlugUniqueness(SlugUniqueness slugUniqueness) {
      this.slugUniqueness = slugUniqueness == null ? SlugUniqueness.GLOBAL : slugUniqueness;
    }

    public IdGeneration getIdGeneration() {
      return idGeneration;
    }

    public void setIdGeneration(IdGeneration idGeneration) {
      this.idGeneration = idGeneration == null ? IdGeneration.DATABASE_GENERATED : idGeneration;
    }

    public TimestampProperties getTimestamps() {
      return timestamps;
    }

    public void setTimestamps(TimestampProperties timestamps) {
      this.timestamps = timestamps == null ? new TimestampProperties() : timestamps;
    }
  }

  public static class TimestampProperties {

    private TimestampStrategy strategy = TimestampStrategy.DATABASE_MANAGED;
    private String updateExpression = "CURRENT_TIMESTAMP";

    public TimestampStrategy getStrategy() {
      return strategy;
    }

    public void setStrategy(TimestampStrategy strategy) {
      this.strategy = strategy == null ? TimestampStrategy.DATABASE_MANAGED : strategy;
    }

    public String getUpdateExpression() {
      return updateExpression;
    }

    public void setUpdateExpression(String updateExpression) {
      this.updateExpression = updateExpression;
    }
  }
}
