# Phase 1 Java Contracts: ReAct 循环（第17节）

包路径：循环与编排 `org.fourfeetcat.core.react`；会话契约 `org.fourfeetcat.core.session`；工具契约 `org.fourfeetcat.core.tool`；持久化 `org.fourfeetcat.storage`。

## 1. core / 会话契约（`org.fourfeetcat.core.session`）

```java
/** 一次对话的全部状态；内存态，持久化的完整实现归第18节 SessionManager。 */
public class Session {
  public Session(String id, String profileName, String channel, String userId);
  public String getId();
  public String getProfileName();
  public String getChannel();
  public String getUserId();
  public List<Message> getMessages();                       // Spring AI Message，按序追加
  public void appendUserMessage(String text);            // UserMessage
  public void appendAssistantMessage(AssistantMessage message);   // 每轮模型响应，整条存（含 toolCalls）
  public void appendToolResponses(List<ToolResponse> responses);  // 一次响应内的工具结果，一条消息承载
}

/** 会话持久化出口（依赖倒置）：第18节补 getOrCreate/get 与 JPA 实现。 */
public interface SessionManager {
  void save(Session session);
}
```

## 2. core / 循环调用模型的端口（`org.fourfeetcat.core.react`）

```java
/** 与第16节 ProviderService.chat 同签名：装配处传 providerService::chat，前序节零改动。 */
@FunctionalInterface
public interface LlmCaller {
  ChatResponse chat(String sessionId, Profile profile, List<ToolDescriptor> tools, Prompt prompt);
}
```

## 3. core / 工具契约（`org.fourfeetcat.core.tool`）

```java
/** 工具表（课件原词）：按名取描述清单供组装请求、按名执行。第20节由 OryxTool + ToolRegistry 取代。 */
public interface ToolTable {
  List<ToolDescriptor> descriptors(List<String> names);   // 名单顺序保持，查不到的名直接失败报错
  ToolExecutionResult execute(String toolName, String inputJson);   // inputJson 为模型给出的 JSON 字符串
}

/** 与第20节 ToolResult 四字段对齐（success/content/errorMessage/retryable），届时按名合并。 */
public record ToolExecutionResult(
    boolean success, String content, String errorMessage, boolean retryable) {
  public static ToolExecutionResult success(String content);
  public static ToolExecutionResult failure(String errorMessage, boolean retryable);
}

/** 工具调用审计端口（依赖倒置，与第16节 LlmCallRecorder 同款）。 */
@FunctionalInterface
public interface ToolInvocationRecorder {
  void record(String sessionId, String toolName, String inputJson, String resultJson,
              boolean success, String errorMessage, long durationMs);
}
```

## 4. core / 循环与编排（`org.fourfeetcat.core.react`）

```java
/** 调度内核：只管转圈、判停、累积（宪法原则一：自实现，不用框架 Agent 抽象）。 */
public class ReActLoop {
  public ReActLoop(PromptBuilder promptBuilder, LlmCaller llmCaller, ToolExecutor toolExecutor);
  public String run(Session session, String userMessage, Profile profile);
  // 无工具调用 → 返回该轮文本；转满 max_iterations → 返回 "达到最大轮数，已停止"
}

/** 每轮请求组装：system（角色+启动信息+当前时间）→ 长期记忆（未启用跳过）→ 截断后历史。 */
public class PromptBuilder {
  public PromptBuilder(ContextLoader contextLoader, Function<Profile, String> longTermMemory);
  public PromptBuilder(ContextLoader contextLoader);      // 便捷构造：长期记忆未启用
  public Prompt build(Session session, Profile profile);
}

/** 工具执行唯一入口：查表 → 沙箱检查位（24 节接线）→ 执行 → 成败都落审计。 */
public class ToolExecutor {
  public ToolExecutor(ToolTable toolTable, ToolInvocationRecorder audit);
  public List<ToolDescriptor> descriptors(List<String> names);       // 转交 ToolTable，供组装请求用
  public ToolExecutionResult execute(String sessionId, AssistantMessage.ToolCall call);
}

/** 三种触发源共用的编排者（FR-010）。 */
public class AgentService {
  public AgentService(ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager);
  public String process(Session session, String userMessage);
  // ProfileContext.set → run → sessionManager.save → finally clear
}

/** 本次执行线程可见的当前 Agent（虚拟线程每请求独立）。 */
public final class ProfileContext {
  public static void set(Profile profile);
  public static Profile current();     // 未设置返回 null
  public static void clear();
}

/** 启动上下文供给：启动信息文件 + 能力包元数据，每次现读不缓存（FR-009）。 */
public class ContextLoader {
  public ContextLoader(Path workspaceRoot);        // 工作区根，如 .fourfeetcat/
  public String load(Profile profile);
  // bootstrap 文件缺失 → WARN 跳过；skills 引用的能力包缺失/元数据不可解析 → 抛异常
}
```

## 5. storage

```java
@Entity @Table(name = "tool_invocations")
public class ToolInvocation {           // 列名与 V17 脚本逐字一致；boolean↔INTEGER 手转、createdAt TEXT ISO-8601
  // id, sessionId, toolName, inputJson, resultJson, success, errorMessage, durationMs, createdAt
}

public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {
  List<ToolInvocation> findBySessionIdOrderByCreatedAtDesc(String sessionId);   // 与第16节 LlmCallRepository 同款
}

@Component
public class ToolInvocationRecorderImpl implements ToolInvocationRecorder { /* 经仓储落库 */ }
```

## 6. 依赖方向（硬约束）

```text
fourfeetcat-provider ──▶ fourfeetcat-core ◀── fourfeetcat-storage
                              ▲
                              └── (LlmCaller / SessionManager / ToolTable / ToolInvocationRecorder 四个端口)
```

`fourfeetcat-core` MUST NOT 依赖 provider 或 storage；上述四个端口是唯一的跨模块接缝。
