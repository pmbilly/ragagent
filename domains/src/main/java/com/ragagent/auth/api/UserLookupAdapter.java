package com.ragagent.auth.api;

import com.ragagent.auth.domain.User;
import com.ragagent.auth.service.UserService;
import com.ragagent.contract.auth.UserLookupPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link UserLookupPort} 的 auth 侧实现：委托 {@code UserService#getUsersByIds}
 * （租户范围与空集合短路语义在服务内逐字保留），只投影展示字段。</p>
 */
@Component
public class UserLookupAdapter implements UserLookupPort {

    private final UserService userService;

    public UserLookupAdapter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Map<String, UserView> byIds(Collection<String> ids) {
        Map<String, User> rows = userService.getUsersByIds(ids);
        Map<String, UserView> out = new LinkedHashMap<>(rows.size());
        rows.forEach((id, u) -> out.put(id, new UserView(u.getId(), u.getUsername(), u.getEmail())));
        return out;
    }
}
