package org.fourfeetcat.web.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：任何异常都收敛为统一 {@link ApiResponse} JSON 信封。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
    return respond(ErrorCode.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException ex) {
    return respond(ErrorCode.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ApiResponse<Void>> serviceUnavailable(ServiceUnavailableException ex) {
    return respond(ErrorCode.SERVICE_UNAVAILABLE, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> internalError(Exception ex) {
    log.error("未处理异常", ex);
    return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.reason());
  }

  private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, String message) {
    return ResponseEntity.status(errorCode.code()).body(ApiResponse.error(errorCode, message));
  }
}
