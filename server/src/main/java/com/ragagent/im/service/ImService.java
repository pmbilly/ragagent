package com.ragagent.im.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.agent.management.domain.CustomAgentEntity;
import com.ragagent.agent.management.service.CustomAgentService;
import com.ragagent.common.context.TenantContext;
import com.ragagent.common.llm.ResponseType;
import com.ragagent.im.domain.ChannelSessionEntity;
import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.mapper.ChannelSessionMapper;
import com.ragagent.im.mapper.ImChannelMapper;
import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.runtime.AdapterInterfaces.Adapter;
import com.ragagent.im.runtime.AdapterInterfaces.StreamSender;
import com.ragagent.im.runtime.Commands;
import com.ragagent.im.runtime.Commands.CommandContext;
import com.ragagent.im.runtime.Commands.CommandRegistry;
import com.ragagent.im.runtime.Commands.CommandResult;
import com.ragagent.im.runtime.ImCommandSet;
import com.ragagent.im.runtime.ImFormat;
import com.ragagent.im.runtime.ImRedisStore;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.runtime.QaQueue;
import com.ragagent.im.runtime.ReplyMessage;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.session.domain.Session;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.SessionAgentQaService;
import com.ragagent.session.service.SessionKnowledgeQaService;
import com.ragagent.session.service.SessionService;
import com.ragagent.stream.StreamEvent;
import com.ragagent.stream.StreamManager;
import com.ragagent.stream.StreamStopWatcher;

/**
 * IM 执行体核心。
 *
 * <h2>Redis 面（内存形态先行）</h2>
 * 以下能力当前为进程内实现，多实例部署换成 Redis 实现即可
 * （键名常量届时随 Redis 实现一并引入）：
 * <ul>
 *   <li>去重：进程内 map + TTL 清理；</li>
 *   <li>WS leader 选举：单实例恒 leader——不做 Redis SETNX 竞选；</li>
 *   <li>跨实例 /stop 标记与 inflight 映射：进程内 map；</li>
 *   <li>渠道配置 pub/sub：本进程内直接失效。</li>
 * </ul>
 */
@Service
public class ImService {

    private static final Logger log = LoggerFactory.getLogger(ImService.class);
    static final ObjectMapper JSON = new ObjectMapper();

    /** 执行前 /stop 标记的 TTL（Go {@code stopMarkerTTL}）。 */
    private static final int STOP_MARKER_TTL_SECONDS = 30;
    /** 跨实例在途映射的 TTL（Go 的 storeInflightMapping 硬编码 10 分钟）。 */
    private static final int INFLIGHT_TTL_SECONDS = 600;

    final ImChannelMapper channels;
    final ChannelSessionMapper channelSessions;
    final SessionService sessionService;
    final MessageService messageService;
    private final CustomAgentService agentService;
    final SessionKnowledgeQaService knowledgeQaService;
    final SessionAgentQaService agentQaService;
    final com.ragagent.storage.support.Resolver storageResolver;

    // ── 调谐参数 ─────────────────────────────────────────────────────────
    private final int rateLimitWindowSec;
    private final int rateLimitMax;
    /** IM 的 Redis 面（stop marker / inflight 映射）；未启用为 null（单实例形态）。 */
    private final ImRedisStore redisStore;

    final ImQaRunner qaRunner;
    final ImStreamPipeline streamPipeline;
    final ImOutboundFormatter outboundFormatter;
    final ImSessionResolver sessionResolver;
    final ImQaRequests qaRequests;
    final ImAttachmentPreparer attachmentPreparer;

    private final CommandRegistry cmdRegistry = new CommandRegistry();
    private final QaQueue qaQueue;

