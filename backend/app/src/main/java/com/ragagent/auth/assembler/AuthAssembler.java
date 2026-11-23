package com.ragagent.auth.assembler;

import com.ragagent.auth.controller.vo.APIPrincipalConfigRespVo;
import com.ragagent.auth.controller.vo.APIPrincipalTestTokenRespVo;
import com.ragagent.auth.controller.vo.AuthConfigRespVo;
import com.ragagent.auth.controller.vo.AuthLoginRespVo;
import com.ragagent.auth.controller.vo.CurrentUserRespVo;
import com.ragagent.auth.controller.vo.InvitationLookupRespVo;
import com.ragagent.auth.controller.vo.MembershipVo;
import com.ragagent.auth.controller.vo.OidcAuthUrlRespVo;
import com.ragagent.auth.controller.vo.OidcConfigRespVo;
import com.ragagent.auth.controller.vo.TenantInvitationRespVo;
import com.ragagent.auth.controller.vo.TenantMemberRespVo;
import com.ragagent.auth.controller.vo.TenantRespVo;
import com.ragagent.auth.controller.vo.TokenPairRespVo;
import com.ragagent.auth.controller.vo.UserCapabilitiesVo;
import com.ragagent.auth.controller.vo.UserInfoVo;
import com.ragagent.auth.dto.APIPrincipalDtos;
import com.ragagent.auth.dto.AuthConfigResponse;
import com.ragagent.auth.dto.AuthLoginResponse;
import com.ragagent.auth.dto.CurrentUserResponse;
import com.ragagent.auth.dto.InvitationLookupResponse;
import com.ragagent.auth.dto.Membership;
import com.ragagent.auth.dto.OidcAuthUrlResponse;
import com.ragagent.auth.dto.OidcConfigResponse;
import com.ragagent.auth.dto.TenantInvitationResponse;
import com.ragagent.auth.dto.TenantMemberResponse;
import com.ragagent.auth.dto.TenantResponse;
import com.ragagent.auth.dto.TokenPairResponse;
import com.ragagent.auth.dto.UserCapabilities;
import com.ragagent.auth.dto.UserInfo;
import java.util.List;

/** 认证域响应 DTO 到 HTTP VO 的装配。 */
public final class AuthAssembler {

    private AuthAssembler() {
    }

    /** 装配认证配置。 */
    public static AuthConfigRespVo toAuthConfigVo(AuthConfigResponse src) {
        if (src == null) {
            return null;
        }
        return new AuthConfigRespVo(
                src.complexPasswordEnabled(), src.registrationMode(), src.edition());
    }

    /** 装配登录响应；用户字段按实体投影原样透传。 */
    public static AuthLoginRespVo toLoginVo(AuthLoginResponse src) {
        if (src == null) {
            return null;
        }
        return new AuthLoginRespVo(
                src.user(),
                toTenantVo(src.activeTenant()),
                toMembershipVoList(src.memberships()),
                src.token(),
                src.refreshToken());
    }

    /** 装配当前用户视图。 */
    public static CurrentUserRespVo toCurrentUserVo(CurrentUserResponse src) {
        if (src == null) {
            return null;
        }
        return new CurrentUserRespVo(
                toCapabilitiesVo(src.capabilities()),
                toMembershipVoList(src.memberships()),
                src.preferenceDefaults(),
                toTenantVo(src.tenant()),
                src.tenantRequired(),
                toUserInfoVo(src.user()));
    }

    /** 装配用户信息。 */
    public static UserInfoVo toUserInfoVo(UserInfo src) {
        if (src == null) {
            return null;
        }
        return new UserInfoVo(
                src.id(),
                src.username(),
                src.email(),
                src.avatar(),
                src.tenantId(),
                src.isActive(),
                src.canAccessAllTenants(),
                src.isSystemAdmin(),
                src.preferences(),
                src.createdAt(),
                src.updatedAt());
    }

