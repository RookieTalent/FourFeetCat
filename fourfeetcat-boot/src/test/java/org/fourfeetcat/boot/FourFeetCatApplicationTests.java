package org.fourfeetcat.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "FOURFEETCAT_ROOT=target")
class FourFeetCatApplicationTests {

  // SQLite 测试库落到 target/（surefire 运行时必然存在），避免依赖 main() 预建工作区目录
  @Test
  void contextLoads() {}
}
