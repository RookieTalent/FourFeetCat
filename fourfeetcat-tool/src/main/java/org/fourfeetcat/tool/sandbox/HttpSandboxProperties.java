package org.fourfeetcat.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 域名白名单配置（第24节课件）：{@code http.allowed_domains}——允许访问的主机名。
 *
 * <p>口径：<b>只比主机名</b>——端口与路径不属"域名"白名单的管辖范围；<b>大小写敏感</b>（配什么写什么，与命令白名单同一口径）。通配项写 {@code
 * *.example.com}，匹配带点号边界：命中 {@code api.example.com}，不命中裸域 {@code example.com}，也不命中形似域名 {@code
 * evil-example.com}。
 *
 * <p>留空 = 一个域名都不许访问（数据不出域）。出站通知（{@code NotifyTools}）的 webhook 走的就是这份白名单，不另造一套。
 */
@ConfigurationProperties(prefix = "http")
public record HttpSandboxProperties(List<String> allowedDomains) {

  /** 键缺省时兜成空列表——"没配"与"配成空"同义：全拒。 */
  public HttpSandboxProperties {
    allowedDomains = allowedDomains == null ? List.of() : List.copyOf(allowedDomains);
  }
}
