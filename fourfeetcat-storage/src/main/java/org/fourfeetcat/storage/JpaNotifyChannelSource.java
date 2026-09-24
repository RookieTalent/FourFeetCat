package org.fourfeetcat.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.fourfeetcat.core.notify.NotifyChannelSource;
import org.springframework.stereotype.Component;

/**
 * 渠道取数端口的实现（第20节）：把 {@code notify_channels} 表的每一行投影成一份通用配置。
 *
 * <p>只做"取出来"这一件事——"没配渠道该怎么办""名缺省取哪条""名字查不到怎么报错"都是消费方（通知工具）的口径，不在这里判断。 表结构沿用第19节交付的 V19
 * 双轨脚本，本节**零迁移**。
 */
@Component
public class JpaNotifyChannelSource implements NotifyChannelSource {

  private final NotifyChannelRepository repository;

  public JpaNotifyChannelSource(NotifyChannelRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Map<String, String>> all() {
    List<Map<String, String>> rows = new ArrayList<>();
    for (NotifyChannel channel : repository.findAll()) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("name", channel.getName());
      row.put("type", channel.getType());
      row.put("url", channel.getUrl());
      row.put("description", channel.getDescription());
      rows.add(row);
    }
    return rows;
  }
}
