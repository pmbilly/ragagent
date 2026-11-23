package com.ragagent.model.controller.vo;

import java.util.Map;

/**
 * credentials 子资源的 HTTP 响应体：{@code {"fields":{"apiKey":{...},"appSecret":{...}}}}。
 */
public record ModelCredentialsRespVo(
        Map<String, ModelCredentialFieldMetaVo> fields) {
}
