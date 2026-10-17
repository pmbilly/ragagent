package com.ragagent.auth.api;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.AuthToken;
import com.ragagent.auth.mapper.AuthTokenMapper;
import com.ragagent.contract.auth.AuthTokenAdminPort;
import org.springframework.stereotype.Component;

/**
 * {@link AuthTokenAdminPort} 的 auth 域实现：批量置 {@code isRevoked=true}，
 * wrapper 与调用点**逐字一致**（原来在 system 侧的写法搬入属主域）。
 */
@Component
public class AuthTokenAdminAdapter implements AuthTokenAdminPort {

    private final AuthTokenMapper authTokenMapper;

    public AuthTokenAdminAdapter(AuthTokenMapper authTokenMapper) {
        this.authTokenMapper = authTokenMapper;
    }

    @Override
    public void revokeAllForUser(String userId) {
        authTokenMapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getUserId, userId)
                .set(AuthToken::isIsRevoked, true));
    }
}