    /** 运行中的渠道（channelID → 状态）。 */
    private final Map<String, ChannelState> channelStates = new ConcurrentHashMap<>();
    /** 去重（进程内分支）：messageID → epoch 秒。 */
    private final Map<String, Long> processedMsgs = new ConcurrentHashMap<>();
    /** 在途请求（/stop 的本地取消面）。 */
    final Map<String, InflightEntry> inflight = new ConcurrentHashMap<>();
    /** 跨实例 /stop 标记的本地等价物。 */
    private final Map<String, Long> stopMarkers = new ConcurrentHashMap<>();

    /** 运行中的渠道状态。 */
    record ChannelState(ImChannelEntity channel, Adapter adapter,
            AtomicReference<Runnable> adapterStop) {
    }

    static final class InflightEntry {
        /** 队列层请求（/stop 取消与引擎取消探针共享同一取消标志）。 */
        final QaQueue.QaRequest queueReq;
        final java.util.function.BooleanSupplier cancel;
        volatile String sessionId = "";
        volatile String assistantMessageId = "";

        InflightEntry(QaQueue.QaRequest queueReq, java.util.function.BooleanSupplier cancel) {
            this.queueReq = queueReq;
            this.cancel = cancel;
        }
    }

    public ImService(ImChannelMapper channels, ChannelSessionMapper channelSessions,
            SessionService sessionService, MessageService messageService,
            CustomAgentService agentService,
            SessionKnowledgeQaService knowledgeQaService, SessionAgentQaService agentQaService,
            java.util.Optional<com.ragagent.storage.support.Resolver> storageResolver,
            ObjectProvider<StringRedisTemplate> redisTemplates,
            ObjectProvider<DocReaderClient> docReaders,
            @Value("${im.workers:5}") int workers,
            @Value("${im.max-queue:50}") int maxQueue,
            @Value("${im.max-per-user:3}") int maxPerUser,
            @Value("${im.redis-enabled:false}") boolean redisEnabled,
            @Value("${im.global-max-workers:0}") int globalMaxWorkers,
            @Value("${im.rate-limit-window-sec:60}") int rateLimitWindowSec,
            @Value("${im.rate-limit-max:10}") int rateLimitMax) {
        this.channels = channels;
        this.channelSessions = channelSessions;
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.agentService = agentService;
        this.knowledgeQaService = knowledgeQaService;
        this.agentQaService = agentQaService;
        this.storageResolver = storageResolver.orElse(null);
        this.rateLimitWindowSec = rateLimitWindowSec;
        this.rateLimitMax = rateLimitMax;
        this.streamPipeline = new ImStreamPipeline(this);
        this.outboundFormatter = new ImOutboundFormatter(this);
        this.sessionResolver = new ImSessionResolver(this);
        this.qaRequests = new ImQaRequests(this);
        this.qaRunner = new ImQaRunner(this);
        this.attachmentPreparer = new ImAttachmentPreparer(docReaders);
        ImCommandSet.registerDefaults(this.cmdRegistry, kbLister(), knowledgeSearcher());
        ImRedisStore store = null;
        if (redisEnabled) {
            StringRedisTemplate template = redisTemplates.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException(
                        "im.redis-enabled=true but no Redis connection is configured");
            }
            // 启动即验：配置成 Redis 却连不上时不静默退化（同 StreamManagerConfig 的口径）
            try {
                template.getConnectionFactory().getConnection().ping();
            } catch (RuntimeException e) {
                throw new IllegalStateException("failed to connect to Redis: " + e.getMessage(), e);
            }
            store = new ImRedisStore(template);
        }
        this.redisStore = store;
        this.qaQueue = new QaQueue(workers, maxQueue, maxPerUser, task -> {
            QaTask t = (QaTask) task.attach();
            qaRunner.executeQARequest(t);
        }, store, globalMaxWorkers, null);
        this.qaQueue.start();
    }

    // ── 命令的依赖面（cmd_info/cmd_search 的 KB/检索读取） ────────────────

    private ImCommandSet.KnowledgeBaseLister kbLister() {
        return new ImCommandSet.KnowledgeBaseLister() {
            @Override
            public List<KbView> listKnowledgeBases() {
                return List.of();
            }

            @Override
            public List<KbView> listKnowledgeBasesByTenantId(long tenantId) {
                return List.of();
            }
        };
    }

    private ImCommandSet.KnowledgeSearcher knowledgeSearcher() {
        return (kbIds, knowledgeIds, documentIds, query) -> List.of();
    }

    // ── 渠道生命周期（adapter 注册表） ─────────────────────────────────────

    /** 渠道启动时必须注册的平台工厂。 */
    public interface AdapterFactory {
        /** 返回适配器与停止函数（长连接的拆除柄）。webhook 型适配器 stop 可为 null。 */
        AdapterRegistration create(ImChannelEntity channel,
                java.util.function.BiConsumer<IncomingMessage, String> msgHandler);
    }

    public record AdapterRegistration(Adapter adapter, Runnable stop) {
    }

    private final Map<String, AdapterFactory> adapterFactories = new ConcurrentHashMap<>();

    /** 注册平台工厂。 */
    public void registerAdapterFactory(String platform, AdapterFactory factory) {
        adapterFactories.put(platform, factory);
    }

    /** 渠道行 → 就绪的适配器；运行态缺失则先尝试启动。 */
    public Adapter adapterFor(ImChannelEntity channel) {
        if (channel == null || !channel.isEnabled()) {
            return null;
        }
        AdapterFactory factory = adapterFactories.get(channel.getPlatform());
        if (factory == null) {
            return null;
        }
        ChannelState state = channelStates.get(channel.getId());
        if (state == null) {
            startChannel(channel);
            state = channelStates.get(channel.getId());
        }
        return state == null ? null : state.adapter();
    }

    /** 启动渠道：经工厂建适配器并进入运行态。 */
    public synchronized void startChannel(ImChannelEntity channel) {
        AdapterFactory factory = adapterFactories.get(channel.getPlatform());
        if (factory == null) {
            log.warn("[IM] no adapter factory for platform {} (channel {})",
                    channel.getPlatform(), channel.getId());
            return;
        }
        AtomicReference<Runnable> stopRef = new AtomicReference<>();
        AdapterRegistration reg;
        try {
            reg = factory.create(channel, (msg, chId) -> handleMessage(msg, chId));
        } catch (RuntimeException e) {
            // 工厂失败（凭据不全 / 出站校验不过 / 平台未实现该模式）时渠道起不来，
            // 适配器不入运行态——回调路径因此走 "adapter not active"
            // （503 "channel not available"），而不是把异常冒成 500。
            log.warn("[IM] Channel start failed: id={} platform={} mode={} err={}",
                    channel.getId(), channel.getPlatform(), channel.getMode(), e.toString());
            return;
        }
        stopRef.set(reg.stop());
        channelStates.put(channel.getId(), new ChannelState(channel, reg.adapter(), stopRef));
        log.info("[IM] Channel started: id={} platform={} mode={}", channel.getId(),
                channel.getPlatform(), channel.getMode());
    }

    /** 停止渠道并拆除适配器。 */
    public synchronized void stopChannel(String channelId) {
        ChannelState cs = channelStates.remove(channelId);
        if (cs != null && cs.adapterStop() != null && cs.adapterStop().get() != null) {
            cs.adapterStop().get().run();
        }
    }

    /**
     * 软删该 agent
     * 的全部 IM 渠道并停止运行中的适配器——概览列表与运行中的适配器不得比 agent
     * 活得更久（自定义 agent 删除时调用）。
     *
     * <p>跨实例配置广播（{@code publishChannelConfigChange}）在单实例装配下无对应面。</p>
     */
    public void deleteChannelsByAgent(String agentId, long tenantId) {
        java.util.List<ImChannelEntity> found = channels.listByAgent(agentId, tenantId);
        if (found.isEmpty()) {
            return;
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        for (ImChannelEntity ch : found) {
            channels.softDelete(ch.getId(), tenantId, now);
            stopChannel(ch.getId());
        }
    }

    /** 启动时拉起全部 enabled 渠道。 */
    public void loadAndStartChannels() {
        for (ImChannelEntity ch : channels.listEnabled()) {
            startChannel(ch);
        }
    }

    /**
     * 应用就绪后从库拉起全部 enabled 渠道（否则重启后渠道全部沉默）。失败只 WARN，不阻塞启动。
     */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startChannelsOnReady() {
        try {
            loadAndStartChannels();
        } catch (RuntimeException e) {
            log.warn("[IM] Failed to load channels from database: {}", e.getMessage());
        }
    }

    /**
     * 停机时停 QA 队列与全部运行中的渠道适配器。
     */
    @jakarta.annotation.PreDestroy
    public void stop() {
        try {
            qaQueue.stop();
        } catch (RuntimeException e) {
            log.warn("[IM] qa queue stop failed: {}", e.getMessage());
        }
        for (String id : new java.util.ArrayList<>(channelStates.keySet())) {
            try {
                stopChannel(id);
            } catch (RuntimeException e) {
                log.warn("[IM] channel {} stop failed: {}", id, e.getMessage());
            }
        }
    }

    private long channelTenantIdOrThrow(String channelId) {
        ChannelState st = channelStates.get(channelId);
        if (st != null) {
            return st.channel().getTenantId();
        }
        ImChannelEntity ch = channels.getById(channelId);
        if (ch == null) {
            throw new IllegalStateException("channel not found: " + channelId);
        }
        return ch.getTenantId();
    }

    // ── 消息入口 ─────────────────────────────────────────────────────────

    /** 同一 messageID 只处理一次（进程内 map）。 */
    boolean isDuplicate(String messageId) {
        long now = System.currentTimeMillis();
        Long prev = processedMsgs.putIfAbsent(messageId, now);
        if (prev != null) {
            return true;
        }
        if (processedMsgs.size() > 10_000) {
            processedMsgs.entrySet().removeIf(e -> now - e.getValue() > 300_000);
        }
        return false;
    }

    /**
     * IM 消息总入口：去重 → 限长 → 适配器解析 → 限流 → 空消息提示 → 会话解析 →
     * 命令分派 → QA 排队。全程绑定渠道租户的合成身份
     * （"system-<tenantID>" 合成用户 + viewer 最小权限——
     * 组织共享 KB 的解析要求非空 UserID）。
     */
    public void handleMessage(IncomingMessage msg, String channelId) {
        com.ragagent.event.TenantContextSnapshot previous =
                com.ragagent.event.TenantContextSnapshot.capture();
        try {
            runHandleMessage(msg, channelId);
        } finally {
            TenantContext.clear();
            previous.replay();
        }
    }

    private void runHandleMessage(IncomingMessage msg, String channelId) {
        if (TenantContext.currentTenantId() == null) {
            // 回调线程无认证上下文——绑渠道租户（QA 管线的仓库查询要求租户在上下文）。
            long tid = channelTenantIdOrThrow(channelId);
            TenantContext.set(tid, new TenantContext.Principal(
                    TenantContext.PrincipalTypes.IM_USER, "system-" + tid),
                    "viewer", false, "system-" + tid, false);
        }
        if (msg.messageId != null && !msg.messageId.isEmpty() && isDuplicate(msg.messageId)) {
            log.info("[IM] Skipping duplicate message: {}", msg.messageId);
            return;
        }
        // 限长（按 code point 计）
        if (msg.content.codePointCount(0, msg.content.length()) > ImFormat.MAX_CONTENT_LENGTH) {
            log.warn("[IM] Message too long, truncating to {}", ImFormat.MAX_CONTENT_LENGTH);
            msg.content = msg.content.substring(0,
                    msg.content.offsetByCodePoints(0, ImFormat.MAX_CONTENT_LENGTH));
        }

        ChannelState state = channelStates.get(channelId);
        Adapter adapter = state == null ? null : state.adapter();
        if (adapter == null) {
            ImChannelEntity ch = channels.getById(channelId);
            if (ch == null) {
                throw new IllegalStateException("channel not found: " + channelId);
            }
            startChannel(ch);
            state = channelStates.get(channelId);
            adapter = state == null ? null : state.adapter();
            if (adapter == null) {
                throw new IllegalStateException(
                        "channel adapter not available after start: " + channelId);
            }
        }
        ImChannelEntity channel = state.channel();

        String threadId = ImTypes.SESSION_MODE_THREAD.equals(channel.getSessionMode())
                ? msg.threadId : "";

        boolean isCommand = cmdRegistry.isRegistered(msg.content);
        if (!isCommand && !rateLimitAllow(makeRateKey(channelId, msg.userId, msg.chatId, threadId))) {
            log.warn("[IM] Rate limited: channel={} user={} chat={}", channelId, msg.userId, msg.chatId);
            sendReplyQuiet(adapter, msg, new ReplyMessage("您的消息发送过于频繁，请稍后再试。", false, true));
            return;
        }

        if (ImTypes.MESSAGE_TYPE_FILE.equals(msg.messageType)
                || ImTypes.MESSAGE_TYPE_IMAGE.equals(msg.messageType)) {
            msg.content = ImFormat.fileMessageQAContent(msg);
        }
        var empty = emptyIncomingMessageReply(msg);
        if (empty.present()) {
            sendReplyQuiet(adapter, msg, new ReplyMessage(empty.hint(), false, true));
            return;
        }

        long tenantId = channel.getTenantId();
        String agentId = channel.getAgentId() == null ? "" : channel.getAgentId();

        // 会话解析（租户已在上下文；ChannelSession/Session 另带显式租户列）。
        ChannelSessionEntity channelSession =
                resolveSession(msg, tenantId, agentId, channelId, channel.getSessionMode());
        CustomAgentEntity customAgent = null;
        if (!agentId.isEmpty()) {
            try {
                var result = agentService.getAgentByID(agentId, "zh-CN");
                customAgent = result == null ? null : result.row();
            } catch (Exception e) {
                log.warn("[IM] Failed to get agent {}: {}, using default", agentId, e.getMessage());
            }
        }

        Commands.ParseResult cmd = cmdRegistry.parse(msg.content);
        if (cmd.matched()) {
            handleCommand(cmd.command(), cmd.args(), msg, adapter, channel, channelSession,
                    customAgent);
            return;
        }
        if (Commands.looksLikeCommand(msg.content)) {
            sendReplyQuiet(adapter, msg,
                    new ReplyMessage("未知指令，发送 `/help` 查看所有可用指令。", false, true));
            return;
        }

        Session session = sessionService.getSession(channelSession.getSessionId());
        if (session == null) {
            // 会话被删：回收陈旧映射并重解析
            channelSessions.softDelete(channelSession.getId(), OffsetDateTime.now());
            channelSession = resolveSession(msg, tenantId, agentId, channelId,
                    channel.getSessionMode());
            session = sessionService.getSession(channelSession.getSessionId());
            if (session == null) {
                throw new IllegalStateException(
                        "get session (retry): " + channelSession.getSessionId());
            }
        }

        String userKey = ImFormat.makeUserKey(channelId, msg.userId, msg.chatId, threadId);
        QaQueue.QaRequest req = new QaQueue.QaRequest(userKey, msg);
        QaTask task = new QaTask(msg, session, customAgent, adapter, channel, channelId, userKey);
        task.bind(req);
        req.attach(task);
        try {
            qaQueue.enqueue(req);
        } catch (QaQueue.RejectedException e) {
            log.warn("[IM] Queue rejected: user={} reason={}", msg.userId, e.getMessage());
            sendReplyQuiet(adapter, msg,
                    new ReplyMessage("当前排队较多，请稍后再试。", false, true));
        }
    }

    /** 限流的本地滑动窗口。 */
    private final Map<String, List<Long>> rateWindows = new ConcurrentHashMap<>();

    private boolean rateLimitAllow(String key) {
        long now = System.currentTimeMillis();
        long windowMs = rateLimitWindowSec * 1000L;
        List<Long> hits = rateWindows.computeIfAbsent(key, k -> java.util.Collections.synchronizedList(new ArrayList<>()));
        synchronized (hits) {
            hits.removeIf(t -> now - t > windowMs);
            if (hits.size() >= rateLimitMax) {
                return false;
            }
            hits.add(now);
            return true;
        }
    }

    private static String makeRateKey(String channelId, String userId, String chatId, String threadId) {
        return "rl:" + ImFormat.makeUserKey(channelId, userId, chatId, threadId);
    }

    /** 空消息的提示语（按原始消息类型给出指引）。 */
    record EmptyHint(String hint, boolean present) {
    }

    static EmptyHint emptyIncomingMessageReply(IncomingMessage msg) {
        if (msg == null || !msg.content.strip().isEmpty()) {
            return new EmptyHint("", false);
        }
        boolean hasAttachment = ImTypes.MESSAGE_TYPE_FILE.equals(msg.messageType)
                || ImTypes.MESSAGE_TYPE_IMAGE.equals(msg.messageType)
                || !msg.fileKey.strip().isEmpty();
        if (hasAttachment) {
            return new EmptyHint("", false);
        }
        String rawType = msg.extra == null ? ""
                : java.util.Optional.ofNullable(msg.extra.get("raw_msgtype")).orElse("").strip().toLowerCase();
        return switch (rawType) {
            case "audio" -> new EmptyHint("未能识别这条语音中的文字内容。请改用纯文本发送，或再说一遍。", true);
            case "video" -> new EmptyHint("暂不支持视频消息。请改用纯文本发送；图片或文件请单独发送。", true);
            default -> new EmptyHint("未能识别这条消息中的文字内容。请改用纯文本发送；图片或文件请单独发送。", true);
        };
    }

    ChannelSessionEntity resolveSession(IncomingMessage msg, long tenantId, String agentId,
            String imChannelId, String sessionMode) {
        return sessionResolver.resolveSession(msg, tenantId, agentId, imChannelId, sessionMode);
    }


    // ── 命令执行 ─────────────────────────────────────────────────────────

    void handleCommand(Commands.ImCommand cmd, List<String> args, IncomingMessage msg,
            Adapter adapter, ImChannelEntity channel, ChannelSessionEntity channelSession,
            CustomAgentEntity customAgent) {
        CommandContext cmdCtx = new CommandContext();
        cmdCtx.incoming = msg;
        cmdCtx.session = channelSession;
        cmdCtx.tenantId = channel.getTenantId();
        cmdCtx.agentName = customAgent == null ? "" : customAgent.getName();
        cmdCtx.customAgent = customAgent;
        cmdCtx.channelOutputMode = channel.getOutputMode() == null ? "" : channel.getOutputMode();

        CommandResult result;
        try {
            result = cmd.execute(cmdCtx, args);
        } catch (Exception e) {
            log.error("[IM] Command /{} error: {}", cmd.name(), e.getMessage(), e);
            sendReplyQuiet(adapter, msg,
                    new ReplyMessage("抱歉，执行指令时出现了异常，请稍后再试。", false, true));
            return;
        }

        if (result.action == Commands.ACTION_CLEAR && channelSession != null) {
            // 软删当前映射：下条 IM 消息解析出全新会话。
            channelSessions.softDelete(channelSession.getId(), OffsetDateTime.now());
        } else if (result.action == Commands.ACTION_STOP) {
            doLocalStop(channel, msg, channelSession);
        }

        boolean sent = false;
        if (!"full".equals(channel.getOutputMode()) && adapter instanceof StreamSender streamer) {
            try {
                sendStreamReply(msg, streamer, result.content);
                sent = true;
            } catch (Exception e) {
                log.warn("[IM] Stream reply for command /{} failed, falling back: {}",
                        cmd.name(), e.getMessage());
            }
        }
        if (!sent) {
            sendReplyQuiet(adapter, msg, new ReplyMessage(result.content, false, true));
        }
        log.info("[IM] Command /{} executed: channel={} user={} action={}",
                cmd.name(), channel.getId(), msg.userId, result.action);
    }

    private void doLocalStop(ImChannelEntity channel, IncomingMessage msg,
            ChannelSessionEntity channelSession) {
        String stopThreadId = ImTypes.SESSION_MODE_THREAD.equals(channel.getSessionMode())
                ? msg.threadId : "";
        String inflightKey = ImFormat.makeUserKey(channel.getId(), msg.userId, msg.chatId,
                stopThreadId);
        // 1. 本地取消：出队或在途取消（在途时同时拿到本实例已知的 session/message）。
        boolean localStopped = qaQueue.remove(inflightKey);
        InflightEntry entry = localStopped ? null : inflight.remove(inflightKey);
        String sessionId = "";
        String messageId = "";
        if (entry != null) {
            entry.cancel.getAsBoolean();
            localStopped = true;
            sessionId = entry.sessionId;
            messageId = entry.assistantMessageId;
        }
        // 2. 跨实例：本地 inflight 没命中时，查 Redis 在途映射拿 IDs。
        if ((sessionId.isEmpty() || messageId.isEmpty()) && redisStore != null) {
            String[] pair = redisStore.loadInflight(inflightKey);
            if (pair != null) {
                sessionId = pair[0];
                messageId = pair[1];
            }
        }
        // 3. 写 stop 事件到 StreamManager（与本仓 web /stop 同契约）：
        //    本轮的 stop watcher 与跨实例的引擎据此取消。
        if (!sessionId.isEmpty() && !messageId.isEmpty()) {
            writeStopEvent(sessionId, messageId);
            log.info("[IM] Wrote stop event to StreamManager: session={} message={}",
                    sessionId, messageId);
        }
        // 4. 标记兜底：给尚未创建 assistant message 的请求（执行前检查消费）。
        if (redisStore != null) {
            redisStore.setStopMarker(inflightKey, STOP_MARKER_TTL_SECONDS);
        } else {
            stopMarkers.put(inflightKey, System.currentTimeMillis());
        }
        if (!localStopped && sessionId.isEmpty()) {
            log.info("[IM] Set stop marker (no inflight found): key={}", inflightKey);
        }
    }

    /** 写 stop 事件到本轮次的流（形状与本仓 web /stop 一致，另带 {@code source=im}）。 */
    private void writeStopEvent(String sessionId, String messageId) {
        StreamManager sm = streamManagerRef;
        if (sm == null) {
            log.warn("[IM] StreamManager not wired; stop event skipped: session={} message={}",
                    sessionId, messageId);
            return;
        }
        StreamEvent stopEvent = new StreamEvent("stop-" + System.nanoTime(), ResponseType.STOP, "", true);
        stopEvent.setTimestamp(OffsetDateTime.now());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("message_id", messageId);
        data.put("reason", "user_requested");
        data.put("source", "im");
        stopEvent.setData(data);
        try {
            sm.appendEvent(sessionId, messageId, stopEvent);
        } catch (RuntimeException e) {
            log.warn("[IM] Failed to write stop event to StreamManager: {}", e.getMessage());
        }
    }

    /** 执行前 /stop 检查：本地标记（单实例）或 Redis 标记（跨实例）命中即清除并返回 true。 */
    boolean checkAndClearStopMarker(String userKey) {
        Long local = stopMarkers.remove(userKey);
        boolean hit = local != null
                && (System.currentTimeMillis() - local) < STOP_MARKER_TTL_SECONDS * 1000L;
        if (redisStore != null && redisStore.checkAndClearStopMarker(userKey)) {
            hit = true;
        }
        return hit;
    }

    /**
     * 在途登记（assistant message 创建后调用）：绑定 entry 的 session/message、
     * 写跨实例 inflight 映射，并启动 StreamManager stop watcher（跨实例 /stop 的消费侧）。
     */
    void bindInflight(QaAttach attach, String assistantMessageId) {
        InflightEntry entry = attach.inflight();
        if (entry == null) {
            return;
        }
        String sessionId = attach.session().getId();
        entry.sessionId = sessionId;
        entry.assistantMessageId = assistantMessageId;
        if (redisStore != null) {
            redisStore.storeInflight(attach.userKey(), sessionId, assistantMessageId,
                    INFLIGHT_TTL_SECONDS);
        }
        if (streamManagerRef != null && entry.queueReq != null) {
            QaQueue.QaRequest req = entry.queueReq;
            StreamStopWatcher.start(streamManagerRef, sessionId, assistantMessageId,
                    () -> !req.isCancelled(), req::cancel);
        }
    }

    /** 在途映射退场（QA 结束调用；Redis 未启用时空操作）。 */
    void unbindInflight(String userKey) {
        if (redisStore != null) {
            redisStore.clearInflight(userKey);
        }
    }

    private volatile StreamManager streamManagerRef;

    /** StreamManager 延迟接（避免与 stream 包的装配环；测试可注 stub）。 */
    public void setStreamManager(StreamManager sm) {
        this.streamManagerRef = sm;
    }

    void sendStreamReply(IncomingMessage msg, StreamSender streamer, String content)
            throws Exception {
        outboundFormatter.sendStreamReply(msg, streamer, content);
    }

    /** 排队任务：QaRequest（队列面）+ 业务束。 */
    static final class QaTask {
        private QaQueue.QaRequest queueReq;

        QaQueue.QaRequest queueReq() {
            return queueReq;
        }
        final IncomingMessage msg;
        final Session session;
        final CustomAgentEntity agent;
        final Adapter adapter;
        final ImChannelEntity channel;
        final String channelId;
        final String userKey;

        void bind(QaQueue.QaRequest r) {
            this.queueReq = r;
        }

        QaTask(IncomingMessage msg, Session session, CustomAgentEntity agent,
                Adapter adapter, ImChannelEntity channel, String channelId, String userKey) {

            this.msg = msg;
            this.session = session;
            this.agent = agent;
            this.adapter = adapter;
            this.channel = channel;
            this.channelId = channelId;
            this.userKey = userKey;
        }

        long tenantId() {
            return channel.getTenantId();
        }

        QaAttach attach(InflightEntry entry) {
            return new QaAttach(msg, session, agent, adapter, channel, channelId, userKey, entry);
        }
    }

    /** QA 输入束（qaRequest 的业务字段 + 在途登记句柄）。 */

    void runFallbackNonStream(QaAttach attach, ImAttachmentPreparer.Prepared prepared) {
        qaRunner.runFallbackNonStream(attach, prepared);
    }
    record QaAttach(IncomingMessage msg, Session session, CustomAgentEntity agent,
            Adapter adapter, ImChannelEntity channel, String channelId, String userKey,
            InflightEntry inflight) {
    }

    record QaOutcome(String answer, Exception error) {
    }
    String formatIMOutboundAnswerOrFallback(String raw) {
        return outboundFormatter.formatIMOutboundAnswerOrFallback(raw);
    }

    String cleanIMContent(String content) {
        return outboundFormatter.cleanIMContent(content);
    }


    void sendReplyQuiet(Adapter adapter, IncomingMessage msg, ReplyMessage reply) {
        outboundFormatter.sendReplyQuiet(adapter, msg, reply);
    }

}
