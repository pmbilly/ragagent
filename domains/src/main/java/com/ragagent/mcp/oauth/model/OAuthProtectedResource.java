package com.ragagent.mcp.oauth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * RFC 9728 {@code /.well-known/oauth-protected-resource} 的响应。
 */
public record OAuthProtectedResource(
        @JsonProperty("authorization_servers") List<String> authorizationServers,
        String resource,
        @JsonProperty("resource_name") String resourceName) {

    public OAuthProtectedResource {
        authorizationServers = authorizationServers == null ? List.of() : List.copyOf(authorizationServers);
        resource = resource == null ? "" : resource;
        resourceName = resourceName == null ? "" : resourceName;
    }

    public boolean hasAuthorizationServers() {
        return !authorizationServers.isEmpty();
    }
}
