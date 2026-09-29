package org.fourfeetcat.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 读超时的**真实**行为（第26节）：对着一个只接受连接、迟迟不应答的本地服务发一次模型调用，看它到底怎么失败。
 *
 * <p>这条测试的价值在于把"60 秒上限"从一句声明变成可验的事实：不设读超时，一次调用会一直挂着（整轮处理也就一直挂着）；设了之后， 失败以传输层异常的形式浮现，Web
 * 层的统一异常出口据此映射为 504。断言"原因链里认得出超时"是刻意宽一点的——它守的是**可辨识性** （上层能靠异常链把超时与普通连接失败分开），而不是某个具体异常类的名字；换个 HTTP
 * 客户端实现时，只要这条性质还在，映射就不必改。
 *
 * <p>用本地服务而非外网：不依赖任何外部可用性，CI 里照跑。
 */
class ProviderReadTimeoutTest {

  @Test
  @DisplayName("读超时_以传输层失败浮现且原因链里认得出超时")
  void readTimeout_surfacesAsTransportFailureCarryingTimeoutCause() throws IOException {
    HttpServer hanging = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    hanging.createContext("/", exchange -> sleepQuietly(Duration.ofSeconds(2)));
    hanging.start();
    try {
      ChatModel model = modelAgainst(hanging.getAddress().getPort(), Duration.ofMillis(300));

      Throwable thrown = catchThrowable(() -> model.call(new Prompt("你好")));

      assertThat(thrown).as("超时必须以异常形式浮现，不能静默挂住").isNotNull();
      String chain = causeChain(thrown);
      assertThat(chain).as("原因链: %s", chain).contains("Timeout");
    } finally {
      hanging.stop(0);
    }
  }

  /** 用生产代码那条构造路径（同一个请求工厂构建器），只把超时换成 300 毫秒。 */
  private static ChatModel modelAgainst(int port, Duration readTimeout) {
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl("http://127.0.0.1:" + port)
            .apiKey("test-key")
            .restClientBuilder(ProviderConfiguration.httpClientWithReadTimeout(readTimeout))
            .build();
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .retryTemplate(ProviderConfiguration.singleAttemptRetry())
        .build();
  }

  private static String causeChain(Throwable thrown) {
    StringBuilder chain = new StringBuilder();
    for (Throwable cursor = thrown; cursor != null; cursor = cursor.getCause()) {
      if (chain.length() > 0) {
        chain.append(" <- ");
      }
      chain.append(cursor.getClass().getName());
    }
    return chain.toString();
  }

  private static void sleepQuietly(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
