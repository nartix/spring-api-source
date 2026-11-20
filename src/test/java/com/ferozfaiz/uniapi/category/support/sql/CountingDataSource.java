package com.ferozfaiz.uniapi.category.support.sql;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Test-only {@link DataSource} wrapper that deterministically inventories JDBC driver execution
 * calls using JDK dynamic proxies.
 */
public final class CountingDataSource implements DataSource {

  private static final List<String> EXECUTION_METHODS = List.of(
    "execute",
    "executeQuery",
    "executeUpdate",
    "executeLargeUpdate",
    "executeBatch",
    "executeLargeBatch"
  );

  private final DataSource delegate;
  private final Object observationLock = new Object();
  private final Map<
    SqlStatementObservation.Window,
    List<SqlStatementObservation.Statement>
  > inventories = new EnumMap<>(SqlStatementObservation.Window.class);

  private SqlStatementObservation.Window currentWindow = SqlStatementObservation.Window.SETUP;

  public CountingDataSource(DataSource delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    for (SqlStatementObservation.Window window : SqlStatementObservation.Window.values()) {
      inventories.put(window, new ArrayList<>());
    }
  }

  /** Clears and activates the setup inventory. */
  public void beginSetup() {
    begin(SqlStatementObservation.Window.SETUP);
  }

  /**
   * Clears and activates the measurement inventory immediately before one measured operation.
   */
  public void reset() {
    begin(SqlStatementObservation.Window.MEASUREMENT);
  }

  /** Clears and activates the teardown inventory. */
  public void beginTeardown() {
    begin(SqlStatementObservation.Window.TEARDOWN);
  }

  public SqlStatementObservation setupObservation() {
    return snapshot(SqlStatementObservation.Window.SETUP);
  }

  public SqlStatementObservation measurementObservation() {
    return snapshot(SqlStatementObservation.Window.MEASUREMENT);
  }

  public SqlStatementObservation teardownObservation() {
    return snapshot(SqlStatementObservation.Window.TEARDOWN);
  }

