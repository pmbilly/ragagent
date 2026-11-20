/**
 * 模型运行时的对外契约：Ollama 本地模型管理端口与进度载荷。
 *
 * <p>原本在 {@code contract.model}，只被本模块与 app 使用。放在 {@code llm} 命名空间下（而不是 {@code modelruntime}）是因为 {@code modelruntime.*} 已依赖 {@code llm.*}，反向放会形成包级环。</p>
 */
package com.ragagent.llm.api;
