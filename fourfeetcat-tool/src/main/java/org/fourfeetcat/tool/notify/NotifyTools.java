package org.fourfeetcat.tool.notify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.notify.NotifyChannelSource;
import org.fourfeetcat.tool.registry.PlainTextResultConverter;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 内置通知工具（第20节课件）：把一条内容按渠道名推到全局注册表里登记的渠道。
 *
 * <p>第19节交付了推送的契约与实现，但它的接线要等"工具注册机制"就位——本节正是那个时点，这个类就是那笔欠账的落点。
 *
 * <p>它只做三件事：把渠道名解析成通知目标、过沙箱校验、交给适配器发送。**顺序不可反**：校验必须发生在发送之前，反了就等于白名单 形同虚设（这一条由 {@code
 * NotifyToolsTest} 用 {@code InOrder} 钉死）。
 */
public class NotifyTools {

  private static final String URL_KEY = "url";

  private final Sandbox sandbox;
  private final NotifyChannelAdapter adapter;
  private final NotifyChannelSource source;

  public NotifyTools(Sandbox sandbox, NotifyChannelAdapter adapter, NotifyChannelSource source) {
    this.sandbox = sandbox;
    this.adapter = adapter;
    this.source = source;
  }

  @Tool(
      name = "notify",
      description = "把一条内容推送到已配置的通知渠道；渠道名不传则用第一个",
      resultConverter = PlainTextResultConverter.class)
  public String notify(
      @ToolParam(description = "要推送的内容") String content,
      @ToolParam(description = "渠道名，缺省用第一个渠道", required = false) String channel) {
    NotifyTarget target = resolve(channel);
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, target.config().get(URL_KEY)));
    adapter.send(target, content);
    return "已推送";
  }

  /** 渠道名 → 通知目标。三种出错都明确报错，绝不静默——调用方不能以为"已经发出去了"。 */
  private NotifyTarget resolve(String channel) {
    List<Map<String, String>> rows = source.all();
    if (rows.isEmpty()) {
      throw new IllegalStateException("未配置任何通知渠道，无法推送");
    }
    Map<String, String> row;
    if (channel == null || channel.isBlank()) {
      row = rows.get(0);
    } else {
      row =
          rows.stream()
              .filter(candidate -> channel.equals(candidate.get("name")))
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("通知渠道不存在: " + channel));
    }
    Map<String, String> config = new LinkedHashMap<>();
    if (row.get(URL_KEY) != null) {
      config.put(URL_KEY, row.get(URL_KEY));
    }
    return new NotifyTarget(row.get("type"), config);
  }
}
