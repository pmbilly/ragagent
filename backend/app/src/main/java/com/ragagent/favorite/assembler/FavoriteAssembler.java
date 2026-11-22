package com.ragagent.favorite.assembler;

import com.ragagent.favorite.controller.vo.FavoriteRespVo;
import com.ragagent.favorite.domain.UserResourceFavorite;
import java.util.List;

/** 收藏实体到 HTTP 响应 VO 的装配。 */
public final class FavoriteAssembler {

    private FavoriteAssembler() {
    }

    /** 装配单个收藏项。 */
    public static FavoriteRespVo toVo(UserResourceFavorite favorite) {
        if (favorite == null) {
            return null;
        }
        return new FavoriteRespVo(
                favorite.getUserId(),
                favorite.getTenantId(),
                favorite.getResourceType(),
                favorite.getResourceId(),
                favorite.getCreatedAt());
    }

    /** 批量装配，保持原有顺序；空列表返回空列表。 */
    public static List<FavoriteRespVo> toVoList(List<UserResourceFavorite> favorites) {
        if (favorites == null) {
            return List.of();
        }
        return favorites.stream().map(FavoriteAssembler::toVo).toList();
    }
}
