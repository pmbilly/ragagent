package com.ragagent.common.session.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ragagent.common.session.embed.SessionView;
import com.ragagent.common.session.domain.message.MessageAttachment;

/**
 * QA 请求载荷（L1）：IM 等装配面构造、会话面消费的最小字段集。
 *
 * <p><b>为什么单独一个载荷</b>：会话域的运行容器 {@code QaSupport.QaRequest} 含
 * family 类型字段（{@code CustomAgentEntity} / {@code SteerSink}）与大量会话内部状态，
 * 不能上提 L1；而装配面（IM）只填下面这些字段。字段名与语义与容器**逐字对应**，
 * 会话侧适配器按字段一一搬运（其余字段保持容器默认值，与装配面直建容器时的行为一致）。</p>
 */
public final class QaRequestPayload {

    /** 会话实体（已 L1）。 */
    public SessionView session;

    public String query = "";

    public String assistantMessageId = "";

    public String userMessageId = "";

    /** 未带实体的调用面只传 agent id；会话面按 id 解析实体。 */
    public String agentId = "";

    /** 归一化后的 agent 配置（缺省补全在装配面完成）。 */
    public ObjectNode agentConfig;

    public boolean webSearchEnabled;

    public String quotedContext = "";

    /** 取消探针：null=存活；非 null 时返回 null=未取消、非 null=取消错误原文。 */
    public Supplier<String> cancellationProbe;

    /** 附件元数据（默认空表，与容器默认一致）。 */
    public List<MessageAttachment> attachments = new ArrayList<>();

    /** vision 图片（data URI）；默认 null，与容器默认一致。 */
    public java.util.List<String> imageUrls;
}
