package org.fourfeetcat.storage;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** scheduled_tasks 表仓储（第28节）。 */
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

  /** 同 Agent 内一台任务只一行：幂等 upsert 的定位键。 */
  Optional<ScheduledTask> findByProfileNameAndScheduleKey(String profileName, String scheduleKey);

  /** 定义协调：找出该 Agent 全部登记（含已退役的，退役只是软标记）。 */
  List<ScheduledTask> findByProfileName(String profileName);
}
