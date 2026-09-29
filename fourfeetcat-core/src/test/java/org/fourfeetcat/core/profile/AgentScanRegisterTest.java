package org.fourfeetcat.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.schedule.AgentScheduler;
import org.fourfeetcat.core.schedule.ScheduledTaskStore;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

/** 第29节验收 harness：扫一个放 N 个 Agent 目录的目录 → ProfileRegistry 出现 N 个、带 schedules 的都进了 AgentScheduler。 */
class AgentScanRegisterTest {

  @TempDir private Path workspace;

  @Test
  @DisplayName("扫描N个Agent目录_全部注册进ProfileRegistry_带schedules的进AgentScheduler")
  void scanTwoAgents_registersBoth_schedulesIntoAgentScheduler() throws IOException {
    Path agentsRoot = Files.createDirectories(workspace.resolve("agents"));
    Path agentA =
        AgentFixtures.writeAgent(
            agentsRoot, "agent-a", "name: agent-a\nprovider:\n  name: deepseek\n", "正文");
    Files.writeString(
        agentA.resolve("AGENT.md"),
        "---\nname: agent-a\nprovider:\n  name: deepseek\nschedules:\n  - id: a-tick\n    cron: 0 0 9 * * *\n    zone: Asia/Shanghai\n    message: 到点\n---\n正文");
    AgentFixtures.writeAgent(
        agentsRoot, "agent-b", "name: agent-b\nprovider:\n  name: deepseek\n", "正文");

    List<Profile> scanned = new AgentLoader(AgentFixtures.PROVIDERS).scan(agentsRoot);

    ProfileRegistry registry = new ProfileRegistry();
    scanned.forEach(registry::register);
    assertThat(registry.all()).hasSize(2);
    assertThat(registry.exists("agent-a")).isTrue();
    assertThat(registry.exists("agent-b")).isTrue();

    TaskScheduler taskScheduler = mock(TaskScheduler.class);
    when(taskScheduler.schedule(
            any(Runnable.class), any(org.springframework.scheduling.Trigger.class)))
        .thenReturn(mock(ScheduledFuture.class));
    ScheduledTaskStore store = mock(ScheduledTaskStore.class);
    AgentScheduler scheduler =
        new AgentScheduler(
            taskScheduler, registry, mock(AgentService.class), mock(SessionManager.class), store);

    scheduler.registerAll();

    // 只有 agent-a 带 schedules → 只登记一条，且归属 agent-a
    ArgumentCaptor<ScheduledTaskStore.ScheduleRegistration> captured =
        ArgumentCaptor.forClass(ScheduledTaskStore.ScheduleRegistration.class);
    verify(store, times(1)).register(captured.capture());
    assertThat(captured.getValue().profileName()).isEqualTo("agent-a");
  }

  @Test
  @DisplayName("扫描目录不存在或为空_注册0个不报错")
  void emptyOrMissingScanDir_registersZero() {
    List<Profile> none = new AgentLoader(AgentFixtures.PROVIDERS).scan(workspace.resolve("nope"));

    assertThat(none).isEmpty();
  }
}
