package org.fourfeetcat.web.agent;

import java.util.UUID;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.web.api.ApiResponse;
import org.fourfeetcat.web.api.MessageRequest;
import org.fourfeetcat.web.api.ReplyResponse;
import org.fourfeetcat.web.api.ResourceNotFoundException;
import org.fourfeetcat.web.session.SessionApiController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 无状态调用端点（第26节课件）：一次性调用一个 Agent、拿回结果就走。
 *
 * <p>与"会话内发消息"走的是同一个处理入口，区别只在**会话身份的来源**：这条路径每次调用都用一个新的唯一身份， 因此上一次调用的历史不会被回放给这一次。若图省事让同一 Agent
 * 的所有调用共用一条会话，历次无关调用的上下文会互相污染—— "无状态"就成了名不副实的说法（协作方会以为每次调用都是干净的）。
 *
 * <p>代价是每次调用落一条会话行，管理台的会话列表会如实列出它们（审计优先，不做隐藏）。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ProfileRegistry profileRegistry;

  public AgentApiController(
      AgentService agentService, SessionManager sessionManager, ProfileRegistry profileRegistry) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
  }

  /**
   * 对指定 Agent 发起一次调用；调用方不需要提供或持有会话标识。
   *
   * <p>Agent 名未加载即 404（在进处理入口之前判掉，见 {@link SessionApiController} 同款消歧说明）。
   */
  @PostMapping("/{name}/invoke")
  public ApiResponse<ReplyResponse> invoke(
      @PathVariable String name, @RequestBody(required = false) MessageRequest request) {
    MessageRequest body = request == null ? new MessageRequest(null) : request;
    String content = body.requireContent();

    if (profileRegistry.find(name).isEmpty()) {
      throw new ResourceNotFoundException("Agent 未加载: " + name);
    }
    // 唯一用户标识：32 位十六进制（去横线）。会话标识仍由会话管理器按三元组拼，这里只提供一个分量。
    String uniqueUser = UUID.randomUUID().toString().replace("-", "");
    Session session =
        sessionManager.getOrCreate(SessionApiController.WEB_CHANNEL, uniqueUser, name);
    return ApiResponse.ok(new ReplyResponse(agentService.process(session, content)));
  }
}