  private SqlStatementObservation snapshot(SqlStatementObservation.Window window) {
    synchronized (observationLock) {
      return new SqlStatementObservation(window, inventories.get(window));
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    return wrapConnection(delegate.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return wrapConnection(delegate.getConnection(username, password));
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return delegate.getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter output) throws SQLException {
    delegate.setLogWriter(output);
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    delegate.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return delegate.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }

  @Override
  public <T> T unwrap(Class<T> type) throws SQLException {
    Objects.requireNonNull(type, "type");
    if (type.isInstance(this)) {
      return type.cast(this);
    }
    return delegate.unwrap(type);
  }

  @Override
  public boolean isWrapperFor(Class<?> type) throws SQLException {
    Objects.requireNonNull(type, "type");
    return type.isInstance(this) || delegate.isWrapperFor(type);
  }

  @Override
  public String toString() {
    return "CountingDataSource[delegate=redacted]";
  }

  private void begin(SqlStatementObservation.Window window) {
    synchronized (observationLock) {
      inventories.get(window).clear();
      currentWindow = window;
    }
  }

  private void record(List<String> sqlTexts, int batchSize) {
    SqlStatementObservation.Statement statement = SqlStatementObservation.statement(
      sqlTexts,
      batchSize
    );
    synchronized (observationLock) {
      inventories.get(currentWindow).add(statement);
    }
  }

  private Connection wrapConnection(Connection connection) {
    ConnectionHandler handler = new ConnectionHandler(connection);
    Connection proxy = proxy(Connection.class, handler);
    handler.proxy = proxy;
    return proxy;
  }

  private <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(
      Proxy.newProxyInstance(
        CountingDataSource.class.getClassLoader(),
        new Class<?>[] { type },
        handler
      )
    );
  }

  private final class ConnectionHandler extends DelegatingHandler {

    private Connection proxy;

    private ConnectionHandler(Connection connection) {
      super(connection);
    }

    @Override
    Object invokeDelegate(Object dynamicProxy, Method method, Object[] arguments) throws Throwable {
      Object result = super.invokeDelegate(dynamicProxy, method, arguments);
      return switch (method.getName()) {
        case "createStatement" -> result instanceof Statement statement
          ? wrapStatement(Statement.class, statement, null, proxy)
          : result;
        case "prepareStatement" -> result instanceof PreparedStatement statement
          ? wrapStatement(PreparedStatement.class, statement, sqlArgument(arguments), proxy)
          : result;
        case "prepareCall" -> result instanceof CallableStatement statement
          ? wrapStatement(CallableStatement.class, statement, sqlArgument(arguments), proxy)
          : result;
        default -> result;
      };
    }
  }

  private <T extends Statement> T wrapStatement(
    Class<T> type,
    T statement,
    String preparedSql,
    Connection connection
  ) {
    return proxy(type, new StatementHandler(statement, preparedSql, connection));
  }

  private final class StatementHandler extends DelegatingHandler {

    private final String preparedSql;
    private final Connection connection;
    private final List<String> plainBatchSql = new ArrayList<>();
    private int preparedBatchSize;

    private StatementHandler(Statement statement, String preparedSql, Connection connection) {
      super(statement);
      this.preparedSql = preparedSql;
      this.connection = connection;
    }

    @Override
    Object invokeDelegate(Object proxy, Method method, Object[] arguments) throws Throwable {
      String methodName = method.getName();
      if (methodName.equals("getConnection")) {
        return connection;
      }
      if (methodName.equals("addBatch")) {
        Object result = super.invokeDelegate(proxy, method, arguments);
        if (arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
          plainBatchSql.add(sql);
        } else if (preparedSql != null) {
          preparedBatchSize++;
        }
        return result;
      }
      if (methodName.equals("clearBatch")) {
        Object result = super.invokeDelegate(proxy, method, arguments);
        clearBatchEvidence();
        return result;
      }
      if (!EXECUTION_METHODS.contains(methodName)) {
        return super.invokeDelegate(proxy, method, arguments);
      }

      boolean batch = methodName.equals("executeBatch") || methodName.equals("executeLargeBatch");
      List<String> sqlTexts = executionSql(arguments, batch);
      int batchSize = batch ? batchSize() : 1;
      record(sqlTexts, batchSize);
      try {
        return super.invokeDelegate(proxy, method, arguments);
      } finally {
        if (batch) {
          clearBatchEvidence();
        }
      }
    }

    private List<String> executionSql(Object[] arguments, boolean batch) {
      String invocationSql = sqlArgument(arguments);
      if (invocationSql != null) {
        return List.of(invocationSql);
      }
      if (!batch) {
        return preparedSql == null ? List.of() : List.of(preparedSql);
      }
      if (!plainBatchSql.isEmpty()) {
        return List.copyOf(plainBatchSql);
      }
      return preparedSql == null ? List.of() : List.of(preparedSql);
    }

    private int batchSize() {
      return plainBatchSql.isEmpty() ? preparedBatchSize : plainBatchSql.size();
    }

    private void clearBatchEvidence() {
      plainBatchSql.clear();
      preparedBatchSize = 0;
    }
  }

  private abstract static class DelegatingHandler implements InvocationHandler {

    private final Object delegate;

    private DelegatingHandler(Object delegate) {
      this.delegate = delegate;
    }

    @Override
    public final Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> delegate.getClass().getSimpleName() + "[delegate=redacted]";
          default -> throw new UnsupportedOperationException(method.getName());
        };
      }
      if (method.getName().equals("isWrapperFor") && arguments[0] instanceof Class<?> type) {
        return type.isInstance(proxy) || ((java.sql.Wrapper) delegate).isWrapperFor(type);
      }
      if (method.getName().equals("unwrap") && arguments[0] instanceof Class<?> type) {
        if (type.isInstance(proxy)) {
          return type.cast(proxy);
        }
        return ((java.sql.Wrapper) delegate).unwrap(type);
      }
      return invokeDelegate(proxy, method, arguments);
    }

    Object invokeDelegate(Object proxy, Method method, Object[] arguments) throws Throwable {
      try {
        return method.invoke(delegate, arguments);
      } catch (InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }

  private static String sqlArgument(Object[] arguments) {
    return arguments != null && arguments.length > 0 && arguments[0] instanceof String sql
      ? sql
      : null;
  }
}
