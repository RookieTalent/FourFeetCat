package org.fourfeetcat.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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
 * 渠道注册表：建表走手工脚本（V19__notify_channels.sql，课件口径：不让 Hibernate 自动建，避免测试绿、生产跑真脚本时列名对 不上）。不用 {@code
 * jdbc:sqlite::memory:}——与连接池不兼容（每连接各一个库）。
 *
 * <p>本表在核心阶段没有写入入口，本节要守的是"结构就位且读写口径正确"：字段往返一致、可空列真的可空。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotifyChannelRepositoryTest {

  private static final Path DB_DIR = createTempDir();

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("fourfeetcat-notify-channel-test");
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

  @Autowired private NotifyChannelRepository repository;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void createSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/sqlite/V19__notify_channels.sql"));
    }
  }

  @Test
  @DisplayName("手工建表脚本建出的渠道表_按名写入再读回_各字段一致")
  void scriptBuiltTable_savesAndReadsByName() {
    NotifyChannel channel = new NotifyChannel();
    channel.setName("team-lark");
    channel.setType("webhook");
    channel.setUrl("https://open.feishu.cn/open-apis/bot/v2/hook/xxxx");
    channel.setDescription("团队日报群");
    channel.setConfig("{\"mention\":\"all\"}");

    repository.saveAndFlush(channel);

    NotifyChannel read = repository.findById("team-lark").orElseThrow();
    assertThat(read.getType()).isEqualTo("webhook");
    assertThat(read.getUrl()).isEqualTo("https://open.feishu.cn/open-apis/bot/v2/hook/xxxx");
    assertThat(read.getDescription()).isEqualTo("团队日报群");
    assertThat(read.getConfig()).isEqualTo("{\"mention\":\"all\"}");
  }

  @Test
  @DisplayName("可空列全空也能存取（类型与描述不是必填）")
  void optionalColumns_mayAllBeNull() {
    NotifyChannel minimal = new NotifyChannel();
    minimal.setName("bare");
    minimal.setType("webhook");

    repository.saveAndFlush(minimal);

    NotifyChannel read = repository.findById("bare").orElseThrow();
    assertThat(read.getType()).isEqualTo("webhook");
    assertThat(read.getUrl()).isNull();
    assertThat(read.getDescription()).isNull();
    assertThat(read.getConfig()).isNull();
  }

  @Test
  @DisplayName("按不存在的渠道名取_返回空而不是报错")
  void findByName_unknownName_returnsEmpty() {
    assertThat(repository.findById("no-such-channel")).isEmpty();
  }
}
