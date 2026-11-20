package com.ferozfaiz.uniapi.category.support.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.boot.json.WritableJson;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Captures immutable snapshots of all information-bearing Logback event surfaces used by the
 * admin-category information-safety contract.
 */
public final class StructuredLogCapture implements AutoCloseable {

  private static final AtomicLong APPENDER_SEQUENCE = new AtomicLong();
  private static final Object LOGGER_STATE_MONITOR = new Object();
  private static final Map<Logger, LoggerCaptureState> ACTIVE_LOGGER_STATES =
    new IdentityHashMap<>();

  private final Logger logger;
  private final LoggerCaptureState loggerState;
  private final SnapshotListAppender appender;
  private final AtomicBoolean closed = new AtomicBoolean();

  private StructuredLogCapture(
    Logger logger,
    LoggerCaptureState loggerState,
    SnapshotListAppender appender
  ) {
    this.logger = logger;
    this.loggerState = loggerState;
    this.appender = appender;
  }

  public static StructuredLogCapture attach(Class<?> loggerType) {
    Objects.requireNonNull(loggerType, "loggerType must not be null");
    return attach(loggerType.getName());
  }

  public static StructuredLogCapture attach(String loggerName) {
    if (loggerName == null || loggerName.isBlank()) {
      throw new IllegalArgumentException("loggerName must not be blank");
    }

    org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(loggerName);
    if (!(slf4jLogger instanceof Logger logbackLogger)) {
      throw new IllegalStateException("StructuredLogCapture requires Logback");
    }
    return attach(logbackLogger);
  }

  public static StructuredLogCapture attach(Logger logger) {
    Objects.requireNonNull(logger, "logger must not be null");

    SnapshotListAppender appender = new SnapshotListAppender();
    appender.setContext(logger.getLoggerContext());
    appender.setName("structured-log-capture-" + APPENDER_SEQUENCE.incrementAndGet());
    appender.start();

    synchronized (LOGGER_STATE_MONITOR) {
      synchronized (logger) {
        LoggerCaptureState loggerState = null;
        try {
          loggerState = acquireLoggerState(logger);
          logger.addAppender(appender);
        } catch (RuntimeException | Error failure) {
          try {
            logger.detachAppender(appender);
          } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
          if (loggerState != null) {
            try {
              releaseLoggerState(logger, loggerState);
            } catch (RuntimeException | Error cleanupFailure) {
              failure.addSuppressed(cleanupFailure);
            }
          }
          try {
            appender.stop();
          } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
          throw failure;
        }

        return new StructuredLogCapture(logger, loggerState, appender);
      }
    }
  }

  /** Returns captured events in append order as an immutable point-in-time snapshot. */
  public List<CapturedEvent> events() {
    return appender.snapshots();
  }

  /** Returns the only captured event without rendering event data in assertion failures. */
  public CapturedEvent singleEvent() {
    List<CapturedEvent> events = events();
    if (events.size() != 1) {
      throw new AssertionError("Expected one captured log event but found " + events.size());
    }
    return events.getFirst();
  }

  public void clear() {
    appender.clearSnapshots();
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    synchronized (LOGGER_STATE_MONITOR) {
      synchronized (logger) {
        try {
          logger.detachAppender(appender);
        } finally {
          try {
            appender.stop();
          } finally {
            releaseLoggerState(logger, loggerState);
          }
        }
      }
    }
  }

