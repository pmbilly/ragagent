package com.ragagent.favorite.repository;

import com.ragagent.favorite.domain.UserResourceFavorite;
import com.ragagent.favorite.mapper.UserResourceFavoriteMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 收藏仓储门面（四键复合主键，方法为显式 SQL 的薄转发）。 */
@Repository
public class UserResourceFavoriteRepository {

    private final UserResourceFavoriteMapper mapper;

    public UserResourceFavoriteRepository(UserResourceFavoriteMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 (user, tenant, type) 过滤，created_at DESC。 */
    public List<UserResourceFavorite> list(String userId, Long tenantId, String resourceType) {
        return mapper.list(userId, tenantId, resourceType);
    }

    /** 四键全等查存在性。 */
    public UserResourceFavorite find(String userId, Long tenantId, String resourceType, String resourceId) {
        return mapper.find(userId, tenantId, resourceType, resourceId);
    }

    public void insert(UserResourceFavorite favorite) {
        mapper.insert(favorite);
    }

    /** 返回受影响行数（0 = 幽灵删除，调用方仍按成功处理）。 */
    public int delete(String userId, Long tenantId, String resourceType, String resourceId) {
        return mapper.delete(userId, tenantId, resourceType, resourceId);
    }
}
