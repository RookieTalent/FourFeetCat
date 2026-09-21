# Contracts: Agent Provider（第16节）

本节对外暴露的都是 Java 跨模块契约（库形态，无 REST/CLI 面）。

## 1. `SpringAiProviderServiceImpl.chat`（provider 模块对外唯一入口，实现 core 的 `LlmCaller` 端口）

```java
// public class SpringAiProviderServiceImpl implements LlmCaller
public ChatResponse chat(String sessionId, Profile profile,
                         List<ToolDescriptor> tools, Prompt prompt)
```

- 语义：按 `profile.provider().name()` 从显式映射表取 `ChatModel`；未知名抛 `ProviderNotFoundException`（消息含所引用名）。options 统一在此构建：model/temperature 来自 Profile、`toolCallbacks` 为适配器翻译产物、`internalToolExecutionEnabled(false)`。成败都经 `LlmCallRecorder` 落审计后返回/上抛。
- 端口实现：签名与 core `LlmCaller` 逐字同形，Spring 把它作为 `LlmCaller` bean 注入第17节 ReActLoop（依赖倒置：provider ──▶ core，core 不反向依赖）。
- 调用方：第17节 ReActLoop（经 `LlmCaller` 端口）。响应中的工具调用请求（`getToolCalls()`）原样交回，不执行。

## 2. `LlmCallRecorder`（core 端口，storage 实现，boot 装配）

```java
public interface LlmCallRecorder {
    void record(String sessionId, String provider, String model,
                Usage usage, boolean success, String errorMessage, long durationMs);
}
```

- 语义：一次 LLM 调用的审计留痕（成功：usage 记 token；失败：success=false + errorMessage）。`Usage` 为 Spring AI 类型。

## 3. `ToolSchemaAdapter`（provider 模块内部件，harness 直接测）

```java
public List<ToolCallback> toSpringAiTools(List<ToolDescriptor> tools)
```

- 语义：只翻译（name/description/inputSchema 一一对齐），产物 `call()` 抛 `IllegalStateException`——不可执行是被测行为，不是待修缺陷。

## 4. `Profile` / `ProfileLoader` / `ProfileRegistry`（core 契约）

- `ProfileLoader.load(Path profilesDir) → List<Profile>`：扫 `*.yaml`，坏文件记错误日志跳过不阻断；env 读取以 `Function<String,String>` 注入（默认 `System::getenv`，测试可 stub）。
- `ProfileRegistry.find(String name) → Optional<Profile>`：构造期收全局 provider 名单做"provider 名可解析"校验。
- `ToolDescriptor(String name, String description, String inputSchema)`：工具 schema 最小载体，第20节工具接口的衔接点。

## 5. 配置面

`fourfeetcat-boot/src/main/resources/application.yaml` 新增段（字面量）：

```yaml
fourfeetcat:
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY}
```

（课件原文 `oryxos.providers` → 仓库 `fourfeetcat.providers`，映射见 spec Assumptions。）
