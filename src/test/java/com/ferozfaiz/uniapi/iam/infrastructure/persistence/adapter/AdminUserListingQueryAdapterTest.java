package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AdminUserListingQueryAdapterTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AdminUserListingQueryAdapter adapter = new AdminUserListingQueryAdapter(jdbc);

  @Test
  void findAllBuildsFilterSortQueryAndMapsReturnedFieldsExceptPassword() {
    when(
      jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))
    ).thenReturn(1L);
    when(
      jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))
    ).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      RowMapper<AdminUserListItem> mapper = invocation.getArgument(2);
      return List.of(mapper.mapRow(resultSet(), 0));
    });

    AdminListUsersQuery query = new AdminListUsersQuery(
      new PageCriteria(0, 20),
      new SortCriteria(
        List.of(
          new SortOrder("roles", SortDirection.DESC),
          new SortOrder("updatedAt", SortDirection.DESC),
          new SortOrder("id", SortDirection.ASC)
        )
      ),
      "feroz",
      null,
      "feroz",
      null,
      null,
      null,
      true,
      true,
      null,
      null,
      null,
      null,
      null,
      null,
      OffsetDateTime.parse("2026-05-01T00:00:00Z"),
      OffsetDateTime.parse("2026-06-01T00:00:00Z"),
      null,
      null,
      null,
      null,
      List.of("ADMIN"),
      List.of("admin:users:read")
    );

    PageResult<AdminUserListItem> result = adapter.findAll(query);

    ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(
      MapSqlParameterSource.class
    );
    verify(jdbc).queryForObject(countSql.capture(), parameters.capture(), eq(Long.class));
    verify(jdbc).query(selectSql.capture(), parameters.capture(), any(RowMapper.class));

    assertThat(selectSql.getValue())
      .contains("effective_roles")
      .contains("effective_permissions")
      .contains("WHERE")
      .contains("lower(username) LIKE :username")
      .contains("created_at >= :createdFrom")
      .contains("created_at < :createdTo")
      .contains("lower(role_filter.code) IN (:roles)")
      .contains("lower(permission_filter.code) IN (:permissions)")
      .contains(
        "ORDER BY roles_sort DESC NULLS LAST, updated_at DESC NULLS LAST, id ASC NULLS LAST"
      )
      .doesNotContain("password_hash");
    assertThat(countSql.getValue()).contains("SELECT COUNT(*) FROM user_rows");
    assertThat(parameters.getValue().getValue("search")).isEqualTo("%feroz%");
    assertThat(parameters.getValue().getValue("username")).isEqualTo("%feroz%");
    assertThat(parameters.getValue().getValue("roles")).isEqualTo(List.of("admin"));
    assertThat(parameters.getValue().getValue("permissions")).isEqualTo(
      List.of("admin:users:read")
    );

    AdminUserListItem item = result.content().getFirst();
    assertThat(item.id()).isEqualTo(1L);
    assertThat(item.roles()).containsExactly("ADMIN");
    assertThat(item.permissions()).containsExactly("admin:users:read");
  }

  private static ResultSet resultSet() throws Exception {
    ResultSet resultSet = mock(ResultSet.class);
    java.sql.Array roles = mock(java.sql.Array.class);
    java.sql.Array permissions = mock(java.sql.Array.class);
    when(roles.getArray()).thenReturn(new String[] { "ADMIN" });
    when(permissions.getArray()).thenReturn(new String[] { "admin:users:read" });
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("feroz");
    when(resultSet.getString("email")).thenReturn("feroz@example.com");
    when(resultSet.getString("first_name")).thenReturn("Feroz");
    when(resultSet.getString("last_name")).thenReturn("Faiz");
    when(resultSet.getBoolean("active")).thenReturn(true);
    when(resultSet.getBoolean("account_non_expired")).thenReturn(true);
    when(resultSet.getBoolean("account_non_locked")).thenReturn(true);
    when(resultSet.getBoolean("credentials_non_expired")).thenReturn(true);
    when(resultSet.getObject("last_login_at", OffsetDateTime.class)).thenReturn(null);
    when(resultSet.getObject("last_activity_at", OffsetDateTime.class)).thenReturn(null);
    when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(
      OffsetDateTime.parse("2026-05-01T06:30:00Z")
    );
    when(resultSet.getObject("updated_at", OffsetDateTime.class)).thenReturn(
      OffsetDateTime.parse("2026-05-01T06:30:00Z")
    );
    when(resultSet.getString("created_by")).thenReturn("system");
    when(resultSet.getString("updated_by")).thenReturn("system");
    when(resultSet.getArray("roles")).thenReturn(roles);
    when(resultSet.getArray("permissions")).thenReturn(permissions);
    return resultSet;
  }
}
