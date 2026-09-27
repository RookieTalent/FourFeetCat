package org.fourfeetcat.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 外部服务档专属：用进程内假服务替掉真实例，验协议转换对不对——不碰真 server、不花 token。 */
class Mem0MemoryStoreTest {

  private record Received(String path, String body) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String USER_ID = "agent-42";

  private HttpServer server;
  private final List<Received> received = new ArrayList<>();
  private volatile int status = 200;
  private volatile String responseBody = "{\"results\":[]}";

  @BeforeEach
  void startFakeMemoryService() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          received.add(new Received(exchange.getRequestURI().getPath(), body));
          byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(status, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopFakeMemoryService() {
    server.stop(0);
  }

  @Test
  @DisplayName("写入请求体带内容_分区 metadata_作用域标识")
  void appendSendsContentScopeAndUserId() throws IOException {
    store().append("用户偏好深色主题", MemoryScope.CORE);

    Received request = received.get(0);
    assertTrue(request.path().contains("/v1/memories"), "写的是它的 add 端点");
    JsonNode body = MAPPER.readTree(request.body());
    assertEquals("用户偏好深色主题", body.get("messages").get(0).get("content").asText());
    assertEquals("CORE", body.get("metadata").get("scope").asText(), "分区落进 metadata");
    assertEquals(USER_ID, body.get("user_id").asText());
  }

  @Test
  @DisplayName("检索被转发并解析成记忆文本")
  void recallForwardsQueryAndParsesResults() {
    responseBody = "{\"results\":[{\"memory\":\"用户喜欢咖啡\"},{\"memory\":\"项目叫 FourFeetCat\"}]}";

    List<String> hits = store().recallByKeyword("用户");

    assertEquals(List.of("用户喜欢咖啡", "项目叫 FourFeetCat"), hits);
    assertTrue(received.get(0).path().contains("/search"), "检索走它的 search 端点");
  }

  @Test
  @DisplayName("响应体不含期望字段_返回空列表不抛异常")
  void unexpectedResponseShapeYieldsEmptyList() {
    responseBody = "{\"results\":[{\"unexpected\":1}]}";

    assertTrue(store().recallByKeyword("用户").isEmpty(), "结构对不上是兼容性问题，不该炸整轮");
  }

  @Test
  @DisplayName("服务端 5xx_异常上抛不静默吞")
  void serverErrorPropagates() {
    status = 500;

    assertThrows(
        RestClientResponseException.class, () -> store().append("x", MemoryScope.ARCHIVAL));
  }

  @Test
  @DisplayName("连接被拒_异常上抛不静默返回空")
  void connectionRefusedPropagates() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort(); // 拿到一个刚被释放的端口：连它必然被拒
    }
    RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + closedPort).build();
    Mem0MemoryStore store = new Mem0MemoryStore(client, USER_ID);

    assertThrows(ResourceAccessException.class, () -> store.recallByKeyword("用户"));
  }

  private Mem0MemoryStore store() {
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    return new Mem0MemoryStore(RestClient.builder().baseUrl(baseUrl).build(), USER_ID);
  }
}
