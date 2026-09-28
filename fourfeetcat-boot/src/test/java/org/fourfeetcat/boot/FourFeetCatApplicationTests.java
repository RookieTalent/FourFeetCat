package org.fourfeetcat.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "FOURFEETCAT_ROOT=${user.dir}/target")
class FourFeetCatApplicationTests {

  // SQLite 测试库落到 target/（surefire 运行时必然存在），避免依赖 main() 预建工作区目录。
  // 第24节起必须写成绝对路径：文件白名单默认引用同一个变量，而白名单项是相对路径时启动即拒
  // （相对路径的语义随工作目录变化，安全配置不能这么漂）。
  @Test
  void contextLoads() {}
}
