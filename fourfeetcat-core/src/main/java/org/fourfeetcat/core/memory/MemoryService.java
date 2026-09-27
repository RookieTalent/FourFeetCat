package org.fourfeetcat.core.memory;

import java.util.List;
import org.fourfeetcat.core.session.Session;

/**
 * 记忆统一门面（第22节，跨模块契约）：上层（组装器 {@code PromptBuilder}、记忆工具 {@code MemoryTools}）只认这三个方法，
 * 不感知底层长期记忆是文件、本地库还是外部服务——这是第21节评审那道"接口墙"的对上一侧。
 *
 * <p><b>为什么接口落 core 而不是记忆模块</b>：组装器在 core、必须注入它；接口若留在实现模块，core → memory → core
 * 就成环了（依赖倒置是本仓的技术约束）。同第16节把 Provider 服务接口上移 core 的既有手法。
 *
 * <p><b>取上下文只返回长期记忆</b>（核心区全量 + 归档区截断后），<b>不拼会话历史</b>——会话历史由组装器自己的历史段独立
 * 负责，两处都拼会让系统提示里出现两份历史。这一口径由第22节课件的验收表格钉死（正文那句"和会话历史拼一起"以表格为准）。
 *
 * <p>{@code session} 参数本节不参与作用域圈定（长期记忆本节为工作区全局单份，对账参考实现第22节版）：保留它是为了签名 稳定——将来要按 Agent
 * 圈定记忆作用域时，这道墙的形状不必再动。
 */
public interface MemoryService {

  /** 拼进系统提示的长期记忆：核心区全量 + 归档区截断后。文件/库为空时返回带两个空区块的文本，不返回 null。 */
  String buildContext(Session session);

  /** save_memory 转发：记一条到指定分区。写入失败向上抛错，不静默成功。 */
  void remember(String content, MemoryScope scope);

  /** recall_memory 转发：按关键词只在归档区检索。未命中返回空列表；读取失败向上抛错，不静默返回空。 */
  List<String> recall(String keyword);
}
