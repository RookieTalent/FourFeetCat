package org.fourfeetcat.boot;

import java.io.IOException;
import java.nio.file.Files;
import org.fourfeetcat.cli.FourFeetCatCli;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * FourFeetCat 启动入口（fat JAR 由 spring-boot-maven-plugin repackage 产出）。
 *
 * <p><b>三件套扫描声明缺一不可</b>（课件"坑四"）：{@code scanBasePackages} 只管普通 Bean 的组件扫描，不会带动
 * {@code @EnableJpaRepositories} / {@code @EntityScan} 跟着跨模块生效——二者默认只按主类所在包扫描。CLI 模块与存放会话/审计
 * 数据的模块分属不同 Java 包，不显式声明就会在启动时报"找到 0 个仓储接口"、会话与审计写不进去。
 *
 * <p>命令按轻重分流：轻命令根本不会调到引擎工厂，因此永远不会启动容器（毫秒级出结果）；重命令才付启动成本。
 */
@SpringBootApplication(scanBasePackages = "org.fourfeetcat")
@EnableJpaRepositories(basePackages = "org.fourfeetcat")
@EntityScan(basePackages = "org.fourfeetcat")
public class FourFeetCatApplication {

  public static void main(String[] args) throws IOException {
    // jdbc:sqlite 只建文件不建目录，先确保工作区根目录存在（FOURFEETCAT_ROOT 可整体搬移）
    Files.createDirectories(AgentRuntimeConfiguration.workspaceRoot());
    int exitCode =
        new FourFeetCatCli(
                type -> new SpringApplicationBuilder(FourFeetCatApplication.class).web(type).run())
            .commandLine()
            .execute(args);
    System.exit(exitCode);
  }
}
