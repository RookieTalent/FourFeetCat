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
 * 建表走手工脚本（V16__llm_calls.sql，课件 L236：不让 Hibernate 自动建，避免测试绿、生产跑真 脚本时列名对不上）。不用
 * jdbc:sqlite::memory:——与连接池不兼容（每连接各一个库）。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LlmCallRepositoryTest {

  private static final Path DB_DIR = createTempDir();

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("fourfeetcat-llmcall-test");
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

  @Autowired private LlmCallRepository repository;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void createSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/sqlite/V16__llm_calls.sql"));
    }
  }

  @Test
  @DisplayName("llm_calls能存能读_审计记录往返保真")
  void saveAndRead_roundTripsAuditRecord() {
    LlmCall call = new LlmCall();
    call.setSessionId("s-1");
    call.setProvider("deepseek");
    call.setModel("deepseek-chat");
    call.setPromptTokens(120);
    call.setCompletionTokens(80);
    call.setTotalTokens(200);
    call.setSuccess(true);
    call.setDurationMs(1500L);
    call.setCreatedAt("2026-09-14T10:00:00Z");

    repository.saveAndFlush(call);

    List<LlmCall> found = repository.findBySessionIdOrderByCreatedAtDesc("s-1");
    assertThat(found).hasSize(1);
    LlmCall read = found.get(0);
    assertThat(read.getProvider()).isEqualTo("deepseek");
    assertThat(read.getModel()).isEqualTo("deepseek-chat");
    assertThat(read.getPromptTokens()).isEqualTo(120);
    assertThat(read.getCompletionTokens()).isEqualTo(80);
    assertThat(read.getTotalTokens()).isEqualTo(200);
    assertThat(read.isSuccess()).isTrue();
    assertThat(read.getErrorMessage()).isNull();
    assertThat(read.getDurationMs()).isEqualTo(1500L);
    assertThat(read.getCreatedAt()).isEqualTo("2026-09-14T10:00:00Z");
  }

  @Test
  @DisplayName("失败调用_存下success为false与error_message")
  void failedCall_persistsSuccessFalseAndErrorMessage() {
    LlmCall call = new LlmCall();
    call.setSessionId("s-2");
    call.setProvider("kimi");
    call.setModel("moonshot-v1");
    call.setSuccess(false);
    call.setErrorMessage("connect timeout");
    call.setDurationMs(5000L);
    call.setCreatedAt("2026-09-14T10:01:00Z");

    repository.saveAndFlush(call);

    LlmCall read = repository.findBySessionIdOrderByCreatedAtDesc("s-2").get(0);
    assertThat(read.isSuccess()).isFalse();
    assertThat(read.getErrorMessage()).isEqualTo("connect timeout");
  }

  @Test
  @DisplayName("手工建表脚本建出的表_success与error_message两列真实存在")
  void scriptBuiltTable_hasSuccessAndErrorColumns() throws SQLException {
    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet rs = metaData.getColumns(null, null, "llm_calls", null)) {
        while (rs.next()) {
          columns.add(rs.getString("COLUMN_NAME"));
        }
      }
    }
    // 失败留痕依赖这两列，不能只在实体里存在、表里缺失
    assertThat(columns).contains("session_id", "provider", "model", "success", "error_message");
  }
}
