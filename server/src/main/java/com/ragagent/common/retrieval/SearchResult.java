package com.ragagent.common.retrieval;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.common.web.SortedMapSerializer;

/**
 * 检索结果条目。
 *
 * <p>本类是共享契约：{@link com.ragagent.llm.domain.StreamResponse#getKnowledgeReferences()} 与
 * {@code Message.knowledge_references} 的载荷都是它，
 * SSE 的 {@code references} 事件要按它的字段序逐字节输出。</p>
 *
 * <h2>字段序</h2>
 * <p>响应按 {@link JsonPropertyOrder} 声明序输出，必须与下方字段声明一致。</p>
 *
 * <h2>零值语义（逐字段）</h2>
 * <ul>
 *   <li><b>恒输出，null 输出 {@code null}</b>：
 *       {@code sub_chunk_id}（null 列表 → {@code null}）、{@code metadata}（null 映射 → {@code null}）。</li>
 *   <li><b>数值/字符串恒输出零值</b>：{@code match_type} 输出 {@code 0}、
 *       {@code score} 输出 {@code 0}、{@code chunk_type} 输出 {@code ""}。</li>
 *   <li><b>空时省略整个键</b>：{@code chunk_metadata} /
 *       {@code matched_content} / {@code knowledge_description} /
 *       {@code knowledge_custom_metadata} / {@code knowledge_base_id}。</li>
 * </ul>
 *
 * <h2>两个不出响应的内部字段</h2>
 * <p>{@code ContentRevision} / {@code ContentRewritten} 是合并管线内部字段：
 * <b>不出响应</b>（{@code @JsonIgnore}），但 {@code content_revision} 有对应的数据库列，
 * 直查行时要能落进对象。故用 {@code @JsonIgnore} 而非删字段。</p>
 *
 * <h2>{@code score} 的浮点输出</h2>
 * <p>走 Jackson 默认的 double 序列化（{@code 1.0} / {@code 1.0E21} 形态）。</p>
 */
public class SearchResult {

    private String id = "";

    private String content = "";

    private String knowledgeId = "";

    private int chunkIndex;

    private String knowledgeTitle = "";

    private int startAt;

    private int endAt;

    private int seq;

    /** 相似度/融合分。恒输出（Jackson 默认 double 形态，零值输出 {@code 0.0}）。 */

    private double score;

    /**
     * 匹配算法（int 枚举，0 = embedding、1 = keywords）。
     * 恒输出数字。
     */
    private int matchType;

    /** 子 chunk ID。恒输出：null 输出 {@code null}（不是 {@code []}）。 */
    private List<String> subChunkId;

    /**
     * 元数据。恒输出：null 输出 {@code null}。
     * 输出**恒按 key 字母序**：setter 归一化为 {@link TreeMap}——
     * 无论产出方给的是什么 Map 实现，输出字节都一致。
     */
    private Map<String, String> metadata;

    private String chunkType = "";

    private String parentChunkId = "";

    private String imageInfo = "";

    private String knowledgeFilename = "";

    private String knowledgeSource = "";

    private String knowledgeChannel = "";

    /**
     * chunk 级元数据（如生成的问题）。原样内联的 JSON 载荷，null 时整键省略。
     *
     * <p>已知边界：{@code @JsonInclude(NON_NULL)} 只看 null，产出方若真写入空对象
     * {@code {}}，它仍会输出——实际语义里该字段要么是结构化 JSON 要么不设，
     * 这个角落不作特判。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonNode chunkMetadata;

    /** 向量检索实际命中的文本（FAQ 场景是命中的问题）。空时整键省略。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String matchedContent;

    /** 知识条目描述。空时整键省略。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String knowledgeDescription;

    /** 用户自撰、可安全下发给模型的上下文。空时整键省略。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String knowledgeCustomMetadata;

    /** 所属知识库 ID。空时整键省略。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String knowledgeBaseId;

    /** 检索时 chunk 的编辑版本号。**仅内部**（不出响应），有对应的数据库列。 */
    @JsonIgnore
    private int contentRevision;

    /** 合并管线是否改写过 {@code content}。**仅内部**（不出响应）。 */
    @JsonIgnore
    private boolean contentRewritten;

    public String getId() { return id; }
    public void setId(String v) { id = v == null ? "" : v; }

    public String getContent() { return content; }
    public void setContent(String v) { content = v == null ? "" : v; }

    public String getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(String v) { knowledgeId = v == null ? "" : v; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int v) { chunkIndex = v; }

