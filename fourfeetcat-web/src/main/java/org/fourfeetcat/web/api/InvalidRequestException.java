package org.fourfeetcat.web.api;

/**
 * 请求参数不合法（第26节课件）→ 400。
 *
 * <p>含义严格限定在"**客户端侧**的问题"：消息为空/纯空白、超过长度上限、向已归档会话发消息。服务端自身的故障一律不走这里——
 * 把它与"服务不可用"混为一谈，客户端会重试一个永远不会成功的请求。
 */
public class InvalidRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidRequestException(String message) {
    super(message);
  }
}
