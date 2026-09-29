package org.fourfeetcat.web.api;

/**
 * 发消息类端点的请求体（第26节课件）：只有一句话。
 *
 * <p>两个端点（会话内发消息、Agent 无状态调用）对内容的校验口径**完全相同**，所以口径只此一处——两处各写一份，迟早会在一边放宽、另一边忘记，
 * 于是同一个底座冒出两套消息上限，而且只在其中一个端点上现形。
 */
public record MessageRequest(String content) {

  /** 单条消息上限：32KB（课件与技术方案 §7.4 定死的防呆值，属"防呆不是治理"那一类）。 */
  private static final int MAX_CONTENT_CHARS = 32 * 1024;

  /**
   * 取出合法内容；不合法即抛 {@link InvalidRequestException}（→ 400）。
   *
   * <p>空与纯空白都拒：喂给模型只会白烧一轮 token。**恰好等于上限放行**——边界值不因防呆被误伤。
   */
  public String requireContent() {
    if (content == null || content.isBlank()) {
      throw new InvalidRequestException("消息内容不能为空");
    }
    if (content.length() > MAX_CONTENT_CHARS) {
      throw new InvalidRequestException("消息超过上限 " + MAX_CONTENT_CHARS + " 字符（32KB）");
    }
    return content;
  }
}
