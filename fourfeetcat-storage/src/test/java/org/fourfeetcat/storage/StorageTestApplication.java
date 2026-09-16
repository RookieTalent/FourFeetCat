package org.fourfeetcat.storage;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/** 库模块无启动类：给 @DataJpaTest 切片提供 @SpringBootConfiguration 搜索锚点。 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class StorageTestApplication {}
