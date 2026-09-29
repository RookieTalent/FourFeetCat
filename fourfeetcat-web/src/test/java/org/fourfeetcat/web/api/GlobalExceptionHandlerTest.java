package org.fourfeetcat.web.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 统一异常出口的切片测试（课件第26节 harness）。
 *
 * <p>用一组**探针端点**（{@link ExceptionProbeController}）逐类把异常抛一次：这样测的是"异常 → 状态码 → 信封"这条映射本身，不掺任何业务逻辑。
 * 切片只装配探针 Controller 与异常处理器，不起业务 Bean、不碰库、不碰模型。
 *
 * <p>最值钱的一条是"内部异常细节绝不能出现在 500 响应里"——门面的分寸。它守的不是格式，而是**部署结构不外泄**：一个 500 响应里带着
 * "jdbc:sqlite:/data/fourfeetcat.db connect failed"，等于把数据库位置白送给调用方。
 *
 * <p>与课件的一处口径说明：课件骨架里那条"门面分寸"回归用状态异常来造 500，而同一页的映射表又把状态异常映射到 503（本实现照后者）。 两条守点都保留——泄漏用**通用内部异常**造
 * 500 来验（那才是真正的兜底路径），状态异常则单列一条按 503 验。骨架上那个没人抛的 "超时专用异常"未被采用：504 由真实的传输层超时映射而来（见 {@code
 * ProviderReadTimeoutTest}），多一个没人抛的异常类只是死代码。
 */
@WebMvcTest(ExceptionProbeController.class)
class GlobalExceptionHandlerTest {

  private static final String PROBE_PREFIX = ExceptionProbeController.PREFIX + "/";

  @Autowired private MockMvc mockMvc;

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "bad-request,400",
    "illegal-argument,400",
    "session-not-found,404",
    "resource-not-found,404",
    "provider-down,503",
    "transport-failure,503",
    "timeout,504",
    "internal-error,500"
  })
  @DisplayName("每类异常_都映射到约定状态码且响应体是统一信封")
  void eachException_mapsToAgreedStatusAndUnifiedEnvelope(String probe, int expectedStatus)
      throws Exception {
    mockMvc
        .perform(get(PROBE_PREFIX + probe))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.code").value(expectedStatus))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("内部异常细节_绝不能出现在500响应里（连接串、库名一个字不漏）")
  void internalError_neverLeaksInternalDetails() throws Exception {
    mockMvc
        .perform(get(PROBE_PREFIX + "internal-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getReason()))
        .andExpect(content().string(not(containsString("jdbc:sqlite"))))
        .andExpect(content().string(not(containsString("fourfeetcat.db"))))
        .andExpect(content().string(not(containsString("connect failed"))));
  }

  @Test
  @DisplayName("模型调用超时_返回504且不回显内部端点（超时与连接失败分得开）")
  void timeout_mapsToGatewayTimeoutWithoutLeakingEndpoint() throws Exception {
    mockMvc
        .perform(get(PROBE_PREFIX + "timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value(504))
        .andExpect(content().string(containsString("超时")))
        .andExpect(content().string(not(containsString("internal-llm"))));
  }
}
