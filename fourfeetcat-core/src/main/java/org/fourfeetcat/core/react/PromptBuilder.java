package org.fourfeetcat.core.react;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.session.Session;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 每轮请求的组装者（第17节课件配角一）：按固定顺序拼四部分——角色与启动信息、长期记忆、对话历史、工具说明。
 *
 * <p>工具说明不在这里出现：它随 {@code LlmCaller.chat(..., tools, prompt)} 的 tools 参数下发，由 Provider 翻译成 Function
 * Calling 格式。历史只留最近 N 轮（默认 20），这是坑二（上下文撑爆）的解法。
 */
public class PromptBuilder {

  private final ContextLoader contextLoader;
  private final MemoryService memoryService;

  /** 未接记忆时用：长期记忆部分整体跳过。 */
  public PromptBuilder(ContextLoader contextLoader) {
    this(contextLoader, null);
  }

  /**
   * @param memoryService 记忆门面（第22节起由它供给长期记忆那一段）；传 {@code null} 即视为未启用记忆，该段整体跳过
   */
  public PromptBuilder(ContextLoader contextLoader, MemoryService memoryService) {
    this.contextLoader = contextLoader;
    this.memoryService = memoryService;
  }

  public Prompt build(Session session, Profile profile) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemText(profile)));
    // 长期记忆段：门面只给长期记忆（核心区全量 + 归档区截断后），会话历史由下面的历史段独立负责——两处都拼会出现两份历史
    String memory = memoryService == null ? null : memoryService.buildContext(session);
    if (memory != null && !memory.isBlank()) {
      messages.add(new SystemMessage(memory));
    }
    messages.addAll(recentMessages(session, ProfileSettings.maxHistoryTurns(profile)));
    return new Prompt(messages);
  }

  private String systemText(Profile profile) {
    StringBuilder text = new StringBuilder();
    Profile.Identity identity = profile.identity();
    if (identity.agentName() != null && !identity.agentName().isBlank()) {
      text.append("你是").append(identity.agentName()).append("。\n");
    }
    if (identity.prompt() != null && !identity.prompt().isBlank()) {
      text.append(identity.prompt()).append('\n');
    }
    String context = contextLoader.load(profile);
    if (!context.isBlank()) {
      text.append(context).append('\n');
    }
    // 模型自己不知道今天几号，定时场景的"今天"全靠这一行——必须在末尾
    text.append("当前时间：")
        .append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    return text.toString();
  }

  /**
   * 只留最近 N 轮：从末尾往前数到第 N 条用户消息，切点定在它之前。
   *
   * <p>切点落在用户消息上，助手工具调用与它的回填消息就不会被切成半截（半截序列在模型端是非法请求）。
   */
  private static List<Message> recentMessages(Session session, int maxHistoryTurns) {
    List<Message> all = session.getMessages();
    int turns = 0;
    for (int i = all.size() - 1; i >= 0; i--) {
      if (all.get(i) instanceof UserMessage) {
        turns++;
        if (turns == maxHistoryTurns) {
          return List.copyOf(all.subList(i, all.size()));
        }
      }
    }
    return all;
  }
}
