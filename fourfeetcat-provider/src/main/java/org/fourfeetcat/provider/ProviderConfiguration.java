package org.fourfeetcat.provider;

import java.time.Duration;
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
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * 坑一的解法：按 {@code fourfeetcat.providers} 全局声明逐条构造 ChatModel，显式 put 进 {@code Map<String,
 * ChatModel>}（宪法原则三：显式映射，不扫描容器 Bean 类型）。
 *
 * <p>ChatModel 实现统一走 OpenAI 兼容端点（DeepSeek/Kimi/Qwen 均兼容），协议差异由 Spring AI 吸收；凭证经 {@code ${ENV}} 占位由
 * Spring 从环境变量解析。
 *
 * <p><b>第26节的改造点</b>：给模型调用设 60 秒读超时并把重试压到单次尝试（见 {@link #MODEL_CALL_READ_TIMEOUT}）。改动只在构造参数上，
 * 本类对外提供的 Bean（{@link LlmCaller}）签名与语义一字未改。
 */
@Configuration
@EnableConfigurationProperties(ProviderConfiguration.ProviderProperties.class)
public class ProviderConfiguration {

  /**
   * 单次模型调用的读超时：60 秒（第26节课件"Agent 调用最长 60 秒"的落点）。
   *
   * <p><b>口径</b>：上限落在**单次调用**，不是整轮处理——一轮多轮工具循环会包含多次调用，其总时长不受此约束。之所以不卡整轮：卡整轮要么
   * 引入"限时等待另一个线程"（与全同步阻塞的执行模型冲突），要么在超时返回后留下仍在写库的半截状态。
   */
  static final Duration MODEL_CALL_READ_TIMEOUT = Duration.ofSeconds(60);

  /**
   * 按给定读超时构造同步 HTTP 客户端。包内可见是为了让测试用短超时跑**同一条**构造路径——否则测试验的是另一套构造，等于没验。
   *
   * <p>用 Boot 的请求工厂构建器而非硬编码某个实现：当前类路径上只有 JDK HttpClient 可用，它会给出可辨识的超时异常；将来有人引入别的 客户端，只要超时仍然以"传输层失败
   * + 超时原因"浮现，上层映射（504）就不必改。
   */
  static RestClient.Builder httpClientWithReadTimeout(Duration readTimeout) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults().withReadTimeout(readTimeout);
    return RestClient.builder()
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
  }

  /**
   * 单次尝试的重试策略：**不重试**。
   *
   * <p>不显式给的后果是框架默认模板可能对超时类失败重试若干次——一次 60 秒超时被放大成数倍墙钟，"最长 60 秒"这句话就不成立了。
   */
  static RetryTemplate singleAttemptRetry() {
    return RetryTemplate.builder().maxAttempts(1).build();
  }

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
      OpenAiApi api =
          OpenAiApi.builder()
              .baseUrl(spec.baseUrl())
              .apiKey(spec.apiKey())
              // 第26节：连接级读超时（超时类失败经 Web 层的统一异常出口映射为 504）
              .restClientBuilder(httpClientWithReadTimeout(MODEL_CALL_READ_TIMEOUT))
              .build();
      // 第26节：单次尝试——重试会把一次超时的墙钟放大成数倍，"最长 60 秒"随之失效
      ChatModel chatModel =
          OpenAiChatModel.builder().openAiApi(api).retryTemplate(singleAttemptRetry()).build();
      ChatModel previous = providerMap.put(spec.name(), chatModel);
      if (previous != null) {
        throw new IllegalStateException("fourfeetcat.providers 里 provider 名重复: " + spec.name());
      }
    }
    return new SpringAiProviderServiceImpl(Map.copyOf(providerMap), new ToolSchemaAdapter(), audit);
  }
}
