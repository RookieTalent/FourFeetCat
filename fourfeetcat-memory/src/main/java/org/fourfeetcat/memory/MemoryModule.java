package org.fourfeetcat.memory;

/**
 * 记忆模块的职责标记（第22节，能力三）：本模块提供记忆统一门面（{@code MemoryService}）的实现、长期记忆的四档后端 （文件 / 结构化库 / 外部记忆服务 /
 * 内存替身）与两个内置工具（{@code save_memory} / {@code recall_memory}）。
 *
 * <p>门面接口与分区枚举在契约层（{@code fourfeetcat-core} 的 memory 包）——组装器在 core 必须注入门面，接口留在本模块 会让 core 与 memory
 * 成环；本模块只提供实现。
 *
 * <p>长期记忆本体由 Agent 通过 {@code save_memory} 写入（系统不做自动提炼）；工作区里是 {@code memory/MEMORY.md}
 * 一个文件、两个区块，归档区超水位只保留最近内容。
 */
public class MemoryModule {}
