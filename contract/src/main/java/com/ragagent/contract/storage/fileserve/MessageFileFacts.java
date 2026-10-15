package com.ragagent.contract.storage.fileserve;

import java.util.List;

/**
 * 消息文件授权所需的事实（L1 载荷）：只取存储侧做"授权 / 匹配"时实际读取的字段，
 * 以免存储侧依赖会话实体。
 *
 * @param content             消息正文（正文里提到引用不算所有权证据，仅作匹配输入之一）
 * @param artifactUrls        产物 URL 清单
 * @param knowledgeReferences 知识引用清单（结构由调用方持有，存储侧只做包含判定）
 * @param images              图片清单（同上）
 * @param toolResults         工具调用结果清单（同上）
 * @param agentTenantId       消息所属 agent 租户（跨租户可见性判定用）
 * @param role                消息角色
 */
public record MessageFileFacts(
        String content,
        List<String> artifactUrls,
        List<?> knowledgeReferences,
        List<?> images,
        List<Object> toolResults,
        long agentTenantId,
        String role) {
}
