/**
 * 初始化与能力自检域：{@code /api/v1/initialization/*} 的初始化配置、Ollama 管理、
 * 模型连通性测试、文本抽取试验与语音识别（ASR）。本域只负责“把模型/语音/抽取能力跑起来”，
 * 智能体定义在 {@code agentmanagement}。
 *
 * <p><b>种类</b>：初始化向导<br>
 * <b>入口</b>：{@code InitializationController}<br>
 * <b>对外契约面</b>：无（编排 {@code contract.model} / {@code contract.knowledge} 端口）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 *
 * <p>响应中的内层 snake 键（agent config jsonb 族）是跨包登记冻结边界，保持 snake。</p>
 */
package com.ragagent.initialization;
