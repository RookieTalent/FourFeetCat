package org.fourfeetcat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * sessions 表实体（第18节）。列名与 db/migration/sqlite/V18__sessions.sql 逐字一致——SQLite 无原生 TIMESTAMP，时间戳落
 * TEXT（ISO-8601），与既有审计实体同款口径。
 *
 * <p>与 {@code fourfeetcat-core} 的内存态会话是两个层次：那个负责一轮处理中按序累积，这个负责跨重启留存。 主键 {@code sessionId}
 * **不是**数据库自增——标识由会话层按三元组生成，那是唯一允许拼接的地方。
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

  @Id
  @Column(name = "session_id", nullable = false, length = 64)
  private String sessionId;

  @Column(name = "profile_name", nullable = false, length = 64)
  private String profileName;

  @Column(name = "channel", nullable = false, length = 32)
  private String channel;

  @Column(name = "user_id", nullable = false, length = 64)
  private String userId;

  @Column(name = "messages_json", nullable = false)
  private String messagesJson;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "last_active_at", nullable = false)
  private String lastActiveAt;

  @Column(name = "archived_at")
  private String archivedAt;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getMessagesJson() {
    return messagesJson;
  }

  public void setMessagesJson(String messagesJson) {
    this.messagesJson = messagesJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(String lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public String getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(String archivedAt) {
    this.archivedAt = archivedAt;
  }
}
