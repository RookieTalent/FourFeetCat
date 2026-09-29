package org.fourfeetcat.web.api;

/**
 * 会话标识不存在（第26节课件）→ 404。
 *
 * <p>与 {@link ResourceNotFoundException} 分开而不合并：两者是不同资源域的 404（"会话"与"其他资源"），将来各自要扩展行为（比如会话不存在
 * 时附带"是否已归档"的提示）时，不必再把一个类拆成两个。当前二者映射到同一个状态码，但资源域不同，所以不复用同一个类。
 */
public class SessionNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SessionNotFoundException(String sessionId) {
    super("会话不存在: " + sessionId);
  }
}
