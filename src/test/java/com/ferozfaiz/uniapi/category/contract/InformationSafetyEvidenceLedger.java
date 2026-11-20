package com.ferozfaiz.uniapi.category.contract;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryStartupInformationSafetyTest;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus.Probe;
import com.ferozfaiz.uniapi.category.contract.InformationSafetyProbeCorpus.ProbeClass;
import java.lang.StackWalker.StackFrame;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Complete executable probe-by-surface accounting for the T044 safety evidence. */
public final class InformationSafetyEvidenceLedger {

  private static final int REQUIRED_SURFACE_COUNT = 25;
  private static final StackWalker STACK_WALKER = StackWalker.getInstance(
    StackWalker.Option.RETAIN_CLASS_REFERENCE
  );
  private static final Map<LedgerKey, ObservedLedgerRow> OBSERVED_ROWS = new ConcurrentHashMap<>();
  private static final Set<ProvingTest> FINISHED_PROVING_TESTS = ConcurrentHashMap.newKeySet();
  private static final Object COMPLETION_MONITOR = new Object();

  private InformationSafetyEvidenceLedger() {}

  /** Returns the complete immutable contract expectation matrix. */
  public static List<LedgerRow> rows() {
    return LedgerRows.ROWS;
  }

  /** Fails on any missing, duplicate, blank, or structurally unexplained expectation cell. */
  public static void assertComplete() {
    if (Surface.values().length != REQUIRED_SURFACE_COUNT) {
      throw new AssertionError(
        "Information-safety surface cardinality mismatch: expected " + REQUIRED_SURFACE_COUNT
      );
    }

    List<LedgerRow> rows = rows();
    int expectedSize = InformationSafetyProbeCorpus.probes().size() * REQUIRED_SURFACE_COUNT;
    if (rows.size() != expectedSize) {
      throw new AssertionError(
        "Information-safety ledger cardinality mismatch: expected " +
          expectedSize +
          " but found " +
          rows.size()
      );
    }

    Set<LedgerKey> keys = new HashSet<>();
    for (LedgerRow row : rows) {
      if (!keys.add(new LedgerKey(row.probeId(), row.surface()))) {
        throw new AssertionError("Information-safety ledger contains a duplicate row");
      }
      row.statusOrCategory().validate();
      row.event().validate();
      row.level().validate();
      row.fieldNames().validate();
      row.throwablePresence().validate();
      row.transactionPhase().validate();
      row.provingTest().validate();
    }

    for (Probe probe : InformationSafetyProbeCorpus.probes()) {
      for (Surface surface : Surface.values()) {
        if (!keys.contains(new LedgerKey(probe.id(), surface))) {
          throw new AssertionError("Information-safety ledger is missing a probe/surface row");
        }
      }
    }
  }

  /**
   * Binds one parameterized probe execution to every actual captured surface owned by the calling
   * test. Every metadata cell and both occurrence counts are derived from the supplied observation.
   */
  public static void assertCovered(Probe probe, ActualEvidence... actualEvidence) {
    Objects.requireNonNull(probe, "probe");
    Objects.requireNonNull(actualEvidence, "actualEvidence");
    ProvingTest callingTest = ProvingTest.fromCurrentStack();
    Set<Surface> expectedSurfaces = callingTest.surfaces();
    if (actualEvidence.length != expectedSurfaces.size()) {
      throw new AssertionError(
        "Actual evidence cardinality mismatch for proving test " + callingTest.methodName()
      );
    }

    Map<Surface, ActualEvidence> actualBySurface = new LinkedHashMap<>();
    for (ActualEvidence actual : actualEvidence) {
      Objects.requireNonNull(actual, "actual evidence row");
      if (actualBySurface.put(actual.surface(), actual) != null) {
        throw new AssertionError("Actual evidence contains a duplicate surface");
      }
    }
    if (!actualBySurface.keySet().equals(expectedSurfaces)) {
      throw new AssertionError(
        "Actual evidence surface inventory mismatch for proving test " + callingTest.methodName()
      );
    }

    for (Surface surface : expectedSurfaces) {
      LedgerRow expected = expectedRow(probe, surface);
      ActualEvidence actual = actualBySurface.get(surface);
      ObservedLedgerRow observed = new ObservedLedgerRow(
        surface,
        probe.id(),
        probe.probeClass(),
        actual.statusOrCategory(),
        actual.event(),
        actual.level(),
        actual.fieldNames(),
        actual.throwablePresence(),
        actual.transactionPhase(),
        occurrenceCount(actual.renderedSurfaces(), probe.literal(), true),
        occurrenceCount(actual.renderedSurfaces(), probe.literal(), false),
        callingTest
      );
      assertMatches(expected, observed);
      LedgerKey key = new LedgerKey(probe.id(), surface);
      if (OBSERVED_ROWS.putIfAbsent(key, observed) != null) {
        throw new AssertionError(
          "Information-safety observation executed more than once for " + probe.id() + "/" + surface
        );
      }
    }
  }

