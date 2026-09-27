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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * memory_entries 的真实 SQL 语义（第22节）：迁移脚本建出的表能存能读、归档条数上限生效、库内匹配只命中归档。
 *
 * <p>契约测试里那一档用的是内存假仓储（不拉容器），"上限与匹配在真库上到底成不成立"就落在这里。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemoryEntryRepositoryTest {

  private static final Path DB_DIR = createTempDir();

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("fourfeetcat-memory-entry-test");
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

  @Autowired private MemoryEntryRepository repository;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void createSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/sqlite/V20__memory_entries.sql"));
    }
  }

  @Test
  @DisplayName("迁移脚本建出的 memory_entries 能存能读_三列真实存在")
  void scriptBuiltTableSupportsSaveAndRead() throws SQLException {
    insert("CORE", "用户叫小王");

    List<MemoryEntry> core = repository.findByScopeOrderByIdAsc("CORE");
    assertThat(core).hasSize(1);
    assertThat(core.get(0).getContent()).isEqualTo("用户叫小王");
    assertThat(core.get(0).getCreatedAt()).isNotNull();

    List<String> columns = new ArrayList<>();
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet rs = metaData.getColumns(null, null, "memory_entries", null)) {
        while (rs.next()) {
          columns.add(rs.getString("COLUMN_NAME"));
        }
      }
    }
    // 分区、正文、时间三列缺一不可：分区没了截断就无从谈起，时间没了归档排序就没有依据
    assertThat(columns).contains("scope", "content", "created_at");
  }

  @Test
  @DisplayName("核心区全量取_不受归档条数上限影响")
  void coreQueryIsUnlimited() {
    for (int i = 0; i < 120; i++) {
      insert("ARCHIVAL", "流水 " + i);
    }
    insert("CORE", "核心一条");
    insert("CORE", "核心两条");

    List<MemoryEntry> core = repository.findByScopeOrderByIdAsc("CORE");

    assertThat(core).hasSize(2);
    assertThat(core.get(0).getContent()).isEqualTo("核心一条"); // 按写入顺序
    assertThat(core.get(1).getContent()).isEqualTo("核心两条");
  }

  @Test
  @DisplayName("归档区条数上限生效_取最近 N 条且最新在前")
  void archivalLimitReturnsMostRecent() {
    for (int i = 0; i < 120; i++) {
      insert("ARCHIVAL", "流水 " + i);
    }

    List<MemoryEntry> recent =
        repository.findByScopeOrderByIdDesc("ARCHIVAL", PageRequest.of(0, 100));

    assertThat(recent).hasSize(100);
    assertThat(recent.get(0).getContent()).isEqualTo("流水 119"); // 最新在前
    assertThat(recent.get(99).getContent()).isEqualTo("流水 20"); // 恰好最近 100 条
  }

  @Test
  @DisplayName("库内匹配只命中归档区")
  void searchArchivalMatchesOnlyArchival() {
    insert("CORE", "核心里也有 needle");
    insert("ARCHIVAL", "归档 needle 一条");
    insert("ARCHIVAL", "无关内容");

    List<MemoryEntry> hits = repository.searchArchival("%needle%");

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).getContent()).isEqualTo("归档 needle 一条");
  }

  private void insert(String scope, String content) {
    MemoryEntry entry = new MemoryEntry();
    entry.setScope(scope);
    entry.setContent(content);
    entry.setCreatedAt("2026-09-26T10:00:00Z");
    repository.saveAndFlush(entry);
  }
}
