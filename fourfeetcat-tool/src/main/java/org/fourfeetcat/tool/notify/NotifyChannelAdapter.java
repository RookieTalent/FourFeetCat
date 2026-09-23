package org.fourfeetcat.tool.notify;

/**
 * 出站通知契约（第19节课件）：入站有 Channel 负责"消息怎么进来"，这个接口补对称的另一半——"结果怎么主动送出去"。
 *
 * <p><b>接口先行</b>：签名只表达"把一条内容送到某个通知目标"这个意图，不出现任何一档实现特有的词（具体渠道名、认证
 * 字段、报文格式）。检验办法是用最重的那档实现去反向套这个签名——换成某家 IM 的官方 SDK 实现或 SMTP 邮件实现，本签名一个 字都不用改。
 *
 * <p><b>安全校验归属调用链上游</b>：往外发是一次对外 IO，理应先过域名白名单。校验由调用方（工具层）在调用本方法**之前** 执行，实现类不内嵌校验——与 {@code
 * http_post} 等内置 Tool 的模式一致（第24节 Sandbox 接线）。
 */
@FunctionalInterface
public interface NotifyChannelAdapter {

  /**
   * 把一条内容送到目标渠道。
   *
   * <p>失败必须抛出，不得静默吞掉：调用方（Agent）不能以为"已经发出去了"。
   */
  void send(NotifyTarget target, String content);
}
