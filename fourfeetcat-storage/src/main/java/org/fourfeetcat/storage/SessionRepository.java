package org.fourfeetcat.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sessions 表仓储（第18节：取/建/存 + 按最后活跃时间列举）。 */
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

  /** 列会话用：最近聊过的排前面。 */
  List<SessionEntity> findAllByOrderByLastActiveAtDesc();
}
