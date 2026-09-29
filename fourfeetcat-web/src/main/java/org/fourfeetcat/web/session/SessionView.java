package org.fourfeetcat.web.session;

import org.fourfeetcat.storage.SessionEntity;

/**
 * 会话的对外视图（第26节课件）。
 *
 * <p><b>这是只读投影，不是实体</b>：它存在的意义是让对外响应形状与库里那张表的列解耦——将来表里加一列内部字段（比如重试计数），对外形状不必跟着动。
 *
 * <p>字段与 {@link SessionEntity} 逐项对应，取值一律取自落库内容，不做任何加工（时间戳保持库里那串 ISO-8601 文本）。
 */
public record SessionView(
    String sessionId,
    String profileName,
    String channel,
    String userId,
    String status,
    String createdAt,
    String lastActiveAt,
    String archivedAt) {

  /** 从落库实体投影。归档时间可空（未归档时为 null）。 */
  public static SessionView from(SessionEntity entity) {
    return new SessionView(
        entity.getSessionId(),
        entity.getProfileName(),
        entity.getChannel(),
        entity.getUserId(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getLastActiveAt(),
        entity.getArchivedAt());
  }
}
