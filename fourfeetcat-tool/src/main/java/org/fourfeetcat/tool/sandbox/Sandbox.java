package org.fourfeetcat.tool.sandbox;

/**
 * 沙箱（第20节前向接口，规则本体归沙箱节）：只表达"在受控环境里执行一个动作"这一个意图。
 *
 * <p><b>接口中立性是硬要求</b>：签名里不出现"白名单""容器镜像""VM 配置"这类某一档实现特有的词——用最重的 microVM 实现去
 * 反向套这个签名，也应该能干净套入。这是校验接口是否中立的办法，也是"换隔离方案只新增实现类、不改调用方"的前提。
 *
 * <p>调用点固定：所有涉外工具（文件 / 命令 / 网络）在真正动手之前的第一行调 {@link #enforce}，校验不过即抛 {@link
 * SandboxViolationException}、动作不发生。异常走 {@code ToolExecutor} 既有的失败审计路径落库，不为沙箱另造审计。
 */
@FunctionalInterface
public interface Sandbox {

  /** 校验一个动作；不通过即抛 {@link SandboxViolationException}。 */
  void enforce(SandboxAction action);
}
