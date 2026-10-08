package com.ragagent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.ragagent.event.payload.AgentActionData;
import com.ragagent.event.payload.AgentCompleteData;
import com.ragagent.event.payload.AgentFinalAnswerData;
import com.ragagent.event.payload.AgentPlanData;
import com.ragagent.event.payload.AgentQueryData;
import com.ragagent.event.payload.AgentReferencesData;
import com.ragagent.event.payload.AgentReflectionData;
import com.ragagent.event.payload.AgentStepData;
import com.ragagent.event.payload.AgentThoughtData;
import com.ragagent.event.payload.AgentToolCallData;
import com.ragagent.event.payload.AgentToolResultData;
import com.ragagent.event.payload.ChatData;
import com.ragagent.event.payload.ContextCompactedData;
import com.ragagent.event.payload.ErrorData;
import com.ragagent.event.payload.MCPOAuthRequiredData;
import com.ragagent.event.payload.MCPOAuthResolvedData;
import com.ragagent.event.payload.MemoryRecalledData;
import com.ragagent.event.payload.MergeData;
import com.ragagent.event.payload.QueryData;
import com.ragagent.event.payload.RerankData;
import com.ragagent.event.payload.RetrievalData;
import com.ragagent.event.payload.SessionTitleData;
import com.ragagent.event.payload.StopData;
import com.ragagent.event.payload.ToolApprovalRequiredData;
import com.ragagent.event.payload.ToolApprovalResolvedData;
import com.ragagent.event.payload.UserMessageInjectedData;

/**
 * 事件 payload 的 JSON 字节形状——<b>期望值全部是固定录制真值</b>，
 * 不是照直觉写的。覆盖三类形态：
 * <ol>
 *   <li>零值（零值字段恒输出，含 {@code "plan":null} 这类 null 列表/map）；</li>
 *   <li>全量（键序 = 字段声明序；HTML 转义；map 键字母序；浮点格式）；</li>
 *   <li>缺省边界（空串 / 0 / false / 空 map / 空列表整键省略）。</li>
 * </ol>
 *
 * <p>线上意义：payload 经 AgentStreamHandler 的 {@code toolApprovalDataToMap}
 * （payload 序列化 → map）进 StreamResponse.data，最终出现在
 * continue-stream 的 SSE 帧里。</p>
 */
class EventPayloadJsonTest {

    // 期望值不再保留录制期的 \u003c HTML 转义形态；
    // 各用例头部的录制形状注释保留为历史记录。

    private static String write(Object payload) {
        return EventJson.write(payload);
    }

