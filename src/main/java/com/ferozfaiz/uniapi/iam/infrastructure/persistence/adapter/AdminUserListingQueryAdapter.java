package com.ferozfaiz.uniapi.iam.infrastructure.persistence.adapter;

import com.ferozfaiz.uniapi.iam.application.port.AdminUserListingRepository;
import com.ferozfaiz.uniapi.iam.application.query.AdminListUsersQuery;
import com.ferozfaiz.uniapi.iam.application.query.AdminUserListItem;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AdminUserListingQueryAdapter implements AdminUserListingRepository {

  private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
    Map.entry("id", "id"),
    Map.entry("username", "username"),
    Map.entry("email", "email"),
    Map.entry("firstName", "first_name"),
    Map.entry("lastName", "last_name"),
    Map.entry("active", "active"),
    Map.entry("accountNonExpired", "account_non_expired"),
    Map.entry("accountNonLocked", "account_non_locked"),
    Map.entry("credentialsNonExpired", "credentials_non_expired"),
    Map.entry("lastLoginAt", "last_login_at"),
    Map.entry("lastActivityAt", "last_activity_at"),
    Map.entry("createdAt", "created_at"),
    Map.entry("updatedAt", "updated_at"),
    Map.entry("createdBy", "created_by"),
    Map.entry("updatedBy", "updated_by"),
    Map.entry("roles", "roles_sort"),
    Map.entry("permissions", "permissions_sort")
  );

  private static final String LISTING_CTE = """
    WITH effective_roles AS (
      SELECT user_role.user_id, role.id AS role_id, role.code, role.name
      FROM iam_user_role user_role
      JOIN iam_role role ON role.id = user_role.role_id
      WHERE role.is_active = true
        AND (user_role.expires_at IS NULL OR user_role.expires_at > CURRENT_TIMESTAMP)
    ),
    direct_permissions AS (
      SELECT user_permission.user_id, permission.code, permission.name
      FROM iam_user_permission user_permission
      JOIN iam_permission permission ON permission.id = user_permission.permission_id
      WHERE permission.is_active = true
        AND (user_permission.expires_at IS NULL OR user_permission.expires_at > CURRENT_TIMESTAMP)
    ),
    role_permissions AS (
      SELECT effective_roles.user_id, permission.code, permission.name
      FROM effective_roles
      JOIN iam_role_permission role_permission ON role_permission.role_id = effective_roles.role_id
      JOIN iam_permission permission ON permission.id = role_permission.permission_id
      WHERE permission.is_active = true
    ),
    effective_permissions AS (
      SELECT user_id, code, name FROM direct_permissions
      UNION
      SELECT user_id, code, name FROM role_permissions
    ),
    user_rows AS (
      SELECT
        iam_user.id,
        iam_user.username,
        iam_user.email,
        iam_user.first_name,
        iam_user.last_name,
        iam_user.is_active AS active,
        iam_user.is_account_non_expired AS account_non_expired,
        iam_user.is_account_non_locked AS account_non_locked,
        iam_user.is_credentials_non_expired AS credentials_non_expired,
        iam_user.last_login_at,
        iam_user.last_activity_at,
        iam_user.created_at,
        iam_user.updated_at,
        iam_user.created_by,
        iam_user.updated_by,
        COALESCE(
          array_agg(DISTINCT effective_roles.code ORDER BY effective_roles.code)
            FILTER (WHERE effective_roles.code IS NOT NULL),
          ARRAY[]::varchar[]
        ) AS roles,
        COALESCE(
          array_agg(DISTINCT effective_permissions.code ORDER BY effective_permissions.code)
            FILTER (WHERE effective_permissions.code IS NOT NULL),
          ARRAY[]::varchar[]
        ) AS permissions,
        COALESCE(string_agg(DISTINCT effective_roles.code, ',' ORDER BY effective_roles.code), '') AS roles_sort,
        COALESCE(string_agg(DISTINCT effective_permissions.code, ',' ORDER BY effective_permissions.code), '') AS permissions_sort,
        (
          COALESCE(string_agg(DISTINCT lower(effective_roles.code), ' '), '') || ' ' ||
          COALESCE(string_agg(DISTINCT lower(effective_roles.name), ' '), '')
        ) AS roles_search,
        (
          COALESCE(string_agg(DISTINCT lower(effective_permissions.code), ' '), '') || ' ' ||
          COALESCE(string_agg(DISTINCT lower(effective_permissions.name), ' '), '')
        ) AS permissions_search
      FROM iam_user
      LEFT JOIN effective_roles ON effective_roles.user_id = iam_user.id
      LEFT JOIN effective_permissions ON effective_permissions.user_id = iam_user.id
      GROUP BY iam_user.id
    )
    """;

  private final NamedParameterJdbcTemplate jdbc;

  public AdminUserListingQueryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PageResult<AdminUserListItem> findAll(AdminListUsersQuery query) {
    MapSqlParameterSource parameters = parameters(query)
      .addValue("limit", query.page().size())
      .addValue("offset", (long) query.page().page() * query.page().size());
    String where = whereClause(query);
    long total = count(where, parameters);
    List<AdminUserListItem> content = jdbc.query(
      selectSql(where, orderBy(query.sort())) + " LIMIT :limit OFFSET :offset",
      parameters,
      this::mapListItem
    );
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / query.page().size());
    boolean first = query.page().page() == 0;
    boolean last = totalPages == 0 || query.page().page() >= totalPages - 1;

    return new PageResult<>(
      content,
      query.page().page(),
      query.page().size(),
      total,
      totalPages,
      first,
      last,
      query.sort()
    );
  }

  private long count(String where, MapSqlParameterSource parameters) {
    Long count = jdbc.queryForObject(
      LISTING_CTE + "SELECT COUNT(*) FROM user_rows " + where,
      parameters,
      Long.class
    );
    return count == null ? 0 : count;
  }

  private static MapSqlParameterSource parameters(AdminListUsersQuery query) {
    MapSqlParameterSource parameters = new MapSqlParameterSource()
      .addValue("id", query.id())
      .addValue("active", query.active())
      .addValue("accountNonExpired", query.accountNonExpired())
      .addValue("accountNonLocked", query.accountNonLocked())
      .addValue("credentialsNonExpired", query.credentialsNonExpired())
      .addValue("lastLoginFrom", query.lastLoginFrom())
      .addValue("lastLoginTo", query.lastLoginTo())
      .addValue("lastActivityFrom", query.lastActivityFrom())
      .addValue("lastActivityTo", query.lastActivityTo())
      .addValue("createdFrom", query.createdFrom())
      .addValue("createdTo", query.createdTo())
      .addValue("updatedFrom", query.updatedFrom())
      .addValue("updatedTo", query.updatedTo())
      .addValue("roles", lowerValues(query.roles()))
      .addValue("permissions", lowerValues(query.permissions()));
    addLike(parameters, "search", query.search());
    addLike(parameters, "username", query.username());
    addLike(parameters, "email", query.email());
    addLike(parameters, "firstName", query.firstName());
    addLike(parameters, "lastName", query.lastName());
    addLike(parameters, "createdBy", query.createdBy());
    addLike(parameters, "updatedBy", query.updatedBy());
    return parameters;
  }

  private static String whereClause(AdminListUsersQuery query) {
    List<String> predicates = new java.util.ArrayList<>();
    add(query.id() != null, predicates, "id = :id");
    add(query.username() != null, predicates, "lower(username) LIKE :username");
    add(query.email() != null, predicates, "lower(email) LIKE :email");
    add(query.firstName() != null, predicates, "lower(COALESCE(first_name, '')) LIKE :firstName");
    add(query.lastName() != null, predicates, "lower(COALESCE(last_name, '')) LIKE :lastName");
    add(query.active() != null, predicates, "active = :active");
    add(query.accountNonExpired() != null, predicates, "account_non_expired = :accountNonExpired");
    add(query.accountNonLocked() != null, predicates, "account_non_locked = :accountNonLocked");
    add(
      query.credentialsNonExpired() != null,
      predicates,
      "credentials_non_expired = :credentialsNonExpired"
    );
    add(query.lastLoginFrom() != null, predicates, "last_login_at >= :lastLoginFrom");
    add(query.lastLoginTo() != null, predicates, "last_login_at < :lastLoginTo");
    add(query.lastActivityFrom() != null, predicates, "last_activity_at >= :lastActivityFrom");
    add(query.lastActivityTo() != null, predicates, "last_activity_at < :lastActivityTo");
    add(query.createdFrom() != null, predicates, "created_at >= :createdFrom");
    add(query.createdTo() != null, predicates, "created_at < :createdTo");
    add(query.updatedFrom() != null, predicates, "updated_at >= :updatedFrom");
    add(query.updatedTo() != null, predicates, "updated_at < :updatedTo");
    add(query.createdBy() != null, predicates, "lower(COALESCE(created_by, '')) LIKE :createdBy");
    add(query.updatedBy() != null, predicates, "lower(COALESCE(updated_by, '')) LIKE :updatedBy");
    add(
      !query.roles().isEmpty(),
      predicates,
      """
      EXISTS (
        SELECT 1 FROM effective_roles role_filter
        WHERE role_filter.user_id = user_rows.id
          AND (lower(role_filter.code) IN (:roles) OR lower(role_filter.name) IN (:roles))
      )
      """
    );
    add(
      !query.permissions().isEmpty(),
      predicates,
      """
      EXISTS (
        SELECT 1 FROM effective_permissions permission_filter
        WHERE permission_filter.user_id = user_rows.id
          AND (
            lower(permission_filter.code) IN (:permissions)
            OR lower(permission_filter.name) IN (:permissions)
          )
      )
      """
    );
    add(query.search() != null, predicates, searchPredicate());
    return predicates.isEmpty() ? "" : "WHERE " + String.join(" AND ", predicates);
  }

  private static String searchPredicate() {
    return """
    (
      CAST(id AS TEXT) LIKE :search
      OR lower(username) LIKE :search
      OR lower(email) LIKE :search
      OR lower(COALESCE(first_name, '')) LIKE :search
      OR lower(COALESCE(last_name, '')) LIKE :search
      OR CAST(active AS TEXT) LIKE :search
      OR CAST(account_non_expired AS TEXT) LIKE :search
      OR CAST(account_non_locked AS TEXT) LIKE :search
      OR CAST(credentials_non_expired AS TEXT) LIKE :search
      OR lower(COALESCE(CAST(last_login_at AS TEXT), '')) LIKE :search
      OR lower(COALESCE(CAST(last_activity_at AS TEXT), '')) LIKE :search
      OR lower(COALESCE(CAST(created_at AS TEXT), '')) LIKE :search
      OR lower(COALESCE(CAST(updated_at AS TEXT), '')) LIKE :search
      OR lower(COALESCE(created_by, '')) LIKE :search
      OR lower(COALESCE(updated_by, '')) LIKE :search
      OR roles_search LIKE :search
      OR permissions_search LIKE :search
    )
    """;
  }

  private static String selectSql(String where, String orderBy) {
    return (
      LISTING_CTE +
      """
      SELECT
        id,
        username,
        email,
        first_name,
        last_name,
        active,
        account_non_expired,
        account_non_locked,
        credentials_non_expired,
        last_login_at,
        last_activity_at,
        created_at,
        updated_at,
        created_by,
        updated_by,
        roles,
        permissions
      FROM user_rows
      """ +
      where +
      " " +
      orderBy
    );
  }

  private static String orderBy(SortCriteria sort) {
    List<String> orders = sort
      .orders()
      .stream()
      .map(AdminUserListingQueryAdapter::orderBy)
      .toList();
    return "ORDER BY " + String.join(", ", orders);
  }

  private static String orderBy(SortOrder order) {
    String column = SORT_COLUMNS.get(order.property());
    if (column == null) {
      throw new InvalidRequestException(
        "iam.api.user.search.sort.allowed",
        "invalid sort",
        String.join(", ", SORT_COLUMNS.keySet())
      );
    }
    String direction = order.direction() == SortDirection.DESC ? "DESC" : "ASC";
    return column + " " + direction + " NULLS LAST";
  }

  private AdminUserListItem mapListItem(ResultSet rs, int rowNum) throws SQLException {
    return new AdminUserListItem(
      rs.getLong("id"),
      rs.getString("username"),
      rs.getString("email"),
      rs.getString("first_name"),
      rs.getString("last_name"),
      rs.getBoolean("active"),
      rs.getBoolean("account_non_expired"),
      rs.getBoolean("account_non_locked"),
      rs.getBoolean("credentials_non_expired"),
      rs.getObject("last_login_at", OffsetDateTime.class),
      rs.getObject("last_activity_at", OffsetDateTime.class),
      rs.getObject("created_at", OffsetDateTime.class),
      rs.getObject("updated_at", OffsetDateTime.class),
      rs.getString("created_by"),
      rs.getString("updated_by"),
      textArray(rs, "roles"),
      textArray(rs, "permissions")
    );
  }

  private static void add(boolean condition, List<String> predicates, String predicate) {
    if (condition) {
      predicates.add(predicate);
    }
  }

  private static void addLike(MapSqlParameterSource parameters, String name, String value) {
    parameters.addValue(name, value == null ? null : "%" + value.toLowerCase(Locale.ROOT) + "%");
  }

  private static List<String> lowerValues(List<String> values) {
    return values
      .stream()
      .map(value -> value.toLowerCase(Locale.ROOT))
      .toList();
  }

  private static List<String> textArray(ResultSet rs, String column) throws SQLException {
    java.sql.Array array = rs.getArray(column);
    if (array == null) {
      return List.of();
    }
    Object value = array.getArray();
    if (value instanceof String[] strings) {
      return Arrays.asList(strings);
    }
    if (value instanceof Object[] objects) {
      return Arrays.stream(objects).map(String::valueOf).toList();
    }
    return List.of();
  }
}
