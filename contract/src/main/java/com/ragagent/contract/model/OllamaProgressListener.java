package com.ragagent.contract.model;

/** 拉取进度回调（每收到一行 NDJSON 触发一次）。 */
@FunctionalInterface
public interface OllamaProgressListener {

    /** 单次进度。 */
    void onProgress(OllamaPullProgress progress);
}
