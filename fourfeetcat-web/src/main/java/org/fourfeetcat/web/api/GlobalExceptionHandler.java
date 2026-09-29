package org.fourfeetcat.web.api;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：任何异常都收敛为统一 {@link ApiResponse} JSON 信封。
 *
 * <p><b>对外只有这一个出口</b>（第26节课件）：六个 Controller 谁都不自己拼错误响应。状态码口径一开始就定死——400 参数错误、404 资源不存在、 500
 * 内部错误、503 服务不可用、504 处理超时——业务系统对接时才不用逐个端点猜错误格式。
 *
 * <p><b>门面的分寸</b>：500 兜底**不把异常消息吐给外部**。内部细节（连接串、表名、堆栈）只进日志，对外只给统一话术——一个内部错误响应里 带着
 * "jdbc:sqlite:/data/fourfeetcat.db connect failed"，等于把部署结构白送给调用方。其余几类（4xx/503/504）回的是可读的、面向调用方的说明，
 * 本身不含内幕；这类消息的构造责任在抛出方，写的时候按"给外面看"的标准写。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
    return respond(ErrorCode.BAD_REQUEST, ex.getMessage());
  }

  /** 400 — 客户端侧参数问题（第26节）：消息为空/超上限、向已归档会话发消息。 */
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ApiResponse<Void>> invalidRequest(InvalidRequestException ex) {
    return respond(ErrorCode.BAD_REQUEST, ex.getMessage());
  }

  /** 400 — 参数非法：调用方给的参数不合法（既有语义，本节补齐这条映射）。 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException ex) {
    return respond(ErrorCode.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException ex) {
    return respond(ErrorCode.NOT_FOUND, ex.getMessage());
  }

  /** 404 — 领域资源不存在（第26节）：会话标识不存在、Agent 名未加载。 */
  @ExceptionHandler({SessionNotFoundException.class, ResourceNotFoundException.class})
  public ResponseEntity<ApiResponse<Void>> resourceNotFound(RuntimeException ex) {
    return respond(ErrorCode.NOT_FOUND, ex.getMessage());
  }

  /**
   * 503 — Provider 侧故障（第26节把状态异常并入这一档）。
   *
   * <p>{@code IllegalStateException} 归这里而不是 500：它在本项目里出现的位置基本是"处理链自己发现状态不对"（例如会话归属的 Agent
   * 在注册表里消失了），对调用方而言属于"服务此刻不可用、稍后可能自愈"，与"代码写错了"（500）是两回事。
   *
   * <p>注意"调用了没加载的 Agent"**不走这里**：那是客户端把名字写错了，按 404 拒绝（见各端点的前置校验）——前者值得重试，后者重试没有意义。
   */
  @ExceptionHandler({IllegalStateException.class, ServiceUnavailableException.class})
  public ResponseEntity<ApiResponse<Void>> providerDown(RuntimeException ex) {
    if (log.isWarnEnabled()) {
      log.warn("服务不可用", ex);
    }
    return respond(ErrorCode.SERVICE_UNAVAILABLE, ex.getMessage());
  }

  /**
   * 504 / 503 — 模型调用的传输层失败（第26节）。
   *
   * <p><b>超时与连接失败必须分开</b>：前者换个时机重试可能就通了，后者多半是端点配错了。混成一种现象会把对接方的重试策略带错。
   *
   * <p>超时的判据是**原因链里认得出超时**（实测：{@code ResourceAccessException <-
   * java.net.http.HttpTimeoutException}， 见 {@code ProviderReadTimeoutTest}）。判据刻意不锁死单个异常类：换 HTTP
   * 客户端实现后，只要"传输层失败 + 超时原因"这条性质还在， 这里的映射就不必改。
   */
  @ExceptionHandler(ResourceAccessException.class)
  public ResponseEntity<ApiResponse<Void>> transportFailure(ResourceAccessException ex) {
    if (causeChainHasTimeout(ex)) {
      if (log.isWarnEnabled()) {
        log.warn("模型调用超时", ex);
      }
      return respond(ErrorCode.REQUEST_TIMEOUT, "模型调用超时，请稍后重试");
    }
    if (log.isWarnEnabled()) {
      log.warn("模型服务连接失败", ex);
    }
    return respond(ErrorCode.SERVICE_UNAVAILABLE, "模型服务不可用");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> internalError(Exception ex) {
    log.error("未处理异常", ex);
    return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getReason());
  }

  private ResponseEntity<ApiResponse<Void>> respond(ErrorCode errorCode, String message) {
    return ResponseEntity.status(errorCode.getCode()).body(ApiResponse.error(errorCode, message));
  }

  /**
   * 原因链里认超时。
   *
   * <p>两个候选都是 JDK 类型（不需要引任何 HTTP 客户端实现）：当前客户端是 JDK HttpClient（{@link HttpTimeoutException}）， 换成基于
   * HttpURLConnection 的实现则是 {@link SocketTimeoutException}。两个都认，是为了让映射在换实现时仍然成立。
   */
  private static boolean causeChainHasTimeout(Throwable thrown) {
    for (Throwable cursor = thrown; cursor != null; cursor = cursor.getCause()) {
      if (cursor instanceof HttpTimeoutException || cursor instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }
}
