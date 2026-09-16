package org.fourfeetcat.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** llm_calls 审计表仓储（第16节只写入；查询接口放扩展阶段）。 */
public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {

  List<LlmCall> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
