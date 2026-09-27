package org.fourfeetcat.memory;

import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.core.session.Session;

/**
 * 门面实现：把长期记忆的读写委托给可插拔的 {@link LongTermMemoryStore}——换后端只换注入的 store，门面签名与上层调用
 * 一字不改。这就是第21节那道"接口墙"两头解耦的回报。
 *
 * <p>{@link #buildContext} 返回长期记忆（核心区全量 + 归档区截断后，由 store 的 {@code load} 保证契约二）；
 * <b>不拼会话历史</b>——会话历史由组装器的历史段独立负责，两处都拼会让系统提示里出现两份历史。
 *
 * <p>{@code session} 参数本节不参与作用域圈定（长期记忆为工作区全局单份）：保留它是为了签名稳定，将来按 Agent 圈定作用域 时这道墙的形状不必再动。
 */
public class MemoryServiceImpl implements MemoryService {

  private final LongTermMemoryStore store;

  public MemoryServiceImpl(LongTermMemoryStore store) {
    this.store = store;
  }

  @Override
  public String buildContext(Session session) {
    return store.load();
  }

  @Override
  public void remember(String content, MemoryScope scope) {
    store.append(content, scope);
  }

  @Override
  public List<String> recall(String keyword) {
    return store.recallByKeyword(keyword);
  }
}
