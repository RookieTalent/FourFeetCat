package org.fourfeetcat.web.ping;

import io.swagger.v3.oas.annotations.Operation;
import org.fourfeetcat.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工程地基自证端点：验证统一响应信封、OpenAPI 文档与虚拟线程请求路径。 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

  @Operation(summary = "健康探针", description = "验证统一响应信封与 OpenAPI 文档")
  @GetMapping("/ping")
  public ApiResponse<String> ping() {
    return ApiResponse.ok("pong");
  }
}
