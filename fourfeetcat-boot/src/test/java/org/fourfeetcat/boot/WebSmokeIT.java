package org.fourfeetcat.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Web 面真上下文冒烟（第26节课件 harness 的第三类）。
 *
 * <p>切片测试测不到的东西全在这里：**Bean 装配与数据访问层扫描范围**。历史上出过一次"找到 0 个仓储接口"——那类问题在切片里看不见 （切片本来就把仓储 mock
 * 掉），单测全绿而真跑必炸。所以这条测试起**真实上下文**、走**真 HTTP**，把"端点真的注册上了、会话层真的接到了库"验一遍。
 *
 * <p><b>不依赖模型</b>：只打四个只读端点与一个列表端点，一次模型调用都不发——冒烟的价值在于"装配没错"，不在于"模型答得好"。
 *
 * <p><b>为什么落在 boot 模块</b>：真上下文（全部能力 Bean + 数据访问层扫描声明）只有 boot 有。放 web 模块就得在测试里复制一份生产装配，
 * 而装配一旦漂移，复制品不会红——恰恰丢了这条测试最值钱的部分。
 *
 * <p>类名以 {@code IT} 结尾，由 Maven 的集成测试插件在 {@code verify} 阶段执行：**会被真跑**，不是被跳过。打
 * {@code @Tag("integration")} 与既有 provider 冒烟同口径，表示"它比单测重、依赖真上下文"。
 */
@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 零凭证：整条 Provider 声明换成一个本地替身、key 显式置空（覆盖环境变量），让"凭证未就位也能起来"成为**确定性**断言，
    // 而不是依赖跑测试的机器上恰好没配 key。注意三个子键要一起覆盖——只覆盖 api-key 会让列表绑定的这一项缺名字。
    properties = {
      "FOURFEETCAT_ROOT=${user.dir}/target/web-smoke",
      "fourfeetcat.providers[0].name=stub-provider",
      "fourfeetcat.providers[0].base-url=http://127.0.0.1:9",
      "fourfeetcat.providers[0].api-key="
    })
class WebSmokeIT {

  /** 本用例自己的工作区（与既有 boot 上下文测试分开：各用各的库，互不牵连）。 */
  private static final Path WORKSPACE =
      Path.of(System.getProperty("user.dir"), "target", "web-smoke");

  static {
    // sqlite 只建文件、不建目录——生产路径上这一步由 main() 做，测试里得自己来，否则 clean 之后数据源打不开
    try {
      Files.createDirectories(WORKSPACE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("信息类端点_真实链路可达（装配与扫描范围无误）")
  void readonlyEndpoints_areReachable() {
    assertOk("/api/v1/health", "\"status\":\"ok\"");
    assertOk("/api/v1/info", "\"credentialConfigured\":false");
    assertOk("/api/v1/profiles", "\"code\":200");
    assertOk("/api/v1/tools", "\"code\":200");
  }

  @Test
  @DisplayName("会话列表_走通了数据访问层（第18节那个'找到 0 个仓储'的坑若复发，这里第一时间红）")
  void sessionList_reachesTheStorageLayer() {
    assertOk("/api/v1/sessions", "\"code\":200");
  }

  @Test
  @DisplayName("管理台入口与前端子路由_直接访问都能拿到入口页（缺资源不回落、接口不回落）")
  void adminEntryAndSpaFallback_areServedWithoutLeakingIntoApi() {
    assertHtml("/admin");
    assertHtml("/admin/");
    assertHtml("/admin/sessions");

    // 带扩展名的路径是静态资源：未命中必须如实 404，不能伪装成页面正常
    assertThat(restTemplate.getForEntity("/admin/missing.js", String.class).getStatusCode())
        .as("缺失的静态资源应如实 404")
        .isEqualTo(HttpStatus.NOT_FOUND);
    // 接口前缀下的路径打错必须如实 404（JSON），不能被回落成一份 HTML
    assertThat(restTemplate.getForEntity("/api/v1/nope", String.class).getStatusCode())
        .as("接口路径打错应如实 404")
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("零凭证可启动_运行信息如实报告凭证未就位且绝不回显凭证")
  void zeroCredentialBoot_reportsUnconfiguredWithoutEchoingSecret() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/info", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).as("响应体: %s", body).contains("\"providers\"");
    assertThat(body).as("替身 Provider 声明必须生效（证明下面两条断言验的确实是零凭证那份声明）").contains("stub-provider");
    assertThat(body)
        .as("凭证未就位必须如实报告（本条同时是'零凭证可 boot'的证明：上下文已经起来了）")
        .contains("\"credentialConfigured\":false");
    assertThat(body).as("凭证内容绝不能出现在响应里").doesNotContain("apiKey").doesNotContain("api-key");
  }

  private void assertOk(String path, String expectedFragment) {
    ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

    assertThat(response.getStatusCode()).as("%s 的状态码", path).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).as("%s 的响应体", path).contains(expectedFragment);
  }

  /** 管理台相关路径必须返回入口页（HTML），而不是一个 404 或一份 JSON。 */
  private void assertHtml(String path) {
    ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

    assertThat(response.getStatusCode()).as("%s 的状态码", path).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).as("%s 的内容类型", path).isNotNull();
    assertThat(response.getHeaders().getContentType().toString())
        .as("%s 应返回 HTML（管理台入口页）", path)
        .contains("text/html");
  }
}
