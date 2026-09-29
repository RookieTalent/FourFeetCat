package org.fourfeetcat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * scheduled_tasks 表实体（第28节）。列名与 db/migration/sqlite/V21__scheduled_tasks.sql 逐字一致——SQLite 无原生
 * BOOLEAN/TIMESTAMP， 布尔落 INTEGER(0/1)、时间戳落 TEXT（ISO-8601）。本表只存"状态与历史"，定义源仍是 Agent 配置。
 */
@Entity
@Table(name = "scheduled_tasks")
public class ScheduledTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long scheduleId;

  @Column(name = "profile_name", nullable = false, length = 64)
  private String profileName;

  @Column(name = "schedule_key", nullable = false, length = 64)
  private String scheduleKey;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "cron", nullable = false)
  private String cron;

  @Column(name = "zone", nullable = false)
  private String zone;

  @Column(name = "message", nullable = false)
  private String message;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "retired", nullable = false)
  private boolean retired;

  @Column(name = "next_run_at")
  private String nextRunAt;

  @Column(name = "last_run_at")
  private String lastRunAt;

  @Column(name = "last_status")
  private String lastStatus;

  @Column(name = "run_count", nullable = false)
  private long runCount;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  public Long getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(Long scheduleId) {
    this.scheduleId = scheduleId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getScheduleKey() {
    return scheduleKey;
  }

  public void setScheduleKey(String scheduleKey) {
    this.scheduleKey = scheduleKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isRetired() {
    return retired;
  }

  public void setRetired(boolean retired) {
    this.retired = retired;
  }

  public String getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(String nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public String getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(String lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public String getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(String lastStatus) {
    this.lastStatus = lastStatus;
  }

  public long getRunCount() {
    return runCount;
  }

  public void setRunCount(long runCount) {
    this.runCount = runCount;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
