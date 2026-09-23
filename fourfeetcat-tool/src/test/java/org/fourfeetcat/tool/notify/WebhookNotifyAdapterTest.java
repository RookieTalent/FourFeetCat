package org.fourfeetcat.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 课件 harness 第一批（本节立即可跑）：通用 webhook 实现的协议守点 + 契约中立性守卫。
 *
 * <p>假接收端用 <b>JDK 内置</b>的 HTTP 服务类（课件写的是 MockWebServer，但本仓依赖里没有该库、锁定的 BOM 也不管理它，为
 * 一个测试脚手架引第三方依赖不划算）。它不算外网依赖——仍是本地单测层。
 */
@DisplayName("通用 webhook 实现（课件 harness 第一批）")
class WebhookNotifyAdapterTest {

  private StubWebhookServer server;
  private StubWebhookServer otherServer;
  private WebhookNotifyAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    server = new StubWebhookServer(200);
    otherServer = new StubWebhookServer(200);
    adapter = new WebhookNotifyAdapter(RestClient.create());
  }

  @AfterEach
  void tearDown() {
    server.stop();
    otherServer.stop();
  }

  @Test
  @DisplayName("发送后_假接收端收到的POST请求body里带所传内容")
  void send_postsBodyCarryingContent() {
    adapter.send(webhookTarget(server), "测试消息");

    assertThat(server.requests()).hasSize(1);
    RecordedRequest request = server.requests().get(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.contentType()).contains("application/json");
    assertThat(request.body()).contains("content").contains("测试消息");
  }

  @Test
  @DisplayName("地址取自通知目标配置_不是硬编码（换个目标就换个接收端）")
  void send_usesUrlFromTargetConfig() {
    adapter.send(webhookTarget(otherServer), "只该到一个地方");

    assertThat(otherServer.requests()).hasSize(1);
    assertThat(server.requests()).isEmpty();
  }

  @Test
  @DisplayName("接收端返回5xx_异常向上抛不静默吞")
  void send_serverError_isNotSwallowed() {
    server.respondWith(503);

    assertThatThrownBy(() -> adapter.send(webhookTarget(server), "发不出去"))
        .isInstanceOf(RestClientResponseException.class);

    assertThat(server.requests()).hasSize(1);
  }

  @Test
  @DisplayName("目标配置缺地址_发送前就报错_不发一个空地址的请求")
  void send_missingUrl_failsBeforeSending() {
    NotifyTarget withoutUrl = new NotifyTarget("webhook", Map.of());

    assertThatThrownBy(() -> adapter.send(withoutUrl, "没地址"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(server.requests()).isEmpty();
  }

  @Test
  @DisplayName("契约中立性_换一档实现不改调用方（签名形状 + 可替换性）")
  void contract_staysNeutralAcrossImplementations() {
    // 签名形状：只有一个抽象方法，入参只有"通知目标 + 内容"——往里塞渠道特有的字段会让本断言变红
    Method[] declared = NotifyChannelAdapter.class.getDeclaredMethods();
    assertThat(declared).hasSize(1);
    assertThat(declared[0].getParameterTypes()).containsExactly(NotifyTarget.class, String.class);
    assertThat(NotifyTarget.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("channelType", "config");

    // 可替换性：同一段调用代码换成"另一档实现"的替身，零改动即跑通
    List<String> delivered = new CopyOnWriteArrayList<>();
    NotifyChannelAdapter anotherTier =
        (target, content) -> delivered.add(target.channelType() + ":" + content);
    NotifyTarget target = new NotifyTarget("feishu", Map.of("url", "https://example.invalid/hook"));
    anotherTier.send(target, "hello");

    assertThat(delivered).containsExactly("feishu:hello");
  }

  private static NotifyTarget webhookTarget(StubWebhookServer target) {
    return new NotifyTarget("webhook", Map.of("url", target.url()));
  }

  private record RecordedRequest(String method, String contentType, String body) {}

  /** 本地假接收端：收 POST、记 body、按设定返回状态码（端口由系统分配，避免测试间抢端口）。 */
  private static final class StubWebhookServer {

    private final HttpServer httpServer;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int status;

    StubWebhookServer(int initialStatus) throws IOException {
      this.status = initialStatus;
      this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      httpServer.createContext(
          "/hook",
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(
                new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
          });
      httpServer.start();
    }

    String url() {
      return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/hook";
    }

    List<RecordedRequest> requests() {
      return List.copyOf(requests);
    }

    void respondWith(int newStatus) {
      this.status = newStatus;
    }

    void stop() {
      httpServer.stop(0);
    }
  }
}
