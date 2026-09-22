package org.fourfeetcat.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * 打包内全局层声明的读取（包内私有）。
 *
 * <p>provider 声明是 jar 的一部分，所以轻命令不用启动容器就能看到它。**只暴露名字与端点，api-key 永不外流**：调用方拿到的 就是原样的 map，打印时自己只取这两个键。
 */
final class GlobalProviders {

  private static final String CONFIG_RESOURCE = "application.yaml";
  private static final String ROOT_KEY = "fourfeetcat";
  private static final String PROVIDERS_KEY = "providers";

  static final String NAME_KEY = "name";
  static final String BASE_URL_KEY = "base-url";

  private GlobalProviders() {}

  static List<Map<String, Object>> declared() {
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("打包内找不到全局层配置 " + CONFIG_RESOURCE);
      }
      Object root = new Yaml().load(stream);
      if (!(root instanceof Map<?, ?> rootMap)) {
        return List.of();
      }
      if (!(rootMap.get(ROOT_KEY) instanceof Map<?, ?> section)) {
        return List.of();
      }
      if (!(section.get(PROVIDERS_KEY) instanceof List<?> list)) {
        return List.of();
      }
      return providersOf(list);
    } catch (IOException e) {
      throw new IllegalStateException("读取全局层配置失败: " + e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> providersOf(List<?> list) {
    return list.stream()
        .filter(Map.class::isInstance)
        .map(item -> (Map<String, Object>) item)
        .toList();
  }

  static String value(Map<String, Object> provider, String key) {
    Object value = provider.get(key);
    return value == null ? "-" : String.valueOf(value);
  }
}
