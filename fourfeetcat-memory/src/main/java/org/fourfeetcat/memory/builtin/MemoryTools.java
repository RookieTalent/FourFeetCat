package org.fourfeetcat.memory.builtin;

import java.util.List;
import java.util.Locale;
import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.core.tool.PlainTextResultConverter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 内置记忆工具（第22节）：把长期记忆暴露给 Agent——第20节登记为跨节的两个工具在这里落地。
 *
 * <p>写入时机永远由 Agent 判断（它觉得某件事值得长期记住就记一条），系统不做自动提炼：这是第21节评审定的口径。写核心还是 写归档由 Agent 经 {@code scope}
 * 显式指定，系统不猜。
 *
 * <p>它只认 {@link MemoryService} 门面，对底下是哪一档后端完全无感——换后端不碰本类，也不碰任何测试里对它的断言。这正是 门面与后端两层解耦的回报。
 *
 * <p>注册走与其余内置工具**完全相同**的注解管道（{@code ToolRegistry.registerAnnotated}），因此留痕、按 Agent 工具清单
 * 过滤都是现成的，本类不新增任何机制。
 */
public class MemoryTools {

  private static final String SCOPE_ARCHIVAL = "ARCHIVAL";

  private final MemoryService memoryService;

  public MemoryTools(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  @Tool(
      name = "save_memory",
      description = "记住一件值得长期记住的事",
      resultConverter = PlainTextResultConverter.class)
  public String saveMemory(
      @ToolParam(description = "要记住的内容") String content,
      @ToolParam(description = "core 或 archival，不确定就填 archival", required = false) String scope) {
    String normalized =
        (scope == null || scope.isBlank()) ? SCOPE_ARCHIVAL : scope.toUpperCase(Locale.ROOT);
    MemoryScope target;
    try {
      target = MemoryScope.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      // 非法分区明确报错点名，不静默落到归档区——落错了地方，Agent 以为记住了、实际记歪了
      return "无法识别的记忆分区: " + scope + "（应为 core 或 archival）";
    }
    memoryService.remember(content, target);
    return "已记住";
  }

  @Tool(
      name = "recall_memory",
      description = "按关键词检索长期记忆",
      resultConverter = PlainTextResultConverter.class)
  public String recallMemory(@ToolParam(description = "检索关键词") String keyword) {
    List<String> hits = memoryService.recall(keyword);
    // 未命中的"空"与读取失败的"错"是两件事：前者如实告诉模型没找到，不抛异常炸掉这一轮
    return hits.isEmpty() ? "没有找到相关记忆" : String.join("\n", hits);
  }
}
