package org.fourfeetcat.tool.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ⚠️ <b>临时装配，不做任何校验——由沙箱节替换。</b>
 *
 * <p>存在理由只有一个：沙箱的规则本体（白名单配置与校验算法）归沙箱节交付，而在那之前，本节已注册的工具需要一个 {@code Sandbox} 实例才能跑通。没有它，"Agent
 * 真的能动手"这条验收在沙箱节之前落不了地。
 *
 * <p><b>这是有意为之的临时状态，不是可用状态。</b>它让所有涉外动作直接通过，因此**不得**据此运行不可信代码、**不得**对外 做多租户。沙箱节落地时，把这个 Bean
 * 换成白名单实现即可——{@link Sandbox} 的接口与所有调用方一行不改。
 *
 * <p>构造时打一条 WARN：一个"什么都不拦"的沙箱在日志里必须显眼，不能让它在生产环境里静悄悄地待着。
 */
public class PermissiveSandbox implements Sandbox {

  private static final Logger log = LoggerFactory.getLogger(PermissiveSandbox.class);

  public PermissiveSandbox() {
    log.warn("沙箱以临时装配启动：所有涉外动作不做校验（规则本体归沙箱节，届时替换）");
  }

  @Override
  public void enforce(SandboxAction action) {
    // 有意为空：临时装配的全部行为就是"放行"。见类注释——由沙箱节替换。
  }
}
