package org.fourfeetcat.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** task_executions 表仓储（第28节）。 */
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

  /** 某任务的历史，最近的一次在前（管理台列表直接喂它）。 */
  List<TaskExecution> findByScheduleIdOrderByIdDesc(Long scheduleId);
}
