package org.fourfeetcat.storage;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * memory_entries 读写（第22节）：核心区全量、归档区取最近 N 条、归档区关键词匹配。
 *
 * <p><b>核心区查询永不带条数上限</b>——上限只出现在归档查询上。"核心记忆永不被截断"这条契约靠 SQL 结构保证， 不靠调用方自觉。
 *
 * <p>检索的 LIKE 模式由调用方拼好（关键词原样拼进去、<b>不转义通配符</b>）：记忆正文与检索词都来自 Agent
 * 自己的对话，是非对抗输入；第22节课件明确"核心阶段就是简单的包含匹配，别一上来上正则或分词"。
 */
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, Long> {

  /** 核心区：全量、按写入顺序（永不截断——契约二）。 */
  List<MemoryEntry> findByScopeOrderByIdAsc(String scope);

  /** 归档区：最近 N 条（id 降序 + 页大小限量，即 LIMIT——截断只作用在归档上）。 */
  List<MemoryEntry> findByScopeOrderByIdDesc(String scope, Pageable pageable);

  /** 归档区关键词匹配：显式限定分区，核心区不参与检索（契约四）。 */
  @Query(
      "SELECT m FROM MemoryEntry m WHERE m.scope = 'ARCHIVAL' AND m.content LIKE :pattern"
          + " ORDER BY m.id ASC")
  List<MemoryEntry> searchArchival(@Param("pattern") String pattern);
}
