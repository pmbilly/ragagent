package com.ragagent.im.callback;

import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.runtime.AdapterInterfaces.Adapter;
import com.ragagent.im.runtime.CallbackExchange;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.service.ImChannelService;
import com.ragagent.im.service.ImTurnOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** IM 平台回调处理：实体/适配器细节从控制器下沉。 */
@Service
public class ImCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(ImCallbackHandler.class);

    private final ImChannelService imChannelService;
    private final ImTurnOrchestrator imTurnOrchestrator;

    public ImCallbackHandler(ImChannelService imChannelService,
            ImTurnOrchestrator imTurnOrchestrator) {
        this.imChannelService = imChannelService;
        this.imTurnOrchestrator = imTurnOrchestrator;
    }

    public void handle(String channelId, HttpServletRequest request, HttpServletResponse response) {
        CallbackExchange.Servlet exchange = new CallbackExchange.Servlet(request, response);
        ImChannelEntity channel;
        try {
            channel = imChannelService.ensureChannelForCallback(channelId);
        } catch (ImChannelService.CallbackChannelNotFoundException e) {
            log.error("[IM] Channel not found for callback: {}", channelId);
            writeJson(response, 404, "channel not found");
            return;
        } catch (ImChannelService.CallbackChannelDisabledException e) {
            log.error("[IM] Channel disabled for callback: {}", channelId);
            writeJson(response, 503, "channel is disabled");
            return;
        } catch (ImChannelService.CallbackChannelUnavailableException e) {
            log.error("[IM] Channel unavailable for callback {}", channelId);
            writeJson(response, 503, "channel not available");
            return;
        }

        Adapter adapter = imTurnOrchestrator.adapterFor(channel);
        if (adapter == null) {
            log.info("[IM] Callback received platform={} path_channel_id={} — adapter not active",
                    channel.getPlatform(), channelId);
            writeJson(response, 503, "channel not available");
            return;
        }
        log.info("[IM] Callback received platform={} path_channel_id={}",
                channel.getPlatform(), channelId);

        if (adapter.handleURLVerification(exchange)) {
            return;
        }
        Exception verifyErr = adapter.verifyCallback(exchange);
        if (verifyErr != null) {
            log.error("[IM] Callback verification failed for channel {}", channelId, verifyErr);
            writeJson(response, 403, "verification failed");
            return;
        }

        IncomingMessage msg;
        try {
            msg = adapter.parseCallback(exchange);
        } catch (Exception e) {
            log.error("[IM] Parse callback failed for channel {}", channelId, e);
            writeJson(response, 400, "parse failed");
            return;
        }
        if (msg == null) {
            if ("mattermost".equals(channel.getPlatform())) {
                log.info("[IM] Mattermost callback ignored (no message): path_channel_id={} — "
                        + "check trigger word/bot_user_id", channelId);
            } else {
                log.info("[IM] Callback parsed no message to process platform={} path_channel_id={}",
                        channel.getPlatform(), channelId);
            }
            writeAck(response, channel.getPlatform());
            return;
        }

        writeAck(response, channel.getPlatform());
        Thread.startVirtualThread(() -> {
            try {
                imTurnOrchestrator.handleMessage(msg, channelId);
            } catch (Exception e) {
                log.error("[IM] Handle message error for channel {}", channelId, e);
            }
        });
    }

    private static void writeAck(HttpServletResponse response, String platform) {
        if ("yunzhijia".equals(platform)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", 2);
            data.put("content", "");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            writeRaw(response, 200, body);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        writeRaw(response, 200, body);
    }

    private static void writeJson(HttpServletResponse response, int status, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        writeRaw(response, status, body);
    }

    private static void writeRaw(HttpServletResponse response, int status, Object body) {
        try {
            response.setStatus(status);
            response.setContentType("application/json; charset=utf-8");
            byte[] bytes = CallbackExchange.ImJson.encode(body)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        } catch (Exception ignored) {
            // 客户端断开
        }
    }
}
