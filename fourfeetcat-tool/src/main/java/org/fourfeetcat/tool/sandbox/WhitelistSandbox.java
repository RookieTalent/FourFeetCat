package org.fourfeetcat.tool.sandbox;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * 应用层白名单沙箱（第24节课件）：核心阶段唯一的 {@link Sandbox} 实现，按动作类型路由到路径 / 命令 / 域名三类校验。
 *
 * <p><b>它的定位要诚实</b>：这是"劝阻级"防线——防的是模型犯傻与误操作，防不住蓄意攻击。核心阶段不建议用它跑完全不可信的代码，也不建议对外做多租户。
 *
 * <p><b>三个校验方法都是 private</b>：外部只看得到 {@link #enforce(SandboxAction)}
 * 这一个入口。若把"查路径""查命令"摆到对外契约上，等于把契约按第一档实现裁剪，将来容器 / microVM 档就套不进来了——这是第23节那道墙能不能立住的分水岭。
 *
 * <p><b>判不了就拒绝</b>：路径解析失败、URL 解析不出主机名，一律归到拒绝一侧。安全校验里"判不了就放行"是最隐蔽的漏洞形态。
 */
public class WhitelistSandbox implements Sandbox {

  private final List<Path> allowedRoots;
  private final Set<String> allowedCommands;
  private final List<String> allowedDomainPatterns;

  /**
   * @param fileProps 路径白名单：每一项必须是绝对路径，否则构造期即拒（相对路径的语义随工作目录变化，安全配置不能这么漂）
   * @param shellProps 命令白名单：可执行文件名精确比对
   * @param httpProps 域名白名单：精确项与 {@code *.} 通配项
   */
  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.allowedRoots =
        fileProps.allowedPaths().stream().map(WhitelistSandbox::checkedRoot).toList();
    this.allowedCommands = Set.copyOf(shellProps.allowedCommands());
    this.allowedDomainPatterns = List.copyOf(httpProps.allowedDomains());
  }

  @Override
  public void enforce(SandboxAction action) {
    // 穷尽枚举四值，故不写 default：将来给 ActionType 加取值时，编译器会在这里报错提醒补路由
    switch (action.type()) {
      case FILE_READ, FILE_WRITE -> checkFilePath(action.target());
      case SHELL_COMMAND -> checkShellCommand(action.target());
      case HTTP_REQUEST -> checkHttpUrl(action.target());
    }
  }

  /**
   * 路径校验：把目标归一成<b>真实形态</b>再比对白名单根。
   *
   * <p>只看字符串前缀挡不住"白名单目录里放一个指向外面的软链"——必须让文件系统自己说出真实路径。
   */
  private void checkFilePath(String rawPath) {
    Path target = realPath(Path.of(rawPath));
    if (!allowedRoots.stream().anyMatch(target::startsWith)) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  /**
   * 把一个路径归一成真实形态：存在（含"存在但是个软链"）就交给 {@link Path#toRealPath()} 解析；不存在就上溯到最近存在的祖先，解析它再把剩余段拼回来。
   *
   * <p>用 {@link LinkOption#NOFOLLOW_LINKS} 判存在：悬空软链会被算作"存在"，于是走 {@code toRealPath} 抛错——
   * 若改用跟随链接的判存在，悬空软链会被当成"新建路径"，白名单放行之后写入会顺着链接落到外面去。
   *
   * <p>{@code ponytail:} 白名单根的真实形态在构造期算一次并缓存；运行期不重算。上限是"根路径上的软链被事后替换"这种情况不在防线内—— 那是管理员动作，且 OryxOS
   * 的威胁模型本就不含蓄意攻击。真要收，改成每次校验重算根即可。
   */
  private static Path realPath(Path raw) {
    Path cursor = raw.toAbsolutePath().normalize();
    Deque<Path> pending = new ArrayDeque<>();
    try {
      while (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
        Path parent = cursor.getParent();
        Path name = cursor.getFileName();
        if (parent == null || name == null) {
          // 一路到根都不存在（不存在的盘符之类）：没有可解析的真实路径，用标准化后的路径本身
          return cursor;
        }
        pending.push(name);
        cursor = parent;
      }
      Path real = cursor.toRealPath();
      for (Path name : pending) {
        real = real.resolve(name);
      }
      return real;
    } catch (IOException e) {
      throw violationOf("路径无法解析为真实路径，拒绝放行: " + raw, e);
    }
  }

  /**
   * 带原始异常一起抛：解析失败的原因（权限、悬空软链、路径过长）都在原始异常里，丢掉它，事故就只剩一句"路径无法解析"。
   *
   * <p>用 {@code initCause} 而不是新增构造重载：{@code SandboxViolationException} 的构造签名归第20节，不为这一处再动它。
   */
  private static SandboxViolationException violationOf(String message, Throwable cause) {
    SandboxViolationException violation = new SandboxViolationException(message);
    violation.initCause(cause);
    return violation;
  }

  /** 白名单根也要过同一套真实形态归一——根还没建出来时同样回退到最近存在的祖先前；解析不了即拒绝启动。 */
  private static Path checkedRoot(String rawRoot) {
    if (!Path.of(rawRoot).isAbsolute()) {
      throw new IllegalStateException("file.allowed_paths 里必须是绝对路径，这一项不是: " + rawRoot);
    }
    try {
      return realPath(Path.of(rawRoot));
    } catch (SandboxViolationException e) {
      throw new IllegalStateException("file.allowed_paths 里这一项无法解析: " + rawRoot, e);
    }
  }

  /** 命令校验：只取首 token 精确比对；先 trim 再切，免得用前导空格绕开比对。 */
  private void checkShellCommand(String command) {
    String firstToken = command.trim().split("\\s+")[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  /** 域名校验：只比主机名（端口与路径不参与），解析不出主机名即拒绝。 */
  private void checkHttpUrl(String url) {
    String host;
    try {
      host = URI.create(url).getHost();
    } catch (IllegalArgumentException e) {
      throw violationOf("URL 无法解析，拒绝放行: " + url, e);
    }
    if (host == null) {
      throw new SandboxViolationException("URL 里没有主机名，拒绝放行: " + url);
    }
    if (allowedDomainPatterns.stream().noneMatch(pattern -> matchesDomain(host, pattern))) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  /**
   * 通配项必须带点号边界：{@code *.example.com} 命中 {@code api.example.com}，但绝不放行 {@code evil-example.com}。
   *
   * <p>少了这个点，{@code "evil-example.com".endsWith("example.com")} 为真——这是这类匹配最经典的漏洞。
   */
  private boolean matchesDomain(String host, String pattern) {
    if (pattern.startsWith("*.")) {
      return host.endsWith(pattern.substring(1));
    }
    return host.equals(pattern);
  }
}
