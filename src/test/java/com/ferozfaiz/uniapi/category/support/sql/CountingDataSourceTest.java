package com.ferozfaiz.uniapi.category.support.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class CountingDataSourceTest {

  @Test
  void keepsSetupMeasurementAndTeardownInventoriesSeparate() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement()
    ) {
      dataSource.beginSetup();
      statement.executeUpdate("insert into setup_probe values (1)");

      dataSource.reset();
      statement.executeQuery("select probe_id from measurement_probe");
      SqlStatementObservation frozenMeasurement = dataSource.measurementObservation();

      dataSource.beginTeardown();
      statement.executeUpdate("delete from teardown_probe");

      assertThat(dataSource.setupObservation().statementCount()).isOne();
      assertThat(frozenMeasurement.statementCount()).isOne();
      assertThat(dataSource.measurementObservation()).isEqualTo(frozenMeasurement);
      assertThat(dataSource.teardownObservation().statementCount()).isOne();
      assertThat(frozenMeasurement.window()).isEqualTo(SqlStatementObservation.Window.MEASUREMENT);
    }
  }

  @Test
  void countsOnlyExecutionEntryAcrossStatementTypes() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (Connection connection = dataSource.getConnection()) {
      Statement statement = connection.createStatement();
      PreparedStatement prepared = connection.prepareStatement("select prepared_value from probe");
      CallableStatement callable = connection.prepareCall("call refresh_probe()");

      dataSource.reset();
      statement.execute("select one from probe");
      statement.executeQuery("select two from probe");
      statement.executeUpdate("update probe set value = 1");
      statement.executeLargeUpdate("delete from probe");
      statement.executeBatch();
      statement.executeLargeBatch();
      prepared.execute();
      prepared.executeQuery();
      prepared.executeUpdate();
      prepared.executeLargeUpdate();
      prepared.executeBatch();
      prepared.executeLargeBatch();
      callable.execute();

      assertThat(dataSource.measurementObservation().statementCount()).isEqualTo(13);
    }
  }

  @Test
  void recordsOneBatchExecutionWithSafeShapeAndEntryEvidence() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (Connection connection = dataSource.getConnection()) {
      Statement statement = connection.createStatement();
      PreparedStatement prepared = connection.prepareStatement(
        "insert into secret_batch_table(secret_value) values (?)"
      );

      dataSource.reset();
      statement.addBatch("insert into secret_batch_table values (1)");
      statement.addBatch("insert into secret_batch_table values (2)");
      statement.executeBatch();
      prepared.addBatch();
      prepared.addBatch();
      prepared.executeLargeBatch();

      SqlStatementObservation observation = dataSource.measurementObservation();

      assertThat(observation.statementCount()).isEqualTo(2);
      assertThat(observation.statements())
        .extracting(
          SqlStatementObservation.Statement::batchSize,
          SqlStatementObservation.Statement::sqlShapeCount
        )
        .containsExactly(tuple(2, 2), tuple(2, 1));
      assertThat(observation.normalizedFingerprints()).hasSize(3);
      assertThat(observation.containsSqlFragment("secret_batch_table")).isTrue();
      assertThat(observation.toString()).doesNotContain("secret_batch_table", "secret_value");
      assertThat(observation.statements().toString()).doesNotContain(
        "secret_batch_table",
        "secret_value"
      );
    }
  }

  @Test
  void preservesExactFingerprintsWhileNormalizingExpandedInListArity() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (Connection connection = dataSource.getConnection()) {
      PreparedStatement one = connection.prepareStatement(
        "select node_id from private_node where system_key = ? and node_id in (?)"
      );
      PreparedStatement many = connection.prepareStatement(
        "select node_id from private_node where system_key = ? and node_id in (?, ?, ?)"
      );

      dataSource.reset();
      one.executeQuery();
      SqlStatementObservation oneObservation = dataSource.measurementObservation();

      dataSource.reset();
      many.executeQuery();
      SqlStatementObservation manyObservation = dataSource.measurementObservation();

      assertThat(oneObservation.normalizedFingerprints()).isNotEqualTo(
        manyObservation.normalizedFingerprints()
      );
      assertThat(oneObservation.cardinalityNormalizedFingerprints()).isEqualTo(
        manyObservation.cardinalityNormalizedFingerprints()
      );
      assertThat(oneObservation.toString()).doesNotContain("private_node", "system_key");
      assertThat(manyObservation.toString()).doesNotContain("private_node", "system_key");
    }
  }

  @Test
  void doesNotNormalizeLiteralOrMixedInListArity() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (Connection connection = dataSource.getConnection()) {
      SqlStatementObservation literalOne = executeAndObserve(
        dataSource,
        connection,
        "select node_id from private_node where node_id in ('one')"
      );
      SqlStatementObservation literalMany = executeAndObserve(
        dataSource,
        connection,
        "select node_id from private_node where node_id in ('one', 'two', 'three')"
      );
      SqlStatementObservation mixedOne = executeAndObserve(
        dataSource,
        connection,
        "select node_id from private_node where node_id in (?, 'fixed')"
      );
      SqlStatementObservation mixedMany = executeAndObserve(
        dataSource,
        connection,
        "select node_id from private_node where node_id in (?, ?, 'fixed')"
      );

      assertThat(literalOne.cardinalityNormalizedFingerprints()).isNotEqualTo(
        literalMany.cardinalityNormalizedFingerprints()
      );
      assertThat(mixedOne.cardinalityNormalizedFingerprints()).isNotEqualTo(
        mixedMany.cardinalityNormalizedFingerprints()
      );
    }
  }

  @Test
  void classifiesCommentedCteCountAndDdlWithoutPublishingSql() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement()
    ) {
      dataSource.reset();
      statement.executeQuery(
        "/* hidden implementation note */ with rows as (select id from private_node) select count(*) from rows"
      );
      statement.execute("create table private_node(id bigint)");

      SqlStatementObservation observation = dataSource.measurementObservation();

      assertThat(observation.statementKinds()).containsExactly(
        SqlStatementObservation.StatementKind.SELECT,
        SqlStatementObservation.StatementKind.DDL
      );
      assertThat(observation.containsTotalCountQuery()).isTrue();
      assertThat(observation.toString()).doesNotContain(
        "private_node",
        "hidden implementation note"
      );
    }
  }

  @Test
  void recordsAnExecutionThatFailsAfterTheDriverCallIsEntered() throws SQLException {
    CountingDataSource dataSource = new CountingDataSource(fakeDataSource());

    try (
      Connection connection = dataSource.getConnection();
      Statement statement = connection.createStatement()
    ) {
      dataSource.reset();

      assertThatThrownBy(() -> statement.execute("select fail_driver_call from probe"))
        .isInstanceOf(SQLException.class)
        .hasMessage("controlled driver failure");
      assertThat(dataSource.measurementObservation().statementCount()).isOne();
    }
  }

  @Test
  void delegatesWrapperAndDatasourceInfrastructureMethodsWithoutCounting() throws SQLException {
    DataSource delegate = fakeDataSource();
    CountingDataSource dataSource = new CountingDataSource(delegate);

    dataSource.reset();
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      connection.commit();
      connection.rollback();
      assertThat(connection.isWrapperFor(Connection.class)).isTrue();
      assertThat(connection.unwrap(Connection.class)).isSameAs(connection);
    }

    assertThat(dataSource.isWrapperFor(CountingDataSource.class)).isTrue();
    assertThat(dataSource.unwrap(CountingDataSource.class)).isSameAs(dataSource);
    assertThat(dataSource.measurementObservation().statementCount()).isZero();
  }

  private static DataSource fakeDataSource() {
    return proxy(DataSource.class, (proxy, method, arguments) -> {
      if (method.getName().equals("getConnection")) {
        return fakeConnection();
      }
      return defaultValue(method.getReturnType());
    });
  }

  private static SqlStatementObservation executeAndObserve(
    CountingDataSource dataSource,
    Connection connection,
    String sql
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      dataSource.reset();
      statement.executeQuery();
      return dataSource.measurementObservation();
    }
  }

  private static Connection fakeConnection() {
    return proxy(
      Connection.class,
      (proxy, method, arguments) ->
        switch (method.getName()) {
          case "createStatement" -> fakeStatement(Statement.class);
          case "prepareStatement" -> fakeStatement(PreparedStatement.class);
          case "prepareCall" -> fakeStatement(CallableStatement.class);
          default -> defaultValue(method.getReturnType());
        }
    );
  }

  private static <T extends Statement> T fakeStatement(Class<T> statementType) {
    return proxy(statementType, (proxy, method, arguments) -> {
      if (
        List.of(
          "execute",
          "executeQuery",
          "executeUpdate",
          "executeLargeUpdate",
          "executeBatch",
          "executeLargeBatch"
        ).contains(method.getName()) &&
        arguments != null &&
        arguments.length > 0 &&
        arguments[0] instanceof String sql &&
        sql.contains("fail_driver_call")
      ) {
        throw new SQLException("controlled driver failure");
      }
      return defaultValue(method.getReturnType());
    });
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(
      Proxy.newProxyInstance(
        type.getClassLoader(),
        new Class<?>[] { type },
        new ObjectMethodHandler(handler)
      )
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == int.class) {
      return 0;
    }
    return null;
  }

  private record ObjectMethodHandler(InvocationHandler delegate) implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "FakeJdbcProxy";
          default -> throw new UnsupportedOperationException(method.getName());
        };
      }
      return delegate.invoke(proxy, method, arguments);
    }
  }
}
