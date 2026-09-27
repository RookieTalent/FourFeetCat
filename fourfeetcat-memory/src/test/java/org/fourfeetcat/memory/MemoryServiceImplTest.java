package org.fourfeetcat.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.core.memory.MemoryService;
import org.fourfeetcat.core.session.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门面：取上下文返回长期记忆（核心区全量 + 归档区截断后，核心记忆完整在内），记与查转发到底层。
 *
 * <p>会话历史不在门面的返回值里——它由组装器的历史段独立负责；这里用内存后端当桩，只钉门面自己的口径。
 */
class MemoryServiceImplTest {

  private static final Session SESSION = new Session("cli:wang:default", "default", "cli", "wang");

  private MemoryService service() {
    return new MemoryServiceImpl(new InMemoryMemoryStore());
  }

  @Test
  @DisplayName("buildContext 返回长期记忆_核心记忆完整在内")
  void buildContextReturnsLongTermMemoryWithCoreIntact() {
    MemoryService service = service();
    service.remember("用户叫小王", MemoryScope.CORE);
    service.remember("归档一条", MemoryScope.ARCHIVAL);

    String context = service.buildContext(SESSION);

    assertTrue(context.contains("用户叫小王"), "核心记忆完整在内");
    assertTrue(context.contains("归档一条"), "归档截断后的部分也在");
  }

  @Test
  @DisplayName("remember / recall 转发给底层 store")
  void rememberAndRecallDelegateToStore() {
    MemoryService service = service();
    service.remember("项目叫 FourFeetCat", MemoryScope.ARCHIVAL);

    assertFalse(service.recall("FourFeetCat").isEmpty(), "写进去的归档能被检索到");
    assertTrue(service.recall("不存在的词").isEmpty(), "未命中返回空列表，不是错误");
  }
}
