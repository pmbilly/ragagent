package com.ragagent.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.auth.domain.AuthToken;
import com.ragagent.auth.mapper.AuthTokenMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;

/**
 * 会话令牌仓储：令牌的按串查询、签发落库与吊销。
 *
 * <p>所有 {@code auth_tokens} 的读写收口在这里，service 不再直接持有
 * {@link AuthTokenMapper}。</p>
 */
@Repository
public class AuthTokenRepository {

    private final AuthTokenMapper mapper;

    public AuthTokenRepository(AuthTokenMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 token 串查一条（缺失返回 {@code null}）。 */
    public AuthToken findByToken(String token) {
        return mapper.selectOne(new LambdaQueryWrapper<AuthToken>()
                .eq(AuthToken::getToken, token)
                .last("LIMIT 1"));
    }

    /** 新增一条令牌记录。 */
    public void insert(AuthToken record) {
        mapper.insert(record);
    }

    /** 整行写回并把 {@code is_revoked} 置真、刷新 {@code updated_at}。 */
    public void revoke(AuthToken record) {
        record.setIsRevoked(true);
        record.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mapper.updateById(record);
    }

    /** 吊销该用户全部**未吊销**令牌（不刷 updated_at；成员移除清理用）。 */
    public void revokeActiveByUserId(String userId) {
        mapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getUserId, userId)
                .eq(AuthToken::isIsRevoked, false)
                .set(AuthToken::isIsRevoked, true));
    }

    /** 吊销该用户全部令牌：{@code is_revoked=true}，同时刷 {@code updated_at}。 */
    public void revokeByUserId(String userId) {
        mapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getUserId, userId)
                .set(AuthToken::isIsRevoked, true)
                .set(AuthToken::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
