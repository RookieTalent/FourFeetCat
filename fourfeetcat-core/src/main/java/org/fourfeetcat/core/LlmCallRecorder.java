package org.fourfeetcat.core;

import org.springframework.ai.chat.metadata.Usage;

/**
 * LLM 调用审计端口（依赖倒置）：provider 模块经此端口留痕，storage 模块出实现、boot 装配， 避免 provider 直依赖 storage。
 *
 * <p>成败都必须记（宪法原则五：审计 Day One 写入）——失败时 usage 可为 null、success 记 false、errorMessage 记原因。
 */
@FunctionalInterface
public interface LlmCallRecorder {

  void record(
      String sessionId,
      String provider,
      String model,
      Usage usage,
      boolean success,
      String errorMessage,
      long durationMs);
}
