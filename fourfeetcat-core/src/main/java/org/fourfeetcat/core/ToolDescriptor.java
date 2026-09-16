package org.fourfeetcat.core;

/**
 * 工具 schema 的最小载体：name + 描述 + 参数 JSON Schema 字符串。
 *
 * <p>第16节 Provider 只翻译工具说明（不执行）；工具执行接口是第20节的交付物，届时提供到本 载体的导出即可衔接。
 */
public record ToolDescriptor(String name, String description, String inputSchema) {}
