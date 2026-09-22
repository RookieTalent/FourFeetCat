package org.fourfeetcat.cli;

import java.io.PrintWriter;
import picocli.CommandLine;

/**
 * 统一报错出口：非零退出码 + **一行能被搜索的错因**。
 *
 * <p>命令行用户的默认体验不该是 40 行 Java 栈——"key 没配"、"Agent 不存在"、"模型端 401" 都是预期内的运行期错误，给 消息就够。真需要栈的时候（自己排查）加
 * {@code -Dfourfeetcat.debug=true}，别让每次失败都糊一屏。
 *
 * <p>异常链只取一层原因：多层嵌套里第一层因通常就是真正的原因，全展开反而把关键信息淹掉。
 */
class CommandErrors implements CommandLine.IExecutionExceptionHandler {

  private static final String DEBUG_FLAG = "fourfeetcat.debug";

  @SuppressWarnings("PMD.CloseResource") // err 是 Picocli 自己的 writer，收摊不归本条命令
  @Override
  public int handleExecutionException(
      Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
    PrintWriter err = commandLine.getErr();
    err.println("错误: " + messageOf(ex));
    if (Boolean.getBoolean(DEBUG_FLAG)) {
      ex.printStackTrace(err);
    } else {
      err.println("（加 -D" + DEBUG_FLAG + "=true 可打印完整栈）");
    }
    err.flush();
    return commandLine.getCommandSpec().exitCodeOnExecutionException();
  }

  private static String messageOf(Throwable ex) {
    StringBuilder text = new StringBuilder(textOf(ex));
    Throwable cause = ex.getCause();
    if (cause != null) {
      text.append(" ← ").append(textOf(cause));
    }
    return text.toString();
  }

  private static String textOf(Throwable ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}
