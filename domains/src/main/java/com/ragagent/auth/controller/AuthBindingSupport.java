package com.ragagent.auth.controller;

import com.ragagent.auth.domain.User;
import com.ragagent.auth.dto.AuthLoginResponse;
import com.ragagent.auth.dto.Membership;
import com.ragagent.auth.dto.TenantResponse;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.tenant.TenantRole;
import com.ragagent.contract.tenant.TenantRowView;
import com.ragagent.platform.web.JsonBodyErrors;
import com.ragagent.platform.web.RequestFields;
import java.util.List;

/**
 * AuthController 的绑定支持簇：登录响应组装、请求体宽松读（未知字段忽略）、
 * gin 风格 binding 文案（供保留手绑的 refresh / switch-tenant 端点使用）、AppError 工厂。
 */
final class AuthBindingSupport {

    AuthLoginResponse buildAuthLoginResponse(User user, TenantRowView activeTenant,
                                             List<Membership> memberships,
                                             String token, String refreshToken) {
        TenantResponse tenantResp = null;
        if (activeTenant != null) {
            String role = AuthController.membershipRoleForTenant(memberships,
                    activeTenant.getId());
            tenantResp = TenantResponse.from(activeTenant,
                    TenantRole.fromString(role).hasPermission(TenantRole.ADMIN));
        }
        return new AuthLoginResponse(user, tenantResp, memberships, token, refreshToken);
    }


    <T> T parseBody(String rawBody, Class<T> type, String message) {
        if (rawBody == null || rawBody.isBlank()) {
            throw invalidParams(message, JsonBodyErrors.EMPTY_BODY);
        }
        try {
            // Jackson 默认忽略未知字段（与既定绑定语义一致）
            return AuthController.MAPPER.readValue(rawBody, type);
        } catch (Exception e) {
            throw invalidParams(message, e.getMessage());
        }
    }


    static String bindingError(String field, String tag) {
        return RequestFields.message(field, tag);
    }


    static BizException invalidParams(String message, String details) {
        return new BizException(AppError.validation(message).withDetails(details));
    }


}
