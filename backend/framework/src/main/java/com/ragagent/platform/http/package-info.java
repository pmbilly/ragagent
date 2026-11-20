/**
 * 平台 HTTP 面：SSRF 策略下的安全抓取工具，以及全进程共享的裸 HTTP 传输层
 * （{@link com.ragagent.platform.http.LlmTransport}：连接级 SSRF 校验 + 兜底超时 + 连接复用，
 * 供模型运行时、websearch、mcp、datasource 等各族共用——它不含模型语义，故落平台）。
 */
package com.ragagent.platform.http;
