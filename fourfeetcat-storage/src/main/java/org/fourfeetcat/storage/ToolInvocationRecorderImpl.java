package org.fourfeetcat.storage;

import java.time.Instant;
import org.fourfeetcat.core.tool.ToolInvocationRecorder;
import org.springframework.stereotype.Component;

/** {@link ToolInvocationRecorder} 端口的存储实现：经 JPA 仓储落 tool_invocations 表。 */
@Component
public class ToolInvocationRecorderImpl implements ToolInvocationRecorder {

  private final ToolInvocationRepository repository;

  public ToolInvocationRecorderImpl(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs) {
    ToolInvocation invocation = new ToolInvocation();
    invocation.setSessionId(sessionId);
    invocation.setToolName(toolName);
    invocation.setInputJson(inputJson);
    invocation.setResultJson(resultJson);
    invocation.setSuccess(success);
    invocation.setErrorMessage(errorMessage);
    invocation.setDurationMs(durationMs);
    invocation.setCreatedAt(Instant.now().toString());
    repository.save(invocation);
  }
}
