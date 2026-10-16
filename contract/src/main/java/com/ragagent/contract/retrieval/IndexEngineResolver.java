package com.ragagent.contract.retrieval;

/**
 * 索引引擎解析端口：把"租户 + 向量店 id"解析成一个可用的 {@link IndexEngine} 句柄。
 *
 * <p>R3b 契约化：业务域原先自己拿 {@code RetrieveEngineRegistry} + {@code RetrieveEngineFactories}
 * + {@code TenantStoreOwnership} 做绑定解析（含跨租户归属校验与失败分类）——那是**引擎知识**，
 * 沉到 retrieval-engine 一处实现。</p>
 *
 * <p><b>未绑定由调用方判定</b>：调用方先看 KB 是否绑定 store（{@code hasVectorStore()}），
 * 未绑定就保持自己的直连路径，不要调本端口。解析失败（跨租户 FORBIDDEN / 店不可用 UNAVAILABLE /
 * 店未注册 NOT_FOUND）抛 {@link RetrieveEngineException}，调用方按各自可重试策略处理。</p>
 */
public interface IndexEngineResolver {

    /** 解析绑定引擎（{@code vectorStoreId} 非空；失败抛 {@link RetrieveEngineException}）。 */
    IndexEngine resolveFor(long tenantId, String vectorStoreId);

    /**
     * 解析绑定引擎，且**未绑定时回落租户有效引擎**（{@code fallback} 见 {@link TenantRetrievalDefault}）。
     *
     * <p>与上面那条的区别只在"店 id 为空/不可用时怎么办"：这条按租户配置的引擎表 + 部署驱动回落
     * （原 domains 侧 {@code ChunkQuestionService} 的口径），那条直接失败。</p>
     */
    IndexEngine resolveFor(long tenantId, String vectorStoreId, TenantRetrievalDefault fallback);
}
