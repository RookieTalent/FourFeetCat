package org.fourfeetcat.web.session;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.fourfeetcat.core.profile.Profile;
import org.fourfeetcat.core.profile.ProfileRegistry;
import org.fourfeetcat.core.react.AgentService;
import org.fourfeetcat.core.session.Session;
import org.fourfeetcat.core.session.SessionManager;
import org.fourfeetcat.storage.SessionEntity;
import org.fourfeetcat.storage.SessionRepository;
import org.fourfeetcat.web.api.ApiResponse;
import org.fourfeetcat.web.api.InvalidRequestException;
import org.fourfeetcat.web.api.MessageRequest;
import org.fourfeetcat.web.api.ReplyResponse;
import org.fourfeetcat.web.api.ResourceNotFoundException;
import org.fourfeetcat.web.api.SessionNotFoundException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理端点（第26节课件）：建会话、发消息、查详情、归档、列会话。
 *
 * <p><b>这个类只做三件事</b>——参数校验、响应包装、错误处理。实际逻辑一律委托出去：发消息走的是与命令行完全同一个入口 （{@link
 * AgentService#process}），会话读写走既有的会话管理器与仓储。之所以把这条写成硬规矩，是因为"顺手在 Controller 里加点逻辑"
 * 是这个架构里最容易被悄悄破坏的一处：一旦绕过处理入口，Web 触发的这一轮就开始与 CLI 那一轮产生行为差异，而两边都不报错。
 *
 * <p><b>渠道名只此一处</b>（{@link #WEB_CHANNEL}）：会话身份是"渠道 + 用户 + Agent"三元组拼出来的，渠道名在两处各写一份、其中一处改了，
 * 同一个人就会出现两条互不相认的历史——与第18节反复强调的"标识只在存储实现里拼"是同一类风险。
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

  /** Web 入口的渠道名。与命令行的 {@code cli}、定时的 {@code scheduler} 并列，是会话身份的第三个来源；全模块只此一处。 */
  public static final String WEB_CHANNEL = "web";

  /** 未指定用户时的缺省身份——建会话的请求体允许为空（课件"怎么做"的第一个 curl 就不带体）。 */
  private static final String DEFAULT_USER = "anonymous";

  /** 归档状态字面量：与第18节落库口径逐字一致（改这里等于改库里的值）。 */
  private static final String STATUS_ARCHIVED = "archived";

  /** 历史返回上限：只回最近 100 条（技术方案 §7.4 定死的防呆值）。 */
  private static final int MAX_HISTORY_MESSAGES = 100;

  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final SessionRepository sessionRepository;
  private final ProfileRegistry profileRegistry;

  public SessionApiController(
      AgentService agentService,
      SessionManager sessionManager,
      SessionRepository sessionRepository,
      ProfileRegistry profileRegistry) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.sessionRepository = sessionRepository;
    this.profileRegistry = profileRegistry;
  }

  /**
   * 创建（或复用）一条 Web 渠道会话。请求体可空。
   *
   * <p>幂等来自既有会话管理器的语义：同一三元组历次取用都是同一条会话，因此重复调用只是刷新最后活跃时间；首次才真正落一行。
   */
  @PostMapping
  public ApiResponse<SessionView> create(
      @RequestBody(required = false) CreateSessionRequest request) {
    String agent =
        request == null || isBlank(request.agent()) ? firstAgentName() : request.agent().trim();
    requireAgentLoaded(agent);
    String user = request == null || isBlank(request.user()) ? DEFAULT_USER : request.user().trim();

    Session session = sessionManager.getOrCreate(WEB_CHANNEL, user, agent);
    // 只在会话尚不存在时落盘：复用既有会话时不动它的最后活跃时间（建会话不该被算作"聊过一轮"）
    if (sessionRepository.findById(session.getId()).isEmpty()) {
      sessionManager.save(session);
    }
    return ApiResponse.ok(SessionView.from(requireEntity(session.getId())));
  }

  /**
   * 向指定会话发消息，触发一轮完整处理。这是承载 ReAct 循环的那个端点。
   *
   * <p>已归档的会话明确拒绝（400），**不静默复活**：归档是用户显式表达"这条不聊了"，把它悄悄改回活跃，等于让归档动作失去意义。
   */
  @PostMapping("/{id}/messages")
  public ApiResponse<ReplyResponse> send(
      @PathVariable String id, @RequestBody(required = false) MessageRequest request) {
    MessageRequest body = request == null ? new MessageRequest(null) : request;
    String content = body.requireContent();

    SessionEntity entity = requireEntity(id);
    if (STATUS_ARCHIVED.equals(entity.getStatus())) {
      throw new InvalidRequestException("会话已归档，请新建会话后再发消息: " + id);
    }
    Session session = sessionManager.get(id).orElseThrow(() -> new SessionNotFoundException(id));
    return ApiResponse.ok(new ReplyResponse(agentService.process(session, content)));
  }

  /** 查会话详情与历史：归档会话照常可查（归档只是软标记，数据一行不删）。 */
  @GetMapping("/{id}")
  public ApiResponse<SessionDetailView> detail(@PathVariable String id) {
    SessionEntity entity = requireEntity(id);
    List<MessageView> all =
        sessionManager
            .get(id)
            .orElseThrow(() -> new SessionNotFoundException(id))
            .getMessages()
            .stream()
            .map(SessionApiController::toMessageView)
            .toList();
    boolean truncated = all.size() > MAX_HISTORY_MESSAGES;
    List<MessageView> recent =
        truncated ? all.subList(all.size() - MAX_HISTORY_MESSAGES, all.size()) : all;
    return ApiResponse.ok(new SessionDetailView(SessionView.from(entity), recent, truncated));
  }

  /** 归档会话：置状态与归档时刻，**幂等**且不覆盖首次归档时刻。 */
  @DeleteMapping("/{id}")
  public ApiResponse<SessionView> archive(@PathVariable String id) {
    SessionEntity entity = requireEntity(id);
    if (!STATUS_ARCHIVED.equals(entity.getStatus())) {
      entity.setStatus(STATUS_ARCHIVED);
      entity.setArchivedAt(Instant.now().toString());
      sessionRepository.save(entity);
    }
    return ApiResponse.ok(SessionView.from(entity));
  }

  /** 列举全部会话（含已归档），最近活跃的在前。不分页、不过滤——"列表 = 全部会话"保持为真。 */
  @GetMapping
  public ApiResponse<List<SessionView>> list() {
    List<SessionView> views =
        sessionRepository.findAllByOrderByLastActiveAtDesc().stream()
            .map(SessionView::from)
            .toList();
    return ApiResponse.ok(views);
  }

  /** 取会话实体，不存在即 404。所有会话端点共用这一处存在性判定。 */
  private SessionEntity requireEntity(String sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new SessionNotFoundException(sessionId));
  }

  /**
   * Agent 名必须已加载，否则 404。
   *
   * <p>这条前置检查是为了与"服务不可用"消歧：处理入口在"会话归属的 Agent 未注册"时抛的状态异常按 503 映射，而**名字写错**是客户端的问题， 必须在进处理入口之前就按 404
   * 拒掉——否则调用方会去重试一个永远不会成功的请求。
   */
  private void requireAgentLoaded(String agent) {
    if (profileRegistry.find(agent).isEmpty()) {
      throw new ResourceNotFoundException("Agent 未加载: " + agent);
    }
  }

  /** 请求体没给 Agent 时取注册表里的第一个：顺序 = 加载顺序（确定性），同一份配置每次启动取到同一个。 */
  private String firstAgentName() {
    return profileRegistry.all().stream()
        .map(Profile::name)
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("一个 Agent 都没加载，无法建会话"));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** 消息 → 对外视图：角色取枚举名的小写形态，工具结果消息逐条展开成"工具名: 结果"。 */
  private static MessageView toMessageView(Message message) {
    String role = message.getMessageType().name().toLowerCase(Locale.ROOT);
    if (message instanceof ToolResponseMessage tool) {
      // 工具结果消息没有正文文本字段，它承载的是若干条工具响应——按模型看到的形状逐条铺开
      StringBuilder rendered = new StringBuilder();
      tool.getResponses()
          .forEach(
              response -> {
                if (rendered.length() > 0) {
                  rendered.append('\n');
                }
                rendered.append(response.name()).append(": ").append(response.responseData());
              });
      return new MessageView(role, rendered.toString());
    }
    String text = message.getText();
    return new MessageView(role, text == null ? "" : text);
  }

  /** 建会话请求体：整体可空，两个字段均可空（缺省口径见 contracts/http-api.md）。 */
  public record CreateSessionRequest(String agent, String user) {}

  /** 一条消息的对外视图。 */
  public record MessageView(String role, String content) {}

  /** 会话详情 = 会话视图 + 最近若干条消息 + 是否因超上限被截断。 */
  public record SessionDetailView(
      SessionView session, List<MessageView> messages, boolean truncated) {

    public SessionDetailView {
      // 防御性不可变拷贝：记录里直接持有外部传入的可变列表，等于把这个视图的内部结构暴露给调用方
      messages = messages == null ? List.of() : List.copyOf(messages);
    }
  }
}
