package org.fourfeetcat.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.storage.MemoryEntry;
import org.fourfeetcat.storage.MemoryEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Pageable;

/**
 * 第22节课件的验收 harness：同一套断言对每一档后端统一跑——谁破了规矩，对应的那一行参数立刻红。
 *
 * <p>三档怎么凑齐：文件档用真文件（零依赖，可以直接用真的）；结构化库档用背靠内存 List 的有状态假仓储（不拉 Spring 容器，真库的 SQL
 * 语义由仓储测试单独验）；外部记忆服务档用内存替身（契约测的是"这一档守不守规矩"，真实 REST 交互由该档 专属测试用进程内假服务验）。
 *
 * <p>{@code PER_CLASS} 生命周期：让参数化数据源工厂能访问实例注入的 {@link TempDir}。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryStoreContractTest {

  @TempDir Path tempRoot;

  Stream<Arguments> allStores() {
    return Stream.of(
        Arguments.of(
            "markdown", (Supplier<LongTermMemoryStore>) () -> new MarkdownMemoryStore(tempRoot)),
        Arguments.of(
            "sqlite",
            (Supplier<LongTermMemoryStore>) () -> new SqliteMemoryStore(fakeRepository())),
        Arguments.of("mem0(替身)", (Supplier<LongTermMemoryStore>) InMemoryMemoryStore::new));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("allStores")
  @DisplayName("截断只裁归档区_核心记忆一字不能少")
  void truncationKeepsCoreIntact(String name, Supplier<LongTermMemoryStore> factory) {
    LongTermMemoryStore memory = factory.get();
    memory.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 500; i++) {
      memory.append("归档流水 " + i, MemoryScope.ARCHIVAL); // 灌到远超阈值
    }

    String loaded = memory.load();

    assertTrue(loaded.contains("用户叫小王，偏好用 Java"), name + ": 核心区完整——始终在场的底线");
    assertFalse(loaded.contains("归档流水 0"), name + ": 归档区最早的被裁掉");
    assertTrue(loaded.contains("归档流水 499"), name + ": 保留的是最近的");
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("allStores")
  @DisplayName("写入后立刻可读_不允许有缓存")
  void writeIsImmediatelyReadable_noCache(String name, Supplier<LongTermMemoryStore> factory) {
    LongTermMemoryStore memory = factory.get();
    memory.append("刚记的事", MemoryScope.ARCHIVAL);

    assertTrue(memory.load().contains("刚记的事"), name + ": 下一次 load 立即可见");
    assertFalse(memory.recallByKeyword("刚记的事").isEmpty(), name + ": 检索同样立即命中");
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("allStores")
  @DisplayName("scope 路由到正确区块")
  void scopeRoutesToCorrectSection(String name, Supplier<LongTermMemoryStore> factory) {
    LongTermMemoryStore memory = factory.get();
    memory.append("核心内容 alpha", MemoryScope.CORE);
    memory.append("归档内容 beta", MemoryScope.ARCHIVAL);

    assertTrue(memory.recallByKeyword("alpha").isEmpty(), name + ": 核心区不参与检索");
    assertFalse(memory.recallByKeyword("beta").isEmpty(), name + ": 归档区可检索");
    assertTrue(
        memory.load().contains("核心内容 alpha") && memory.load().contains("归档内容 beta"),
        name + ": 两个区块都在上下文里");
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("allStores")
  @DisplayName("recall 只搜归档区")
  void recallSearchesArchivalOnly(String name, Supplier<LongTermMemoryStore> factory) {
    LongTermMemoryStore memory = factory.get();
    memory.append("秘密关键词 zzz 在核心区", MemoryScope.CORE);

    assertTrue(memory.recallByKeyword("zzz").isEmpty(), name + ": 核心区的词检索不到");
  }

  /** 背靠内存 List 的有状态假仓储：只 stub 结构化库档用到的四个方法。 */
  private static MemoryEntryRepository fakeRepository() {
    List<MemoryEntry> data = new ArrayList<>();
    long[] sequence = {0};
    MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              MemoryEntry entry = invocation.getArgument(0);
              assignId(entry, ++sequence[0]);
              data.add(entry);
              return entry;
            });
    when(repository.findByScopeOrderByIdAsc(anyString()))
        .thenAnswer(
            invocation -> {
              String scope = invocation.getArgument(0);
              return data.stream().filter(entry -> entry.getScope().equals(scope)).toList();
            });
    when(repository.findByScopeOrderByIdDesc(anyString(), any(Pageable.class)))
        .thenAnswer(
            invocation -> {
              String scope = invocation.getArgument(0);
              Pageable pageable = invocation.getArgument(1);
              List<MemoryEntry> matched =
                  new ArrayList<>(
                      data.stream().filter(entry -> entry.getScope().equals(scope)).toList());
              matched.sort((left, right) -> Long.compare(right.getId(), left.getId()));
              return matched.stream().limit(pageable.getPageSize()).toList();
            });
    when(repository.searchArchival(anyString()))
        .thenAnswer(
            invocation -> {
              String needle = ((String) invocation.getArgument(0)).replace("%", "");
              return data.stream()
                  .filter(
                      entry ->
                          "ARCHIVAL".equals(entry.getScope())
                              && entry.getContent().contains(needle))
                  .toList();
            });
    return repository;
  }

  /** 实体只有 getter（与既有几张表同款），测试靠反射给自增主键补值。 */
  private static void assignId(MemoryEntry entry, long id) {
    try {
      var field = MemoryEntry.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entry, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("测试无法设置 MemoryEntry.id", e);
    }
  }
}