  public record CapturedEvent(
    long captureIndex,
    Instant instant,
    long eventSequenceNumber,
    Level level,
    String loggerName,
    String threadName,
    String message,
    String formattedMessage,
    List<String> arguments,
    List<StructuredField> structuredFields,
    Map<String, String> mdc,
    List<MarkerSnapshot> markers,
    ThrowableSnapshot throwable,
    boolean transactionActive
  ) {
    public CapturedEvent {
      if (captureIndex < 0) {
        throw new IllegalArgumentException("captureIndex must not be negative");
      }
      instant = Objects.requireNonNull(instant, "instant must not be null");
      level = Objects.requireNonNull(level, "level must not be null");
      loggerName = nonNullText(loggerName);
      threadName = nonNullText(threadName);
      message = nonNullText(message);
      formattedMessage = nonNullText(formattedMessage);
      arguments = immutableList(arguments, "arguments");
      structuredFields = immutableList(structuredFields, "structuredFields");
      mdc = immutableSortedMap(mdc, "mdc");
      markers = immutableList(markers, "markers");
    }

    public Optional<ThrowableSnapshot> attachedThrowable() {
      return Optional.ofNullable(throwable);
    }

    public List<String> structuredFieldNames() {
      return structuredFields.stream().map(StructuredField::key).toList();
    }

    /**
     * Returns each captured value as a separate immutable surface. Keeping surfaces separate avoids
     * false matches formed only by joining unrelated fields.
     */
    public List<String> searchableSurfaces() {
      List<String> surfaces = new ArrayList<>();
      surfaces.add(loggerName);
      surfaces.add(threadName);
      surfaces.add(message);
      surfaces.add(formattedMessage);
      surfaces.addAll(arguments);
      structuredFields.forEach(field -> field.addSearchableSurfaces(surfaces));
      mdc.forEach((key, value) -> {
        surfaces.add(key);
        surfaces.add(value);
      });
      markers.forEach(marker -> marker.addSearchableSurfaces(surfaces));
      if (throwable != null) {
        throwable.addSearchableSurfaces(surfaces);
      }
      return List.copyOf(surfaces);
    }

    /** Keeps ordinary assertion rendering free of captured values. */
    @Override
    public String toString() {
      return (
        "CapturedEvent[captureIndex=" +
        captureIndex +
        ", eventSequenceNumber=" +
        eventSequenceNumber +
        ", level=" +
        level +
        ", argumentCount=" +
        arguments.size() +
        ", structuredFieldCount=" +
        structuredFields.size() +
        ", mdcEntryCount=" +
        mdc.size() +
        ", markerCount=" +
        markers.size() +
        ", throwableAttached=" +
        (throwable != null) +
        ", transactionActive=" +
        transactionActive +
        "]"
      );
    }

    private static CapturedEvent from(ILoggingEvent event, long captureIndex) {
      Objects.requireNonNull(event, "event must not be null");
      event.prepareForDeferredProcessing();

      return new CapturedEvent(
        captureIndex,
        eventInstant(event),
        event.getSequenceNumber(),
        Objects.requireNonNullElse(event.getLevel(), Level.TRACE),
        event.getLoggerName(),
        event.getThreadName(),
        event.getMessage(),
        event.getFormattedMessage(),
        snapshotArguments(event.getArgumentArray()),
        snapshotStructuredFields(event.getKeyValuePairs()),
        snapshotMdc(event.getMDCPropertyMap()),
        snapshotMarkers(event.getMarkerList()),
        ThrowableSnapshot.from(event.getThrowableProxy()),
        TransactionSynchronizationManager.isActualTransactionActive()
      );
    }

    private static Instant eventInstant(ILoggingEvent event) {
      Instant instant = event.getInstant();
      return instant != null ? instant : Instant.ofEpochMilli(event.getTimeStamp());
    }
  }

  public record StructuredField(String key, String value, List<String> nestedValueSurfaces) {
    public StructuredField {
      key = nonNullText(key);
      value = nonNullText(value);
      nestedValueSurfaces = immutableList(nestedValueSurfaces, "nestedValueSurfaces");
    }

    public StructuredField(String key, String value) {
      this(key, value, List.of());
    }

    private void addSearchableSurfaces(List<String> surfaces) {
      surfaces.add(key);
      surfaces.add(value);
      surfaces.addAll(nestedValueSurfaces);
    }

    /** Keeps ordinary assertion rendering free of structured keys and values. */
    @Override
    public String toString() {
      return (
        "StructuredField[keyPresent=" +
        !key.isEmpty() +
        ", valuePresent=" +
        !value.isEmpty() +
        ", nestedSurfaceCount=" +
        nestedValueSurfaces.size() +
        "]"
      );
    }
  }

