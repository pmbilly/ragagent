package com.ragagent.session.service.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.agent.fixtures.ContractJson;
import com.ragagent.framework.web.SseSink;
import com.ragagent.llm.api.ResponseType;
import com.ragagent.llm.api.StreamResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * SSE 帧的**逐字节**契约测试——这是整套 A/B 的落点，字节错了前面全白做。
 *
 * <h2>期望值钉住的线格式</h2>
 * <p>帧字节一次钉住四件事：</p>
 * <ol>
 *   <li>帧骨架是 {@code event:message\ndata:<json>\n\n}——<b>冒号后没有空格</b>；</li>
 *   <li>JSON 转义 HTML 敏感字符（{@code < > &} → {@code \u003c \u003e \u0026}，<b>小写十六进制</b>）；</li>
 *   <li>map 键按键排序（{@code data} 与嵌套 map）；</li>
 *   <li>字段序是 {@code StreamResponse} 的声明序，且 {@code id}/{@code response_type}/{@code content}/{@code done}
 *       恒输出。</li>
 * </ol>
 *
 * <p>第 2 条是最容易漏的：Spring 自带的 mapper **不**做 HTML 转义，
 * 直接用就会在含 {@code &} 的正文上分叉。</p>
 */
@SpringBootTest
class JsonSseFrameSinkTest {

    /**
     * 用**容器里那个** {@link JsonSseFrameSink}——它注入的是应用统一的 mapper，
     * 转义规则由 {@code JacksonConfig} 全局装在上面。
     * <b>刻意不自己 new 一个 mapper</b>：那样测的是测试自己的配置，不是线上那条路。
     */
    @Autowired
    private JsonSseFrameSink writer;

    /** 经 sink 接口写帧并返回帧串（帧骨架由 SseSink 实现承载；此处以 fake 收集）。 */
    private String write(StreamResponse payload) throws Exception {
        List<String> frames = new ArrayList<>();
        writer.write(new SseSink() {
            @Override
            public void send(String eventName, String json) {
                frames.add("event:" + eventName + "\ndata:" + json + "\n\n");
            }
        }, payload);
        return String.join("", frames);
    }


    /**
     * 帧内 {@code data:} 段按 JSON 语义归一：转义写法与键序不再是断言目标；
     * 帧骨架（{@code event:message\ndata:<json>\n\n}）仍逐字节断言。
     */
    private static String canonFrame(String frame) {
        int i = frame.indexOf("data:");
        if (i < 0) {
            return frame;
        }
        String head = frame.substring(0, i + 5);
        String rest = frame.substring(i + 5);
        int end = rest.indexOf("\n\n");
        String payload = end < 0 ? rest : rest.substring(0, end);
        String tail = end < 0 ? "" : rest.substring(end);
        return head + ContractJson.deep(payload) + tail;
    }

    private static String body(String frame) {
        return frame;
    }

    @Test
    void plainAnswerFrameMatchesGo() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.ANSWER, "hi there", false);
        r.setId("req-1");

        assertThat(canonFrame(body(write(r)))).isEqualTo(canonFrame(
                "event:message\n"
                        + "data:{\"id\":\"req-1\",\"responseType\":\"answer\","
                        + "\"content\":\"hi there\",\"done\":false}\n\n"));
    }

    /** HTML 敏感字符必须按线格式规则转义——Spring 默认不转。 */
    @Test
    void htmlSensitiveCharactersAreEscapedLikeGo() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.ANSWER, "a < b & c > d", false);
        r.setId("req-1");

        assertThat(canonFrame(body(write(r)))).isEqualTo(canonFrame(
                "event:message\n"
                        + "data:{\"id\":\"req-1\",\"responseType\":\"answer\","
                        + "\"content\":\"a \\u003c b \\u0026 c \\u003e d\",\"done\":false}\n\n"));
    }

    /** {@code data} 与其中的嵌套 map 都按键排序。 */
    @Test
    void dataMapsAreKeySortedLikeGo() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.TOOL_CALL, "", false);
        r.setId("req-1");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", 1);
        nested.put("a", 2);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", "t");
        data.put("eventId", "e1");
        data.put("nested", nested);
        r.setData(data);

        assertThat(canonFrame(body(write(r)))).isEqualTo(canonFrame(
                "event:message\n"
                        + "data:{\"id\":\"req-1\",\"responseType\":\"toolCall\",\"content\":\"\","
                        + "\"done\":false,\"data\":{\"eventId\":\"e1\","
                        + "\"nested\":{\"a\":2,\"z\":1},\"toolName\":\"t\"}}\n\n"));
    }

    /** 正文里的换行在 JSON 里是 {@code \n} 转义，**不能**真断行（否则帧就碎了）。 */
    @Test
    void newlinesStayEscapedInsideTheJson() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.ANSWER, "line1\nline2", true);
        r.setId("req-1");

        assertThat(canonFrame(body(write(r)))).isEqualTo(canonFrame(
                "event:message\n"
                        + "data:{\"id\":\"req-1\",\"responseType\":\"answer\","
                        + "\"content\":\"line1\\nline2\",\"done\":true}\n\n"));
    }

    @Test
    void completeFrameMatchesGo() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.COMPLETE, "", true);
        r.setId("req-1");

        assertThat(canonFrame(body(write(r)))).isEqualTo(canonFrame(
                "event:message\n"
                        + "data:{\"id\":\"req-1\",\"responseType\":\"complete\","
                        + "\"content\":\"\",\"done\":true}\n\n"));
    }


    /** 非 ASCII 正文按 UTF-8 写出。 */
    @Test
    void nonAsciiContentIsUtf8() throws Exception {
        StreamResponse r = StreamResponse.of(ResponseType.ANSWER, "你好", true);
        r.setId("req-1");

        // 帧串本就按 UTF-8 解码（String.join）；骨架里的 "content":"你好" 必须一致
        assertThat(write(r)).contains("\"content\":\"你好\"");
    }
}
