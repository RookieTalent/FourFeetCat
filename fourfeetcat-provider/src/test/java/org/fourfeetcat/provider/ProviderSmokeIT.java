package org.fourfeetcat.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.fourfeetcat.core.LlmCallRecorder;
import org.fourfeetcat.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 集成冒烟（课件第16节"做完怎么验"的人工项）：真 key 真调一次 DeepSeek，验证"key 对、依赖对、 真的通"。CI 默认跳过 @Tag("integration")；本地手动跑：
 * {@code DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl fourfeetcat-provider}。
 *
 * <p>注：审计断言走 {@link LlmCallRecorder} 端口的内存记录实现（llm_calls 表级读写往返由 storage 模块的 LlmCallRepositoryTest
 * 承载，boot 装配后同一实现即落库）。
 */
@Tag("integration")
class ProviderSmokeIT {

  private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

  @Test
  void realCall_returnsNonEmptyResponseAndAuditsSuccess() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isBlank(), "DEEPSEEK_API_KEY 未配置，跳过冒烟");

    ChatModel deepseek =
        OpenAiChatModel.builder()
            .openAiApi(OpenAiApi.builder().baseUrl(DEEPSEEK_BASE_URL).apiKey(apiKey).build())
            .build();
    List<Boolean> auditResults = new CopyOnWriteArrayList<>();
    LlmCallRecorder recorder =
        (sessionId, provider, model, usage, success, errorMessage, durationMs) ->
            auditResults.add(success);

    ProviderService service =
        new ProviderService(Map.of("deepseek", deepseek), new ToolSchemaAdapter(), recorder);

    Profile profile =
        new Profile(
            "smoke-agent",
            null,
            null,
            new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.7),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    ChatResponse response =
        service.chat("smoke-session", profile, List.of(), new Prompt("用一句话回答：1+1等于几？"));

    assertThat(response.getResult().getOutput().getText()).isNotBlank();
    // llm_calls 语义：真调一次、success=1
    assertThat(auditResults).containsExactly(true);
  }
}
