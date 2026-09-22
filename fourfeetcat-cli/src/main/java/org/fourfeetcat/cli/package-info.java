/**
 * 命令行入口：Picocli 主入口（{@code FourFeetCatCli}）与 12 个子命令。
 *
 * <p>轻重命令分流：不跑引擎的命令（{@code init}、{@code profile *}、{@code provider list}、{@code tool list}）直接走 标准文件
 * API、不打开容器；要跑引擎或读库的命令（{@code chat}、{@code serve}、{@code gateway}、{@code status}、 {@code session
 * list}）才经根命令的引擎工厂按需启动容器。
 *
 * <p><b>关于 {@code @SuppressWarnings("PMD.SystemPrintln")}</b>：本包的输出本来就是它的产品——命令行工具的职责就是往
 * stdout/stderr 打字，PMD 那条规则针对的是库里到处乱打日志，在这里不适用。因此逐个命令类显式声明豁免，而不是把整个包 排除在 PMD 之外（那会连 {@code
 * CloseResource} 这类真问题一起漏掉）。
 */
package org.fourfeetcat.cli;
