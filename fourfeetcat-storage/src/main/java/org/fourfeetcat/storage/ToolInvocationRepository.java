package org.fourfeetcat.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** tool_invocations 的 JPA 仓储（口径与第16节 LlmCallRepository 一致）。 */
public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {

  /** 按会话追溯一次处理的工具调用链（审计查询接口留扩展阶段，这里先给最低限度的一条）。 */
  List<ToolInvocation> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
