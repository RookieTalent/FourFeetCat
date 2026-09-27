package org.fourfeetcat.memory.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.memory.InMemoryMemoryStore;
import org.fourfeetcat.memory.MemoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 记忆工具：分区缺省走归档、显式核心落核心、非法分区点名报错且不落库、未命中友好提示不抛异常。 */
class MemoryToolsTest {

  private InMemoryMemoryStore store;
  private MemoryTools tools;

  @BeforeEach
  void setUp() {
    store = new InMemoryMemoryStore();
    tools = new MemoryTools(new MemoryServiceImpl(store));
  }

  @Test
  @DisplayName("scope 缺省写入归档区")
  void defaultScopeWritesArchival() {
    tools.saveMemory("默认该进归档", null);

    assertEquals("默认该进归档", store.recallByKeyword("默认").get(0), "缺省落归档，可检索到");
  }

  @Test
  @DisplayName("scope 为空串同样落归档")
  void blankScopeWritesArchival() {
    tools.saveMemory("空串也进归档", "  ");

    assertEquals(1, store.recallByKeyword("空串").size());
  }

  @Test
  @DisplayName("显式 core 写入核心区_不参与归档检索")
  void explicitCoreWritesCore() {
    tools.saveMemory("这是核心", "core");

    assertTrue(store.recallByKeyword("这是核心").isEmpty(), "核心区不参与归档检索");
    assertTrue(store.load().contains("这是核心"), "但它完整出现在上下文里");
  }

  @Test
  @DisplayName("scope 大小写不一致同样被接受")
  void scopeIsCaseInsensitive() {
    tools.saveMemory("大小写混写", "Core");

    assertTrue(store.load().contains("大小写混写"));
    assertTrue(store.recallByKeyword("大小写混写").isEmpty(), "归一到 CORE，落的是核心区");
  }

  @Test
  @DisplayName("非法 scope 报错点名_且不落库")
  void invalidScopeReportsErrorAndWritesNothing() {
    String result = tools.saveMemory("内容", "bogus");

    assertTrue(result.contains("bogus"), "报错点名非法分区值");
    assertTrue(
        store.load().replace("## 核心记忆", "").replace("## 归档记忆", "").isBlank(), "长期记忆一个字都没被改写");
  }

  @Test
  @DisplayName("检索未命中_返回友好提示不抛异常")
  void recallMissReturnsFriendlyMessage() {
    assertEquals("没有找到相关记忆", tools.recallMemory("查无此词"));
  }

  @Test
  @DisplayName("记忆往返_工具写进去的能按关键词查回来")
  void savedMemoryCanBeRecalled() {
    tools.saveMemory("项目叫 FourFeetCat", MemoryScope.ARCHIVAL.name());

    assertEquals("项目叫 FourFeetCat", tools.recallMemory("FourFeetCat").split("\n")[0]);
  }
}
