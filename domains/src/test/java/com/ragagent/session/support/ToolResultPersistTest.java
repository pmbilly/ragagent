package com.ragagent.session.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolResultPersistTest {

    @Test
    @DisplayName("list_knowledge_chunks 历史保留前几条正文，避免模型反复翻页")
    void keepsChunkExcerptForHistory() {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput("full output");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("displayType", "knowledge_chunks_list");
        data.put("knowledgeTitle", "昊铂S600参数配置表.xlsx");
        data.put("fetchedChunks", 30);
        data.put("totalChunks", 67);
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("chunkIndex", i);
            chunk.put("content", "chunk-" + i + "-" + "x".repeat(500));
            chunks.add(chunk);
        }
        data.put("chunks", chunks);
        result.setData(data);

        String history = ToolResultPersist.compactToolOutputForHistory("list_knowledge_chunks", result);

        assertThat(history).contains("Listed 30/67 chunks from 昊铂S600参数配置表.xlsx");
        assertThat(history).contains("[1] chunk #0: chunk-0-");
        assertThat(history).contains("[5] chunk #4: chunk-4-");
        assertThat(history).doesNotContain("[6]");
        assertThat(history).contains("(1 more chunks omitted; use grep_chunks for targeted search)");
        assertThat(history).doesNotContain("content omitted from history");
    }

    @Test
    @DisplayName("没有 chunks 数据时回退旧摘要")
    void fallsBackWhenChunksMissing() {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput("full output");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("displayType", "knowledge_chunks_list");
        data.put("knowledgeTitle", "doc");
        data.put("fetchedChunks", 10);
        data.put("totalChunks", 10);
        result.setData(data);

        String history = ToolResultPersist.compactToolOutputForHistory("list_knowledge_chunks", result);

        assertThat(history).isEqualTo("Listed 10/10 chunks from doc (content omitted from history)");
    }
}
