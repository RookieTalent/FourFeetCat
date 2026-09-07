/**
 * FourFeetCat 核心抽象：CatTool 接口、Session、Profile、ContextLoader、ReActLoop、
 * PromptBuilder、ToolExecutor、AgentService 及 channel / knowledge 跨模块契约。
 *
 * <p>宪法约束：本模块是依赖倒置的契约层，只被其他模块依赖，不依赖任何业务模块。
 */
package org.fourfeetcat.core;
