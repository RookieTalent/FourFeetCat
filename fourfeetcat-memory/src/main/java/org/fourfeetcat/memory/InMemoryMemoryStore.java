package org.fourfeetcat.memory;

import java.util.ArrayList;
import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;

/**
 * 内存后端：进程内两个列表，满足同样四条契约。两个用途——
 *
 * <ol>
 *   <li><b>契约测试里代替外部记忆服务档</b>：契约测试要的是"这一档守不守规矩"，而真实的 REST 交互由外部服务档的专属
 *       测试单独验证（用进程内假服务跑）。用一个进程内的替身把"守规矩"和"协议对不对"两件事分开测。
 *   <li><b>门面与工具测试的轻量基建</b>：不落盘、不查库、不起容器，秒级跑完。
 * </ol>
 *
 * <p>检索口径与文件档对齐（按内容包含、区分大小写）；归档水位按条数取尾部 N 条，与结构化库档对齐——两档各自的对齐对象 不同是有意的：替身要能同时替得了"文件式"与"库式"两类断言。
 */
public class InMemoryMemoryStore implements LongTermMemoryStore {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";
  private static final int MAX_ARCHIVE_ROWS = 100;

  private final List<String> core = new ArrayList<>();
  private final List<String> archive = new ArrayList<>();

  @Override
  public void append(String content, MemoryScope scope) {
    (scope == MemoryScope.CORE ? core : archive).add(content);
  }

  @Override
  public String load() {
    List<String> recentArchive =
        archive.size() <= MAX_ARCHIVE_ROWS
            ? archive
            : archive.subList(archive.size() - MAX_ARCHIVE_ROWS, archive.size());
    return CORE_HEADER
        + "\n"
        + String.join("\n", core)
        + "\n"
        + ARCHIVE_HEADER
        + "\n"
        + String.join("\n", recentArchive);
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    return archive.stream().filter(line -> line.contains(keyword)).toList();
  }
}
