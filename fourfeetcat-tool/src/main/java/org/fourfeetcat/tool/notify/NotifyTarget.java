package org.fourfeetcat.tool.notify;

import java.util.Map;

/**
 * 通知目标（第19节课件）：一次推送的目的地描述——只带"渠道类型 + 一份配置"。
 *
 * <p>配置里是 webhook 地址还是别的认证信息，由实现类自己解释：通用 webhook 档取 {@code url}，某家渠道的专用档取它自己需要
 * 的键。接口与值对象都不出现某一档实现特有的词，这是"换渠道不改调用方"的前提。
 *
 * <p>它不是持久化数据，而是一次调用的入参——由"按名解析渠道"的动作从渠道注册表记录投影而来（第24节接线）。
 */
public record NotifyTarget(String channelType, Map<String, String> config) {

  public NotifyTarget {
    config = config == null ? Map.of() : Map.copyOf(config);
  }
}