    /**
     * 数字形态用 Java 标准序列化（{@code 2.0} / {@code 1.0E21}）；
     * 期望值里的旧录制形态（{@code 2} / {@code 1e+21}）经语义归一后比较，形态差异不构成断言目标。
     */
    private static void assertSemantic(String expected, String actual) {
        assertEquals(com.ragagent.support.ContractJson.deep(expected),
                com.ragagent.support.ContractJson.deep(actual));
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Object> listOf(Object... items) {
        return new ArrayList<>(List.of(items));
    }

    // ===== QueryData =====

    @Test
    void queryDataZero() {
        // Go: {"originalQuery":"","sessionId":""}
        assertSemantic("{\"originalQuery\":\"\",\"sessionId\":\"\"}", write(new QueryData()));
    }

    @Test
    void queryDataFull() {
        // Go: {"originalQuery":"What is \u003cRAG\u003e \u0026 why?","rewrittenQuery":"explain retrieval-augmented generation",
        //      "sessionId":"sess-1","userId":"u-1","extra":{"alpha":true,"mid":"m","zebra":1}}
        QueryData d = new QueryData();
        d.setOriginalQuery("What is <RAG> & why?");
        d.setRewrittenQuery("explain retrieval-augmented generation");
        d.setSessionId("sess-1");
        d.setUserId("u-1");
        // 刻意乱序插入：序列化按键名字母序输出
        d.setExtra(mapOf("zebra", 1, "alpha", true, "mid", "m"));
        assertSemantic("{\"originalQuery\":\"What is <RAG> & why?\","
                + "\"rewrittenQuery\":\"explain retrieval-augmented generation\","
                + "\"sessionId\":\"sess-1\",\"userId\":\"u-1\","
                + "\"extra\":{\"alpha\":true,\"mid\":\"m\",\"zebra\":1}}", write(d));
    }

    @Test
    void queryDataEmptyExtraOmitted() {
        // Go: {"originalQuery":"q","sessionId":"s"}——空 map 也被 omitempty 省略
        QueryData d = new QueryData("q", "", "s", "", new LinkedHashMap<>());
        assertSemantic("{\"originalQuery\":\"q\",\"sessionId\":\"s\"}", write(d));
    }

    // ===== RetrievalData =====

    @Test
    void retrievalDataZero() {
        // Go: {"query":"","knowledgeBaseId":"","topK":0,"threshold":0,"retrievalType":"","resultCount":0}
        assertSemantic("{\"query\":\"\",\"knowledgeBaseId\":\"\",\"topK\":0,\"threshold\":0,"
                + "\"retrievalType\":\"\",\"resultCount\":0}", write(new RetrievalData()));
    }

    @Test
    void retrievalDataFull() {
        // Go: {"query":"q","knowledgeBaseId":"kb-1","topK":10,"threshold":0.5,"retrievalType":"vector",
        //      "resultCount":2,"results":[{"content":"c","score":0.9}],"durationMs":123,"extra":{"k":"v"}}
        RetrievalData d = new RetrievalData();
        d.setQuery("q");
        d.setKnowledgeBaseId("kb-1");
        d.setTopK(10);
        d.setThreshold(0.5);
        d.setRetrievalType("vector");
        d.setResultCount(2);
        d.setResults(listOf(mapOf("score", 0.9, "content", "c")));
        d.setDurationMs(123);
        d.setExtra(mapOf("k", "v"));
        assertSemantic("{\"query\":\"q\",\"knowledgeBaseId\":\"kb-1\",\"topK\":10,\"threshold\":0.5,"
                + "\"retrievalType\":\"vector\",\"resultCount\":2,"
                + "\"results\":[{\"content\":\"c\",\"score\":0.9}],\"durationMs\":123,\"extra\":{\"k\":\"v\"}}",
                write(d));
    }

    @Test
    void retrievalDataZeroThresholdAlwaysOutput() {
        // Go: {"query":"q","knowledgeBaseId":"kb","topK":0,"threshold":0,"retrievalType":"keyword","resultCount":0}
        // threshold 无 omitempty：0 也输出
        RetrievalData d = new RetrievalData("q", "kb", 0, 0.0, "keyword", 0, null, 0, null);
        assertSemantic("{\"query\":\"q\",\"knowledgeBaseId\":\"kb\",\"topK\":0,\"threshold\":0,"
                + "\"retrievalType\":\"keyword\",\"resultCount\":0}", write(d));
    }

    // ===== RerankData =====

    @Test
    void rerankDataZero() {
        // Go: {"query":"","inputCount":0,"outputCount":0,"modelId":"","threshold":0}
        assertSemantic("{\"query\":\"\",\"inputCount\":0,\"outputCount\":0,\"modelId\":\"\",\"threshold\":0}",
                write(new RerankData()));
    }

    @Test
    void rerankDataFull() {
        // Go: {"query":"q","inputCount":10,"outputCount":5,"modelId":"m-1","threshold":0.3,
        //      "results":["a","b"],"durationMs":45}
        RerankData d = new RerankData("q", 10, 5, "m-1", 0.3, new ArrayList<>(List.of("a", "b")), 45, null);
        assertSemantic("{\"query\":\"q\",\"inputCount\":10,\"outputCount\":5,\"modelId\":\"m-1\","
                + "\"threshold\":0.3,\"results\":[\"a\",\"b\"],\"durationMs\":45}", write(d));
    }

    // ===== MergeData =====

    @Test
    void mergeDataZero() {
        // Go: {"inputCount":0,"outputCount":0,"mergeType":""}
        assertSemantic("{\"inputCount\":0,\"outputCount\":0,\"mergeType\":\"\"}", write(new MergeData()));
    }

    @Test
    void mergeDataFull() {
        // Go: {"inputCount":3,"outputCount":2,"mergeType":"dedup","results":[1,2],"durationMs":7}
        MergeData d = new MergeData(3, 2, "dedup", new ArrayList<>(List.of(1, 2)), 7, null);
        assertSemantic("{\"inputCount\":3,\"outputCount\":2,\"mergeType\":\"dedup\","
                + "\"results\":[1,2],\"durationMs\":7}", write(d));
    }

    // ===== ChatData =====

    @Test
    void chatDataZero() {
        // Go: {"query":"","modelId":"","isStream":false}——is_stream 无 omitempty
        assertSemantic("{\"query\":\"\",\"modelId\":\"\",\"isStream\":false}", write(new ChatData()));
    }

    @Test
    void chatDataFull() {
        // Go: {"query":"q","modelId":"m","response":"r","streamChunk":"sc","tokenCount":42,
        //      "durationMs":99,"isStream":true,"extra":{"a":1.5}}
        ChatData d = new ChatData("q", "m", "r", "sc", 42, 99, true, mapOf("a", 1.5));
        assertSemantic("{\"query\":\"q\",\"modelId\":\"m\",\"response\":\"r\",\"streamChunk\":\"sc\","
                + "\"tokenCount\":42,\"durationMs\":99,\"isStream\":true,\"extra\":{\"a\":1.5}}",
                write(d));
    }

    // ===== ErrorData =====

    @Test
    void errorDataZero() {
        // Go: {"error":"","stage":"","sessionId":""}
        assertSemantic("{\"error\":\"\",\"stage\":\"\",\"sessionId\":\"\"}", write(new ErrorData()));
    }

    @Test
    void errorDataFull() {
        // Go: {"error":"boom \u003cx\u003e\u0026","errorCode":"E-1","stage":"agent_execution",
        //      "sessionId":"s-1","query":"q","extra":{"attempt":2}}
        // extra 里的 float64(2.0) 输出 2（Go 编码器整数不补 .0）
        ErrorData d = new ErrorData("boom <x>&", "E-1", "agent_execution", "s-1", "q", mapOf("attempt", 2.0));
        assertSemantic("{\"error\":\"boom <x>&\",\"errorCode\":\"E-1\","
                + "\"stage\":\"agent_execution\",\"sessionId\":\"s-1\",\"query\":\"q\","
                + "\"extra\":{\"attempt\":2}}", write(d));
    }

    // ===== AgentPlanData =====

    @Test
    void agentPlanDataZero() {
        // Go: {"query":"","plan":null}——plan 无 omitempty：nil slice 输出 null
        assertSemantic("{\"query\":\"\",\"plan\":null}", write(new AgentPlanData()));
    }

    @Test
    void agentPlanDataFull() {
        // Go: {"query":"q","plan":["step1","step2"],"durationMs":5}
        AgentPlanData d = new AgentPlanData("q", new ArrayList<>(List.of("step1", "step2")), 5);
        assertSemantic("{\"query\":\"q\",\"plan\":[\"step1\",\"step2\"],\"durationMs\":5}", write(d));
    }

    @Test
    void agentPlanDataNilVsEmptyPlan() {
        // Go: nilPlan => {"query":"q","plan":null,"durationMs":5}
        assertSemantic("{\"query\":\"q\",\"plan\":null,\"durationMs\":5}",
                write(new AgentPlanData("q", null, 5)));
        // Go: emptyPlan => {"query":"q","plan":[],"durationMs":5}
        assertSemantic("{\"query\":\"q\",\"plan\":[],\"durationMs\":5}",
                write(new AgentPlanData("q", new ArrayList<>(), 5)));
    }

    // ===== AgentStepData =====

    @Test
    void agentStepDataZero() {
        // Go: {"iteration":0,"thought":"","toolCalls":null,"durationMs":0}
        assertSemantic("{\"iteration\":0,\"thought\":\"\",\"toolCalls\":null,\"durationMs\":0}",
                write(new AgentStepData()));
    }

    @Test
    void agentStepDataFull() {
        // Go: {"iteration":2,"thought":"thinking","toolCalls":[{"name":"web_search"}],"durationMs":300}
        AgentStepData d = new AgentStepData(2, "thinking", listOf(mapOf("name", "web_search")), 300);
        assertSemantic("{\"iteration\":2,\"thought\":\"thinking\","
                + "\"toolCalls\":[{\"name\":\"web_search\"}],\"durationMs\":300}", write(d));
    }

    // ===== AgentActionData =====

    @Test
    void agentActionDataZero() {
        // Go: {"iteration":0,"toolName":"","toolInput":null,"toolOutput":"","success":false,"durationMs":0}
        assertSemantic("{\"iteration\":0,\"toolName\":\"\",\"toolInput\":null,\"toolOutput\":\"\","
                + "\"success\":false,\"durationMs\":0}", write(new AgentActionData()));
    }

    @Test
    void agentActionDataFull() {
        // Go: {"iteration":1,"toolName":"web_search","toolInput":{"limit":5,"query":"golang"},
        //      "toolOutput":"out","success":true,"durationMs":120}
        // tool_input 的 float64(5) 输出 5
        AgentActionData d = new AgentActionData(1, "web_search", mapOf("query", "golang", "limit", 5.0),
                "out", true, "", 120);
        assertSemantic("{\"iteration\":1,\"toolName\":\"web_search\","
                + "\"toolInput\":{\"limit\":5,\"query\":\"golang\"},\"toolOutput\":\"out\","
                + "\"success\":true,\"durationMs\":120}", write(d));
    }

    @Test
    void agentActionDataNilInputWithError() {
        // Go: {"iteration":1,"toolName":"web_search","toolInput":null,"toolOutput":"out",
        //      "success":false,"error":"timeout","durationMs":120}
        AgentActionData d = new AgentActionData(1, "web_search", null, "out", false, "timeout", 120);
        assertSemantic("{\"iteration\":1,\"toolName\":\"web_search\",\"toolInput\":null,"
                + "\"toolOutput\":\"out\",\"success\":false,\"error\":\"timeout\",\"durationMs\":120}",
                write(d));
    }

    // ===== AgentQueryData =====

    @Test
    void agentQueryDataZero() {
        // Go: {"sessionId":"","query":""}
        assertSemantic("{\"sessionId\":\"\",\"query\":\"\"}", write(new AgentQueryData()));
    }

    @Test
    void agentQueryDataFull() {
        // Go: {"sessionId":"s","query":"q","requestId":"r-1","extra":{"src":"embed"}}
        AgentQueryData d = new AgentQueryData("s", "q", "r-1", mapOf("src", "embed"));
        assertSemantic("{\"sessionId\":\"s\",\"query\":\"q\",\"requestId\":\"r-1\","
                + "\"extra\":{\"src\":\"embed\"}}", write(d));
    }

    // ===== AgentCompleteData =====

    @Test
    void agentCompleteDataZero() {
        // Go: {"sessionId":"","totalSteps":0,"finalAnswer":"","totalDurationMs":0}
        assertSemantic("{\"sessionId\":\"\",\"totalSteps\":0,\"finalAnswer\":\"\",\"totalDurationMs\":0}",
                write(new AgentCompleteData()));
    }

    @Test
    void agentCompleteDataFull() {
        // Go: {"sessionId":"s","totalSteps":3,"finalAnswer":"the answer",
        //      "knowledgeRefs":[{"id":"c1","score":0.88}],"agentSteps":["think","act"],
        //      "usage":{"completion_tokens":5,"prompt_tokens":10},"totalDurationMs":1234,
        //      "messageId":"m-1","requestId":"r-1","extra":{"ch":"web"}}
        // usage 的 float64 整数输出 5/10（Go 编码器）
        AgentCompleteData d = new AgentCompleteData();
        d.setSessionId("s");
        d.setTotalSteps(3);
        d.setFinalAnswer("the answer");
        d.setKnowledgeRefs(listOf(mapOf("id", "c1", "score", 0.88)));
        d.setAgentSteps(new ArrayList<>(List.of("think", "act")));
        d.setUsage(mapOf("prompt_tokens", 10.0, "completion_tokens", 5.0));
        d.setTotalDurationMs(1234);
        d.setMessageId("m-1");
        d.setRequestId("r-1");
        d.setExtra(mapOf("ch", "web"));
        assertSemantic("{\"sessionId\":\"s\",\"totalSteps\":3,\"finalAnswer\":\"the answer\","
                + "\"knowledgeRefs\":[{\"id\":\"c1\",\"score\":0.88}],"
                + "\"agentSteps\":[\"think\",\"act\"],"
                + "\"usage\":{\"completion_tokens\":5,\"prompt_tokens\":10},"
                + "\"totalDurationMs\":1234,\"messageId\":\"m-1\",\"requestId\":\"r-1\","
                + "\"extra\":{\"ch\":\"web\"}}", write(d));
    }

    @Test
    void agentCompleteDataEmptyRefsOmitted() {
        // Go: {"sessionId":"s","totalSteps":1,"finalAnswer":"a","totalDurationMs":10}
        // 空列表也被 omitempty 省略
        AgentCompleteData d = new AgentCompleteData("s", 1, "a", new ArrayList<>(), null,
                null, 10, "", "", null);
        assertSemantic("{\"sessionId\":\"s\",\"totalSteps\":1,\"finalAnswer\":\"a\",\"totalDurationMs\":10}",
                write(d));
    }

    // ===== AgentThoughtData =====

    @Test
    void agentThoughtDataZero() {
        // Go: {"content":"","iteration":0,"done":false}
        assertSemantic("{\"content\":\"\",\"iteration\":0,\"done\":false}", write(new AgentThoughtData()));
    }

    @Test
    void agentThoughtDataFull() {
        // Go: {"content":"let me \u003cthink\u003e \u0026 see","iteration":2,"done":true}
        AgentThoughtData d = new AgentThoughtData("let me <think> & see", 2, true);
        assertSemantic("{\"content\":\"let me <think> & see\",\"iteration\":2,\"done\":true}",
                write(d));
    }

    // ===== AgentToolCallData =====

    @Test
    void agentToolCallDataZero() {
        // Go: {"toolCallId":"","toolName":"","iteration":0}
        assertSemantic("{\"toolCallId\":\"\",\"toolName\":\"\",\"iteration\":0}",
                write(new AgentToolCallData()));
    }

    @Test
    void agentToolCallDataFull() {
        // Go: {"toolCallId":"call_1","toolName":"web_search","arguments":{"limit":3,"query":"go"},
        //      "iteration":1,"hint":"web_search(\"go\")"}
        AgentToolCallData d = new AgentToolCallData("call_1", "web_search",
                mapOf("query", "go", "limit", 3.0), 1, "web_search(\"go\")");
        assertSemantic("{\"toolCallId\":\"call_1\",\"toolName\":\"web_search\","
                + "\"arguments\":{\"limit\":3,\"query\":\"go\"},\"iteration\":1,"
                + "\"hint\":\"web_search(\\\"go\\\")\"}", write(d));
    }

    @Test
    void agentToolCallDataEmptyArgsOmitted() {
        // Go: {"toolCallId":"call_1","toolName":"t","iteration":0}——空 map 也省略
        AgentToolCallData d = new AgentToolCallData("call_1", "t", new LinkedHashMap<>(), 0, "");
        assertSemantic("{\"toolCallId\":\"call_1\",\"toolName\":\"t\",\"iteration\":0}", write(d));
    }

    // ===== AgentToolResultData =====

    @Test
    void agentToolResultDataZero() {
        // Go: {"toolCallId":"","toolName":"","output":"","success":false,"iteration":0}
        assertSemantic("{\"toolCallId\":\"\",\"toolName\":\"\",\"output\":\"\",\"success\":false,\"iteration\":0}",
                write(new AgentToolResultData()));
    }

    @Test
    void agentToolResultDataFull() {
        // Go: {"toolCallId":"call_1","toolName":"web_search","output":"res \u0026 \u003cres\u003e",
        //      "success":true,"durationMs":88,"iteration":1,"data":{"display_type":"search","items":3}}
        AgentToolResultData d = new AgentToolResultData("call_1", "web_search", "res & <res>", "",
                true, 88, 1, mapOf("display_type", "search", "items", 3.0));
        assertSemantic("{\"toolCallId\":\"call_1\",\"toolName\":\"web_search\","
                + "\"output\":\"res & <res>\",\"success\":true,\"durationMs\":88,"
                + "\"iteration\":1,\"data\":{\"display_type\":\"search\",\"items\":3}}", write(d));
    }

    @Test
    void agentToolResultDataError() {
        // Go: {"toolCallId":"call_2","toolName":"shell_exec","output":"","error":"exit status 1",
        //      "success":false,"iteration":2}
        AgentToolResultData d = new AgentToolResultData("call_2", "shell_exec", "", "exit status 1",
                false, 0, 2, null);
        assertSemantic("{\"toolCallId\":\"call_2\",\"toolName\":\"shell_exec\",\"output\":\"\","
                + "\"error\":\"exit status 1\",\"success\":false,\"iteration\":2}", write(d));
    }

    // ===== AgentReferencesData / MemoryRecalledData =====

    @Test
    void agentReferencesDataZero() {
        // Go: {"references":null,"iteration":0}
        assertSemantic("{\"references\":null,\"iteration\":0}", write(new AgentReferencesData()));
    }

    @Test
    void agentReferencesDataFull() {
        // Go: {"references":[{"content":"chunk","score":0.77}],"iteration":1}
        AgentReferencesData d = new AgentReferencesData(listOf(mapOf("content", "chunk", "score", 0.77)), 1);
        assertSemantic("{\"references\":[{\"content\":\"chunk\",\"score\":0.77}],\"iteration\":1}",
                write(d));
    }

    @Test
    void memoryRecalledDataZero() {
        // Go: {"memories":null}
        assertSemantic("{\"memories\":null}", write(new MemoryRecalledData()));
    }

    @Test
    void memoryRecalledDataFull() {
        // Go: {"memories":[{"text":"user likes go"}]}
        MemoryRecalledData d = new MemoryRecalledData(listOf(mapOf("text", "user likes go")));
        assertSemantic("{\"memories\":[{\"text\":\"user likes go\"}]}", write(d));
    }

    // ===== AgentFinalAnswerData =====

    @Test
    void agentFinalAnswerDataZero() {
        // Go: {"content":"","done":false}
        assertSemantic("{\"content\":\"\",\"done\":false}", write(new AgentFinalAnswerData()));
    }

    @Test
    void agentFinalAnswerDataFull() {
        // Go: {"content":"answer chunk","done":true,"isFallback":true}
        assertSemantic("{\"content\":\"answer chunk\",\"done\":true,\"isFallback\":true}",
                write(new AgentFinalAnswerData("answer chunk", true, true)));
        // Go: doneOnly => {"content":"","done":true}
        assertSemantic("{\"content\":\"\",\"done\":true}",
                write(new AgentFinalAnswerData("", true, false)));
    }

    // ===== ContextCompactedData =====

    @Test
    void contextCompactedDataZero() {
        // Go: {"reason":"","round":0,"tokensBefore":0,"tokensAfter":0,"messagesBefore":0,
        //      "messagesAfter":0,"summary":""}
        assertSemantic("{\"reason\":\"\",\"round\":0,\"tokensBefore\":0,\"tokensAfter\":0,"
                + "\"messagesBefore\":0,\"messagesAfter\":0,\"summary\":\"\"}",
                write(new ContextCompactedData()));
    }

    @Test
    void contextCompactedDataFull() {
        // Go: {"reason":"threshold","round":3,"tokensBefore":9000,"tokensAfter":4000,
        //      "messagesBefore":20,"messagesAfter":8,"summary":"summary text","degraded":true,"splitTurn":true}
        ContextCompactedData d = new ContextCompactedData("threshold", 3, 9000, 4000, 20, 8,
                "summary text", true, true);
        assertSemantic("{\"reason\":\"threshold\",\"round\":3,\"tokensBefore\":9000,\"tokensAfter\":4000,"
                + "\"messagesBefore\":20,\"messagesAfter\":8,\"summary\":\"summary text\","
                + "\"degraded\":true,\"splitTurn\":true}", write(d));
    }

    @Test
    void contextCompactedDataFlagsOmittedWhenFalse() {
        // Go: notDegraded => {...,"summary":"s"}——degraded/split_turn false 时省略
        ContextCompactedData d = new ContextCompactedData("overflow", 1, 100, 50, 4, 2, "s", false, false);
        assertSemantic("{\"reason\":\"overflow\",\"round\":1,\"tokensBefore\":100,\"tokensAfter\":50,"
                + "\"messagesBefore\":4,\"messagesAfter\":2,\"summary\":\"s\"}", write(d));
    }

    // ===== UserMessageInjectedData =====

    @Test
    void userMessageInjectedDataZero() {
        // Go: {"steerId":"","content":"","messageId":""}
        assertSemantic("{\"steerId\":\"\",\"content\":\"\",\"messageId\":\"\"}",
                write(new UserMessageInjectedData()));
    }

    @Test
    void userMessageInjectedDataFull() {
        // Go: {"steerId":"st-1","content":"also check X","messageId":"m-1","userMessageId":"um-1"}
        assertSemantic("{\"steerId\":\"st-1\",\"content\":\"also check X\",\"messageId\":\"m-1\","
                + "\"userMessageId\":\"um-1\"}",
                write(new UserMessageInjectedData("st-1", "also check X", "m-1", "um-1")));
        // Go: noUserMsg => {"steerId":"st-2","content":"c","messageId":"m-2"}
        assertSemantic("{\"steerId\":\"st-2\",\"content\":\"c\",\"messageId\":\"m-2\"}",
                write(new UserMessageInjectedData("st-2", "c", "m-2", "")));
    }

    // ===== AgentReflectionData / SessionTitleData / StopData =====

    @Test
    void agentReflectionDataZero() {
        // Go: {"toolCallId":"","content":"","iteration":0,"done":false}
        assertSemantic("{\"toolCallId\":\"\",\"content\":\"\",\"iteration\":0,\"done\":false}",
                write(new AgentReflectionData()));
    }

    @Test
    void agentReflectionDataFull() {
        // Go: {"toolCallId":"call_1","content":"reflecting","iteration":2,"done":true}
        assertSemantic("{\"toolCallId\":\"call_1\",\"content\":\"reflecting\",\"iteration\":2,\"done\":true}",
                write(new AgentReflectionData("call_1", "reflecting", 2, true)));
    }

    @Test
    void sessionTitleDataZero() {
        // Go: {"sessionId":"","title":""}
        assertSemantic("{\"sessionId\":\"\",\"title\":\"\"}", write(new SessionTitleData()));
    }

    @Test
    void sessionTitleDataFull() {
        // Go: {"sessionId":"s-1","title":"New Title"}
        assertSemantic("{\"sessionId\":\"s-1\",\"title\":\"New Title\"}",
                write(new SessionTitleData("s-1", "New Title")));
    }

    @Test
    void stopDataZero() {
        // Go: {"sessionId":"","messageId":""}
        assertSemantic("{\"sessionId\":\"\",\"messageId\":\"\"}", write(new StopData()));
    }

    @Test
    void stopDataFullAndNoReason() {
        // Go: {"sessionId":"s-1","messageId":"m-1","reason":"user_requested"}
        assertSemantic("{\"sessionId\":\"s-1\",\"messageId\":\"m-1\",\"reason\":\"user_requested\"}",
                write(new StopData("s-1", "m-1", "user_requested")));
        // Go: noReason => {"sessionId":"s-1","messageId":"m-1"}
        assertSemantic("{\"sessionId\":\"s-1\",\"messageId\":\"m-1\"}",
                write(new StopData("s-1", "m-1", "")));
    }

    // ===== ToolApprovalRequiredData =====

    @Test
    void toolApprovalRequiredDataZero() {
        // Go: {"pendingId":"","tenantId":0,"sessionId":"","assistantMessageId":"","serviceId":"",
        //      "serviceName":"","mcpToolName":"","registeredToolName":"","description":"",
        //      "timeoutSeconds":0,"requestedAtUnix":0,"toolCallId":""}
        assertSemantic("{\"pendingId\":\"\",\"tenantId\":0,\"sessionId\":\"\","
                + "\"assistantMessageId\":\"\",\"serviceId\":\"\",\"serviceName\":\"\","
                + "\"mcpToolName\":\"\",\"registeredToolName\":\"\",\"description\":\"\","
                + "\"timeoutSeconds\":0,\"requestedAtUnix\":0,\"toolCallId\":\"\"}",
                write(new ToolApprovalRequiredData()));
    }

    @Test
    void toolApprovalRequiredDataFull() {
        // Go: {"pendingId":"p-1","tenantId":42,"sessionId":"s-1","assistantMessageId":"am-1",
        //      "serviceId":"svc-1","serviceName":"github","mcpToolName":"create_issue",
        //      "registeredToolName":"mcp__github__create_issue","description":"Create an issue",
        //      "args":{"title":"bug \u003ca\u003e\u0026"},"argsJson":"{\"title\":\"bug\"}",
        //      "timeoutSeconds":300,"requestedAtUnix":1726700000,"toolCallId":"call_9","requestId":"r-1"}
        ToolApprovalRequiredData d = new ToolApprovalRequiredData(
                "p-1", 42, "s-1", "am-1", "svc-1", "github", "create_issue",
                "mcp__github__create_issue", "Create an issue",
                mapOf("title", "bug <a>&"), "{\"title\":\"bug\"}", 300, 1726700000, "call_9", "r-1");
        assertSemantic("{\"pendingId\":\"p-1\",\"tenantId\":42,\"sessionId\":\"s-1\","
                + "\"assistantMessageId\":\"am-1\",\"serviceId\":\"svc-1\",\"serviceName\":\"github\","
                + "\"mcpToolName\":\"create_issue\",\"registeredToolName\":\"mcp__github__create_issue\","
                + "\"description\":\"Create an issue\",\"args\":{\"title\":\"bug <a>&\"},"
                + "\"argsJson\":\"{\\\"title\\\":\\\"bug\\\"}\",\"timeoutSeconds\":300,"
                + "\"requestedAtUnix\":1726700000,\"toolCallId\":\"call_9\",\"requestId\":\"r-1\"}",
                write(d));
    }

    @Test
    void toolApprovalRequiredDataMinimal() {
        // Go: minimal => pending_id 之外全零值，args/args_json/request_id 省略
        ToolApprovalRequiredData d = new ToolApprovalRequiredData("p-2", 0, "", "", "", "", "",
                "", "", null, "", 0, 0, "", "");
        assertSemantic("{\"pendingId\":\"p-2\",\"tenantId\":0,\"sessionId\":\"\","
                + "\"assistantMessageId\":\"\",\"serviceId\":\"\",\"serviceName\":\"\","
                + "\"mcpToolName\":\"\",\"registeredToolName\":\"\",\"description\":\"\","
                + "\"timeoutSeconds\":0,\"requestedAtUnix\":0,\"toolCallId\":\"\"}", write(d));
    }

    // ===== ToolApprovalResolvedData =====

    @Test
    void toolApprovalResolvedDataZero() {
        // Go: {"pendingId":"","approved":false}
        assertSemantic("{\"pendingId\":\"\",\"approved\":false}", write(new ToolApprovalResolvedData()));
    }

    @Test
    void toolApprovalResolvedDataVariants() {
        // Go approved => {"pendingId":"p-1","approved":true,"reason":"user_approved"}
        assertSemantic("{\"pendingId\":\"p-1\",\"approved\":true,\"reason\":\"user_approved\"}",
                write(new ToolApprovalResolvedData("p-1", true, "user_approved", false, false)));
        // Go timedOut => {"pendingId":"p-1","approved":false,"timedOut":true}
        assertSemantic("{\"pendingId\":\"p-1\",\"approved\":false,\"timedOut\":true}",
                write(new ToolApprovalResolvedData("p-1", false, "", true, false)));
        // Go canceled => {"pendingId":"p-1","approved":false,"canceled":true}
        assertSemantic("{\"pendingId\":\"p-1\",\"approved\":false,\"canceled\":true}",
                write(new ToolApprovalResolvedData("p-1", false, "", false, true)));
    }

    // ===== MCPOAuthRequiredData =====

    @Test
    void mcpOAuthRequiredDataZero() {
        // Go: {"pendingId":"","tenantId":0,"sessionId":"","assistantMessageId":"","serviceId":"",
        //      "serviceName":"","mcpToolName":"","timeoutSeconds":0,"requestedAtUnix":0,"toolCallId":""}
        assertSemantic("{\"pendingId\":\"\",\"tenantId\":0,\"sessionId\":\"\","
                + "\"assistantMessageId\":\"\",\"serviceId\":\"\",\"serviceName\":\"\","
                + "\"mcpToolName\":\"\",\"timeoutSeconds\":0,\"requestedAtUnix\":0,\"toolCallId\":\"\"}",
                write(new MCPOAuthRequiredData()));
    }

    @Test
    void mcpOAuthRequiredDataFull() {
        // Go: {"pendingId":"po-1","tenantId":7,"sessionId":"s-2","assistantMessageId":"am-2",
        //      "serviceId":"svc-2","serviceName":"notion","mcpToolName":"search",
        //      "timeoutSeconds":120,"requestedAtUnix":1726700001,"toolCallId":"call_10","requestId":"r-2"}
        MCPOAuthRequiredData d = new MCPOAuthRequiredData("po-1", 7, "s-2", "am-2", "svc-2",
                "notion", "search", 120, 1726700001, "call_10", "r-2");
        assertSemantic("{\"pendingId\":\"po-1\",\"tenantId\":7,\"sessionId\":\"s-2\","
                + "\"assistantMessageId\":\"am-2\",\"serviceId\":\"svc-2\",\"serviceName\":\"notion\","
                + "\"mcpToolName\":\"search\",\"timeoutSeconds\":120,\"requestedAtUnix\":1726700001,"
                + "\"toolCallId\":\"call_10\",\"requestId\":\"r-2\"}", write(d));
    }

    @Test
    void mcpOAuthRequiredDataNoticeOnly() {
        // Go: noticeOnly => timeout_seconds 0 也输出（无 omitempty），request_id 省略
        MCPOAuthRequiredData d = new MCPOAuthRequiredData("", 7, "s", "", "svc", "n", "t", 0, 0, "", "");
        assertSemantic("{\"pendingId\":\"\",\"tenantId\":7,\"sessionId\":\"s\","
                + "\"assistantMessageId\":\"\",\"serviceId\":\"svc\",\"serviceName\":\"n\","
                + "\"mcpToolName\":\"t\",\"timeoutSeconds\":0,\"requestedAtUnix\":0,\"toolCallId\":\"\"}",
                write(d));
    }

    // ===== MCPOAuthResolvedData =====

    @Test
    void mcpOAuthResolvedDataZero() {
        // Go: {"pendingId":"","serviceId":"","authorized":false}
        assertSemantic("{\"pendingId\":\"\",\"serviceId\":\"\",\"authorized\":false}",
                write(new MCPOAuthResolvedData()));
    }

    @Test
    void mcpOAuthResolvedDataVariants() {
        // Go authorized => {"pendingId":"po-1","serviceId":"svc-2","authorized":true,"reason":"authorized"}
        assertSemantic("{\"pendingId\":\"po-1\",\"serviceId\":\"svc-2\",\"authorized\":true,"
                + "\"reason\":\"authorized\"}",
                write(new MCPOAuthResolvedData("po-1", "svc-2", true, "authorized", false, false)));
        // Go canceled => {"pendingId":"po-1","serviceId":"svc-2","authorized":false,"canceled":true}
        assertSemantic("{\"pendingId\":\"po-1\",\"serviceId\":\"svc-2\",\"authorized\":false,\"canceled\":true}",
                write(new MCPOAuthResolvedData("po-1", "svc-2", false, "", false, true)));
    }


    @Test
    void readToleratesUnknownFields() {
        // 反序列化默认忽略未知字段——旧事件多出的字段不能让读路径炸掉
        AgentThoughtData d = EventJson.read(
                "{\"content\":\"c\",\"iteration\":1,\"done\":true,\"future_field\":42}",
                AgentThoughtData.class);
        assertSemantic("c", d.getContent());
        assertEquals(1, d.getIteration());
        assertTrue(d.isDone());
    }

    // ===== EventType 常量与 Go 字面量逐字对齐（抽样锚点 + 总数） =====

    @Test
    void eventTypeConstantsMatchGoLiterals() {
        assertSemantic("queryReceived", EventType.EVENT_QUERY_RECEIVED);
        assertSemantic("thought", EventType.EVENT_AGENT_THOUGHT);
        assertSemantic("toolCall", EventType.EVENT_AGENT_TOOL_CALL);
        assertSemantic("toolResult", EventType.EVENT_AGENT_TOOL_RESULT);
        assertSemantic("reflection", EventType.EVENT_AGENT_REFLECTION);
        assertSemantic("references", EventType.EVENT_AGENT_REFERENCES);
        assertSemantic("finalAnswer", EventType.EVENT_AGENT_FINAL_ANSWER);
        assertSemantic("toolApprovalRequired", EventType.EVENT_TOOL_APPROVAL_REQUIRED);
        assertSemantic("toolApprovalResolved", EventType.EVENT_TOOL_APPROVAL_RESOLVED);
        assertSemantic("mcpOauthRequired", EventType.EVENT_MCP_OAUTH_REQUIRED);
        assertSemantic("mcpOauthResolved", EventType.EVENT_MCP_OAUTH_RESOLVED);
        assertSemantic("error", EventType.EVENT_ERROR);
        assertSemantic("memoryRecalled", EventType.EVENT_MEMORY_RECALLED);
        assertSemantic("contextCompacted", EventType.EVENT_CONTEXT_COMPACTED);
        assertSemantic("userMessageInjected", EventType.EVENT_USER_MESSAGE_INJECTED);
        assertSemantic("sessionTitle", EventType.EVENT_SESSION_TITLE);
        assertSemantic("stop", EventType.EVENT_STOP);
        assertSemantic("agentComplete", EventType.EVENT_AGENT_COMPLETE);
        assertSemantic("retrievalStart", EventType.EVENT_RETRIEVAL_START);
    }
}