    /** 装配租户投影；秘密字段的可见性已由源 DTO 决定。 */
    public static TenantRespVo toTenantVo(TenantResponse src) {
        if (src == null) {
            return null;
        }
        return new TenantRespVo(
                src.id(),
                src.name(),
                src.description(),
                src.status(),
                src.retrieverEngines(),
                src.business(),
                src.storageQuota(),
                src.storageUsed(),
                src.contextConfig(),
                src.webSearchConfig(),
                src.parserEngineConfig(),
                src.credentials(),
                src.storageEngineConfig(),
                src.chatHistoryConfig(),
                src.retrievalConfig(),
                src.memoryConfig(),
                src.createdAt(),
                src.updatedAt(),
                src.deletedAt());
    }

    /** 装配令牌对。 */
    public static TokenPairRespVo toTokenPairVo(TokenPairResponse src) {
        if (src == null) {
            return null;
        }
        return new TokenPairRespVo(src.token(), src.refreshToken());
    }

    /** 装配邀请查询结果。 */
    public static InvitationLookupRespVo toInvitationLookupVo(InvitationLookupResponse src) {
        if (src == null) {
            return null;
        }
        return new InvitationLookupRespVo(
                src.tenantId(), src.tenantName(), src.role(), src.expiresAt());
    }

    /** 装配 OIDC 配置。 */
    public static OidcConfigRespVo toOidcConfigVo(OidcConfigResponse src) {
        if (src == null) {
            return null;
        }
        return new OidcConfigRespVo(src.enabled(), src.providerDisplayName());
    }

    /** 装配 OIDC 授权地址。 */
    public static OidcAuthUrlRespVo toOidcAuthUrlVo(OidcAuthUrlResponse src) {
        if (src == null) {
            return null;
        }
        return new OidcAuthUrlRespVo(
                src.providerDisplayName(), src.authorizationUrl(), src.state());
    }

    /** 装配成员投影。 */
    public static TenantMemberRespVo toMemberVo(TenantMemberResponse src) {
        if (src == null) {
            return null;
        }
        return new TenantMemberRespVo(
                src.userId(),
                src.email(),
                src.username(),
                src.avatar(),
                src.role(),
                src.status(),
                src.invitedBy(),
                src.joinedAt());
    }

    /** 装配邀请投影。 */
    public static TenantInvitationRespVo toInvitationVo(TenantInvitationResponse src) {
        if (src == null) {
            return null;
        }
        return new TenantInvitationRespVo(
                src.id(),
                src.tenantId(),
                src.tenantName(),
                src.inviteeUserId(),
                src.inviteeEmail(),
                src.inviteeName(),
                src.invitedBy(),
                src.inviterEmail(),
                src.inviterName(),
                src.role(),
                src.status(),
                src.message(),
                src.expiresAt(),
                src.respondedAt(),
                src.createdAt(),
                src.inviteUrl(),
                src.isShareLink(),
                src.acceptedCount());
    }

    /** 装配 API 主体配置。 */
    public static APIPrincipalConfigRespVo toApiPrincipalConfigVo(APIPrincipalDtos.APIPrincipalConfigResponse src) {
        if (src == null) {
            return null;
        }
        return new APIPrincipalConfigRespVo(
                src.mode(),
                src.directHeaderName(),
                src.signedTokenHeaderName(),
                src.requireDirectHeader(),
                src.hasHmacSecret());
    }

    /** 装配 API 主体测试令牌。 */
    public static APIPrincipalTestTokenRespVo toApiPrincipalTestTokenVo(
            APIPrincipalDtos.APIPrincipalTestTokenResponse src) {
        if (src == null) {
            return null;
        }
        return new APIPrincipalTestTokenRespVo(
                src.token(),
                src.headerName(),
                src.expiresInSeconds(),
                src.expiresAtUnix(),
                src.externalUserId());
    }

    private static UserCapabilitiesVo toCapabilitiesVo(UserCapabilities src) {
        if (src == null) {
            return null;
        }
        return new UserCapabilitiesVo(src.autoAcceptInvitation(), src.canCreateTenant());
    }

    private static List<MembershipVo> toMembershipVoList(List<Membership> src) {
        if (src == null) {
            return null;
        }
        return src.stream()
                .map(m -> m == null ? null : new MembershipVo(m.tenantId(), m.tenantName(), m.role()))
                .toList();
    }
}
