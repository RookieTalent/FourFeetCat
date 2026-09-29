package org.fourfeetcat.web.api;

import java.net.http.HttpTimeoutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * 异常映射测试的探针端点（第26节，**只存在于测试源码**）：每个方法只做一件事——把某一类异常抛出来。
 *
 * <p>有了它，异常映射的测试就不必穿过任何业务 Controller：测的是"异常 → 状态码 → 信封"这条映射本身，不掺业务逻辑的干扰。
 *
 * <p>为什么是独立的顶层类而不是测试类里的嵌套类：切片测试只装配它点名的顶层 Controller，嵌套类不会被收进上下文（实测如此）， 于是所有探针都变成
 * 404，测试看起来"通过"了却什么都没测到——这种假绿比红更危险。
 */
@RestController
@RequestMapping(ExceptionProbeController.PREFIX)
class ExceptionProbeController {

  /** 探针路径前缀：带下划线，避免与真端点混淆。 */
  static final String PREFIX = "/api/v1/_probe";

  /** 内部错误的真实形态：连接串这种内幕必须留在日志里。 */
  static final String INTERNAL_SECRET = "jdbc:sqlite:/data/fourfeetcat.db connect failed";

  @GetMapping("/bad-request")
  ApiResponse<Void> badRequest() {
    throw new InvalidRequestException("消息超过上限 32768 字符（32KB）");
  }

  @GetMapping("/illegal-argument")
  ApiResponse<Void> illegalArgument() {
    throw new IllegalArgumentException("参数非法");
  }

  @GetMapping("/session-not-found")
  ApiResponse<Void> sessionNotFound() {
    throw new SessionNotFoundException("web:wang:ops-agent");
  }

  @GetMapping("/resource-not-found")
  ApiResponse<Void> resourceNotFound() {
    throw new ResourceNotFoundException("Agent 未加载: ghost");
  }

  @GetMapping("/provider-down")
  ApiResponse<Void> providerDown() {
    throw new IllegalStateException("Provider 未就绪");
  }

  @GetMapping("/timeout")
  ApiResponse<Void> timeout() {
    throw new ResourceAccessException(
        "I/O error on POST request for \"http://internal-llm:8080/v1/chat/completions\"",
        new HttpTimeoutException("request timed out"));
  }

  @GetMapping("/transport-failure")
  ApiResponse<Void> transportFailure() {
    throw new ResourceAccessException("Connection refused");
  }

  @GetMapping("/internal-error")
  ApiResponse<Void> internalError() {
    // 兜底路径的真实形态：没被任何一条映射认领的异常（这里用"代码没写到这里"这一类）
    throw new UnsupportedOperationException(INTERNAL_SECRET);
  }
}
