# 反直觉机制

这些地方"读起来应该有一个东西，实际却没有"或"看起来该这样改，实际不能"。每条按**现象 / 原因 / 去哪改**写。

## 1. 队列没有独立的 consumer

**现象**：搜"谁消费队列"找不到一个 `Consumer` 类。

**原因**：服务自己就是消费者：

- `KnowledgeProcessWorker implements KnowledgeProcessingQueue`（`domains/.../knowledge/task/KnowledgeProcessWorker.java:77`）；
- `DataSourceService implements DataSourceSyncHandler`（`domains/.../datasource/service/DataSourceService.java:89`）。

队列接口是给**跨域调用方**用的端口；实现类同时提供入队与执行。Go 时代的 service 结构就是"方法即处理器"，迁移时保留了这一形态。

**去哪改**：入队与消费都在上述两个类；改重试/租约/崩溃回收时看对应 `*TaskQueue` 与 `*Wiring`。

## 2. 五套队列，各有 In-Process / Redis 双实现

| 队列 | 接口 | 进程内 | Redis |
|---|---|---|---|
| 分块抽取 | `ChunkExtractTaskQueue` | `InProcessChunkExtractTaskQueue` | `RedisChunkExtractTaskQueue` |
| 问题生成 | `QuestionGenerationTaskQueue` | `InProcessQuestionGenerationTaskQueue` | `RedisQuestionGenerationTaskQueue` |
| Wiki 摄取 | `WikiIngestTaskQueue` | `InProcessWikiIngestTaskQueue` | `RedisWikiIngestTaskQueue` |
| 记忆蒸馏 | `MemoryExtractTaskQueue` | `InProcessMemoryExtractTaskQueue` | `RedisMemoryExtractTaskQueue` |
| 数据源同步 | `DataSourceSyncTaskQueue` | `InProcessDataSourceSyncTaskQueue` | `RedisDataSourceSyncTaskQueue` |

**原因**：单实例默认用进程内；多实例/崩溃回收用 Redis。选择方式是 `@ConditionalOnProperty(prefix="<域>", name="redis-enabled")` + `@Primary` 覆盖无条件的 `@Component` 实现——**不是** `@Profile`。

**去哪改**：`KnowledgeRedisWiring`、`MemoryTaskQueueWiring`、`DataSourceTaskQueueWiring` 与 `WikiRedisWiring`；开关见 `.env.example` 的 `*_REDIS_ENABLED`。打开后启动时会 ping 一次 Redis，连不上直接失败（不静默退化）。

## 3. embedding 有两条路径

**现象**：同时存在 `domains/.../knowledge/client/EmbedderClient` 与 `model-runtime/.../embedding/EmbedderFactory`。

**分叉点**在 `ChunkVectorIndexer.indexAndStore`：

- KB **绑定了外部 vector store**（`vectorWrites.boundEngine(...) != null`）→ 走引擎口：`modelRuntimeAssembler.getEmbeddingModel(...)` 拿 `Embedder`，批量/重试由引擎承担；
- **未绑定**（本地 pg 直连）→ `EmbedderClient.configFrom(...)` 直接 `POST {base_url}/embeddings`。

查询侧同理：`HybridSearchService` 对 pg 用 `EmbeddingGateway`（`EmbedderClient` 实现）；外部引擎在自己的 `batchIndex` / search 内部嵌入。

**原因**：外部引擎的批大小、超时、重试语义由引擎决定；本地 pg 保留 Go 时代的直连路径，避免为了"统一"引入一层不必要的工厂。

**去哪改**：`ChunkVectorIndexer`、`EmbedderClient`、`HybridSearchService`；不要只改其中一条而假定另一条也会变。

## 4. 全仓 0 处 `@Scheduled`

**现象**：`grep @Scheduled` 为空，容易得出"没有定时任务"。

**原因**：定时/周期行为是自建调度或队列驱动：

- `domains/.../datasource/task/Scheduler.java`：数据源同步的计划触发；
- knowledge / wiki / memory 的 worker：队列驱动 + 租约/重试；
- `boot/.../StartupTaskRecovery.java`：启动时回收未完成任务。

**原因**：多实例下 `@Scheduled` 会在每个副本各触发一次；自建调度要配合 Redis 锁/选主/去重。