  public record MarkerSnapshot(
    String name,
    String renderedValue,
    List<MarkerSnapshot> references,
    boolean cyclic
  ) {
    public MarkerSnapshot {
      name = nonNullText(name);
      renderedValue = nonNullText(renderedValue);
      references = immutableList(references, "references");
    }

    private static MarkerSnapshot from(Marker marker, IdentityHashMap<Marker, Boolean> activePath) {
      Objects.requireNonNull(marker, "marker must not be null");
      if (activePath.put(marker, Boolean.TRUE) != null) {
        return new MarkerSnapshot(marker.getName(), renderValue(marker), List.of(), true);
      }

      try {
        List<MarkerSnapshot> references = new ArrayList<>();
        Iterator<Marker> iterator = marker.iterator();
        while (iterator.hasNext()) {
          Marker reference = iterator.next();
          if (reference != null) {
            references.add(from(reference, activePath));
          }
        }
        return new MarkerSnapshot(marker.getName(), renderValue(marker), references, false);
      } finally {
        activePath.remove(marker);
      }
    }

    private void addSearchableSurfaces(List<String> surfaces) {
      surfaces.add(name);
      surfaces.add(renderedValue);
      references.forEach(reference -> reference.addSearchableSurfaces(surfaces));
    }

    /** Keeps ordinary assertion rendering free of marker names and rendered values. */
    @Override
    public String toString() {
      return ("MarkerSnapshot[referenceCount=" + references.size() + ", cyclic=" + cyclic + "]");
    }
  }

  public record ThrowableSnapshot(
    String className,
    String message,
    String overridingMessage,
    List<String> stackTrace,
    int commonFrames,
    boolean cyclic,
    List<ThrowableSnapshot> suppressed,
    ThrowableSnapshot cause
  ) {
    public ThrowableSnapshot {
      className = nonNullText(className);
      message = nonNullText(message);
      overridingMessage = nonNullText(overridingMessage);
      stackTrace = immutableList(stackTrace, "stackTrace");
      if (commonFrames < 0) {
        throw new IllegalArgumentException("commonFrames must not be negative");
      }
      suppressed = immutableList(suppressed, "suppressed");
    }

    public Optional<ThrowableSnapshot> causeOptional() {
      return Optional.ofNullable(cause);
    }

    private static ThrowableSnapshot from(IThrowableProxy throwable) {
      return from(throwable, new IdentityHashMap<>());
    }

    private static ThrowableSnapshot from(
      IThrowableProxy throwable,
      IdentityHashMap<IThrowableProxy, Boolean> activePath
    ) {
      if (throwable == null) {
        return null;
      }
      if (activePath.put(throwable, Boolean.TRUE) != null) {
        return new ThrowableSnapshot(
          throwable.getClassName(),
          throwable.getMessage(),
          throwable.getOverridingMessage(),
          snapshotStackTrace(throwable.getStackTraceElementProxyArray()),
          Math.max(throwable.getCommonFrames(), 0),
          true,
          List.of(),
          null
        );
      }

      try {
        List<ThrowableSnapshot> suppressed = new ArrayList<>();
        IThrowableProxy[] suppressedProxies = throwable.getSuppressed();
        if (suppressedProxies != null) {
          for (IThrowableProxy suppressedProxy : suppressedProxies) {
            if (suppressedProxy != null) {
              suppressed.add(from(suppressedProxy, activePath));
            }
          }
        }

        return new ThrowableSnapshot(
          throwable.getClassName(),
          throwable.getMessage(),
          throwable.getOverridingMessage(),
          snapshotStackTrace(throwable.getStackTraceElementProxyArray()),
          Math.max(throwable.getCommonFrames(), 0),
          throwable.isCyclic(),
          suppressed,
          throwable.isCyclic() ? null : from(throwable.getCause(), activePath)
        );
      } finally {
        activePath.remove(throwable);
      }
    }

    private void addSearchableSurfaces(List<String> surfaces) {
      surfaces.add(className);
      surfaces.add(message);
      surfaces.add(overridingMessage);
      surfaces.addAll(stackTrace);
      suppressed.forEach(value -> value.addSearchableSurfaces(surfaces));
      if (cause != null) {
        cause.addSearchableSurfaces(surfaces);
      }
    }

    /** Keeps ordinary assertion rendering free of throwable and stack-trace values. */
    @Override
    public String toString() {
      return (
        "ThrowableSnapshot[stackTraceElementCount=" +
        stackTrace.size() +
        ", commonFrames=" +
        commonFrames +
        ", cyclic=" +
        cyclic +
        ", suppressedCount=" +
        suppressed.size() +
        ", causeAttached=" +
        (cause != null) +
        "]"
      );
    }
  }

