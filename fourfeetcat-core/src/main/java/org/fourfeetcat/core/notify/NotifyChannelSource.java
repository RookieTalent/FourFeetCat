package org.fourfeetcat.core.notify;

import java.util.List;
import java.util.Map;

/**
 * 通知渠道取数端口（第20节，依赖倒置）：core 定接口，storage 读库出实现，tool 的通知工具消费它。
 *
 * <p>为什么返回值是通用 {@code Map} 而不是自造值对象：通知目标（渠道类型 + 一份配置）这个形状的值对象住在 {@code fourfeetcat-tool}，core
 * 看不见它；用通用对象承载就不必在 core 里再造一份同形状的类型表达同一件事。"怎么解析成通知 目标"归消费方——core 只负责把库里那几行取出来。
 *
 * <p>表里只有运营方手配的几行，一次取全足够；不为此再切一个"按名查"的方法（少一个方法就少一处口径分叉）。
 */
@FunctionalInterface
public interface NotifyChannelSource {

  /**
   * 全部渠道记录，每条的键为 {@code name} / {@code type} / {@code url} / {@code description}。
   *
   * <p>表中无记录 → 返回空列表（"一个都没配"该怎么报错是消费方的口径，本端口不做判断）。
   */
  List<Map<String, String>> all();
}
