package org.fourfeetcat.core.react;

import java.util.List;
import org.fourfeetcat.core.ToolDescriptor;
import org.fourfeetcat.core.profile.Profile;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 循环调用大模型的端口（依赖倒置）：循环在 core、Provider 能力域在 {@code fourfeetcat-provider}，模块依赖只能由 后者指向前者，故在此开孔。
 *
 * <p>签名与第16节 {@code ProviderService.chat} 逐字同形，装配处传 {@code providerService::chat} 方法引用即可—— 前序节零改动。
 *
 * <p>职责刻意划窄：发起一次调用、拿回响应。循环与工具执行都不归它管。
 */
@FunctionalInterface
public interface LlmCaller {

  ChatResponse chat(String sessionId, Profile profile, List<ToolDescriptor> tools, Prompt prompt);
}
