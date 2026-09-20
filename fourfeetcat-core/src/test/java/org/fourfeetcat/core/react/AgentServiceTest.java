package org.fourfeetcat.core.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

  private ProfileRegistry profileRegistry;
  private ReActLoop reActLoop;
  private SessionManager sessionManager;
  private AgentService service;
  private Session session;

  @BeforeEach
  void setUp() {
    profileRegistry = mock(ProfileRegistry.class);
    reActLoop = mock(ReActLoop.class);
    sessionManager = mock(SessionManager.class);
    when(profileRegistry.find("ops-agent")).thenReturn(Optional.of(profile()));
    service = new AgentService(profileRegistry, reActLoop, sessionManager);
    session = new Session("cli:u-1:ops-agent", "ops-agent", "cli", "u-1");
  }

  @AfterEach
  void clearContext() {
    ProfileContext.clear();
  }

  @Test
  @DisplayName("处理期间_ProfileContext能取到当前Profile")
  void duringProcess_profileContextHoldsCurrentProfile() {
    when(reActLoop.run(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Profile current = ProfileContext.current();
              assertThat(current).isNotNull();
              assertThat(current.name()).isEqualTo("ops-agent");
              return "今天晴";
            });

    assertThat(service.process(session, "今天穿什么")).isEqualTo("今天晴");
  }

  @Test
  @DisplayName("处理中抛异常_ProfileContext也必须被清掉")
  void runThrows_profileContextStillCleared() {
    when(reActLoop.run(any(), any(), any())).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.process(session, "hi")).isInstanceOf(RuntimeException.class);

    // finally 没清，下一个复用此线程的请求会拿到别人的 Profile（单请求测试永远不报错的一类 bug）
    assertThat(ProfileContext.current()).isNull();
  }

  @Test
  @DisplayName("处理结束_ProfileContext同样被清掉")
  void normalReturn_profileContextCleared() {
    when(reActLoop.run(any(), any(), any())).thenReturn("好");

    service.process(session, "hi");

    assertThat(ProfileContext.current()).isNull();
  }

  @Test
  @DisplayName("处理结束_会话被持久化")
  void afterProcess_sessionPersisted() {
    when(reActLoop.run(any(), any(), any())).thenReturn("好");

    service.process(session, "hi");

    verify(sessionManager).save(session);
  }

  @Test
  @DisplayName("会话归属的Agent查不到_报错并指明名字")
  void unknownProfile_throwsWithProfileName() {
    when(profileRegistry.find("ops-agent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.process(session, "hi"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ops-agent");
  }

  private static Profile profile() {
    return new Profile(
        "ops-agent",
        "运维助手",
        new Profile.Identity("运维小欧", "你是一个专业的运维助手"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.7),
        List.of("http_get"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        List.of(),
        List.of("AGENTS.md"),
        Map.of("max_iterations", 10));
  }
}
