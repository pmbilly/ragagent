package com.ragagent.session.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolResultPersistTest {

    @Test
    @DisplayName("list_knowledge_chunks 历史保留完整输出，不再压缩成摘要")
    void keepsFullChunkOutputForHistory() {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput("full chunk output");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("displayType", "knowledge_chunks_list");
        data.put("knowledgeTitle", "昊铂S600参数配置表.xlsx");
        data.put("fetchedChunks", 30);
        data.put("totalChunks", 67);
        result.setData(data);

        String history = ToolResultPersist.compactToolOutputForHistory("list_knowledge_chunks", result);

        assertThat(history).isEqualTo("full chunk output");
        assertThat(history).doesNotContain("content omitted from history");
    }

    @Test
    @DisplayName("没有输出时回退摘要")
    void fallsBackWhenOutputMissing() {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput("");
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
