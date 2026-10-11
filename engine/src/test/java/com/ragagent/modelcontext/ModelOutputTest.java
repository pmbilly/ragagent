package com.ragagent.modelcontext;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.common.llm.ToolResult;

/**
 * 空结果的输出契约与模型面渲染：
 * 内置工具把"空表"按输出契约序列化为 <b>null</b>（不是 {@code []}）——grep 零命中时的
 * {@code chunkResults}、持久化投影会剥离的 {@code chunks} 等。
 * 渲染层遇到这些 null 必须与"零行"同路回落原始文本，不得 NPE 打死整轮（实测事故：
 * grep 零命中 → agent 回合以空回答结束）。
 */
class ModelOutputTest {

    private static final String RAW = "<retrieval>raw-fallback-text</retrieval>";

    private static ToolResult result(String displayType, Map<String, Object> fields) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput(RAW);
        Map<String, Object> data = new LinkedHashMap<>(fields);
        data.put("displayType", displayType);
        result.setData(data);
        return result;
    }

    private static String render(String toolName, ToolResult result) {
        return new Registry(true).modelToolResultForTool(toolName, result);
    }

    @Test
    @DisplayName("grep 零命中（chunkResults=null）回落原始输出")
    void grepEmptyChunkResultsFallsBack() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunkResults", null);
        data.put("knowledgeResults", null);
        data.put("resultCount", 0);

        String out = render("grep_chunks", result("grep_results", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }

    @Test
    @DisplayName("检索无结果（results=null）回落原始输出")
    void searchEmptyResultsFallsBack() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", null);

        String out = render("knowledge_search", result("search_results", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }

    @Test
    @DisplayName("图谱查询无结果（results=null）回落原始输出")
    void graphEmptyResultsFallsBack() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", null);

        String out = render("query_knowledge_graph", result("graph_query_results", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }

    @Test
    @DisplayName("chunk 列表缺 chunks 键（持久化剥离）回落原始输出")
    void knowledgeChunksMissingKeyFallsBack() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgeId", "k-1");
        data.put("totalChunks", 10);
        data.put("fetchedChunks", 0);

        String out = render("list_knowledge_chunks", result("knowledge_chunks_list", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }

    @Test
    @DisplayName("数据库查询无行（rows=null）回落原始输出")
    void databaseQueryEmptyRowsFallsBack() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", null);

        String out = render("database_query", result("database_query", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }

    @Test
    @DisplayName("网页搜索/抓取空结果（results=null）回落原始输出")
    void webOutputsFallBackOnNullRows() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", null);

        assertTrue(render("web_search", result("web_search_results", data)).contains("raw-fallback-text"));
        assertTrue(render("web_fetch", result("web_fetch_results", data)).contains("raw-fallback-text"));
    }

    @Test
    @DisplayName("文档信息空结果（documents=null）回落原始输出")
    void documentInfoFallsBackOnNullRows() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documents", null);

        // 该工具的 ToolPolicy 可能不走模型面渲染，这里直接对渲染入口断言。
        String out = ModelOutput.modelOutput(
                new SourceRegistry(true), result("document_info", data));
        assertTrue(out.contains("raw-fallback-text"), out);
    }
}
