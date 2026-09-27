package org.fourfeetcat.memory;

import java.time.Instant;
import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;
import org.fourfeetcat.storage.MemoryEntry;
import org.fourfeetcat.storage.MemoryEntryRepository;
import org.springframework.data.domain.PageRequest;

/**
 * 档二：长期记忆按条入 {@code memory_entries} 表。记忆量变大后的结构化升级，仍零外部依赖（复用既有的 SQLite）。
 *
 * <p>四条契约的落地与文件档**同一套语义、完全不同的手法**：截断从"字符串裁尾"变成归档查询的条数上限（核心区走另一条 查询、天然不受影响——契约二靠 SQL
 * 结构保证）；检索从按行包含变成库内匹配（契约四）；不缓存则天然成立（每次查库， 契约一）。
 *
 * <p>条目行渲染成 {@code - 内容}（时间在库里是单独一列，不再重复进文本）；渲染带分区标题是为了让门面交给组装器的文本 与文件档形态一致，上层不必知道底下是哪一档。
 */
public class SqliteMemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";

  /** 归档区水位：只带最近 N 条（查询条数上限，不是删除）。 */
  private static final int MAX_ARCHIVE_ROWS = 100;

  private final MemoryEntryRepository repository;

  public SqliteMemoryStore(MemoryEntryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    MemoryEntry entry = new MemoryEntry();
    entry.setScope(scope.name());
    entry.setContent(content);
    entry.setCreatedAt(Instant.now().toString());
    repository.save(entry);
  }

  @Override
  public String load() {
    String core = render(repository.findByScopeOrderByIdAsc(MemoryScope.CORE.name()));
    // 归档取最近 N 条，再翻回时间正序拼接——上限只作用在归档上（契约二）
    List<MemoryEntry> recent =
        repository.findByScopeOrderByIdDesc(
            MemoryScope.ARCHIVAL.name(), PageRequest.of(0, MAX_ARCHIVE_ROWS));
    List<MemoryEntry> ascending = recent.reversed();
    return CORE_HEADER + "\n" + core + "\n" + ARCHIVE_HEADER + "\n" + render(ascending);
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    return repository.searchArchival("%" + keyword + "%").stream()
        .map(MemoryEntry::getContent)
        .toList();
  }

  private static String render(List<MemoryEntry> entries) {
    return entries.stream()
        .map(entry -> "- " + entry.getContent())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }
}
