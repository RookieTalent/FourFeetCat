package org.fourfeetcat.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.storage.MemoryEntry;
import org.fourfeetcat.storage.MemoryEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 库档专属（第22节课件点名）：截断与检索的落地方式与文件档完全不同——差异全在"走了哪条查询"，所以这里用调用验证直接钉住 它。真实 SQLite 的建表、条数上限、匹配语义由仓储测试那边验。
 */
class SqliteMemoryStoreTest {

  private final MemoryEntryRepository repository = mock(MemoryEntryRepository.class);

  private final SqliteMemoryStore store = new SqliteMemoryStore(repository);

  @Test
  @DisplayName("写入落分区名与内容_时间由写入方给")
  void appendStoresScopeContentAndCreatedAt() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    store.append("一条核心记忆", MemoryScope.CORE);

    ArgumentCaptor<MemoryEntry> saved = ArgumentCaptor.forClass(MemoryEntry.class);
    verify(repository).save(saved.capture());
    assertEquals("CORE", saved.getValue().getScope(), "分区落的是枚举名");
    assertEquals("一条核心记忆", saved.getValue().getContent());
    assertNotNull(saved.getValue().getCreatedAt(), "时间由写入方给 ISO-8601 字符串");
  }

  @Test
  @DisplayName("核心区走全量查询_条数上限只加在归档查询上")
  void coreQueryIsUnlimitedAndArchivalQueryIsCapped() {
    when(repository.findByScopeOrderByIdAsc(anyString())).thenReturn(List.of());
    when(repository.findByScopeOrderByIdDesc(anyString(), any(Pageable.class)))
        .thenReturn(List.of());

    store.load();

    // 核心区：全量、按写入顺序（不带任何分页参数——"核心永不被截断"靠这条查询的形状保证）
    verify(repository).findByScopeOrderByIdAsc("CORE");

    // 归档区：页大小就是水位，偏移为 0（只取最近 N 条）
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findByScopeOrderByIdDesc(eq("ARCHIVAL"), pageable.capture());
    assertEquals(PageRequest.of(0, 100), pageable.getValue(), "上限只出现在归档查询上");
  }

  @Test
  @DisplayName("归档区按时间正序渲染_上限之外的不出现")
  void archivalRendersInAscendingOrderWithinCap() {
    MemoryEntry first = entry(1L, "ARCHIVAL", "最早的");
    MemoryEntry second = entry(2L, "ARCHIVAL", "最近的");
    when(repository.findByScopeOrderByIdAsc(anyString())).thenReturn(List.of());
    // 仓储按 id 降序返回最近 N 条，store 负责翻回时间正序
    when(repository.findByScopeOrderByIdDesc(anyString(), any(Pageable.class)))
        .thenReturn(List.of(second, first));

    String loaded = store.load();

    assertTrue(loaded.indexOf("最早的") < loaded.indexOf("最近的"), "翻回正序：最早的在前");
  }

  @Test
  @DisplayName("检索走只在归档内匹配的那条查询")
  void recallUsesArchivalOnlyQuery() {
    when(repository.searchArchival(anyString())).thenReturn(List.of());

    store.recallByKeyword("关键词");

    verify(repository).searchArchival("%关键词%");
  }

  private static MemoryEntry entry(Long id, String scope, String content) {
    MemoryEntry entry = new MemoryEntry();
    entry.setScope(scope);
    entry.setContent(content);
    entry.setCreatedAt("2026-01-01T00:00:00Z");
    assignId(entry, id);
    return entry;
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
