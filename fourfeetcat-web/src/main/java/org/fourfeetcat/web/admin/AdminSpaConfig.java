package org.fourfeetcat.web.admin;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 管理台的静态资源与前端路由回落（第26节课件）。
 *
 * <p>管理台是一个单页应用：它的前端路由（{@code /admin/sessions} 这类）在服务端没有对应的文件，直接访问或刷新时会走到"文件不存在"。
 * 所以未命中的**非静态资源**路径要回落到入口页，由前端自己按路径渲染——这就是"刷新子路由不 404"的实现。
 *
 * <p><b>为什么不用一个 {@code @GetMapping("/admin/**")} 的控制器转发</b>：那个模式会把 {@code /admin/index.html}
 * 自己也匹配进去， 于是"转发到 index.html"变成"再进一次同一个控制器"，形成自转发死循环；而资源解析器能按"是不是静态资源"分流，正是要在这里做的判断。
 *
 * <p>回落只覆盖 {@code /admin/**}：API 前缀下的行为一个字不动——**接口路径打错必须如实 404**，不能回落成入口页， 否则调用方会拿到一份 HTML 当成 JSON
 * 去解析。
 */
@Configuration
public class AdminSpaConfig implements WebMvcConfigurer {

  /** 管理台挂载路径与产物位置（构建产物落 classpath:/static/admin/，产物不入库）。 */
  private static final String ADMIN_PATH_PATTERN = "/admin/**";

  private static final String ADMIN_LOCATION = "classpath:/static/admin/";

  /** 挂载点本身（裸路径）与入口页的可访问路径：前者要转发到后者。 */
  private static final String ADMIN_MOUNT_PATH = "/admin";

  private static final String ADMIN_LOCATION_PATH = "/admin/index.html";

  /** 入口页文件名：前端路由的落点，也是唯一允许被回落到的资源。 */
  private static final String SPA_ENTRY = "index.html";

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler(ADMIN_PATH_PATTERN)
        .addResourceLocations(ADMIN_LOCATION)
        .resourceChain(true)
        .addResolver(new SpaFallbackResolver());
  }

  /**
   * 挂载点本身：{@code /admin} 这一条**不归资源映射管**——`/admin/**` 不匹配裸路径（实测：只让它管的话，访问 `/admin` 落到默认静态处理上，
   * 报"找不到资源"），于是"管理台入口页"这条最该先通的路径反而 404。这里显式把挂载点转到入口页。
   *
   * <p>转发的目标 `/admin/index.html` 是真实存在的文件，且不与本映射同路径，不会形成自转发。
   */
  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController(ADMIN_MOUNT_PATH).setViewName("forward:" + ADMIN_LOCATION_PATH);
    // 带尾斜杠的写法（用户手输极常见）同样不归资源映射管，一并认下
    registry
        .addViewController(ADMIN_MOUNT_PATH + "/")
        .setViewName("forward:" + ADMIN_LOCATION_PATH);
  }

  /** 静态资源优先；未命中的非静态路径回落到入口页。 */
  private static final class SpaFallbackResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
      Resource requested = location.createRelative(resourcePath);
      if (requested.exists() && requested.isReadable()) {
        return requested;
      }
      // 挂载点本身（/admin）与带斜杠的目录路径：一律给入口页。注意这里必须显式认下 "" 与 "."——
      // 裸路径 /admin 匹配到本处理器时，传给解析器的 resourcePath 是 "."（不是空串），
      // 只按"含点即静态资源"判断会把挂载点自己判成找不到的资源，于是 /admin 反而 404（实测踩过）。
      if (resourcePath.isEmpty() || ".".equals(resourcePath)) {
        return location.createRelative(SPA_ENTRY);
      }
      // 带扩展名的路径是静态资源（脚本、样式、图片、图标）：未命中就如实 404，回落成入口页只会把"资源丢了"伪装成页面正常。
      // 不带扩展名的多半是前端路由（/admin/sessions 这类），交给入口页。
      String lastSegment = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
      if (lastSegment.contains(".")) {
        return null;
      }
      return location.createRelative(SPA_ENTRY);
    }
  }
}
