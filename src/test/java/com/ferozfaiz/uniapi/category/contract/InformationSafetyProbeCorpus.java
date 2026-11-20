package com.ferozfaiz.uniapi.category.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact prohibited-value corpus and ID-only leak assertions for information-safety tests. */
public final class InformationSafetyProbeCorpus {

  private static final int REQUIRED_PROBE_COUNT = 20;
  private static final Pattern SAFE_PROBE_ID = Pattern.compile("P\\d{2}_[A-Z0-9_]+");

  private static final List<Probe> PROBES = validateCorpus(
    List.of(
      new Probe(
        "P01_SQL_STATEMENT",
        ProbeClass.SQL_STATEMENT,
        "SELECT secret_value FROM admin_contract_probe"
      ),
      new Probe("P02_TABLE_IDENTIFIER", ProbeClass.TABLE_IDENTIFIER, "admin_contract_probe_table"),
      new Probe("P03_COLUMN_IDENTIFIER", ProbeClass.COLUMN_IDENTIFIER, "secret_column"),
      new Probe("P04_INDEX_IDENTIFIER", ProbeClass.INDEX_IDENTIFIER, "idx_admin_contract_probe"),
      new Probe(
        "P05_SEQUENCE_IDENTIFIER",
        ProbeClass.SEQUENCE_IDENTIFIER,
        "admin_contract_probe_seq"
      ),
      new Probe(
        "P06_CLOSURE_TABLE_IDENTIFIER",
        ProbeClass.CLOSURE_TABLE_IDENTIFIER,
        "admin_contract_probe_closure"
      ),
      new Probe("P07_PROVIDER_CLASS", ProbeClass.PROVIDER_CLASS, "ProbeInternalProvider"),
      new Probe("P08_ADAPTER_CLASS", ProbeClass.ADAPTER_CLASS, "ProbeInternalAdapter"),
      new Probe("P09_BEAN_NAME", ProbeClass.BEAN_NAME, "probeInternalBean"),
      new Probe("P10_PACKAGE_NAME", ProbeClass.PACKAGE_NAME, "com.example.internal.probe"),
      new Probe(
        "P11_CONFIGURATION_PATH",
        ProbeClass.CONFIGURATION_PATH,
        "app.category.admin.jdbc.systems.probe.table"
      ),
      new Probe("P12_PERMISSION", ProbeClass.PERMISSION, "admin:probe-categories:write"),
      new Probe("P13_LOGICAL_POLICY_ID", ProbeClass.LOGICAL_POLICY_ID, "probe:secret-policy"),
      new Probe("P14_RAW_CURSOR", ProbeClass.RAW_CURSOR, "probe.raw.cursor+/="),
      new Probe("P15_BEARER_TOKEN", ProbeClass.BEARER_TOKEN, "Bearer probe-secret-token"),
      new Probe("P16_CREDENTIAL", ProbeClass.CREDENTIAL, "password=probe-credential"),
      new Probe("P17_SECRET", ProbeClass.SECRET, "probe-secret-value"),
      new Probe("P18_REQUEST_BODY", ProbeClass.REQUEST_BODY, "{\"probeRequest\":\"secret\"}"),
      new Probe(
        "P19_MERGE_PATCH_DOCUMENT",
        ProbeClass.MERGE_PATCH_DOCUMENT,
        "{\"active\":null,\"probePatch\":\"secret\"}"
      ),
      new Probe(
        "P20_RAW_EXCEPTION_OR_CAUSE",
        ProbeClass.RAW_EXCEPTION_OR_CAUSE,
        "probe raw cause message"
      )
    )
  );

  private static final List<String> CATEGORY_OWNED_STRUCTURED_FIELD_NAMES = List.of(
    "category.event",
    "systemKey",
    "operation",
    "outcome",
    "errorCode",
    "categoryId"
  );

  private static final List<String> PLATFORM_MDC_FIELD_NAMES = List.of("request.id");

  private InformationSafetyProbeCorpus() {}

  /** Returns the exact corpus in stable contract order. */
  public static List<Probe> probes() {
    return PROBES;
  }

  public static List<String> probeIds() {
    return PROBES.stream().map(Probe::id).toList();
  }

  public static List<String> prohibitedLiterals() {
    return PROBES.stream().map(Probe::literal).toList();
  }

  public static Probe probe(String probeId) {
    validateProbeId(probeId);
    return PROBES.stream()
      .filter(probe -> probe.id().equals(probeId))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unknown probe ID"));
  }

  /** The complete closed generic category-owned structured-field allow-list. */
  public static List<String> categoryOwnedStructuredFieldNames() {
    return CATEGORY_OWNED_STRUCTURED_FIELD_NAMES;
  }

  /** The independently platform-owned MDC allow-list relevant to this contract. */
  public static List<String> platformMdcFieldNames() {
    return PLATFORM_MDC_FIELD_NAMES;
  }

  public static List<String> findCaseSensitiveLeaks(CharSequence surface) {
    return findCaseSensitiveLeaks(List.of(requireSurface(surface)));
  }

  public static List<String> findCaseSensitiveLeaks(Iterable<? extends CharSequence> surfaces) {
    return findLeaks(snapshotSurfaces(surfaces), true);
  }

  public static List<String> findCaseInsensitiveLeaks(CharSequence surface) {
    return findCaseInsensitiveLeaks(List.of(requireSurface(surface)));
  }

