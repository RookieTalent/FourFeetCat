package org.fourfeetcat.web.api;

/** 统一错误码：业务 code 与 HTTP status 同值，REST 约定统一前缀 /api/v1。 */
public enum ErrorCode {
  OK(200, "OK"),
  BAD_REQUEST(400, "请求参数错误"),
  NOT_FOUND(404, "资源不存在"),
  INTERNAL_ERROR(500, "服务器内部错误"),
  SERVICE_UNAVAILABLE(503, "服务暂不可用");

  private final int code;
  private final String reason;

  ErrorCode(int code, String reason) {
    this.code = code;
    this.reason = reason;
  }

  public int getCode() {
    return code;
  }

  public String getReason() {
    return reason;
  }
}
