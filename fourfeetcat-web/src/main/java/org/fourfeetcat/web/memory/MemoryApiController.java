package org.fourfeetcat.web.memory;

import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 长期记忆查询端点（第26节课件）：返回记忆全文。
 *
 * <p>走既有的记忆门面，因此**对底下是哪一档后端无感**（文件 / 本地库 / 自托管的外部服务三种实现对本端点完全等价）——换后端只改配置，
 * 这个类一行不动。这是第21节那道"接口墙"在外面的样子。
 *
 * <p>只读：写记忆的入口是 Agent 在对话里调 {@code save_memory}，不从这里开（管理台第一版也只能看）。
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

  private final MemoryService memoryService;

  public MemoryApiController(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  /** 记忆全文；为空时返回带空区块的文本，不是错误。 */
  @GetMapping
  public ApiResponse<String> fullContent() {
    // session 传 null（本节唯一一处）：门面当前口径是"取全文，session 不参与作用域圈定"，此处也没有会话上下文。
    // 耦合点登记：将来门面按 Agent 圈定记忆作用域时，这个调用点需要补上上下文——这正是门面签名保留 session 的兑现处。
    return ApiResponse.ok(memoryService.buildContext(null));
  }
}
