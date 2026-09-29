package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.react.ContextLoader;
import org.fourfeetcat.core.react.PromptBuilder;
import org.fourfeetcat.core.react.ReActLoop;
import org.fourfeetcat.core.react.ToolExecutor;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.memory.MarkdownMemoryStore;
import org.fourfeetcat.memory.MemoryServiceImpl;
import org.fourfeetcat.memory.builtin.MemoryTools;
import org.fourfeetcat.provider.MockChatModel;
import org.fourfeetcat.provider.SpringAiProviderServiceImpl;
import org.fourfeetcat.provider.ToolSchemaAdapter;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 免 key 全链路门禁（第27节）：手工装配 ReActLoop + mock provider + 真实工具 / 记忆 / 会话，把"想 → 查 → 答 → 记账" 在 gate
 * 里自动走一遍。
 *
 * <p>只有"模型"是假的（{@link MockChatModel}）——ReActLoop / ToolExecutor / Memory / Session 与审计全部走真实路径，因此
 * 这是"全链路能不能跑通"的最小自动化证：llm 恰两轮、save_memory 恰一次、MEMORY.md 真写下、会话历史完整。
 */
class MockProviderFlowTest {

  @TempDir Path tempRoot;

  @Test
  @DisplayName("记住一句_走两轮 ReAct_记账不多不少")
  void rememberPhrase_walksTwoRoundsAndAuditsExactly() throws Exception {
    List<Boolean> llmSuccess = new ArrayList<>();
    List<String> toolNames = new ArrayList<>();
    List<String> toolSessions = new ArrayList<>();

    // —— 只"模型"是假的：其余全真实 ——
    ContextLoader context = new ContextLoader(tempRoot);
    MarkdownMemoryStore store = new MarkdownMemoryStore(tempRoot);
    MemoryServiceImpl memory = new MemoryServiceImpl(store);
    PromptBuilder builder = new PromptBuilder(context, memory);

    ChatModel mock = new MockChatModel();
    var llmCaller =
        new SpringAiProviderServiceImpl(
            Map.of("mock", mock),
            new ToolSchemaAdapter(),
            (sid, p, m, u, s, e, d) -> llmSuccess.add(s));
    ToolRegistry registry = new ToolRegistry();
    ToolExecutor executor =
        new ToolExecutor(
            registry,
            (sid, tool, input, result, s, e, d) -> {
              toolNames.add(tool);
              toolSessions.add(sid);
            });
    registry.registerAnnotated(new MemoryTools(memory));
    ReActLoop loop = new ReActLoop(builder, llmCaller, executor);

    Session session = new Session("cli:u1:mock-agent", "mock-agent", "cli", "u1");

    // —— 触发：一句"记住…" ——
    String reply = loop.run(session, "记住：我喜欢喝美式咖啡", mockProfile());

    // 答得出
    assertThat(reply).isEqualTo("已记住：我喜欢喝美式咖啡");

    // 账记得对：llm 恰 2 次、全成功；save_memory 恰 1 次、带回本次会话 id
    assertThat(llmSuccess).hasSize(2);
    assertThat(llmSuccess).containsOnly(true);
    assertThat(toolNames).containsExactly("save_memory");
    assertThat(toolSessions).containsExactly("cli:u1:mock-agent");

    // 记忆真写进临时工作区、读得到（store.load = GET /memory 走的那道门面，跨对话记得住的雏形）
    assertThat(store.load()).contains("美式咖啡");
    assertThat(Files.readString(tempRoot.resolve("memory").resolve("MEMORY.md"))).contains("美式咖啡");

    // 会话历史完整：user / assistant(工具请求) / tool / assistant(最终答案) 四条
    assertThat(session.getMessages()).hasSize(4);
  }

  private static Profile mockProfile() {
    return new Profile(
        "mock-agent",
        "mock 测试 Agent",
        new Profile.Identity("mock助手", "测试用"),
        new Profile.ProviderConfig("mock", "mock-model", 0.7),
        List.of("save_memory"),
        List.of(),
        List.of(),
        List.of("cli"),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }
}