  /**
   * Cross-class completion barrier. Each test class marks only its own proving tests finished from
   * {@code @AfterAll}; whichever class finishes last validates the complete 20 x 25 observed matrix.
   */
  public static void finishAndAssertObserved(ProvingTest... completedTests) {
    Objects.requireNonNull(completedTests, "completedTests");
    Class<?> callingTestClass = ProvingTest.testClassFromCurrentStack();
    synchronized (COMPLETION_MONITOR) {
      Set<ProvingTest> completedSubset = new HashSet<>();
      for (ProvingTest completedTest : completedTests) {
        Objects.requireNonNull(completedTest, "completed proving test");
        if (completedTest.testClass != callingTestClass) {
          throw new AssertionError("A test class may finish only its own proving tests");
        }
        if (!completedSubset.add(completedTest)) {
          throw new AssertionError("A proving test was declared finished more than once");
        }
      }
      assertObservedSubset(completedSubset);
      for (ProvingTest completedTest : completedSubset) {
        if (!FINISHED_PROVING_TESTS.add(completedTest)) {
          throw new AssertionError("A proving test was marked finished more than once");
        }
      }
      if (!FINISHED_PROVING_TESTS.equals(Set.of(ProvingTest.values()))) {
        return;
      }

      Set<LedgerKey> expectedKeys = rows()
        .stream()
        .map(row -> new LedgerKey(row.probeId(), row.surface()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
      if (!OBSERVED_ROWS.keySet().equals(expectedKeys)) {
        Set<LedgerKey> missing = new HashSet<>(expectedKeys);
        missing.removeAll(OBSERVED_ROWS.keySet());
        Set<LedgerKey> unexpected = new HashSet<>(OBSERVED_ROWS.keySet());
        unexpected.removeAll(expectedKeys);
        throw new AssertionError(
          "Observed information-safety ledger is incomplete: expected=" +
            expectedKeys.size() +
            ", observed=" +
            OBSERVED_ROWS.size() +
            ", missing=" +
            missing.size() +
            ", unexpected=" +
            unexpected.size()
        );
      }
      if (
        OBSERVED_ROWS.size() !=
        InformationSafetyProbeCorpus.probes().size() * REQUIRED_SURFACE_COUNT
      ) {
        throw new AssertionError("Observed information-safety ledger cardinality is invalid");
      }
    }
  }

  private static void assertObservedSubset(Set<ProvingTest> provingTests) {
    Set<LedgerKey> expectedKeys = rows()
      .stream()
      .filter(row -> provingTests.contains(row.provingTest()))
      .map(row -> new LedgerKey(row.probeId(), row.surface()))
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
    Set<LedgerKey> observedKeys = OBSERVED_ROWS.entrySet()
      .stream()
      .filter(entry -> provingTests.contains(entry.getValue().provingTest()))
      .map(Map.Entry::getKey)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!observedKeys.equals(expectedKeys)) {
      Set<LedgerKey> missing = new HashSet<>(expectedKeys);
      missing.removeAll(observedKeys);
      Set<LedgerKey> unexpected = new HashSet<>(observedKeys);
      unexpected.removeAll(expectedKeys);
      throw new AssertionError(
        "Observed information-safety subset is incomplete: expected=" +
          expectedKeys.size() +
          ", observed=" +
          observedKeys.size() +
          ", missing=" +
          missing.size() +
          ", unexpected=" +
          unexpected.size()
      );
    }
  }

  private static LedgerRow expectedRow(Probe probe, Surface surface) {
    return rows()
      .stream()
      .filter(row -> row.probeId().equals(probe.id()) && row.surface() == surface)
      .findFirst()
      .orElseThrow(() -> new AssertionError("Information-safety expectation row is missing"));
  }

  private static void assertMatches(LedgerRow expected, ObservedLedgerRow actual) {
    if (
      expected.surface() != actual.surface() ||
      !expected.probeId().equals(actual.probeId()) ||
      expected.injectedProbeClass() != actual.injectedProbeClass() ||
      expected.provingTest() != actual.provingTest()
    ) {
      throw new AssertionError("Observed information-safety row identity does not match contract");
    }
    assertCell(expected, "status/category", expected.statusOrCategory(), actual.statusOrCategory());
    assertCell(expected, "event", expected.event(), actual.event());
    assertCell(expected, "level", expected.level(), actual.level());
    assertCell(expected, "field names", expected.fieldNames(), actual.fieldNames());
    assertCell(
      expected,
      "throwable presence",
      expected.throwablePresence(),
      actual.throwablePresence()
    );
    assertCell(
      expected,
      "transaction phase",
      expected.transactionPhase(),
      actual.transactionPhase()
    );
    if (
      actual.caseSensitiveOccurrenceCount() != 0 || actual.caseInsensitiveOccurrenceCount() != 0
    ) {
      throw new AssertionError(
        "Information-safety occurrence count is nonzero for probe " +
          expected.probeId() +
          " on surface " +
          expected.surface()
      );
    }
  }

  private static void assertCell(
    LedgerRow row,
    String cell,
    EvidenceValue expected,
    ActualValue actual
  ) {
    actual.validate();
    if (
      !expected.observation().equals(actual.observation()) ||
      !Objects.equals(
        expected.structuralNotApplicableReason(),
        actual.structuralNotApplicableReason()
      )
    ) {
      throw new AssertionError(
        "Actual " + cell + " does not match contract for " + row.probeId() + "/" + row.surface()
      );
    }
  }

  private static int occurrenceCount(
    List<String> renderedSurfaces,
    String prohibitedLiteral,
    boolean caseSensitive
  ) {
    String needle = caseSensitive ? prohibitedLiteral : prohibitedLiteral.toLowerCase(Locale.ROOT);
    int occurrences = 0;
    for (String renderedSurface : renderedSurfaces) {
      String haystack = caseSensitive ? renderedSurface : renderedSurface.toLowerCase(Locale.ROOT);
      int offset = 0;
      while ((offset = haystack.indexOf(needle, offset)) >= 0) {
        occurrences++;
        offset += needle.length();
      }
    }
    return occurrences;
  }

  public enum Surface {
    HTTP_RESPONSE_BODY(
      applicable("HTTP 404 / UNKNOWN_CATEGORY_SYSTEM"),
      notApplicable("an HTTP response body is structurally not a log event"),
      notApplicable("an HTTP response body has no log level"),
      applicable("code,fieldErrors,message,path,timestamp"),
      applicable("absent; nested injected cause is not serialized"),
      applicable("before provider work and outside a provider transaction"),
      ProvingTest.PUBLIC_ERROR
    ),
    HTTP_RESPONSE_HEADERS(
      applicable("HTTP 404 / UNKNOWN_CATEGORY_SYSTEM"),
      notApplicable("HTTP response headers are structurally not a log event"),
      notApplicable("HTTP response headers have no log level"),
      applicable(
        "Cache-Control,Content-Type,Expires,Pragma,X-Content-Type-Options,X-Frame-Options,X-Request-Id,X-XSS-Protection"
      ),
      notApplicable("HTTP headers have no throwable slot"),
      applicable("before provider work and outside a provider transaction"),
      ProvingTest.PUBLIC_ERROR
    ),
    GENERIC_LOG_MESSAGE(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("message=unknown category system"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_FORMATTED_MESSAGE(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("formattedMessage=unknown category system"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_ARGUMENTS(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("exactly no logger arguments"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_KEY_VALUES(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("category.event,systemKey,outcome,errorCode"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_MDC(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("request.id"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_MARKERS(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("exactly no markers"),
      applicable("absent"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    GENERIC_LOG_THROWABLE(
      notApplicable("a generic application log is structurally not an HTTP/startup status"),
      applicable("system.unknown"),
      applicable("WARN"),
      applicable("exactly no attached throwable fields"),
      applicable("absent, including cause and suppressed chains"),
      applicable("before provider work and outside a transaction"),
      ProvingTest.GENERIC_LOG
    ),
    STARTUP_EXCEPTION_MESSAGE(
      applicable("startup / invalid-collection"),
      notApplicable("a typed startup exception is structurally not a category log event"),
      notApplicable("an exception message has no log level"),
      applicable("systemKey,category,component"),
      applicable("cause-free and suppressed-free"),
      applicable("during registry construction before publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_DIAGNOSTIC_FIELDS(
      applicable("startup / invalid-collection"),
      notApplicable("typed diagnostic fields are structurally not a category log event"),
      notApplicable("typed diagnostic fields have no log level"),
      applicable("systemKey,category,component,capability,policyObligation"),
      applicable("diagnostic object retains no throwable"),
      applicable("during registry construction before publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_MESSAGE(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("Spring context cancellation message"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_FORMATTED_MESSAGE(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("formatted Spring context cancellation message plus sanitized exception"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_ARGUMENTS(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("exactly no logger arguments"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_KEY_VALUES(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("exactly no key/value fields"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_MDC(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("exactly no MDC fields"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_MARKERS(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("exactly no markers"),
      applicable("no attached throwable"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_LOG_THROWABLE(
      applicable("startup / context refresh rejected"),
      notApplicable("the Spring lifecycle warning has no category-owned event field"),
      applicable("WARN"),
      applicable("exactly no attached throwable fields"),
      applicable("absent; raw source cause was discarded before logging"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    STARTUP_CONSOLE_OUTPUT(
      applicable("startup / context refresh rejected"),
      notApplicable("rendered console output has no category-owned event field"),
      applicable("WARN lifecycle output"),
      applicable("captured stdout and stderr as one complete transport surface"),
      notApplicable("rendered console text has no attached throwable object slot"),
      applicable("during failed refresh before registry publication"),
      ProvingTest.STARTUP
    ),
    SQL_DATASOURCE_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("a datasource rendering is structurally not a log event"),
      notApplicable("a datasource rendering has no log level"),
      applicable("redacted datasource rendering"),
      notApplicable("a datasource rendering has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    ),
    SQL_CONNECTION_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("a connection rendering is structurally not a log event"),
      notApplicable("a connection rendering has no log level"),
      applicable("redacted connection rendering"),
      notApplicable("a connection rendering has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    ),
    SQL_STATEMENT_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("a statement rendering is structurally not a log event"),
      notApplicable("a statement rendering has no log level"),
      applicable("redacted JDBC statement rendering"),
      notApplicable("a statement rendering has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    ),
    SQL_OBSERVATION_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("an observation rendering is structurally not a log event"),
      notApplicable("an observation rendering has no log level"),
      applicable("window,statementCount,statementKinds,normalizedFingerprints"),
      notApplicable("an observation rendering has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    ),
    SQL_STATEMENT_SUMMARY_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("a statement summary is structurally not a log event"),
      notApplicable("a statement summary has no log level"),
      applicable("kind,fingerprints,batchSize,sqlShapeCount"),
      notApplicable("a statement summary has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    ),
    SQL_FINGERPRINT_RENDERING(
      notApplicable("a test SQL capture is structurally not an HTTP/startup status"),
      notApplicable("a fingerprint rendering is structurally not a log event"),
      notApplicable("a fingerprint rendering has no log level"),
      applicable("one SHA-256 fingerprint"),
      notApplicable("a fingerprint rendering has no throwable slot"),
      applicable("test-only measurement window outside a transaction"),
      ProvingTest.SQL_CAPTURE
    );

    private final EvidenceValue statusOrCategory;
    private final EvidenceValue event;
    private final EvidenceValue level;
    private final EvidenceValue fieldNames;
    private final EvidenceValue throwablePresence;
    private final EvidenceValue transactionPhase;
    private final ProvingTest provingTest;

    Surface(
      EvidenceValue statusOrCategory,
      EvidenceValue event,
      EvidenceValue level,
      EvidenceValue fieldNames,
      EvidenceValue throwablePresence,
      EvidenceValue transactionPhase,
      ProvingTest provingTest
    ) {
      this.statusOrCategory = statusOrCategory;
      this.event = event;
      this.level = level;
      this.fieldNames = fieldNames;
      this.throwablePresence = throwablePresence;
      this.transactionPhase = transactionPhase;
      this.provingTest = provingTest;
    }
  }

  public record ActualEvidence(
    Surface surface,
    ActualValue statusOrCategory,
    ActualValue event,
    ActualValue level,
    ActualValue fieldNames,
    ActualValue throwablePresence,
    ActualValue transactionPhase,
    List<String> renderedSurfaces
  ) {
    public ActualEvidence {
      Objects.requireNonNull(surface, "surface");
      Objects.requireNonNull(statusOrCategory, "statusOrCategory");
      Objects.requireNonNull(event, "event");
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(fieldNames, "fieldNames");
      Objects.requireNonNull(throwablePresence, "throwablePresence");
      Objects.requireNonNull(transactionPhase, "transactionPhase");
      Objects.requireNonNull(renderedSurfaces, "renderedSurfaces");
      renderedSurfaces = renderedSurfaces
        .stream()
        .map(value -> Objects.requireNonNull(value, "rendered surface"))
        .toList();
    }
  }

  public record ActualValue(String observation, String structuralNotApplicableReason) {
    public ActualValue {
      if (observation == null || observation.isBlank()) {
        throw new IllegalArgumentException("actual observation must not be blank");
      }
      if (structuralNotApplicableReason != null && structuralNotApplicableReason.isBlank()) {
        throw new IllegalArgumentException("actual structural N/A reason must not be blank");
      }
    }

    public static ActualValue observed(String observation) {
      return new ActualValue(observation, null);
    }

    public static ActualValue observedWhen(
      String observation,
      boolean actualFact,
      String factDescription
    ) {
      if (!actualFact) {
        throw new AssertionError("Actual evidence fact was false: " + factDescription);
      }
      return observed(observation);
    }

    public static ActualValue structurallyAbsent(String structuralReason, boolean actualAbsence) {
      if (!actualAbsence) {
        throw new AssertionError("Claimed structural absence was not observed");
      }
      return new ActualValue("N/A", structuralReason);
    }

    private boolean notApplicable() {
      return structuralNotApplicableReason != null;
    }

    private void validate() {
      if (notApplicable() && !observation.equals("N/A")) {
        throw new AssertionError("actual structural N/A evidence must use the closed N/A token");
      }
      if (!notApplicable() && observation.equals("N/A")) {
        throw new AssertionError("actual N/A evidence must include a structural reason");
      }
    }
  }

  public record LedgerRow(
    Surface surface,
    String probeId,
    ProbeClass injectedProbeClass,
    EvidenceValue statusOrCategory,
    EvidenceValue event,
    EvidenceValue level,
    EvidenceValue fieldNames,
    EvidenceValue throwablePresence,
    EvidenceValue transactionPhase,
    ProvingTest provingTest
  ) {
    public LedgerRow {
      Objects.requireNonNull(surface, "surface");
      Objects.requireNonNull(injectedProbeClass, "injectedProbeClass");
      Objects.requireNonNull(statusOrCategory, "statusOrCategory");
      Objects.requireNonNull(event, "event");
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(fieldNames, "fieldNames");
      Objects.requireNonNull(throwablePresence, "throwablePresence");
      Objects.requireNonNull(transactionPhase, "transactionPhase");
      Objects.requireNonNull(provingTest, "provingTest");
      if (probeId == null || probeId.isBlank()) {
        throw new IllegalArgumentException("probeId must not be blank");
      }
    }
  }

  private record ObservedLedgerRow(
    Surface surface,
    String probeId,
    ProbeClass injectedProbeClass,
    ActualValue statusOrCategory,
    ActualValue event,
    ActualValue level,
    ActualValue fieldNames,
    ActualValue throwablePresence,
    ActualValue transactionPhase,
    int caseSensitiveOccurrenceCount,
    int caseInsensitiveOccurrenceCount,
    ProvingTest provingTest
  ) {}

  public record EvidenceValue(String observation, String structuralNotApplicableReason) {
    public EvidenceValue {
      if (observation == null || observation.isBlank()) {
        throw new IllegalArgumentException("evidence observation must not be blank");
      }
      if (structuralNotApplicableReason != null && structuralNotApplicableReason.isBlank()) {
        throw new IllegalArgumentException("structural N/A reason must not be blank");
      }
    }

    public boolean notApplicable() {
      return structuralNotApplicableReason != null;
    }

    private void validate() {
      if (notApplicable() && !observation.equals("N/A")) {
        throw new AssertionError("structural N/A evidence must use the closed N/A token");
      }
      if (!notApplicable() && observation.equals("N/A")) {
        throw new AssertionError("N/A evidence must include a structural reason");
      }
    }
  }

  public enum ProvingTest {
    PUBLIC_ERROR(
      AdminCategoryInformationSafetyContractTest.class,
      "publicErrorBodyAndHeadersNeverRenderNestedInternalProbeCauses"
    ),
    GENERIC_LOG(
      AdminCategoryInformationSafetyContractTest.class,
      "everyProbeFlowsThroughRealGenericUnknownLoggingWithoutReachingAnyLogSurface"
    ),
    SQL_CAPTURE(
      AdminCategoryInformationSafetyContractTest.class,
      "executableSqlCaptureRetainsProbePrivatelyButPublishesOnlySafeShapeEvidence"
    ),
    STARTUP(
      AdminCategoryStartupInformationSafetyTest.class,
      "realApplicationContextStartupFailureSanitizesLogConsoleAndCauseChain"
    );

    private final Class<?> testClass;
    private final String methodName;

    ProvingTest(Class<?> testClass, String methodName) {
      this.testClass = testClass;
      this.methodName = methodName;
    }

    String methodName() {
      return methodName;
    }

    Set<Surface> surfaces() {
      Set<Surface> result = new HashSet<>();
      for (Surface surface : Surface.values()) {
        if (surface.provingTest == this) {
          result.add(surface);
        }
      }
      return Set.copyOf(result);
    }

    private boolean matches(StackFrame frame) {
      return frame.getDeclaringClass() == testClass && frame.getMethodName().equals(methodName);
    }

    private void validate() {
      boolean executableTest = Arrays.stream(testClass.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .flatMap(method -> Arrays.stream(method.getDeclaredAnnotations()))
        .map(Annotation::annotationType)
        .map(Class::getSimpleName)
        .anyMatch(name -> name.equals("Test") || name.equals("ParameterizedTest"));
      if (!executableTest) {
        throw new AssertionError("Ledger proving-test binding does not resolve to a JUnit test");
      }
    }

    private static ProvingTest fromCurrentStack() {
      List<ProvingTest> matches = STACK_WALKER.walk(frames ->
        frames.flatMap(ProvingTest::matchingTests).distinct().toList()
      );
      if (matches.size() != 1) {
        throw new AssertionError("Ledger coverage call is not bound to one approved proving test");
      }
      return matches.getFirst();
    }

    private static Class<?> testClassFromCurrentStack() {
      List<Class<?>> matches = STACK_WALKER.walk(frames ->
        frames
          .map(StackFrame::getDeclaringClass)
          .filter(candidate ->
            Arrays.stream(values()).anyMatch(test -> test.testClass == candidate)
          )
          .distinct()
          .toList()
      );
      if (matches.size() != 1) {
        throw new AssertionError("Completion gate is not bound to one approved test class");
      }
      return matches.getFirst();
    }

    private static Stream<ProvingTest> matchingTests(StackFrame frame) {
      return Arrays.stream(values()).filter(test -> test.matches(frame));
    }
  }

  private static EvidenceValue applicable(String observation) {
    return new EvidenceValue(observation, null);
  }

  private static EvidenceValue notApplicable(String structuralReason) {
    return new EvidenceValue("N/A", structuralReason);
  }

  private static final class LedgerRows {

    private static final List<LedgerRow> ROWS = InformationSafetyProbeCorpus.probes()
      .stream()
      .flatMap(probe ->
        Arrays.stream(Surface.values()).map(surface ->
          new LedgerRow(
            surface,
            probe.id(),
            probe.probeClass(),
            surface.statusOrCategory,
            surface.event,
            surface.level,
            surface.fieldNames,
            surface.throwablePresence,
            surface.transactionPhase,
            surface.provingTest
          )
        )
      )
      .toList();

    private LedgerRows() {}
  }

  private record LedgerKey(String probeId, Surface surface) {}
}
