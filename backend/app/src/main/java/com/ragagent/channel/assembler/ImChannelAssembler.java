package com.ragagent.channel.assembler;

import com.ragagent.channel.controller.vo.ImChannelRowRespVo;
import com.ragagent.channel.controller.vo.ImChannelSummaryRespVo;
import com.ragagent.channel.dto.ImChannelRows;
import java.util.List;

/** IM 渠道响应 DTO 到 HTTP VO 的装配。 */
public final class ImChannelAssembler {

    private ImChannelAssembler() {
    }

    /** 装配渠道资源行。 */
    public static ImChannelRowRespVo toChannelVo(ImChannelRows.ChannelRow src) {
        if (src == null) {
            return null;
        }
        return new ImChannelRowRespVo(
                src.id(),
                src.tenantId(),
                src.agentId(),
                src.platform(),
                src.name(),
                src.enabled(),
                src.mode(),
                src.outputMode(),
                src.knowledgeBaseId(),
                src.botIdentity(),
                src.sessionMode(),
                src.credentials(),
                src.createdAt(),
                src.updatedAt(),
                src.deletedAt());
    }

    /** 装配渠道列表行。 */
    public static ImChannelSummaryRespVo toSummaryVo(ImChannelRows.ChannelSummary src) {
        if (src == null) {
            return null;
        }
        return new ImChannelSummaryRespVo(
                src.id(),
                src.tenantId(),
                src.agentId(),
                src.platform(),
                src.name(),
                src.enabled(),
                src.mode(),
                src.outputMode(),
                src.knowledgeBaseId(),
                src.botIdentity(),
                src.sessionMode(),
                src.credentialsConfigured(),
                src.createdAt(),
                src.updatedAt());
    }

    /** 批量装配列表行，保持原有顺序。 */
    public static List<ImChannelSummaryRespVo> toSummaryVoList(List<ImChannelRows.ChannelSummary> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(ImChannelAssembler::toSummaryVo).toList();
    }
}
