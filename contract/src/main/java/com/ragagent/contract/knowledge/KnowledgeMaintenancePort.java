package com.ragagent.contract.knowledge;

/**
 * 知识条目的**清理端口**（跨域删除面，与建行端口 {@link KnowledgeBaseProvisioner}、
 * 只读端口 {@link KnowledgeBaseGateway} 分列）。
 *
 * <p><b>为什么需要它</b>：websearch 的临时 KB 会话状态在会话删除时要清理
 * ——先删知识条目、再删知识库、最后删 Redis 键。此前 websearch 直连
 * {@code knowledge.service.management.KnowledgeService} /
 * {@code KnowledgeBaseService}（域服务 ✗）。本端口只暴露"删"两个动作，
 * 软删/级联/索引清理等语义归知识域实现。</p>
 *
 * <p>调用方对单条失败的处理是"逐条 warn 后继续"——实现照常抛运行时异常即可。</p>
 */
public interface KnowledgeMaintenancePort {

    /** 删除单个知识条目（软删/索引清理等语义归实现方）。 */
    void deleteKnowledge(String knowledgeId);

    /** 删除知识库。 */
    void deleteKnowledgeBase(String knowledgeBaseId);
}
