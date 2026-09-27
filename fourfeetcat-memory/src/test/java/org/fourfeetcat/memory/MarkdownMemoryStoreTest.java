package org.fourfeetcat.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.fourfeetcat.core.memory.MemoryScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 文件档专属：空文件、缺区块标题、字符串截断的边界、两区块互不串区。 */
class MarkdownMemoryStoreTest {

  @TempDir Path root;

  @Test
  @DisplayName("空记忆文件_load 返回空区块不报错")
  void emptyFileLoadsEmptySections() {
    String loaded = new MarkdownMemoryStore(root).load();

    assertTrue(loaded.contains("## 核心记忆"), "区块标题在——上层不必知道文件还不存在");
    assertTrue(loaded.contains("## 归档记忆"), "区块标题在");
    assertTrue(new MarkdownMemoryStore(root).recallByKeyword("随便").isEmpty(), "检索返回空列表，不报错");
  }

  @Test
  @DisplayName("文件存在但缺某个区块标题_该区块按空处理不报错")
  void missingSectionHeaderTreatedAsEmpty() throws IOException {
    Path memoryDir = root.resolve("memory");
    Files.createDirectories(memoryDir);
    // 手写的记忆文件：只有核心区，没有归档区标题
    Files.writeString(memoryDir.resolve("MEMORY.md"), "## 核心记忆\n- [2026-01-01] 我是核心\n");

    MarkdownMemoryStore memory = new MarkdownMemoryStore(root);

    assertTrue(memory.load().contains("我是核心"), "已有区块照常读出");
    assertTrue(memory.recallByKeyword("我是核心").isEmpty(), "缺标题的归档区按空处理——核心区不参与检索");

    memory.append("新归档", MemoryScope.ARCHIVAL); // 首次写入把两区块结构补齐
    assertEquals(1, memory.recallByKeyword("新归档").size(), "补齐后归档区可检索");
  }

  @Test
  @DisplayName("归档恰好等于上限不截断_超过一位才裁最早")
  void archivalTruncatesOnlyBeyondLimit() throws IOException {
    assertTrue(loadWithArchiveBody("HEAD" + "x".repeat(3996)).contains("HEAD"), "恰好 4000 字符：不裁");
    assertFalse(
        loadWithArchiveBody("HEAD" + "x".repeat(3997)).contains("HEAD"),
        "4001 字符：从尾部取 4000，最早的那个字符被裁掉");
  }

  @Test
  @DisplayName("核心与归档写入互不串区")
  void coreAndArchivalStaySeparate() {
    MarkdownMemoryStore memory = new MarkdownMemoryStore(root);
    memory.append("我是核心", MemoryScope.CORE);
    memory.append("我是归档", MemoryScope.ARCHIVAL);

    assertEquals(1, memory.recallByKeyword("归档").size(), "归档检索只命中归档条目");
    assertTrue(memory.recallByKeyword("核心").isEmpty(), "核心区不参与归档检索");
    assertTrue(memory.load().contains("我是核心") && memory.load().contains("我是归档"));
  }

  /** 直接写文件，精确控制归档区正文的字符数——字符串截断的边界只能这么测。 */
  private String loadWithArchiveBody(String archiveBody) throws IOException {
    Path memoryDir = root.resolve("memory");
    Files.createDirectories(memoryDir);
    Files.writeString(memoryDir.resolve("MEMORY.md"), "## 核心记忆\n\n## 归档记忆\n" + archiveBody + "\n");
    return new MarkdownMemoryStore(root).load();
  }
}
