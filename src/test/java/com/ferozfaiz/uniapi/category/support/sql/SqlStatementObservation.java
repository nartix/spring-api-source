package com.ferozfaiz.uniapi.category.support.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable, assertion-safe inventory of JDBC statement executions captured during one test
 * window.
 *
 * <p>Normalized SQL is retained privately for structural predicates. Public inventory values and
 * {@code toString()} use only statement kinds and one-way fingerprints, so assertion rendering
 * cannot disclose captured SQL.
 */
public final class SqlStatementObservation {

  private static final Pattern TOTAL_COUNT = Pattern.compile("\\bcount\\s*\\(");
  private static final Pattern IN_PARAMETER_LIST = Pattern.compile(
    "\\bin\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)*\\s*\\)"
  );

  private final Window window;
  private final List<Statement> statements;

  SqlStatementObservation(Window window, List<Statement> statements) {
    this.window = Objects.requireNonNull(window, "window");
    this.statements = List.copyOf(statements);
  }

  public Window window() {
    return window;
  }

  public int statementCount() {
    return statements.size();
  }

  /** Returns immutable summaries whose string representation never contains SQL text. */
  public List<Statement> statements() {
    return statements;
  }

  public List<StatementKind> statementKinds() {
    return statements.stream().map(Statement::kind).toList();
  }

  /**
   * Returns one-way SHA-256 fingerprints in execution order. A batch contributes one fingerprint
   * for each distinct normalized SQL shape used by that driver call.
   */
  public List<String> normalizedFingerprints() {
    return statements
      .stream()
      .flatMap(statement -> statement.fingerprints().stream())
      .toList();
  }

  /**
   * Returns structural fingerprints with JDBC-expanded {@code IN} bind-list arity collapsed.
   * Exact normalized fingerprints remain available through {@link #normalizedFingerprints()}.
   */
  public List<String> cardinalityNormalizedFingerprints() {
    return statements
      .stream()
      .flatMap(statement -> statement.cardinalityNormalizedFingerprints().stream())
      .toList();
  }

  /**
   * Performs a case-insensitive structural probe without returning the captured SQL to callers.
   */
  public boolean containsSqlFragment(String fragment) {
    String normalizedFragment = normalizeSql(Objects.requireNonNull(fragment, "fragment"));
    if (normalizedFragment.isEmpty()) {
      throw new IllegalArgumentException("SQL fragment must not be blank");
    }
    return statements
      .stream()
      .anyMatch(statement -> statement.containsSqlFragment(normalizedFragment));
  }

  /** Detects a {@code COUNT(...)} expression after comments and string literals are masked. */
  public boolean containsTotalCountQuery() {
    return statements.stream().anyMatch(Statement::containsTotalCountQuery);
  }

  @Override
  public boolean equals(Object candidate) {
    if (this == candidate) {
      return true;
    }
    if (!(candidate instanceof SqlStatementObservation other)) {
      return false;
    }
    return window == other.window && statements.equals(other.statements);
  }

  @Override
  public int hashCode() {
    return Objects.hash(window, statements);
  }

  @Override
  public String toString() {
    return (
      "SqlStatementObservation[window=" +
      window +
      ", statementCount=" +
      statementCount() +
      ", statementKinds=" +
      statementKinds() +
      ", normalizedFingerprints=" +
      normalizedFingerprints() +
      "]"
    );
  }

  static Statement statement(List<String> sqlTexts, int batchSize) {
    return new Statement(sqlTexts, batchSize);
  }

  static String normalizeSql(String sql) {
    StringBuilder normalized = new StringBuilder(sql.length());
    boolean previousWhitespace = true;
    for (int index = 0; index < sql.length(); index++) {
      char character = Character.toLowerCase(sql.charAt(index));
      if (Character.isWhitespace(character)) {
        if (!previousWhitespace) {
          normalized.append(' ');
          previousWhitespace = true;
        }
      } else {
        normalized.append(character);
        previousWhitespace = false;
      }
    }
    int length = normalized.length();
    if (length > 0 && normalized.charAt(length - 1) == ' ') {
      normalized.setLength(length - 1);
    }
    return normalized.toString();
  }

  private static String structuralSql(String sql) {
    return maskCommentsAndLiterals(sql, "?");
  }

  private static String cardinalityNormalizedSql(String sql) {
    String bindAwareStructuralSql = maskCommentsAndLiterals(sql, "@literal@");
    return IN_PARAMETER_LIST.matcher(bindAwareStructuralSql).replaceAll("in (?*)");
  }

