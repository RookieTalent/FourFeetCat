package org.fourfeetcat.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.tool.registry.AnnotatedToolAdapter;

/**
 * 内置工具测试的两个小工具：造参数 JSON、按名取适配后的工具。
 *
 * <p>测试私用（包内可见），不进对外概念面。取工具走的是**生产同一条**适配器，所以这些测试顺带证明了"内置工具与业务方写的 工具同管道"。
 */
final class BuiltinToolTestSupport {

  private static final ObjectMapper JSON = new ObjectMapper();

  private BuiltinToolTestSupport() {}

  /** 按 name/value 交替传入拼参数 JSON——路径里的反斜杠交给 Jackson 转义，不手拼字符串。 */
  static JsonNode json(Object... keyValues) {
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      params.put((String) keyValues[i], keyValues[i + 1]);
    }
    return JSON.valueToTree(params);
  }

  /** 把一个带 {@code @Tool} 注解的 Bean 适配后按名取出某个工具。 */
  static CatTool tool(Object bean, String name) {
    return AnnotatedToolAdapter.adapt(bean).stream()
        .filter(tool -> tool.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("适配后没有这个工具: " + name));
  }
}