    public String getKnowledgeTitle() { return knowledgeTitle; }
    public void setKnowledgeTitle(String v) { knowledgeTitle = v == null ? "" : v; }

    public int getStartAt() { return startAt; }
    public void setStartAt(int v) { startAt = v; }

    public int getEndAt() { return endAt; }
    public void setEndAt(int v) { endAt = v; }

    public int getSeq() { return seq; }
    public void setSeq(int v) { seq = v; }

    public double getScore() { return score; }
    public void setScore(double v) { score = v; }

    public int getMatchType() { return matchType; }
    public void setMatchType(int v) { matchType = v; }

    public List<String> getSubChunkId() { return subChunkId; }
    public void setSubChunkId(List<String> v) { subChunkId = v; }

    public Map<String, String> getMetadata() { return metadata; }

    /** 归一化为按 UTF-8 字节序排好的 {@link TreeMap}，见字段注释。 */
    public void setMetadata(Map<String, String> v) {
        if (v == null) {
            metadata = null;
            return;
        }
        TreeMap<String, String> sorted = new TreeMap<>(SortedMapSerializer.KEY_BYTE_ORDER);
        sorted.putAll(v);
        metadata = sorted;
    }

    public String getChunkType() { return chunkType; }
    public void setChunkType(String v) { chunkType = v == null ? "" : v; }

    public String getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(String v) { parentChunkId = v == null ? "" : v; }

    public String getImageInfo() { return imageInfo; }
    public void setImageInfo(String v) { imageInfo = v == null ? "" : v; }

    public String getKnowledgeFilename() { return knowledgeFilename; }
    public void setKnowledgeFilename(String v) { knowledgeFilename = v == null ? "" : v; }

    public String getKnowledgeSource() { return knowledgeSource; }
    public void setKnowledgeSource(String v) { knowledgeSource = v == null ? "" : v; }

    public String getKnowledgeChannel() { return knowledgeChannel; }
    public void setKnowledgeChannel(String v) { knowledgeChannel = v == null ? "" : v; }

    public JsonNode getChunkMetadata() { return chunkMetadata; }
    public void setChunkMetadata(JsonNode v) { chunkMetadata = v; }

    public String getMatchedContent() { return matchedContent; }
    public void setMatchedContent(String v) { matchedContent = v; }

    public String getKnowledgeDescription() { return knowledgeDescription; }
    public void setKnowledgeDescription(String v) { knowledgeDescription = v; }

    public String getKnowledgeCustomMetadata() { return knowledgeCustomMetadata; }
    public void setKnowledgeCustomMetadata(String v) { knowledgeCustomMetadata = v; }

    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String v) { knowledgeBaseId = v == null ? "" : v; }

    /**
     * 浅拷贝。
     *
     * <p>{@code Rewriter.CopyReferences} 用它来"复制后再就地改写"，因为 SSE 的 references
     * 载荷与流的重放缓冲、以及正在落库的助手消息**共享同一批 {@code *SearchResult} 指针**——
     * 就地改写会把那两处一起弄坏。</p>
     *
     * <p>名字不是 {@code getXxx}/{@code isXxx}，Jackson 不会把它当属性——这正是要的。</p>
     */
    public SearchResult copy() {
        SearchResult c = new SearchResult();
        c.id = id;
        c.content = content;
        c.knowledgeId = knowledgeId;
        c.chunkIndex = chunkIndex;
        c.knowledgeTitle = knowledgeTitle;
        c.startAt = startAt;
        c.endAt = endAt;
        c.seq = seq;
        c.score = score;
        c.matchType = matchType;
        c.subChunkId = subChunkId;
        c.metadata = metadata;
        c.chunkType = chunkType;
        c.parentChunkId = parentChunkId;
        c.imageInfo = imageInfo;
        c.knowledgeFilename = knowledgeFilename;
        c.knowledgeSource = knowledgeSource;
        c.knowledgeChannel = knowledgeChannel;
        c.chunkMetadata = chunkMetadata;
        c.matchedContent = matchedContent;
        c.knowledgeDescription = knowledgeDescription;
        c.knowledgeCustomMetadata = knowledgeCustomMetadata;
        c.knowledgeBaseId = knowledgeBaseId;
        c.contentRevision = contentRevision;
        c.contentRewritten = contentRewritten;
        return c;
    }

    public int getContentRevision() { return contentRevision; }
    public void setContentRevision(int v) { contentRevision = v; }

    public boolean isContentRewritten() { return contentRewritten; }
    public void setContentRewritten(boolean v) { contentRewritten = v; }
}
