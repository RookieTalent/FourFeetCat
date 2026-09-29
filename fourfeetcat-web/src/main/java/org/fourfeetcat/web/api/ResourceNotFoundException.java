package org.fourfeetcat.web.api;

/**
 * 会话以外的资源不存在（第26节课件）→ 404。
 *
 * <p>当前的唯一来源是"调用了一个没加载的 Agent"——名字写错是客户端的问题，必须与"服务不可用"（503）区分开：前者重试无意义，后者值得重试， 混成一种现象会把对接方的重试策略带错。
 */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
