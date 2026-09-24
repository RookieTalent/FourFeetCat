package org.fourfeetcat.tool.builtin;

import org.fourfeetcat.tool.registry.PlainTextResultConverter;
import org.fourfeetcat.tool.sandbox.ActionType;
import org.fourfeetcat.tool.sandbox.Sandbox;
import org.fourfeetcat.tool.sandbox.SandboxAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 内置 HTTP 工具两件（第20节课件）：{@code http_get} / {@code http_post}。
 *
 * <p>每个方法体的第一行是沙箱校验（域名白名单）——域名不在白名单里直接抛异常，请求根本发不出去；过了校验才真正去请求。这条 顺序是本工具唯一的守点：把它写在请求之后，等于白名单只是装饰。
 */
public class HttpTools {

  private final Sandbox sandbox;
  private final RestClient restClient;

  public HttpTools(Sandbox sandbox, RestClient restClient) {
    this.sandbox = sandbox;
    this.restClient = restClient;
  }

  @Tool(
      name = "http_get",
      description = "发起一个 HTTP GET 请求，返回响应体",
      resultConverter = PlainTextResultConverter.class)
  public String httpGet(@ToolParam(description = "要请求的完整 URL") String url) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    return restClient.get().uri(url).retrieve().body(String.class);
  }

  @Tool(
      name = "http_post",
      description = "发起一个 HTTP POST 请求，返回响应体",
      resultConverter = PlainTextResultConverter.class)
  public String httpPost(
      @ToolParam(description = "要请求的完整 URL") String url,
      @ToolParam(description = "请求体文本（JSON 场景传 JSON 文本）") String body) {
    sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));
    return restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }
}
