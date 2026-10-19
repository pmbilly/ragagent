package com.ragagent.memory.domain;

/**
 * 蒸馏任务的执行端口：队列实现只依赖本接口，不依赖用例层类型。
 *
 * <p>这样 {@code runtime}（队列实现）与 {@code service}（执行方）都只指向
 * {@code domain}，不形成包级环。</p>
 */
public interface MemoryExtractHandler {

    /** 执行一次蒸馏；实现方负责自身的幂等与租约认领。 */
    void handle(MemoryExtractPayload payload);
}