  private static final class LoggerCaptureState {

    private final Level originalLevel;
    private final boolean originalAdditive;
    private int activeCaptures;

    private LoggerCaptureState(Level originalLevel, boolean originalAdditive) {
      this.originalLevel = originalLevel;
      this.originalAdditive = originalAdditive;
    }

    private void retain() {
      activeCaptures++;
    }

    private int release() {
      if (activeCaptures <= 0) {
        throw new IllegalStateException("logger capture state is already released");
      }
      return --activeCaptures;
    }

    private boolean hasMultipleCaptures() {
      return activeCaptures > 1;
    }
  }

  private static final class SnapshotListAppender extends ListAppender<ILoggingEvent> {

    private final List<CapturedEvent> capturedEvents = new ArrayList<>();
    private long nextCaptureIndex;

    @Override
    protected synchronized void append(ILoggingEvent event) {
      super.append(event);
      capturedEvents.add(CapturedEvent.from(event, nextCaptureIndex++));
    }

    private synchronized List<CapturedEvent> snapshots() {
      return List.copyOf(capturedEvents);
    }

    private synchronized void clearSnapshots() {
      list.clear();
      capturedEvents.clear();
      nextCaptureIndex = 0;
    }
  }

  private static List<String> snapshotArguments(Object[] arguments) {
    if (arguments == null || arguments.length == 0) {
      return List.of();
    }

    List<String> snapshots = new ArrayList<>(arguments.length);
    for (Object argument : arguments) {
      snapshots.add(renderValue(argument));
    }
    return List.copyOf(snapshots);
  }

  private static List<StructuredField> snapshotStructuredFields(List<KeyValuePair> keyValuePairs) {
    if (keyValuePairs == null || keyValuePairs.isEmpty()) {
      return List.of();
    }

    List<StructuredField> snapshots = new ArrayList<>(keyValuePairs.size());
    for (KeyValuePair pair : keyValuePairs) {
      if (pair != null) {
        snapshots.add(
          new StructuredField(
            pair.key,
            renderValue(pair.value),
            snapshotNestedStructuredValueSurfaces(pair.value)
          )
        );
      }
    }
    return List.copyOf(snapshots);
  }

  private static List<String> snapshotNestedStructuredValueSurfaces(Object value) {
    List<String> surfaces = new ArrayList<>();
    addNestedStructuredValueSurfaces(value, surfaces, new IdentityHashMap<>());
    return List.copyOf(surfaces);
  }

