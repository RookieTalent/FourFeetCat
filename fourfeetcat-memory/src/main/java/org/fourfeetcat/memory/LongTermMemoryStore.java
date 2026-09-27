package org.fourfeetcat.memory;

import java.util.List;
import org.fourfeetcat.core.memory.MemoryScope;

/**
 * 长期记忆的可插拔后端接口（第22节，第21节评审那道"接口墙"的对下一侧）。四档实现（文件 / 结构化库 / 外部记忆服务 / 内存替身）各写各的存储，但共守四条行为契约：
 *
 * <ol>
 *   <li><b>不缓存</b>：{@link #load} 每次重新读文件 / 查库 / 调服务，写完立刻可见——这样 Agent 调完"记一条"， 下一轮就能看到；
 *   <li><b>核心记忆永不被截断</b>：截断只作用在归档区，核心区完整返回；
 *   <li><b>写核心还是写归档由调用方经 {@code scope} 显式指定</b>，系统不猜；
 *   <li>{@link #recallByKeyword} <b>只在归档区</b>做简单关键词匹配（核心区本就全量注入、不参与检索）。
 * </ol>
 *
 * <p>签名保持中立：不出现"文件""表""HTTP""向量"这类某一档实现特有的词——将来接更重的一档实现，也应能干净套进这个签名。
 *
 * <p>四条契约由 {@code MemoryStoreContractTest} 对每一档统一断言：谁破了规矩，对应的那一行参数立刻红。
 */
public interface LongTermMemoryStore {

  /** 追加一条记忆到指定分区。写入失败向上抛错，不静默当成功。 */
  void append(String content, MemoryScope scope);

  /** 核心区全量 + 归档区（截断后）。 */
  String load();

  /** 只在归档区检索（核心区本就全量注入、不参与检索）。未命中返回空列表，不是错误。 */
  List<String> recallByKeyword(String keyword);
}
