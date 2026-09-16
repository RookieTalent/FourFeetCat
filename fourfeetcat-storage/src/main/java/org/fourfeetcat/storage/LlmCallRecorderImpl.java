package org.fourfeetcat.storage;

import java.time.Instant;
import org.fourfeetcat.core.LlmCallRecorder;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/** {@link LlmCallRecorder} 端口的存储实现：经 JPA 仓储落 llm_calls 表。 */
@Component
public class LlmCallRecorderImpl implements LlmCallRecorder {

  private final LlmCallRepository repository;

  public LlmCallRecorderImpl(LlmCallRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String sessionId,
      String provider,
      String model,
      Usage usage,
      boolean success,
      String errorMessage,
      long durationMs) {
    LlmCall call = new LlmCall();
    call.setSessionId(sessionId);
    call.setProvider(provider);
    call.setModel(model);
    // 失败调用没有 usage（null），token 列落 0
    call.setPromptTokens(
        usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens());
    call.setCompletionTokens(
        usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    call.setTotalTokens(
        usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens());
    call.setSuccess(success);
    call.setErrorMessage(errorMessage);
    call.setDurationMs(durationMs);
    call.setCreatedAt(Instant.now().toString());
    repository.save(call);
  }
}
