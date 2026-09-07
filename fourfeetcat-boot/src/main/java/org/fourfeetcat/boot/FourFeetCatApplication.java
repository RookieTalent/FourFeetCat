package org.fourfeetcat.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** FourFeetCat 启动入口（fat JAR 由 spring-boot-maven-plugin repackage 产出）。 */
@SpringBootApplication(scanBasePackages = "org.fourfeetcat")
public class FourFeetCatApplication {

  public static void main(String[] args) throws IOException {
    // jdbc:sqlite 只建文件不建目录，先确保工作区根目录存在（FOURFEETCAT_ROOT 可整体搬移）
    Files.createDirectories(
        Path.of(System.getenv().getOrDefault("FOURFEETCAT_ROOT", ".fourfeetcat")));
    SpringApplication.run(FourFeetCatApplication.class, args);
  }
}
