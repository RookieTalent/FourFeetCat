package org.fourfeetcat.core.schedule;

import java.util.Map;

/**
 * 一条定时任务的定义（第25节课件：id / cron / zone / message 四项）。
 *
 * <p>来源是 Agent 配置里 {@code schedules} 列表的一条原始条目——那份字段自第16节起就在，本节把它接上消费方。 解析发生在**注册期**（{@link
 * #fromEntry}）：坏的一条例外、由调度器记为"该条非法"后跳过，不牵连同一 Agent 的 其他条目（这比让整份 Agent
 * 配置被跳过更细）。解析成对象之后，调度器只认它、不再回头看配置。
 */
public record ScheduleConfig(
    /** 执行权的分配维度（进程内唯一）：显式声明的标识优先，缺省按「Agent 名 + 条目序号」派生。 */
    String id,
    /** 触发规则：调度框架原生 **6 段**（秒 分 时 日 月 周），如每天 09:00 = {@code 0 0 9 * * *}。 */
    String cron,
    /** 时区：可空，空则按服务器系统时区运行；非空时须是合法时区标识（如 {@code Asia/Shanghai}）。 */
    String zone,
    /** 到点发给 Agent 的话：交给与 CLI / Web 完全相同的处理入口，说什么由 Agent 配置负责。 */
    String message) {

  private static final String KEY_ID = "id";
  private static final String KEY_CRON = "cron";
  private static final String KEY_ZONE = "zone";
  private static final String KEY_MESSAGE = "message";

  /**
   * 从配置里的一条原始条目解析（包内可见：只有调度器注册期用它）。
   *
   * <p>取值一律 {@code strip()}：手写 YAML 里的缩进与尾随空白不该让一条看着没问题的定时任务静默失效。 {@code cron} 与 {@code message}
   * 缺失即抛——**语法**合法性（表达式方言、时区标识是否存在）不在这里判，交给 调度框架在注册时抛，那时能连"哪一条"一起报。{@code id} 缺省按「Agent 名 +
   * 条目序号」派生：手写配置最省事， 需要稳定标识（后续阶段拿它做状态协调）时再显式写。
   */
  static ScheduleConfig fromEntry(String profileName, int index, Map<String, Object> entry) {
    String id = text(entry, KEY_ID);
    return new ScheduleConfig(
        id == null ? profileName + "#" + index : id,
        required(entry, KEY_CRON),
        text(entry, KEY_ZONE),
        required(entry, KEY_MESSAGE));
  }

  /** 必填项：缺失、非字符串、空白一律视为"没写"（配置不静默失败——缺了就说，别当空串跑过去）。 */
  private static String required(Map<String, Object> entry, String key) {
    String value = text(entry, key);
    if (value == null) {
      throw new IllegalArgumentException("定时任务缺少必填项: " + key);
    }
    return value;
  }

  /** 可选项：缺失或空白返回 null（"没配"与"配了空白"同义）。 */
  private static String text(Map<String, Object> entry, String key) {
    Object value = entry.get(key);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).strip();
    return text.isEmpty() ? null : text;
  }
}
