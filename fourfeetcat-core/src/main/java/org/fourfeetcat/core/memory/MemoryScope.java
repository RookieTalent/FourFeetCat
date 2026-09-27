package org.fourfeetcat.core.memory;

/**
 * 长期记忆的两类分区（第22节）：
 *
 * <ul>
 *   <li>{@link #CORE} 核心记忆——始终完整在场、小而恒定：用户是谁、项目背景、关键偏好。永不参与截断，也不参与检索（它本来 就每轮全量注入，"检索"对它是多余的）。
 *   <li>{@link #ARCHIVAL} 归档记忆——按时间累积的流水。超水位只保留最近内容，关键词检索只在这一区里做。
 * </ul>
 *
 * <p>写哪个分区由调用方经 {@code scope} 显式指定，系统不猜、也不自动判定（第21节评审定的契约三）。
 */
public enum MemoryScope {
  CORE,
  ARCHIVAL
}
