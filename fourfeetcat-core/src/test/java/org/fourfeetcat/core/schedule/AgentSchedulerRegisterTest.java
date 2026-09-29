package org.fourfeetcat.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/** 第29节验收 harness：registerProfile 后 scheduledTasks 句柄表有句柄（30 节注销前提）；cron/时区来自 Profile.schedules。 */
class AgentSchedulerRegisterTest {

  private TaskScheduler taskScheduler;
  private ScheduledTaskStore store;
  private AgentScheduler scheduler;

  @BeforeEach
  void setUp() {
    taskScheduler = mock(TaskScheduler.class);
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
        .thenReturn(mock(ScheduledFuture.class));
    store = mock(ScheduledTaskStore.class);
    scheduler =
        new AgentScheduler(
            taskScheduler,
            mock(ProfileRegistry.class),
            mock(AgentService.class),
            mock(SessionManager.class),
            store);
  }

  @Test
  @DisplayName("registerProfile后_scheduledTasks句柄表有句柄")
  void registerProfile_handleLandsInScheduledTasks() {
    Map<String, Object> entry = scheduleEntry("recon-morning", "0 0 9 * * *", "Asia/Shanghai");

    int registered = scheduler.registerProfile(profile("recon", List.of(entry)));

    assertThat(registered).isEqualTo(1);
    assertThat(scheduler.scheduledTasksView()).containsKey("recon-morning");
  }

  @Test
  @DisplayName("多条schedule各留句柄_标识为键")
  void multipleSchedules_eachKeepsAHandle() {
    Map<String, Object> a = scheduleEntry("a-tick", "0 0 9 * * *", "Asia/Shanghai");
    Map<String, Object> b = scheduleEntry("b-tick", "0 0 18 * * *", "America/New_York");

    scheduler.registerProfile(profile("recon", List.of(a, b)));

    assertThat(scheduler.scheduledTasksView()).hasSize(2).containsKeys("a-tick", "b-tick");
  }

  @Test
  @DisplayName("cron与时区来自Profile的schedules")
  void cronAndZone_comeFromProfileSchedules() {
    Map<String, Object> entry = scheduleEntry("recon-morning", "0 0 9 * * *", "Asia/Shanghai");

    scheduler.registerProfile(profile("recon", List.of(entry)));

    ArgumentCaptor<ScheduledTaskStore.ScheduleRegistration> captured =
        ArgumentCaptor.forClass(ScheduledTaskStore.ScheduleRegistration.class);
    verify(store).register(captured.capture());
    assertThat(captured.getValue().profileName()).isEqualTo("recon");
    assertThat(captured.getValue().cron()).isEqualTo("0 0 9 * * *");
    assertThat(captured.getValue().zone()).isEqualTo("Asia/Shanghai");
  }

  private static Profile profile(String name, List<Map<String, Object>> schedules) {
    return new Profile(
        name,
        "desc",
        new Profile.Identity(name + "市", "人格 prompt"),
        new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.2),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        schedules,
        List.of(),
        Map.of());
  }

  private static Map<String, Object> scheduleEntry(String id, String cron, String zone) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", id);
    entry.put("cron", cron);
    entry.put("zone", zone);
    entry.put("message", "到点执行");
    return entry;
  }
}
