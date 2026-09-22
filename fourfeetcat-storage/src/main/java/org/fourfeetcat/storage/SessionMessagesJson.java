package org.fourfeetcat.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 对话历史 ↔ {@code messages_json} 一列的往返编解码（包内私有）。
 *
 * <p>为什么不直接让 Jackson 序列化 Spring AI 的消息对象：它们没有反序列化入口（构造器受保护、无
 * {@code @JsonCreator}），直接读写无法往返。这里只用树模型写三种已知形状，读的时候按 type 分派，**未知 type 直接抛
 * 异常**——丢消息等于下一轮模型看到残缺上下文，是最难查的一类软故障。
 *
 * <p>会话里只会出现三种消息：system 消息每轮由 PromptBuilder 现拼、不入会话（{@code ReActLoop} 只追加用户消息、模型响应、工具结果）。
 */
final class SessionMessagesJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String FIELD_TYPE = "type";
  private static final String TYPE_USER = "user";
  private static final String TYPE_ASSISTANT = "assistant";
  private static final String TYPE_TOOL = "tool";

  private static final String FIELD_TEXT = "text";
  private static final String FIELD_TOOL_CALLS = "toolCalls";
  private static final String FIELD_RESPONSES = "responses";
  private static final String FIELD_ID = "id";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_ARGUMENTS = "arguments";
  private static final String FIELD_RESPONSE_DATA = "responseData";

  private SessionMessagesJson() {}

  static String write(List<Message> messages) {
    ArrayNode array = MAPPER.createArrayNode();
    for (Message message : messages) {
      array.add(toNode(message));
    }
    try {
      return MAPPER.writeValueAsString(array);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("会话历史序列化失败", e);
    }
  }

  static List<Message> read(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("会话历史反序列化失败", e);
    }
    if (!root.isArray()) {
      throw new IllegalStateException("会话历史必须是 JSON 数组，实际为: " + root.getNodeType());
    }
    List<Message> messages = new ArrayList<>(root.size());
    for (JsonNode node : root) {
      messages.add(fromNode(node));
    }
    return List.copyOf(messages);
  }

  private static JsonNode toNode(Message message) {
    if (message instanceof UserMessage user) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put(FIELD_TYPE, TYPE_USER);
      node.put(FIELD_TEXT, text(user.getText()));
      return node;
    }
    if (message instanceof AssistantMessage assistant) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put(FIELD_TYPE, TYPE_ASSISTANT);
      node.put(FIELD_TEXT, text(assistant.getText()));
      ArrayNode toolCalls = node.putArray(FIELD_TOOL_CALLS);
      for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
        ObjectNode callNode = toolCalls.addObject();
        callNode.put(FIELD_ID, call.id());
        callNode.put(FIELD_TYPE, call.type());
        callNode.put(FIELD_NAME, call.name());
        callNode.put(FIELD_ARGUMENTS, call.arguments());
      }
      return node;
    }
    if (message instanceof ToolResponseMessage toolResponse) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put(FIELD_TYPE, TYPE_TOOL);
      ArrayNode responses = node.putArray(FIELD_RESPONSES);
      for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
        ObjectNode responseNode = responses.addObject();
        responseNode.put(FIELD_ID, response.id());
        responseNode.put(FIELD_NAME, response.name());
        responseNode.put(FIELD_RESPONSE_DATA, text(response.responseData()));
      }
      return node;
    }
    throw new IllegalStateException("不支持的消息类型，无法落库: " + message.getClass().getName());
  }

  private static Message fromNode(JsonNode node) {
    String type = node.path(FIELD_TYPE).asText();
    if (TYPE_USER.equals(type)) {
      return new UserMessage(node.path(FIELD_TEXT).asText());
    }
    if (TYPE_ASSISTANT.equals(type)) {
      List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
      for (JsonNode call : node.path(FIELD_TOOL_CALLS)) {
        toolCalls.add(
            new AssistantMessage.ToolCall(
                call.path(FIELD_ID).asText(),
                call.path(FIELD_TYPE).asText(),
                call.path(FIELD_NAME).asText(),
                call.path(FIELD_ARGUMENTS).asText()));
      }
      return AssistantMessage.builder()
          .content(node.path(FIELD_TEXT).asText())
          .toolCalls(toolCalls)
          .build();
    }
    if (TYPE_TOOL.equals(type)) {
      List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
      for (JsonNode response : node.path(FIELD_RESPONSES)) {
        responses.add(
            new ToolResponseMessage.ToolResponse(
                response.path(FIELD_ID).asText(),
                response.path(FIELD_NAME).asText(),
                response.path(FIELD_RESPONSE_DATA).asText()));
      }
      return ToolResponseMessage.builder().responses(responses).build();
    }
    throw new IllegalStateException("会话历史里出现未知消息 type: " + type);
  }

  /** null 文本落空串：模型可以只给工具调用不给文本，null 会让 JSON 里出现 null 而不是 ""。 */
  private static String text(String value) {
    return value == null ? "" : value;
  }
}
