package org.fourfeetcat.web.api;

/**
 * 一轮处理的回复（第26节课件）：两个触发端点（会话内发消息、Agent 无状态调用）的响应同形。
 *
 * <p>刻意**不含会话标识**：无状态调用的调用方不需要、也不该被要求维护它——返回值里带上它，等于暗示调用方存下来接着用， 而无状态路径的每次调用都是独立会话，存下来也接不上。
 */
public record ReplyResponse(String reply) {}
