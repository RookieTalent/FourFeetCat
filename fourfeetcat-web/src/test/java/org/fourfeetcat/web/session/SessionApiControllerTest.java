package org.fourfeetcat.web.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.storage.SessionEntity;
import org.fourfeetcat.storage.SessionRepository;
import org.fourfeetcat.web.agent.AgentApiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 会话端点与无状态调用端点的切片测试（课件第26节 harness）。
 *
 * <p>切片只起 MVC 层、把处理入口与会话层全部 mock 掉：不碰模型、不碰库，跑得飞快。切片同时装配两个 Controller
 * （会话与无状态调用）——SC-011"每次调用独立会话身份"的守点落在后者，而课件指定的测试类清单里没有单独给它一个文件。
 *
 * <p>最值钱的一条是"处理入口恰被调用一次"：它同时挡住两种反向的错——端点自己夹带业务逻辑（多调）与端点没接到同一个人推入口（不调）。
 *
 * <p>另外三条依赖错误映射的守点（超 32KB → 400、会话不存在 → 404、已归档拒发 → 400）随映射一起落地，见 {@code GlobalExceptionHandler}
 * 扩展那一批。
 */
@WebMvcTest({SessionApiController.class, AgentApiController.class})
class SessionApiControllerTest {

  private static final String SESSION_ID = "web:wang:ops-agent";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AgentService agentService;

  @MockitoBean private SessionManager sessionManager;

  @MockitoBean private SessionRepository sessionRepository;

  @MockitoBean private ProfileRegistry profileRegistry;

  @Test
  @DisplayName("正常请求_处理入口恰被调用一次（端点没夹带私货、也没绕过同一个人推入口）")
  void sendMessage_callsProcessingEntryExactlyOnce() throws Exception {
    givenActiveSession();
    when(agentService.process(any(), anyString())).thenReturn("今天北京晴，穿薄外套");

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"今天北京天气怎么样\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.reply").value("今天北京晴，穿薄外套"));

    verify(agentService, times(1)).process(any(), anyString());
  }

  @Test
  @DisplayName("列会话_按最后活跃时间倒序（最近的在最前）")
  void listSessions_ordersByLastActiveDesc() throws Exception {
    when(sessionRepository.findAllByOrderByLastActiveAtDesc())
        .thenReturn(List.of(entity("web:newer:ops-agent"), entity("web:older:ops-agent")));

    mockMvc
        .perform(get("/api/v1/sessions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].sessionId").value("web:newer:ops-agent"))
        .andExpect(jsonPath("$.data[1].sessionId").value("web:older:ops-agent"));
  }

  @Test
  @DisplayName("连续两次一次性调用_拿到两条不同会话（无状态名副其实）")
  void invoke_createsDistinctSessionPerCall() throws Exception {
    when(profileRegistry.find("ops-agent")).thenReturn(Optional.of(profileStub()));
    when(agentService.process(any(), anyString())).thenReturn("ok");
    when(sessionManager.getOrCreate(
            eq(SessionApiController.WEB_CHANNEL), anyString(), eq("ops-agent")))
        .thenAnswer(
            invocation ->
                new Session(
                    "web:" + invocation.getArgument(1) + ":ops-agent",
                    "ops-agent",
                    SessionApiController.WEB_CHANNEL,
                    invocation.getArgument(1)));

    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/api/v1/agents/{name}/invoke", "ops-agent")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"一句话介绍你自己\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.reply").value("ok"))
          .andExpect(jsonPath("$.data.sessionId").doesNotExist());
    }

    ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
    verify(sessionManager, times(2))
        .getOrCreate(eq(SessionApiController.WEB_CHANNEL), users.capture(), eq("ops-agent"));
    assertThat(users.getAllValues()).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("消息超32KB_返回400")
  void sendMessage_over32Kb_returnsBadRequest() throws Exception {
    String tooLong = "字".repeat(32 * 1024 + 1);

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    verify(agentService, never()).process(any(), anyString());
  }

  @Test
  @DisplayName("会话不存在_返回404（且不进处理入口——名字写错不该白跑一轮）")
  void sendMessage_unknownSession_returnsNotFound() throws Exception {
    when(sessionRepository.findById("web:ghost:ops-agent")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", "web:ghost:ops-agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"你好\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    verify(agentService, never()).process(any(), anyString());
  }

  @Test
  @DisplayName("向已归档会话发消息_明确拒绝不静默复活")
  void sendMessage_toArchivedSession_returnsBadRequest() throws Exception {
    SessionEntity archived = entity(SESSION_ID);
    archived.setStatus("archived");
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(archived));

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"你好\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    verify(agentService, never()).process(any(), anyString());
  }

  private void givenActiveSession() {
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(entity(SESSION_ID)));
    when(sessionManager.get(SESSION_ID))
        .thenReturn(Optional.of(new Session(SESSION_ID, "ops-agent", "web", "wang")));
  }

  /** 落库实体替身：只填端点会读到的列（状态、时间戳、身份三分量）。 */
  private static SessionEntity entity(String sessionId) {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(sessionId);
    entity.setProfileName("ops-agent");
    entity.setChannel(SessionApiController.WEB_CHANNEL);
    entity.setUserId("wang");
    entity.setMessagesJson("[]");
    entity.setStatus("active");
    entity.setCreatedAt("2026-09-28T10:00:00Z");
    entity.setLastActiveAt("2026-09-28T10:00:00Z");
    return entity;
  }

  /** Agent 配置替身：注册表是 mock 的，这里只需要一个能通过编译的实例。 */
  private static Profile profileStub() {
    return new Profile(
        "ops-agent", "运维助手", null, null, null, null, null, null, null, null, null, null);
  }
}
