package org.fourfeetcat.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 建表走手工脚本（V17__tool_invocations.sql）：不让 Hibernate 自动建，避免测试绿、生产跑真脚本时列名对不上。 不用
 * jdbc:sqlite::memory:——与连接池不兼容（每连接各一个库）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ToolInvocationRepositoryTest {

  private static final Path DB_DIR = createTempDir();

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("fourfeetcat-toolinvocation-test");
    } catch (IOException e) {
      throw new IllegalStateException("无法创建临时目录", e);
    }
  }

  @DynamicPropertySource
  static void sqliteDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_DIR.resolve("test.db"));
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add(
        "spring.jpa.database-platform", () -> "org.hibernate.community.dialect.SQLiteDialect");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }

  @Autowired private ToolInvocationRepository repository;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void createSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/sqlite/V17__tool_invocations.sql"));
    }
  }

  @Test
  @DisplayName("tool_invocations能存能读_审计记录往返保真")
  void saveAndRead_roundTripsAuditRecord() {
    ToolInvocation invocation = new ToolInvocation();
    invocation.setSessionId("s-1");
    invocation.setToolName("http_get");
    invocation.setInputJson("{\"url\":\"https://example.com\"}");
    invocation.setResultJson("{\"temp\":10}");
    invocation.setSuccess(true);
    invocation.setDurationMs(320L);
    invocation.setCreatedAt("2026-09-16T10:00:00Z");

    repository.saveAndFlush(invocation);

    List<ToolInvocation> found = repository.findBySessionIdOrderByCreatedAtDesc("s-1");
    assertThat(found).hasSize(1);
    ToolInvocation read = found.get(0);
    assertThat(read.getToolName()).isEqualTo("http_get");
    assertThat(read.getInputJson()).isEqualTo("{\"url\":\"https://example.com\"}");
    assertThat(read.getResultJson()).isEqualTo("{\"temp\":10}");
    assertThat(read.isSuccess()).isTrue();
    assertThat(read.getErrorMessage()).isNull();
    assertThat(read.getDurationMs()).isEqualTo(320L);
    assertThat(read.getCreatedAt()).isEqualTo("2026-09-16T10:00:00Z");
  }

  @Test
  @DisplayName("失败调用_存下success为false与error_message")
  void failedInvocation_persistsSuccessFalseAndErrorMessage() {
    ToolInvocation invocation = new ToolInvocation();
    invocation.setSessionId("s-2");
    invocation.setToolName("shell");
    invocation.setInputJson("{}");
    invocation.setSuccess(false);
    invocation.setErrorMessage("连接超时");
    invocation.setDurationMs(5000L);
    invocation.setCreatedAt("2026-09-16T10:01:00Z");

    repository.saveAndFlush(invocation);

    ToolInvocation read = repository.findBySessionIdOrderByCreatedAtDesc("s-2").get(0);
    assertThat(read.isSuccess()).isFalse();
    assertThat(read.getErrorMessage()).isEqualTo("连接超时");
    assertThat(read.getResultJson()).isNull();
  }

  @Test
  @DisplayName("手工建表脚本建出的表_success与error_message两列真实存在")
  void scriptBuiltTable_hasSuccessAndErrorColumns() throws SQLException {
    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet rs = metaData.getColumns(null, null, "tool_invocations", null)) {
        while (rs.next()) {
          columns.add(rs.getString("COLUMN_NAME"));
        }
      }
    }
    // 失败留痕依赖这两列，不能只在实体里存在、表里缺失
    assertThat(columns)
        .contains(
            "session_id", "tool_name", "input_json", "result_json", "success", "error_message");
  }
}
