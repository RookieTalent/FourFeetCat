package org.fourfeetcat.core.session;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 一次对话的全部状态（第17节课件：循环累积的载体）。
 *
 * <p>本节为内存态：循环与编排只需要「按序追加 + 读回」。持久化的完整实现归第18节 SessionManager， {@code session_id}
 * 的拼接公式也只在那一处落地（本节只持有调用方给的 id）。
 *
 * <p>消息直接用 Spring AI 的 {@link Message} 承载：组装请求时近乎透传，工具结果回填天然带 toolCallId。
 */
public class Session {

  private final String id;
  private final String profileName;
  private final String channel;
  private final String userId;
  private final List<Message> messages = new ArrayList<>();

  public Session(String id, String profileName, String channel, String userId) {
    this.id = id;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
  }

  public String getId() {
    return id;
  }

  public String getProfileName() {
    return profileName;
  }

  public String getChannel() {
    return channel;
  }

  public String getUserId() {
    return userId;
  }

  /** 用户这一轮说的话。 */
  public void appendUserMessage(String text) {
    messages.add(new UserMessage(text));
  }

  /** 整条存模型响应（含 toolCalls）：下一轮要拿它接上，事后也靠它审计。 */
  public void appendAssistantMessage(AssistantMessage message) {
    messages.add(message);
  }

  /** 一次响应里的工具结果合成一条消息：模型端要求工具结果紧跟对应助手消息、成组给出，分条追加会造成非法 消息序列。 */
  public void appendToolResponses(List<ToolResponseMessage.ToolResponse> responses) {
    messages.add(ToolResponseMessage.builder().responses(responses).build());
  }

  /** 按发生顺序的只读快照：循环与组装只读它，不改写会话状态。 */
  public List<Message> getMessages() {
    return List.copyOf(messages);
  }
}
