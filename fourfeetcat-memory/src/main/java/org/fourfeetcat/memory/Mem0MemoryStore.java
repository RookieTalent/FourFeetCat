package org.fourfeetcat.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.memory.MemoryScope;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 档三：接一个**自托管**的 Mem0 记忆层（数据不出域），把三个操作翻译成它的写入 / 读取 / 检索调用——提炼、冲突消解、 语义召回都交给它。真需要"智能记忆"时的外部集成档。
 *
 * <p>契约在这一档的形态变了：契约二变成"信任它的作用域机制"（截断归它管），契约四被升级成语义检索（它自带的 {@code search} 比关键词强）。协议转换是这一档唯一做的事。
 *
 * <p>端点路径按该服务的社区版约定编写，<b>具体路径与参数以你部署的版本为准</b>。地址与作用域标识由装配处从环境变量 注入，代码里不出现明文凭证。
 *
 * <p><b>边界判断</b>：本类会出网，但它<b>不经工具执行的沙箱校验位</b>——它是装配期按配置选定的后端，读写在门面内发生、 不属工具执行路径；地址本身就是部署者配的（详见第22节
 * plan 的 Complexity Tracking）。别把它误判成漏检。
 */
public class Mem0MemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestClient restClient;
  private final String userId;

  /**
   * @param restClient 基址指向自托管实例的同步 HTTP 客户端（装配处按配置构造）
   * @param userId 记忆作用域标识（该服务按作用域组织记忆）
   */
  public Mem0MemoryStore(RestClient restClient, String userId) {
    this.restClient = restClient;
    this.userId = userId;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    // 它的 add 自己做提炼与冲突消解；分区落进 metadata 供检索区分
    restClient
        .post()
        .uri("/v1/memories/")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "messages", List.of(Map.of("role", "user", "content", content)),
                "user_id", userId,
                "metadata", Map.of("scope", scope.name())))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public String load() {
    return CORE_HEADER
        + "\n"
        + String.join("\n", getByScope(MemoryScope.CORE.name()))
        + "\n"
        + ARCHIVE_HEADER
        + "\n"
        + String.join("\n", getByScope(MemoryScope.ARCHIVAL.name()));
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    // 它的 search 是语义检索——契约四的加强版实现
    String body =
        restClient
            .post()
            .uri("/v1/memories/search/")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("query", keyword, "user_id", userId))
            .retrieve()
            .body(String.class);
    return extractMemoryTexts(body);
  }

  private List<String> getByScope(String scope) {
    String body =
        restClient
            .get()
            .uri("/v1/memories/?user_id={u}&scope={s}", userId, scope)
            .retrieve()
            .body(String.class);
    return extractMemoryTexts(body);
  }

  /**
   * 从响应里抽记忆文本：有 {@code results} 就取 {@code results[].memory}，否则把根当数组。
   *
   * <p>响应结构随服务版本而异——解析不出（或响应为空）一律返回空列表，<b>不抛异常</b>：返回结构对不上是兼容性问题，
   * 不该让整轮对话失败；而连接失败与服务端错误由客户端直接抛出，那是真故障，必须上抛。
   */
  private static List<String> extractMemoryTexts(String body) {
    List<String> texts = new ArrayList<>();
    if (body == null || body.isBlank()) {
      return texts;
    }
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode results = root.has("results") ? root.get("results") : root;
      for (JsonNode item : results) {
        JsonNode memory = item.get("memory");
        if (memory != null) {
          texts.add(memory.asText());
        }
      }
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("记忆服务响应解析失败", e);
    }
    return texts;
  }
}
