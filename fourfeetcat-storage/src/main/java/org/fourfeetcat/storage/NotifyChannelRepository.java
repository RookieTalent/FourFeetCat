package org.fourfeetcat.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * notify_channels 表仓储（第19节）。
 *
 * <p>本节只用到父接口的能力（按主键取、写入）；"按名解析成通知目标"的封装归第24节——那时才有真正的消费方，现在定它等于为 没有调用方的接口做设计。
 */
public interface NotifyChannelRepository extends JpaRepository<NotifyChannel, String> {}
