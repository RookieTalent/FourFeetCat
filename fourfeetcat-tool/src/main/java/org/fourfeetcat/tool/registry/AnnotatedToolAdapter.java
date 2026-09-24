package org.fourfeetcat.tool.registry;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.core.tool.CatTool;
import org.fourfeetcat.core.tool.ToolResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * {@code @Tool} 注解方法 → {@link CatTool} 的适配器（第20节课件）。
 *
 * <p>底座自带的工具与业务方"方式三"写的工具走的是同一条管道——都用 Spring AI 的 {@code @Tool} 注解标注、启动时扫描、由 Spring AI 生成参数
 * schema。这是宪法原则二允许 Spring AI 做的第二件事（第一件是协议转换）。
 *
 * <p><b>不启用自动执行。</b>{@code ChatClient.prompt().tools(...).call()} 那条路在本工程不存在：{@link ToolCallback}
 * 从不挂到 {@code ChatClient} 上，{@code call()} 只可能从 {@link CallbackTool#execute} 进来，而那只可能从 {@code
 * ToolRegistry.execute} 进来，再往上只有 {@code ToolExecutor} 一个入口——执行权唯一。
 */
public final class AnnotatedToolAdapter {

  private AnnotatedToolAdapter() {}

  /**
   * 扫一个 Bean 上所有带 {@code @Tool} 注解的方法，逐个包成 {@link CatTool}。
   *
   * @param beanWithToolMethods 带注解方法的 Bean（内置工具实例或业务方的深度集成 Bean）
   */
  public static List<CatTool> adapt(Object beanWithToolMethods) {
    ToolCallback[] callbacks =
        MethodToolCallbackProvider.builder()
            .toolObjects(beanWithToolMethods)
            .build()
            .getToolCallbacks();
    List<CatTool> tools = new ArrayList<>(callbacks.length);
    for (ToolCallback callback : callbacks) {
      tools.add(new CallbackTool(callback));
    }
    return tools;
  }

  /**
   * 把 {@link ToolCallback} 包成 {@link CatTool}。
   *
   * <p>名字、用途描述、参数说明都取自它的工具定义——schema 生成归 Spring AI，我们不重造（重造必然与它的方言对不上）。
   */
  private record CallbackTool(ToolCallback callback) implements CatTool {

    @Override
    public String getName() {
      return callback.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
      return callback.getToolDefinition().description();
    }

    @Override
    public String getInputSchema() {
      return callback.getToolDefinition().inputSchema();
    }

    /**
     * 刻意拆包后重抛：Spring AI 会把目标方法的异常包一层，若原样抛出，沙箱拦截的原因就被埋进"调用方法失败"这类包装文案里——
     * 审计表里的失败原因是给人查事故用的。丢掉的是包装异常那层栈，原始异常的栈完整保留，定位不受影响。
     */
    @Override
    @SuppressWarnings("PMD.PreserveStackTrace")
    public ToolResult execute(JsonNode input) {
      try {
        return ToolResult.success(callback.call(input.toString()));
      } catch (ToolExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw e;
      }
    }
  }
}
