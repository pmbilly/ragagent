package com.ragagent.llm.limiter;

import java.util.List;

/**
 * 限流后端的可选观测能力。
 *
 * <p>{@link ConcurrencyGovernor#runtimeStats()} 用它把"后端是否支持观测"与
 * 具体实现类型解耦：本地信号量与 Redis 信号量都实现本接口，将来新增后端
 * 也只需实现它，不用再改 Governor 的 instanceof 链。</p>
 *
 * <p>语义：Active 在 Redis 后端是<b>全局</b>（跨实例在途数）、在本地后端是
 * 进程内；Waiting 始终是进程内（等待者阻塞在各自的应用进程里）。</p>
 */
public interface RuntimeInspectable {

    /** 按 model ID 升序返回各信号量的观测快照；后端故障时尽力而为（跳过查不到的项）。 */
    List<RuntimeStat> runtimeStats();
}
