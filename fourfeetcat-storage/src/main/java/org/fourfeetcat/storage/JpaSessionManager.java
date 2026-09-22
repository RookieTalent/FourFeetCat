package org.fourfeetcat.storage;

import java.time.Instant;
import java.util.Optional;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * {@link SessionManager} 端口的存储实现：经 JPA 仓储读写 sessions 表。
 *
 * <p><b>会话标识只在这里拼</b>——所有入口只提供"渠道 + 用户 + Agent"三元组。拼接结果必须落在 {@code VARCHAR(64)} 内，三元组本身已由各入口保证是短标识。
 */
@Component
public class JpaSessionManager implements SessionManager {

  private static final String ID_SEPARATOR = ":";
  private static final String STATUS_ACTIVE = "active";

  private final SessionRepository repository;

  public JpaSessionManager(SessionRepository repository) {
    this.repository = repository;
  }

  @Override
  public Session getOrCreate(String channel, String user, String profileName) {
    String sessionId = sessionId(channel, user, profileName);
    return repository
        .findById(sessionId)
        .map(JpaSessionManager::toSession)
        .orElseGet(() -> new Session(sessionId, profileName, channel, user));
  }

  @Override
  public Optional<Session> get(String sessionId) {
    return repository.findById(sessionId).map(JpaSessionManager::toSession);
  }

  @Override
  public void save(Session session) {
    SessionEntity entity =
        repository.findById(session.getId()).orElseGet(() -> newSessionEntity(session));
    entity.setProfileName(session.getProfileName());
    entity.setChannel(session.getChannel());
    entity.setUserId(session.getUserId());
    entity.setMessagesJson(SessionMessagesJson.write(session.getMessages()));
    entity.setLastActiveAt(Instant.now().toString());
    repository.save(entity);
  }

  /** 会话标识的唯一生成处（幂等与隔离都由它决定）。 */
  private static String sessionId(String channel, String user, String profileName) {
    return channel + ID_SEPARATOR + user + ID_SEPARATOR + profileName;
  }

  /** 首次落盘才写创建时间：后续 save 只刷最后活跃时间，创建时间保留首次值。 */
  private static SessionEntity newSessionEntity(Session session) {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(session.getId());
    entity.setCreatedAt(Instant.now().toString());
    entity.setStatus(STATUS_ACTIVE);
    return entity;
  }

  /** 逐条按类型回填：内存态会话只提供三个追加入口，不新增旁路（第17节契约不动）。 */
  private static Session toSession(SessionEntity entity) {
    Session session =
        new Session(
            entity.getSessionId(),
            entity.getProfileName(),
            entity.getChannel(),
            entity.getUserId());
    for (Message message : SessionMessagesJson.read(entity.getMessagesJson())) {
      if (message instanceof UserMessage user) {
        session.appendUserMessage(user.getText());
      } else if (message instanceof AssistantMessage assistant) {
        session.appendAssistantMessage(assistant);
      } else if (message instanceof ToolResponseMessage toolResponse) {
        session.appendToolResponses(toolResponse.getResponses());
      }
    }
    return session;
  }
}
