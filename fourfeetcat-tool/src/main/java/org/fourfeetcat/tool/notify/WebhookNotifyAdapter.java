package org.fourfeetcat.tool.notify;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 webhook 实现（第19节课件）：核心阶段唯一一档，覆盖"拿到一个地址、POST 一条纯文本"这类最常见的推送——企业微信、 飞书、钉钉的群机器人都提供 webhook
 * 地址，这一档就能兜住大部分场景。
 *
 * <p><b>它只负责"发送"</b>：从目标配置取地址、把内容包成 JSON 发出去。两个刻意的"不做"：
 *
 * <ul>
 *   <li><b>不内嵌白名单校验</b>——校验由调用方（工具层）在发送前执行，与 {@code http_post} 等内置 Tool 的模式一致 （第24节 Sandbox
 *       接线，别在此处另造一套；顺序断言也钉在调用方那一侧）。
 *   <li><b>不做失败兜底</b>——接收端 4xx/5xx、超时、不可达一律向上抛：Agent 不能以为"已经发出去了"。
 * </ul>
 *
 * <p>某家渠道的报文格式与通用档不一致时，扩展阶段按目标里的渠道类型新增一档实现即可，本类与所有调用方都不用改。 换渠道只是改配置。
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private static final String URL_KEY = "url";
  private static final String CONTENT_KEY = "content";

  private final RestClient restClient;

  public WebhookNotifyAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public void send(NotifyTarget target, String content) {
    String url = target.config().get(URL_KEY);
    if (url == null || url.isBlank()) {
      // 地址缺失是配置错误：既不发一个空地址的请求，也不静默返回让调用方以为送达了
      throw new IllegalArgumentException("通知目标缺少 " + URL_KEY + " 配置，无法发送");
    }
    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(CONTENT_KEY, content))
        .retrieve()
        .toBodilessEntity();
  }
}