  private static String maskCommentsAndLiterals(String sql, String literalMarker) {
    StringBuilder result = new StringBuilder(sql.length());
    ScanState state = ScanState.SQL;
    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
      switch (state) {
        case SQL -> {
          if (current == '-' && next == '-') {
            state = ScanState.LINE_COMMENT;
            result.append(' ');
            index++;
          } else if (current == '/' && next == '*') {
            state = ScanState.BLOCK_COMMENT;
            result.append(' ');
            index++;
          } else if (current == '\'') {
            state = ScanState.STRING_LITERAL;
            result.append(' ').append(literalMarker).append(' ');
          } else {
            result.append(current);
          }
        }
        case LINE_COMMENT -> {
          if (current == '\n' || current == '\r') {
            state = ScanState.SQL;
            result.append(' ');
          }
        }
        case BLOCK_COMMENT -> {
          if (current == '*' && next == '/') {
            state = ScanState.SQL;
            result.append(' ');
            index++;
          }
        }
        case STRING_LITERAL -> {
          if (current == '\'' && next == '\'') {
            index++;
          } else if (current == '\'') {
            state = ScanState.SQL;
          }
        }
      }
    }
    return normalizeSql(result.toString());
  }

  private static StatementKind classify(List<String> normalizedSql) {
    Set<StatementKind> kinds = new LinkedHashSet<>();
    for (String sql : normalizedSql) {
      kinds.add(classify(sql));
    }
    return kinds.size() == 1 ? kinds.iterator().next() : StatementKind.OTHER;
  }

  private static StatementKind classify(String sql) {
    String structural = structuralSql(sql);
    List<String> words = words(structural);
    if (words.isEmpty()) {
      return StatementKind.OTHER;
    }
    String first = words.getFirst();
    if (first.equals("with")) {
      for (String word : words) {
        StatementKind kind = dataStatementKind(word);
        if (kind != null) {
          return kind;
        }
      }
      return StatementKind.OTHER;
    }
    StatementKind dataKind = dataStatementKind(first);
    if (dataKind != null) {
      return dataKind;
    }
    return switch (first) {
      case "call" -> StatementKind.CALL;
      case "create", "alter", "drop", "truncate", "comment", "grant", "revoke" -> StatementKind.DDL;
      default -> StatementKind.OTHER;
    };
  }

  private static StatementKind dataStatementKind(String word) {
    return switch (word) {
      case "select" -> StatementKind.SELECT;
      case "insert" -> StatementKind.INSERT;
      case "update" -> StatementKind.UPDATE;
      case "delete" -> StatementKind.DELETE;
      case "merge" -> StatementKind.MERGE;
      default -> null;
    };
  }

  private static List<String> words(String sql) {
    List<String> words = new ArrayList<>();
    int start = -1;
    for (int index = 0; index <= sql.length(); index++) {
      boolean wordCharacter = index < sql.length() && Character.isLetter(sql.charAt(index));
      if (wordCharacter && start < 0) {
        start = index;
      } else if (!wordCharacter && start >= 0) {
        words.add(sql.substring(start, index).toLowerCase(Locale.ROOT));
        start = -1;
      }
    }
    return words;
  }

  private static String fingerprint(String normalizedSql) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        normalizedSql.getBytes(StandardCharsets.UTF_8)
      );
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", failure);
    }
  }

  public enum Window {
    SETUP,
    MEASUREMENT,
    TEARDOWN,
  }

  public enum StatementKind {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    MERGE,
    CALL,
    DDL,
    OTHER,
  }

  /** Immutable summary of one driver execution call. */
  public static final class Statement {

    private final StatementKind kind;
    private final List<String> normalizedSql;
    private final List<String> fingerprints;
    private final List<String> cardinalityNormalizedFingerprints;
    private final int batchSize;

    private Statement(List<String> sqlTexts, int batchSize) {
      if (batchSize < 0) {
        throw new IllegalArgumentException("batch size must not be negative");
      }
      LinkedHashSet<String> uniqueSql = new LinkedHashSet<>();
      for (String sql : sqlTexts) {
        if (sql != null && !sql.isBlank()) {
          uniqueSql.add(normalizeSql(sql));
        }
      }
      this.normalizedSql = List.copyOf(uniqueSql);
      this.kind = classify(normalizedSql);
      this.fingerprints = normalizedSql.stream().map(SqlStatementObservation::fingerprint).toList();
      this.cardinalityNormalizedFingerprints = normalizedSql
        .stream()
        .map(SqlStatementObservation::cardinalityNormalizedSql)
        .map(SqlStatementObservation::fingerprint)
        .toList();
      this.batchSize = batchSize;
    }

    public StatementKind kind() {
      return kind;
    }

    public List<String> fingerprints() {
      return fingerprints;
    }

    private List<String> cardinalityNormalizedFingerprints() {
      return cardinalityNormalizedFingerprints;
    }

    public int batchSize() {
      return batchSize;
    }

    public int sqlShapeCount() {
      return normalizedSql.size();
    }

    private boolean containsSqlFragment(String fragment) {
      return normalizedSql.stream().anyMatch(sql -> sql.contains(fragment));
    }

    private boolean containsTotalCountQuery() {
      return normalizedSql
        .stream()
        .map(SqlStatementObservation::structuralSql)
        .anyMatch(sql -> TOTAL_COUNT.matcher(sql).find());
    }

    @Override
    public boolean equals(Object candidate) {
      if (this == candidate) {
        return true;
      }
      if (!(candidate instanceof Statement other)) {
        return false;
      }
      return batchSize == other.batchSize && normalizedSql.equals(other.normalizedSql);
    }

    @Override
    public int hashCode() {
      return Objects.hash(normalizedSql, batchSize);
    }

    @Override
    public String toString() {
      return (
        "Statement[kind=" +
        kind +
        ", fingerprints=" +
        fingerprints +
        ", batchSize=" +
        batchSize +
        ", sqlShapeCount=" +
        sqlShapeCount() +
        "]"
      );
    }
  }

  private enum ScanState {
    SQL,
    LINE_COMMENT,
    BLOCK_COMMENT,
    STRING_LITERAL,
  }
}
