package org.fourfeetcat.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 内置 HTTP 工具（课件 harness 一类）：正常能跑通 + 越界会被拦。
 *
 * <p>假接收端沿用第19节的做法：JDK 内置的 HTTP 服务类起在本地随机端口，不碰外网、不引第三方测试依赖。
 */
class HttpToolsTest {

  private static final Sandbox ALLOW_ALL = action -> {};

  private StubServer server;
  private HttpTools tools;

  @BeforeEach
  void setUp() throws IOException {
    server = new StubServer();
    tools = new HttpTools(ALLOW_ALL, RestClient.create());
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  @DisplayName("GET_取回响应体")
  void get_returnsResponseBody() {
    server.respondWith("{\"temp\":10}");

    String body =
        BuiltinToolTestSupport.tool(tools, "http_get")
            .execute(BuiltinToolTestSupport.json("url", server.url()))
            .content();

    assertThat(body).isEqualTo("{\"temp\":10}");
    assertThat(server.requests()).hasSize(1);
  }

  @Test
  @DisplayName("POST_带上请求体发给接收端")
  void post_sendsBodyToServer() {
    server.respondWith("ok");

    BuiltinToolTestSupport.tool(tools, "http_post")
        .execute(BuiltinToolTestSupport.json("url", server.url(), "body", "{\"hello\":1}"));

    assertThat(server.requests()).hasSize(1);
    assertThat(server.requests().get(0)).contains("{\"hello\":1}");
  }

  @Test
  @DisplayName("越界域名_请求根本没发出去")
  void blockedDomain_requestNeverLeaves() {
    Sandbox sandbox = mock(Sandbox.class);
    doThrow(new SandboxViolationException("域名不在白名单内")).when(sandbox).enforce(any());
    CatTool httpGet =
        BuiltinToolTestSupport.tool(new HttpTools(sandbox, RestClient.create()), "http_get");

    assertThatThrownBy(() -> httpGet.execute(BuiltinToolTestSupport.json("url", server.url())))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("白名单");

    // 拦在动手之前：接收端一个请求都没收到，才是真的没发出去
    assertThat(server.requests()).isEmpty();
    verify(sandbox).enforce(argThat(action -> action.type() == ActionType.HTTP_REQUEST));
  }

  /** 本地假接收端：记收到的请求体、按设定返回响应（端口由系统分配，避免测试间抢端口）。 */
  private static final class StubServer {

    private final HttpServer httpServer;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String response = "";

    StubServer() throws IOException {
      this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      httpServer.createContext(
          "/probe",
          exchange -> {
            requests.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
          });
      httpServer.start();
    }

    String url() {
      return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/probe";
    }

    List<String> requests() {
      return List.copyOf(requests);
    }

    void respondWith(String body) {
      this.response = body;
    }

    void stop() {
      httpServer.stop(0);
    }
  }
}
