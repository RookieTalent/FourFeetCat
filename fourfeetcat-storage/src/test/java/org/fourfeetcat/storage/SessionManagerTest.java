package org.fourfeetcat.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.fourfeetcat.core.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 会话管理器：身份判定是本节唯一有分支的逻辑，用内存替身钉死，不碰数据库（落盘口径由 {@link SessionRepositoryTest} 用真实 SQLite 覆盖）。 */
class SessionManagerTest {

  private final Map<String, SessionEntity> store = new HashMap<>();

  private JpaSessionManager sessionManager;

  @BeforeEach
  void setUp() {
    SessionRepository repository = mock(SessionRepository.class);
    when(repository.findById(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
    when(repository.save(any(SessionEntity.class)))
        .thenAnswer(
            invocation -> {
              SessionEntity entity = invocation.getArgument(0);
              store.put(entity.getSessionId(), entity);
              return entity;
            });
    when(repository.count()).thenAnswer(invocation -> (long) store.size());
    sessionManager = new JpaSessionManager(repository);
  }

  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void getOrCreate_sameTriple_returnsSameSession() {
    Session first = sessionManager.getOrCreate("cli", "wang", "default");
    Session second = sessionManager.getOrCreate("cli", "wang", "default");

    assertThat(second.getId()).isEqualTo(first.getId());
    Session other = sessionManager.getOrCreate("web", "wang", "default");
    assertThat(other.getId()).isNotEqualTo(first.getId());
  }

  @Test
  @DisplayName("三元组任一分量不同_就是不同Session（换人不串台、换Agent不串台）")
  void getOrCreate_differentUserOrProfile_isDifferentSession() {
    String base = sessionManager.getOrCreate("cli", "wang", "default").getId();

    assertThat(sessionManager.getOrCreate("cli", "li", "default").getId()).isNotEqualTo(base);
    assertThat(sessionManager.getOrCreate("cli", "wang", "weather").getId()).isNotEqualTo(base);
  }

  @Test
  @DisplayName("同一三元组反复取用_落库恰好一行（主键唯一兜底）")
  void getOrCreateAndSave_calledTwice_persistsExactlyOneRow() {
    sessionManager.save(sessionManager.getOrCreate("cli", "wang", "default"));
    sessionManager.save(sessionManager.getOrCreate("cli", "wang", "default"));

    assertThat(store).hasSize(1);
  }

  @Test
  @DisplayName("取不存在的会话_返回空而不是报错")
  void get_unknownSessionId_returnsEmpty() {
    assertThat(sessionManager.get("cli:wang:default")).isEmpty();
  }
}
