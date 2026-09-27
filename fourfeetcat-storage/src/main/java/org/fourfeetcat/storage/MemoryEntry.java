package org.fourfeetcat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * memory_entries 实体（第22节）：结构化库档的一条长期记忆。列名与 {@code db/migration/sqlite/V20__memory_entries.sql}
 * 逐字一致——SQLite 无原生 TIMESTAMP，时间戳落 TEXT （ISO-8601），与既有几张表同口径。
 *
 * <p>{@code createdAt} 由写入方给 ISO-8601 字符串、不用 JPA 生命周期回调：与既有实体同口径（调用方给时间），
 * 且"谁填的时间"不藏在容器回调里——单测能直接断言。
 */
@Entity
@Table(name = "memory_entries")
public class MemoryEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 分区：CORE / ARCHIVAL（{@code MemoryScope} 的枚举名）。 */
  @Column(nullable = false, length = 16)
  private String scope;

  @Column(nullable = false)
  private String content;

  /** 写入时间，ISO-8601 字符串。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  public Long getId() {
    return id;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
