package com.ragagent.session.service.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.framework.web.SseSink;
import com.ragagent.llm.api.StreamResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * {@link SseFrameSink} 的默认实现：把 {@link StreamResponse} 序列化成 JSON 交 {@link SseSink} 写出。
 *
 * <p>帧骨架（{@code event:<name>
data:<json>

}）由 {@code SseSink} 实现承载
 * （{@code EmitterSseSink} 与字节实测见 {@code EmitterFrameBytesTest}）；本类只负责序列化
 * 与写出口的统一。HTML 转义随全局 mapper（迁移前曾局部特批，蓝图 5.6.6 已移除）。</p>
 */
@Component
public class JsonSseFrameSink implements SseFrameSink {


    /** 事件名固定为 {@code message}。 */
    public static final String EVENT_NAME = "message";

    private final ObjectMapper mapper;

    public JsonSseFrameSink(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 组帧（序列化 + {@code event:}/{@code data:} 行）——写出口无关（servlet 直写与 SseSink 共用）。 */
    @Override
    public String frameOf(StreamResponse payload) throws IOException {
        String json = jsonOf(payload);

        StringBuilder frame = new StringBuilder(json.length() + 32);
        frame.append("event:").append(EVENT_NAME).append('\n');
        frame.append("data:").append(json).append('\n').append('\n');
        return frame.toString();
    }

    /** 写一帧到 {@link SseSink}（embed/QA 会话流的出口）。 */
    @Override
    public void write(SseSink sink, StreamResponse payload) throws IOException {
        sink.send(EVENT_NAME, jsonOf(payload));
    }

    /** 序列化载荷为 JSON（组帧的 data 段；帧骨架由 SseSink 实现负责）。 */
    String jsonOf(StreamResponse payload) throws IOException {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IOException("failed to marshal stream response: " + e.getMessage(), e);
        }
    }
}
