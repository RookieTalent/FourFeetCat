package org.fourfeetcat.core.session;

/**
 * 会话持久化出口（依赖倒置）：core 的编排者依赖它，存储侧出实现。
 *
 * <p>第17节只需要 {@code save}——一次处理结束把累积完的会话落盘。取/建会话（{@code getOrCreate}、{@code get}）与 {@code
 * session_id} 的拼接公式归第18节，届时本接口扩容、实现落到 {@code fourfeetcat-storage}。
 */
// 本节只有一个方法（PMD 要求单一抽象方法的接口显式标注）；第18节补 getOrCreate/get 后本注解即去掉
@FunctionalInterface
public interface SessionManager {

  void save(Session session);
}
