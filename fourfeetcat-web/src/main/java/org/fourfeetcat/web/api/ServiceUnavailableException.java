package org.fourfeetcat.web.api;

/** 业务主动声明暂不可用时抛出，由全局异常处理器收敛为 503 统一响应。 */
public class ServiceUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ServiceUnavailableException(String message) {
    super(message);
  }
}
