package org.fourfeetcat.tool.registry;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * 把工具方法的返回值**原样**交给模型，不做任何包装。
 *
 * <p>存在的理由（实测得出，不是推测）：Spring AI 默认的结果转换器会把返回值 JSON 化——一个返回 {@code abc} 的 {@code read_file} 到模型手里变成
 * {@code "abc"}，换行会被转义成字面的 {@code \n}。后果有两个：`list_dir` 的多行输出挤成一行、 `read_file`
 * 读长文件时整篇被转义。文件内容本身没写错，错的是"递给模型的那份文本"。
 *
 * <p>我们的内置工具返回的就是"给人读的一段文本"（文件内容、命令输出、响应体），本来就不需要 JSON 包装。因此每个 {@code @Tool}
 * 注解上挂本转换器。哪天某个工具真要返回结构化对象，那个工具自己换一档转换器即可，不影响其余。
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

  @Override
  public String convert(Object result, Type returnType) {
    return result == null ? "" : result.toString();
  }
}
