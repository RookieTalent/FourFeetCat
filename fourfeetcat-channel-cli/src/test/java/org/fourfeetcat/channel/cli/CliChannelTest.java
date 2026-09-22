package org.fourfeetcat.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.nio.charset.StandardCharsets;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 交互壳：读—转交—打印的契约，以及 /quit 与空行两条分支（本节唯一有分支的新逻辑）。 */
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
    String output = runWith("今天天气怎么样\n");

    verify(agentService).process(any(Session.class), eq("今天天气怎么样"));
    assertThat(output).contains("答复");
  }

  @Test
  @DisplayName("/quit 之后_不再读下一行、引擎不再被调用")
  void run_quit_stopsLoopWithoutFurtherEngineCalls() {
    String output = runWith("/quit\n今天天气怎么样\n");

    verify(agentService, never()).process(any(Session.class), anyString());
    assertThat(output).doesNotContain("答复");
  }

  @Test
  @DisplayName("空行_跳过、不交给引擎（也不产生空消息）")
  void run_blankLine_isSkipped() {
    runWith("\n   \n/quit\n");

    verify(agentService, never()).process(any(Session.class), anyString());
  }

  @Test
  @DisplayName("输入到底（EOF）_干净收场，不抛异常")
  void run_endOfInput_returnsQuietly() {
    runWith("你好\n");
  }

  private String runWith(String input) {
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    new CliChannel(
            agentService,
            sessionManager,
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(captured, true, StandardCharsets.UTF_8))
        .run("default");
    return captured.toString(StandardCharsets.UTF_8);
  }
}
