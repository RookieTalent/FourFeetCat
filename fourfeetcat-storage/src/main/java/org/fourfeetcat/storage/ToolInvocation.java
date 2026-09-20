package org.fourfeetcat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * tool_invocations 审计表实体（宪法原则五：Day One 写入）。列名与 db/migration/sqlite/V17__tool_invocations.sql
 * 逐字一致——SQLite 无原生 BOOLEAN/TIMESTAMP， 布尔落 INTEGER(0/1)、时间戳落 TEXT（ISO-8601）。
 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 64)
  private String sessionId;

  @Column(name = "tool_name", nullable = false, length = 64)
  private String toolName;

  @Column(name = "input_json")
  private String inputJson;

  @Column(name = "result_json")
  private String resultJson;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  public Long getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getInputJson() {
    return inputJson;
  }

  public void setInputJson(String inputJson) {
    this.inputJson = inputJson;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}
