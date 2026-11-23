package com.ragagent.audit.assembler;

import com.ragagent.audit.controller.vo.AuditLogItemVo;
import com.ragagent.audit.controller.vo.AuditLogListRespVo;
import com.ragagent.audit.domain.AuditLog;
import com.ragagent.audit.dto.AuditLogListResponse;
import java.util.List;

/** 审计域对象到 HTTP 响应 VO 的装配。 */
public final class AuditLogAssembler {

    private AuditLogAssembler() {
    }

    /** 装配审计列表；{@code nextCursor} 由源 DTO 计算，此处原样透传。 */
    public static AuditLogListRespVo toVo(AuditLogListResponse src) {
        if (src == null) {
            return null;
        }
        List<AuditLog> items = src.items();
        List<AuditLogItemVo> voItems = items == null
                ? null
                : items.stream().map(AuditLogAssembler::toItemVo).toList();
        return new AuditLogListRespVo(voItems, src.nextCursor());
    }

    /** 装配单行审计记录。 */
    public static AuditLogItemVo toItemVo(AuditLog src) {
        if (src == null) {
            return null;
        }
        return new AuditLogItemVo(
                src.getId(),
                src.getTenantId(),
                src.getActorUserId(),
                src.getActorRole(),
                src.getAction(),
                src.getScopeType(),
                src.getScopeId(),
                src.getTargetType(),
                src.getTargetId(),
                src.getTargetUserId(),
                src.getRequestPath(),
                src.getRequestMethod(),
                src.getOutcome(),
                src.getDetails(),
                src.getCreatedAt());
    }
}
