package org.fourfeetcat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * notify_channels 表实体（第19节）：通知渠道的**全局注册表**。
 *
 * <p>列名与 db/migration/sqlite/V19__notify_channels.sql 逐字一致。主键 {@code name} **不是**数据库自增——渠道名由运营方
 * 指定，Agent 侧按名引用；地址与凭证不进 Agent 配置正文、不进对话上下文（宪法原则四与 §6.8 的口径）。
 *
 * <p>核心阶段没有写入入口（管理台与 CRUD 端点归扩展阶段），本表是"结构就位、写入待后续"：本节只保证按名能读到。
 */
@Entity
@Table(name = "notify_channels")
public class NotifyChannel {

  @Id
  @Column(name = "name", nullable = false, length = 64)
  private String name;

  @Column(name = "type", nullable = false, length = 32)
  private String type;

  @Column(name = "url")
  private String url;

  @Column(name = "description")
  private String description;

  @Column(name = "config")
  private String config;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getConfig() {
    return config;
  }

  public void setConfig(String config) {
    this.config = config;
  }
}
