/**
 * Agent 引擎的对外契约：执行请求/句柄/工厂、工具能力、技能目录、预算与流式信号。
 *
 * <p>这些类型原本在 {@code contract.agent} / {@code contract.tool}，只被本模块与 app 使用。
 * 放在 {@code agentengine} 命名空间（而不是 {@code agent.*}）是因为 {@code agent} 是模块内的
 * hub 包，已依赖全部子包——契约若挂在它下面，子包引用契约会与 hub 形成包级环。</p>
 */
package com.ragagent.agentengine.api;