  private static void addNestedStructuredValueSurfaces(
    Object value,
    List<String> surfaces,
    IdentityHashMap<Object, Boolean> activePath
  ) {
    if (value == null) {
      surfaces.add("null");
      return;
    }
    if (value instanceof WritableJson writableJson) {
      StringBuilder renderedJson = new StringBuilder();
      try {
        writableJson.to(renderedJson);
        surfaces.add(renderedJson.toString());
      } catch (IOException | RuntimeException | Error ignored) {
        surfaces.add("<unreadable>");
      }
      return;
    }

    Class<?> valueType = value.getClass();
    boolean recursive =
      valueType.isArray() || value instanceof Iterable<?> || value instanceof Map<?, ?>;
    if (!recursive) {
      surfaces.add(renderValue(value));
      return;
    }
    if (activePath.put(value, Boolean.TRUE) != null) {
      surfaces.add("<cycle>");
      return;
    }

    try {
      if (valueType.isArray()) {
        if (value instanceof char[] characters) {
          surfaces.add(new String(characters));
        }
        int length = Array.getLength(value);
        for (int index = 0; index < length; index++) {
          addNestedStructuredValueSurfaces(Array.get(value, index), surfaces, activePath);
        }
      } else if (value instanceof Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          addNestedStructuredValueSurfaces(entry.getKey(), surfaces, activePath);
          addNestedStructuredValueSurfaces(entry.getValue(), surfaces, activePath);
        }
      } else if (value instanceof Iterable<?> iterable) {
        for (Object element : iterable) {
          addNestedStructuredValueSurfaces(element, surfaces, activePath);
        }
      }
    } catch (RuntimeException | Error ignored) {
      surfaces.add("<unreadable>");
    } finally {
      activePath.remove(value);
    }
  }

  private static LoggerCaptureState acquireLoggerState(Logger logger) {
    LoggerCaptureState state = ACTIVE_LOGGER_STATES.get(logger);
    if (state != null) {
      forceCaptureLevel(logger);
      state.retain();
      return state;
    }

    state = new LoggerCaptureState(logger.getLevel(), logger.isAdditive());
    try {
      forceCaptureLevel(logger);
      state.retain();
      ACTIVE_LOGGER_STATES.put(logger, state);
      return state;
    } catch (RuntimeException | Error failure) {
      restoreLoggerState(logger, state);
      throw failure;
    }
  }

  private static void releaseLoggerState(Logger logger, LoggerCaptureState expectedState) {
    LoggerCaptureState activeState = ACTIVE_LOGGER_STATES.get(logger);
    if (activeState != expectedState) {
      throw new IllegalStateException("logger capture state does not match active state");
    }

    if (activeState.hasMultipleCaptures()) {
      forceCaptureLevel(logger);
      activeState.release();
      return;
    }

    activeState.release();
    ACTIVE_LOGGER_STATES.remove(logger);
    restoreLoggerState(logger, activeState);
  }

  @SuppressWarnings("deprecation")
  private static void forceCaptureLevel(Logger logger) {
    logger.setLevel(Level.ALL);
  }

  private static void restoreLoggerState(Logger logger, LoggerCaptureState state) {
    try {
      logger.setLevel(state.originalLevel);
    } finally {
      logger.setAdditive(state.originalAdditive);
    }
  }

  private static Map<String, String> snapshotMdc(Map<String, String> mdc) {
    if (mdc == null || mdc.isEmpty()) {
      return Map.of();
    }

    Map<String, String> snapshot = new TreeMap<>();
    mdc.forEach((key, value) -> snapshot.put(nonNullText(key), nonNullText(value)));
    return Collections.unmodifiableMap(snapshot);
  }

  private static List<MarkerSnapshot> snapshotMarkers(List<Marker> markers) {
    if (markers == null || markers.isEmpty()) {
      return List.of();
    }

    List<MarkerSnapshot> snapshots = new ArrayList<>(markers.size());
    for (Marker marker : markers) {
      if (marker != null) {
        snapshots.add(MarkerSnapshot.from(marker, new IdentityHashMap<>()));
      }
    }
    return List.copyOf(snapshots);
  }

  private static List<String> snapshotStackTrace(StackTraceElementProxy[] stackTrace) {
    if (stackTrace == null || stackTrace.length == 0) {
      return List.of();
    }

    List<String> snapshots = new ArrayList<>(stackTrace.length);
    for (StackTraceElementProxy element : stackTrace) {
      if (element != null) {
        snapshots.add(nonNullText(element.getSTEAsString()));
      }
    }
    return List.copyOf(snapshots);
  }

  private static String renderValue(Object value) {
    try {
      return nonNullText(MessageFormatter.basicArrayFormat("{}", new Object[] { value }));
    } catch (RuntimeException | Error ignored) {
      return "<unrenderable>";
    }
  }

  private static String nonNullText(String value) {
    return Objects.requireNonNullElse(value, "");
  }

  private static <T> List<T> immutableList(List<T> values, String name) {
    Objects.requireNonNull(values, name + " must not be null");
    return List.copyOf(values);
  }

  private static Map<String, String> immutableSortedMap(Map<String, String> values, String name) {
    Objects.requireNonNull(values, name + " must not be null");
    Map<String, String> copy = new TreeMap<>();
    values.forEach((key, value) ->
      copy.put(
        Objects.requireNonNull(key, name + " key must not be null"),
        Objects.requireNonNull(value, name + " value must not be null")
      )
    );
    return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
  }
}
