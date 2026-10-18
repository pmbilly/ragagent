package com.ragagent.auth.api;

import com.ragagent.auth.domain.User;
import com.ragagent.auth.service.UserService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 用户目录读面（agentmanagement 直接注入）：委托 {@code UserService#getUsersByIds}，
 * 租户范围与空集合短路语义在服务内保留，只投影展示字段 {@link AuthUserView}。
 */
@Component
public class UserLookupAdapter {

    private final UserService userService;

    public UserLookupAdapter(UserService userService) {
        this.userService = userService;
    }

    public Map<String, AuthUserView> byIds(Collection<String> ids) {
        Map<String, User> rows = userService.getUsersByIds(ids);
        Map<String, AuthUserView> out = new LinkedHashMap<>(rows.size());
        rows.forEach((id, u) -> out.put(id, new AuthUserView(u.getId(), u.getUsername(), u.getEmail())));
        return out;
    }
}
