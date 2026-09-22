package org.fourfeetcat.storage;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.fourfeetcat.core.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 建表走手工脚本（V18__sessions.sql，课件口径：不让 Hibernate 自动建，避免测试绿、生产跑真脚本时列名对不上）。 不用 {@code
 * jdbc:sqlite::memory:}——与连接池不兼容（每连接各一个库）。
 *
 * <p>消息往返与"重启"两条都要求读到库、不是读到持久化上下文里的内存对象：前者用 {@code clear()} 逼一次真读，后者整条
 * 方法挂在事务外（NOT_SUPPORTED），让写入真提交、再由另一条连接读——这才叫重启。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SessionRepositoryTest {

  private static final Path DB_DIR = createTempDir();

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("fourfeetcat-session-test");
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

  @Autowired private SessionRepository repository;

  @Autowired private DataSource dataSource;

  @Autowired private EntityManager entityManager;

  private JpaSessionManager sessionManager;

  @BeforeEach
  void createSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/sqlite/V18__sessions.sql"));
    }
    sessionManager = new JpaSessionManager(repository);
  }

  @Test
  @DisplayName("手工建表脚本建出的sessions表_能存能读")
  void scriptBuiltTable_savesAndReads() {
    sessionManager.save(sessionManager.getOrCreate("cli", "read-back", "default"));
    // 先 flush 再 clear：clear() 会丢弃尚未写库的挂起变更，只清持久化上下文
    entityManager.flush();
    entityManager.clear();

    SessionEntity read = repository.findById("cli:read-back:default").orElseThrow();
    assertThat(read.getProfileName()).isEqualTo("default");
    assertThat(read.getChannel()).isEqualTo("cli");
    assertThat(read.getUserId()).isEqualTo("read-back");
    assertThat(read.getStatus()).isEqualTo("active");
    assertThat(read.getMessagesJson()).isEqualTo("[]");
    assertThat(read.getCreatedAt()).isNotBlank();
    assertThat(read.getLastActiveAt()).isNotBlank();
    assertThat(read.getArchivedAt()).isNull();
  }

  @Test
  @DisplayName("messages_json_序列化回读后消息完整（条数、顺序、工具调用参数逐字保真）")
  void messagesJson_roundTrip_preservesAllMessages() {
    Session session = sessionManager.getOrCreate("cli", "round-trip", "default");
    session.appendUserMessage("今天天气怎么样");
    session.appendAssistantMessage(
        AssistantMessage.builder()
            .content("")
            .toolCalls(
                List.of(
                    new AssistantMessage.ToolCall(
                        "call_1", "function", "http_get", "{\"url\":\"https://x\"}")))
            .build());
    session.appendToolResponses(
        List.of(new ToolResponseMessage.ToolResponse("call_1", "http_get", "{\"temp\":26}")));
    sessionManager.save(session);
    entityManager.flush();
    entityManager.clear();

    List<Message> messages = sessionManager.get(session.getId()).orElseThrow().getMessages();

    assertThat(messages).hasSize(3);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    assertThat(((UserMessage) messages.get(0)).getText()).isEqualTo("今天天气怎么样");
    AssistantMessage assistant = (AssistantMessage) messages.get(1);
    assertThat(assistant.getToolCalls()).hasSize(1);
    assertThat(assistant.getToolCalls().get(0).id()).isEqualTo("call_1");
    assertThat(assistant.getToolCalls().get(0).name()).isEqualTo("http_get");
    assertThat(assistant.getToolCalls().get(0).arguments()).isEqualTo("{\"url\":\"https://x\"}");
    ToolResponseMessage toolResponse = (ToolResponseMessage) messages.get(2);
    assertThat(toolResponse.getResponses()).hasSize(1);
    assertThat(toolResponse.getResponses().get(0).responseData()).isEqualTo("{\"temp\":26}");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("模拟重启_历史真的落盘了、新会话层实例与另一条连接都能读到")
  void reopenedContext_historyStillPresent() throws SQLException {
    Session session = sessionManager.getOrCreate("cli", "restart", "default");
    session.appendUserMessage("记住我喜欢喝美式");
    sessionManager.save(session);

    // 直连库看那一列本身：绕过 ORM，证明数据真的写到盘上了
    assertThat(readMessagesJsonDirectly(session.getId())).contains("记住我喜欢喝美式");

    // 换一个新的会话层实例重查（等价于重启后新建），历史完整
    List<Message> messages =
        new JpaSessionManager(repository).get(session.getId()).orElseThrow().getMessages();
    assertThat(messages).hasSize(1);
    assertThat(((UserMessage) messages.get(0)).getText()).isEqualTo("记住我喜欢喝美式");
  }

  private String readMessagesJsonDirectly(String sessionId) throws SQLException {
    String sql = "SELECT messages_json FROM sessions WHERE session_id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sessionId);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).isTrue();
        return rs.getString("messages_json");
      }
    }
  }
}
