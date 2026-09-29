package org.fourfeetcat.web.schedule;

import java.util.List;
import org.fourfeetcat.core.schedule.AgentScheduler;
import org.fourfeetcat.core.schedule.ScheduledTaskView;
import org.fourfeetcat.core.schedule.TaskExecutionView;
import org.fourfeetcat.web.api.ApiResponse;
import org.fourfeetcat.web.api.InvalidRequestException;
import org.fourfeetcat.web.api.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务管理端点（第28节课件）：列表 / 执行历史 / 立即执行 / 启用停用—— 26 节只读管理台后的第一个写操作面。
 *
 * <p>与 {@code SessionApiController} 同纪律：这个类只做参数校验、错误包装与占位——实际操作全部委托 core 的 {@link
 * AgentScheduler}（"立即执行"最终走的就是与人推完全相同的处理入口）。id 不存在 → 404（由调度器抛的缺 id 异常映射），body 非法 → 400。
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleApiController {

  private final AgentScheduler scheduler;

  public ScheduleApiController(AgentScheduler scheduler) {
    this.scheduler = scheduler;
  }

  /** 全部定时任务及运行状态（下次触发 / 上次结果 / 次数 / 启用与否）。 */
  @GetMapping
  public ApiResponse<List<ScheduledTaskView>> list() {
    return ApiResponse.ok(scheduler.allTasks());
  }

  /** 某任务的执行历史。 */
  @GetMapping("/{id}/executions")
  public ApiResponse<List<TaskExecutionView>> executions(@PathVariable long id) {
    try {
      return ApiResponse.ok(scheduler.executionsOf(id));
    } catch (IllegalArgumentException e) {
      throw notFound(e);
    }
  }

  /** 立即执行一次（手动触发，无视启用状态）。 */
  @PostMapping("/{id}/run")
  public ApiResponse<ScheduledTaskView> runNow(@PathVariable long id) {
    try {
      scheduler.runNow(id);
      return ApiResponse.ok(requireView(id));
    } catch (IllegalArgumentException e) {
      throw notFound(e);
    }
  }

  /** 启用 / 停用：请求体必须给 enabled（布尔）。 */
  @PutMapping("/{id}")
  public ApiResponse<ScheduledTaskView> toggle(
      @PathVariable long id, @RequestBody(required = false) ToggleRequest request) {
    if (request == null || request.enabled() == null) {
      throw new InvalidRequestException("请求体必须包含布尔字段 enabled");
    }
    try {
      scheduler.setEnabled(id, request.enabled());
      return ApiResponse.ok(requireView(id));
    } catch (IllegalArgumentException e) {
      throw notFound(e);
    }
  }

  /** 动作完成后回读当前视图，让调用方确认到最新状态（run_count / last_status / enabled 都是这次动作的结果）。 */
  private ScheduledTaskView requireView(long id) {
    return scheduler.allTasks().stream()
        .filter(task -> task.scheduleId() == id)
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("无该定时任务: " + id));
  }

  private static ResourceNotFoundException notFound(IllegalArgumentException cause) {
    return new ResourceNotFoundException(cause.getMessage());
  }

  /** 启停请求体：字段是 {@link Boolean}（包装型）以便区分"没给"与"给了 false"。 */
  public record ToggleRequest(Boolean enabled) {}
}
