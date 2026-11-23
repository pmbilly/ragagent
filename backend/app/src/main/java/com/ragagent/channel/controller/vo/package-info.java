/**
 * 渠道模块的 HTTP 出参：只承载传输形状，不参与业务判断。
 *
 * <p>与领域内 DTO 的分工：本包对象由装配层产生、序列化后即为响应字节；领域 DTO 可随内部
 * 演进调整，不直接出现在 HTTP 契约上。</p>
 */
package com.ragagent.channel.controller.vo;
