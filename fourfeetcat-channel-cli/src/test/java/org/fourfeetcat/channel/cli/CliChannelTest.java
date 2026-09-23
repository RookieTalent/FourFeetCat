package org.fourfeetcat.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 交互壳：读—转交—打印的契约，/quit 与空行两条分支，以及控制台输入的编码。 */
class CliChannelTest {

  private AgentService agentService;
  private SessionManager sessionManager;

  @BeforeEach
  void setUp() {
    agentService = mock(AgentService.class);
    sessionManager = mock(SessionManager.class);
    when(sessionManager.getOrCreate(anyString(), anyString(), anyString()))
        .thenReturn(new Session("cli:someone:default", "default", "cli", "someone"));
    when(agentService.process(any(), anyString())).thenReturn("答复");
  }

  @Test
  @DisplayName("每行输入都交给引擎_并把答复打到输出流")
  void run_eachLine_goesToEngineAndReplyIsPrinted() {
    String output = runWith("今天天气怎么样\n", StandardCharsets.UTF_8);

    verify(agentService).process(any(Session.class), eq("今天天气怎么样"));
    assertThat(output).contains("答复");
  }

  @Test
  @DisplayName("/quit 之后_不再读下一行、引擎不再被调用")
  void run_quit_stopsLoopWithoutFurtherEngineCalls() {
    String output = runWith("/quit\n今天天气怎么样\n", StandardCharsets.UTF_8);

    verify(agentService, never()).process(any(Session.class), anyString());
    assertThat(output).doesNotContain("答复");
  }

  @Test
  @DisplayName("空行_跳过、不交给引擎（也不产生空消息）")
  void run_blankLine_isSkipped() {
    runWith("\n   \n/quit\n", StandardCharsets.UTF_8);

    verify(agentService, never()).process(any(Session.class), anyString());
  }

  @Test
  @DisplayName("输入到底（EOF）_干净收场，不抛异常")
  void run_endOfInput_returnsQuietly() {
    runWith("你好\n", StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("中文控制台按本地编码（GBK）送进来的文本_交给引擎时不乱码")
  void run_decodesConsoleInputWithGivenCharset() {
    // 回归守卫：拿 UTF-8 去解 GBK 字节会解出替换字符，模型收到的就是乱码（实测踩过这个坑）
    runWith("你好\n/quit\n", Charset.forName("GBK"));

    verify(agentService).process(any(Session.class), eq("你好"));
  }

  @Test
  @DisplayName("输入编码取自平台本地编码_不是硬编UTF-8")
  void stdinCharset_isPlatformEncodingNotHardcodedUtf8() {
    assumeTrue(
        !"UTF-8".equalsIgnoreCase(System.getProperty("native.encoding", "UTF-8")),
        "平台本地编码本就是 UTF-8，本断言无区分度");

    assertThat(CliChannel.stdinCharset()).isNotEqualTo(StandardCharsets.UTF_8);
  }

  private String runWith(String input, Charset charset) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    new CliChannel(
            agentService,
            sessionManager,
            new ByteArrayInputStream(input.getBytes(charset)),
            new PrintStream(captured, true, StandardCharsets.UTF_8),
            charset)
        .run("default");
    return captured.toString(StandardCharsets.UTF_8);
  }
}