**去哪改**：先确认是"单实例定时"还是"多实例协作"；后者看 `*RedisWiring` 与 `Scheduler`。

## 5. session 域 0 事务，知识/Wiki 仓储层有事务

**现象**：`domains/.../session` 没有 `@Transactional`；knowledge/wiki 的仓储层有；另有少量 `TransactionTemplate`（`StorageBackendService`、`VectorStoreConfigService`）。

**原因**：一轮问答横跨 LLM 流式输出（数秒到数分钟）、外部调用与 SSE。把整轮包进一个事务会长时间持锁，且外部副作用无法回滚。因此：

- 单条消息的落库/状态推进各自成事务；
- 失败路径由 `QaTurnFinalizer` 手工补偿（提交/回滚/后续轮次交接）；
- 需要原子性的多表写才用 `TransactionTemplate`。

**规则**：不要给 `QaTurnExecutor` / `SessionKnowledgeQaService` 加一个大 `@Transactional`；它会把流式期间的所有读也卷进同一个连接。

**去哪改**：`QaTurnFinalizer`、各域 repository、`StorageBackendService`。

## 6. 身份不随虚拟线程传播

**现象**：`TenantContext` 是 ThreadLocal；虚拟线程/异步任务里 `currentTenantId()` 为空，查询静默 0 行。

**原因**：JDK 虚拟线程不继承 ThreadLocal（即使配置了 inheritable，也不适合请求级身份）。必须在边界显式快照：

```java
TenantContextSnapshot snap = TenantContextSnapshot.capture();
Thread.ofVirtual().start(() -> TenantScope.run(snap, () -> {
    // 这里能读到捕获时刻的租户/身份；退出时自动恢复原上下文
}));
```

全仓 15 个 `capture()` 站点（原先 30+ 处手工 `replay()`）已统一收口到 `TenantScope`（`run` / `supply` / `callable` / `runnable`），覆盖 domains / agent-engine / retrieval-engine / model-runtime / platform / boot，包括 `QaSseOrchestrator`、`QaTurnFinalizer`、`QaTurnExecutor`、`SteerSinkBridge`、`MessageSuggestionService`、`ActPhase`、`EventBus`、`HybridSearchService` 等。

**去哪改**：`platform/.../access/TenantScope.java`；守卫 `scripts/check-tenant-scope.py` 拦裸 `.replay()` 与未走 TenantScope 的 `capture()`。

## 7. StreamManager 是 append-only，SSE 靠 100ms 轮询

**现象**：生成侧写事件与输出侧读事件不是同一个线程；日志时间戳与帧顺序对不上。

**原因**：

- `AgentStreamBridge` 订阅 16 种事件，按到达序 `appendEvent(...)`；
- `SessionStreamController` / `SseFrameWriter` 每 `100ms` 轮询 `getEvents(fromOffset)`（`SessionStreamController.POLL_INTERVAL_MILLIS = 100`）；
- `StreamManager` 只有 append/get，没有"推送"；`complete` 是结束信号；
- steer（运行中转向）走独立子列表，永不混进用户可见的 SSE 流。

**排障规则**：以 `messageId + event id/offset` 为唯一事实，不要用日志顺序推断帧顺序；断流检测靠写失败（`IOException`），不是连接回调。

**去哪改**：`platform/.../stream/StreamManager.java`、`AgentStreamBridge`、`SessionStreamController`。

## 8. 新增 `@ConfigurationProperties` 可能"静默不生效"

**现象**：新建了一个 `@ConfigurationProperties` 类，启动不报错，但值一直是最小默认值。

**原因**：`RagAgentApplication` 用的是**白名单扫描**，不是 `com.ragagent` 根包：

```java
@ConfigurationPropertiesScan({"com.ragagent.config", "com.ragagent.platform.settings", ...})
```

类不在列表里就不会被绑定。

**去哪改**：`boot/.../RagAgentApplication.java` 的注解；新增属性类时把包加进去。不要改成根包扫描——会把一批未注册的配置类一并绑定，属行为变化。

## 相关文档

- 调用链：[`request-path.md`](request-path.md)
- 管线段落：[`rag-pipeline.md`](rag-pipeline.md)
- 模块总览：[`overview.md`](overview.md)
