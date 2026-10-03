package com.ragagent.vectorstore.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 向量库索引配置。
 * 空字段整键省略；未知键容忍（jsonb 演进 + 其它引擎的键可能出现在历史行里——
 * 序列化形态不得因引擎不同而变化）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexConfig {

    public String indexName = "";
    public int numberOfShards;
    public int numberOfReplicas;
    public String collectionPrefix = "";
    public String collectionName = "";
    public int shardNumber;
    public int replicationFactor;
    public int shardsNum;
    public int replicaNumber;
    public int desiredShardCount;
    public int bucketsNum;
    public int replicationNum;
    public int hnswM;
    public int hnswEfConstruction;
    public int hnswEfSearch;
    public String knnEngine = "";

    /** 引擎缺省索引名（服务层去重用；env 回退不在本层）。@JsonIgnore 同上 */
    @JsonIgnore
    public String getIndexNameOrDefault(String engineType) {
        return switch (engineType == null ? "" : engineType) {
            case "elasticsearch" -> notEmpty(indexName) ? indexName : "xwrag_default";
            case "qdrant" -> notEmpty(collectionPrefix) ? collectionPrefix : "weknora_embeddings";
            case "milvus" -> notEmpty(collectionName) ? collectionName : "weknora_embeddings";
            case "tencent_vectordb" -> notEmpty(collectionName) ? collectionName : "weknora_embeddings";
            case "weaviate" -> notEmpty(collectionPrefix) ? collectionPrefix : "Weknora_embeddings";
            case "doris" -> {
                if (notEmpty(collectionPrefix)) {
                    yield collectionPrefix;
                }
                yield notEmpty(collectionName) ? collectionName : "weknora_embeddings";
            }
            default -> indexName;
        };
    }

    static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
