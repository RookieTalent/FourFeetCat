package org.fourfeetcat.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;

/**
 * 档一（默认）：长期记忆存工作区里的 {@code memory/MEMORY.md} 一个 Markdown 文件，按 {@code ## 核心记忆} / {@code ## 归档记忆}
 * 两个区块组织。零依赖、人可读、可随 git 跟踪——记忆量不大时的首选。
 *
 * <p>四条契约的落地：{@link #load} 每次 {@code Files.readString} 不缓存（契约一）；{@link #truncateIfNeeded}
 * 只接归档区那段字符串裁尾、物理上碰不到核心区（契约二）；分区由 {@code scope} 决定（契约三）； {@link #recallByKeyword} 只搜归档区（契约四）。
 *
 * <p>写入是整文件重写：读全文、往对应区块追加、再写回"两区块"形态。因此工作区初始化时生成的占位说明文字在首次写入后不会 被保留——这是有意为之：{@code MEMORY.md} 的正文归
 * Agent 的成长记录所有（由 save_memory 写入），不混人写的说明。
 */
public class MarkdownMemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";

  /** 归档区水位：只管归档区，核心区不在这个函数的入参里。 */
  private static final int MAX_ARCHIVE_CHARS = 4000;

  private final Path memoryFile;

  /**
   * @param workspaceRoot 工作区根（{@code FOURFEETCAT_ROOT}，缺省 {@code .fourfeetcat}）——路径口径只在装配处有一处
   */
  public MarkdownMemoryStore(Path workspaceRoot) {
    this.memoryFile = workspaceRoot.resolve("memory").resolve("MEMORY.md");
  }

  @Override
  public void append(String content, MemoryScope scope) {
    String entry = "- [" + LocalDate.now() + "] " + content;
    String raw = read();
    String core = extractSection(raw, CORE_HEADER);
    String archive = extractSection(raw, ARCHIVE_HEADER);
    if (scope == MemoryScope.CORE) {
      core = core.isEmpty() ? entry : core + "\n" + entry;
    } else {
      archive = archive.isEmpty() ? entry : archive + "\n" + entry;
    }
    write(CORE_HEADER + "\n" + core + "\n" + ARCHIVE_HEADER + "\n" + archive);
  }

  @Override
  public String load() {
    String raw = read(); // 每次重新读——契约一
    String core = extractSection(raw, CORE_HEADER); // 核心区：完整返回
    String archive = truncateIfNeeded(extractSection(raw, ARCHIVE_HEADER));
    return CORE_HEADER + "\n" + core + "\n" + ARCHIVE_HEADER + "\n" + archive;
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    return extractSection(read(), ARCHIVE_HEADER)
        .lines()
        .filter(line -> !line.isBlank() && line.contains(keyword))
        .toList();
  }

  /** 只裁归档段字符串，核心区不在入参里——契约二靠物理隔离保证。裁点可能落在一条记忆中间，这是简单截断的已知代价。 */
  private static String truncateIfNeeded(String archive) {
    if (archive.length() <= MAX_ARCHIVE_CHARS) {
      return archive;
    }
    return archive.substring(archive.length() - MAX_ARCHIVE_CHARS);
  }

  /**
   * 取某个区块的正文：该标题之后、到下一个标题之前。
   *
   * <p>标题缺失（文件还不存在、或被人手改过）按空区块处理，不报错——格式问题不该让整轮对话失败。
   */
  private static String extractSection(String raw, String header) {
    int start = raw.indexOf(header);
    if (start < 0) {
      return "";
    }
    int contentStart = start + header.length();
    int nextCore = raw.indexOf(CORE_HEADER, contentStart);
    int nextArchive = raw.indexOf(ARCHIVE_HEADER, contentStart);
    int end = raw.length();
    if (nextCore >= 0) {
      end = Math.min(end, nextCore);
    }
    if (nextArchive >= 0) {
      end = Math.min(end, nextArchive);
    }
    return raw.substring(contentStart, end).strip();
  }

  private String read() {
    if (!Files.isRegularFile(memoryFile)) {
      return "";
    }
    try {
      return Files.readString(memoryFile);
    } catch (IOException e) {
      throw new UncheckedIOException("读取 MEMORY.md 失败: " + memoryFile, e);
    }
  }

  private void write(String content) {
    try {
      Path parent = memoryFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(memoryFile, content);
    } catch (IOException e) {
      throw new UncheckedIOException("写入 MEMORY.md 失败: " + memoryFile, e);
    }
  }
}
