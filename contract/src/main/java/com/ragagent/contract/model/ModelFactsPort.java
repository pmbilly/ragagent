package com.ragagent.contract.model;

/**
 * 模型的跨域只读端口：按 id 读取最小事实集。
 *
 * <p>由 model 域实现（{@code ModelService}），消费方注入接口而非 {@code ModelService} / 实体。
 * 与 {@link ModelCatalogPort} 的分工：本端口只回答“这个 id 的 base_url / api_key / 名称是什么”，
 * 目录与配置面走 {@code ModelCatalogPort}。</p>
 */
public interface ModelFactsPort {

    /** 按 id 查模型事实；查不到返回 {@code null}（调用方保留自己的回落语义）。 */
    ModelFacts findFacts(String modelId);
}
