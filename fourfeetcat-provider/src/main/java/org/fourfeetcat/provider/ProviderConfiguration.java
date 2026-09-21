package org.fourfeetcat.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.LlmCallRecorder;
import org.fourfeetcat.core.react.LlmCaller;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 坑一的解法：按 {@code fourfeetcat.providers} 全局声明逐条构造 ChatModel，显式 put 进 {@code Map<String,
 * ChatModel>}（宪法原则三：显式映射，不扫描容器 Bean 类型）。
 *
 * <p>ChatModel 实现统一走 OpenAI 兼容端点（DeepSeek/Kimi/Qwen 均兼容），协议差异由 Spring AI 吸收；凭证经 {@code ${ENV}} 占位由
 * Spring 从环境变量解析。
 */
@Configuration
@EnableConfigurationProperties(ProviderConfiguration.ProviderProperties.class)
public class ProviderConfiguration {

  /** 全局层配置：本实例接了哪些 provider（{@code fourfeetcat.providers}）。 */
  @ConfigurationProperties(prefix = "fourfeetcat")
  public record ProviderProperties(List<ProviderSpec> providers) {
    public ProviderProperties {
      providers = providers == null ? List.of() : List.copyOf(providers);
    }
  }

  /** 单个 provider 声明：name 唯一；baseUrl 为 OpenAI 兼容端点；apiKey 走 ${ENV} 占位。 */
  public record ProviderSpec(String name, String baseUrl, String apiKey) {}

  @Bean
  public LlmCaller providerService(ProviderProperties properties, LlmCallRecorder audit) {
    Map<String, ChatModel> providerMap = new HashMap<>();
    List<ProviderSpec> specs = properties.providers() == null ? List.of() : properties.providers();
    for (ProviderSpec spec : specs) {
      OpenAiApi api = OpenAiApi.builder().baseUrl(spec.baseUrl()).apiKey(spec.apiKey()).build();
      ChatModel chatModel = OpenAiChatModel.builder().openAiApi(api).build();
      ChatModel previous = providerMap.put(spec.name(), chatModel);
      if (previous != null) {
        throw new IllegalStateException("fourfeetcat.providers 里 provider 名重复: " + spec.name());
      }
    }
    return new SpringAiProviderServiceImpl(Map.copyOf(providerMap), new ToolSchemaAdapter(), audit);
  }
}