  public static List<String> findCaseInsensitiveLeaks(Iterable<? extends CharSequence> surfaces) {
    return findLeaks(snapshotSurfaces(surfaces), false);
  }

  public static void assertNoCaseSensitiveLeaks(CharSequence surface) {
    assertNoCaseSensitiveLeaks(List.of(requireSurface(surface)));
  }

  public static void assertNoCaseSensitiveLeaks(Iterable<? extends CharSequence> surfaces) {
    assertNoLeaksById(findCaseSensitiveLeaks(surfaces), "case-sensitive");
  }

  public static void assertNoCaseInsensitiveLeaks(CharSequence surface) {
    assertNoCaseInsensitiveLeaks(List.of(requireSurface(surface)));
  }

  public static void assertNoCaseInsensitiveLeaks(Iterable<? extends CharSequence> surfaces) {
    assertNoLeaksById(findCaseInsensitiveLeaks(surfaces), "case-insensitive");
  }

  /** Runs both contract-required searches and reports only stable probe IDs. */
  public static void assertNoLeaks(CharSequence surface) {
    assertNoLeaks(List.of(requireSurface(surface)));
  }

  /** Runs both contract-required searches and reports only stable probe IDs. */
  public static void assertNoLeaks(Iterable<? extends CharSequence> surfaces) {
    List<String> snapshots = snapshotSurfaces(surfaces);
    LinkedHashSet<String> leakingProbeIds = new LinkedHashSet<>();
    leakingProbeIds.addAll(findLeaks(snapshots, true));
    leakingProbeIds.addAll(findLeaks(snapshots, false));
    assertNoLeaksById(List.copyOf(leakingProbeIds), "case-sensitive/case-insensitive");
  }

  public enum ProbeClass {
    SQL_STATEMENT,
    TABLE_IDENTIFIER,
    COLUMN_IDENTIFIER,
    INDEX_IDENTIFIER,
    SEQUENCE_IDENTIFIER,
    CLOSURE_TABLE_IDENTIFIER,
    PROVIDER_CLASS,
    ADAPTER_CLASS,
    BEAN_NAME,
    PACKAGE_NAME,
    CONFIGURATION_PATH,
    PERMISSION,
    LOGICAL_POLICY_ID,
    RAW_CURSOR,
    BEARER_TOKEN,
    CREDENTIAL,
    SECRET,
    REQUEST_BODY,
    MERGE_PATCH_DOCUMENT,
    RAW_EXCEPTION_OR_CAUSE,
  }

  public record Probe(String id, ProbeClass probeClass, String literal) {
    public Probe {
      validateProbeId(id);
      probeClass = Objects.requireNonNull(probeClass, "probeClass must not be null");
      if (literal == null || literal.isEmpty()) {
        throw new IllegalArgumentException("probe literal must not be empty");
      }
    }

    /** Prevents accidental assertion output from rendering a prohibited literal. */
    @Override
    public String toString() {
      return id;
    }
  }

  private static List<Probe> validateCorpus(List<Probe> probes) {
    Objects.requireNonNull(probes, "probes must not be null");
    if (probes.size() != REQUIRED_PROBE_COUNT) {
      throw new IllegalStateException("Information-safety corpus cardinality is invalid");
    }

    Set<String> ids = new HashSet<>();
    Set<ProbeClass> classes = new HashSet<>();
    Set<String> literals = new HashSet<>();
    for (Probe probe : probes) {
      Objects.requireNonNull(probe, "probe must not be null");
      if (
        !ids.add(probe.id()) || !classes.add(probe.probeClass()) || !literals.add(probe.literal())
      ) {
        throw new IllegalStateException("Information-safety corpus contains duplicates");
      }
    }
    return List.copyOf(probes);
  }

  private static List<String> findLeaks(List<String> surfaces, boolean caseSensitive) {
    List<String> comparableSurfaces = caseSensitive
      ? surfaces
      : surfaces
          .stream()
          .map(value -> value.toLowerCase(Locale.ROOT))
          .toList();
    List<String> leakingProbeIds = new ArrayList<>();

    for (Probe probe : PROBES) {
      String needle = caseSensitive ? probe.literal() : probe.literal().toLowerCase(Locale.ROOT);
      if (comparableSurfaces.stream().anyMatch(surface -> surface.contains(needle))) {
        leakingProbeIds.add(probe.id());
      }
    }
    return List.copyOf(leakingProbeIds);
  }

  private static List<String> snapshotSurfaces(Iterable<? extends CharSequence> surfaces) {
    Objects.requireNonNull(surfaces, "surfaces must not be null");
    List<String> snapshots = new ArrayList<>();
    for (CharSequence surface : surfaces) {
      snapshots.add(requireSurface(surface));
    }
    return List.copyOf(snapshots);
  }

  private static String requireSurface(CharSequence surface) {
    Objects.requireNonNull(surface, "surface must not be null");
    return surface.toString();
  }

  private static void assertNoLeaksById(List<String> leakingProbeIds, String searchMode) {
    if (!leakingProbeIds.isEmpty()) {
      throw new AssertionError(
        "Information-safety leak (" + searchMode + ") probe IDs: " + leakingProbeIds
      );
    }
  }

  private static void validateProbeId(String probeId) {
    if (probeId == null || !SAFE_PROBE_ID.matcher(probeId).matches()) {
      throw new IllegalArgumentException("probe ID is invalid");
    }
  }
}
