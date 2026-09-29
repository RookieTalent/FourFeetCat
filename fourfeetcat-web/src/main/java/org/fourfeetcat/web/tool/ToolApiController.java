package org.fourfeetcat.web.tool;

import java.util.List;
import org.fourfeetcat.tool.registry.ToolRegistry;
import org.fourfeetcat.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具信息端点（第26节课件）：列出工具表里全部已注册工具（内置的 + 经 {@code @Tool} 注解挂进来的 + 从外部工具服务接进来的）。
 *
 * <p>呈现的是**模型看到的那份说明**（名称、描述、参数说明），因为运维要看的就是"这个 Agent 手里有哪些牌"。 参数说明直接给工具的 schema
 * 文本，不做二次加工——改了展示形态就等于多一份"和模型看到的不一样"的说明，两处迟早对不上。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {

  private final ToolRegistry toolRegistry;

  public ToolApiController(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  /** 列出全部已注册工具；工具表为空时返回空列表。 */
  @GetMapping
  public ApiResponse<List<ToolView>> list() {
    List<ToolView> views =
        toolRegistry.all().stream()
            .map(tool -> new ToolView(tool.getName(), tool.getDescription(), tool.getInputSchema()))
            .toList();
    return ApiResponse.ok(views);
  }

  /** 工具的对外视图：名称 + 描述 + 参数 JSON Schema 文本。 */
  public record ToolView(String name, String description, String inputSchema) {}
}
