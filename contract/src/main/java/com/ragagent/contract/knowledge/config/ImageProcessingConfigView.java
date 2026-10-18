package com.ragagent.contract.knowledge.config;


/** 图像处理配置视图（仅模型 ID，描述由 VLM 配置驱动）。 */
public record ImageProcessingConfigView(String modelId) {
}
