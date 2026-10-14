/**
 * 索引面（§8.8.2 `index/`）：知识条目与向量/检索引擎的绑定与写入适配。
 *
 * <p> `KnowledgeVectorWrites`（按 KB 解析"已绑定引擎"的只读门面）由 `service/` 归位到本层；
 * 它依赖 `:engine` 的 `retrieval.engine.*`，且被 `task/KnowledgeProcessWorker` 使用——归位后
 * `task → service` 的 9 处引用少一处。</p>
 */
package com.ragagent.knowledge.index;
