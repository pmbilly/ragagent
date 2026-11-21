package com.ragagent.knowledge.api.config;


/** ASR（音频转写）配置视图。 */
public record AsrConfigView(boolean enabled, String modelId, String language) {
}
