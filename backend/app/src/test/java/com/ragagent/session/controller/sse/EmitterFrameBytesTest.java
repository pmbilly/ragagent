package com.ragagent.session.controller.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseEmitter 实际产出帧字节的回归钉（迁移前探针）。
 *
 * <p>用途：钉住 Spring 对 {@code event("message").data(json)} 的逐字节形态——
 * 与既定金片骨架 {@code event:message\ndata:<json>\n\n}（冒号后无空格）比对，
 * 决定 SseEmitter 迁移是否需要重锚金片（蓝图 5.6.6 授权 spec 级差异）。</p>
 */
class EmitterFrameBytesTest {

    @RestController
    static class ProbeController {

        @GetMapping(path = "/probe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter probe() throws Exception {
            SseEmitter emitter = new SseEmitter(0L);
            // 返回前的 send 会被 ResponseBodyEmitter 缓冲，初始化后统一刷出
            emitter.send(SseEmitter.event().name("message").data("{\"a\":1}"));
            emitter.complete();
            return emitter;
        }

        /** 探针：SseEmitter 返回类型的 handler 同步抛 BizException——advice 是否解析？ */
        @GetMapping(path = "/probe-throw", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter probeThrow() {
            throw com.ragagent.framework.error.BizException.badRequest("probe-400");
        }
    }

    @Test
    @DisplayName("SseEmitter 的帧骨架与 Content-Type 实测")
    void emitterFrameSkeleton() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).build();
        MvcResult result = mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andExpect(resultOf -> assertThat(resultOf.getRequest().isAsyncStarted()).isTrue())
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        byte[] body = result.getResponse().getContentAsByteArray();
        String frame = new String(body, StandardCharsets.UTF_8);
        // 记录实测骨架：断言按 spec 解析器口径（event 行与 data 行），同时钉住具体字节防漂移
        assertThat(frame).startsWith("event:").contains("\ndata:").endsWith("\n\n");
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        // 逐字节钉：任何 Spring 升级改变帧形态时此行红——迁移决策的依据不可静默漂移
        assertThat(frame)
                .as("SseEmitter 帧字节（迁移依据）: " + frame.replace("\n", "\\n"))
                .isEqualTo("event:message\ndata:{\"a\":1}\n\n");
    }
}
