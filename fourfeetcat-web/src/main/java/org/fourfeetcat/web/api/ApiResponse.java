package org.fourfeetcat.web.api;

import java.time.Instant;

/** 统一响应体：所有 REST 端点的唯一出参信封。 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getReason(), data, Instant.now());
  }

  public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
    return new ApiResponse<>(errorCode.getCode(), message, null, Instant.now());
  }
}
