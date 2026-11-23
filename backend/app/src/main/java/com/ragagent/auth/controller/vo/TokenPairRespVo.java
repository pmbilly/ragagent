package com.ragagent.auth.controller.vo;

/** {@code POST /auth/refresh}：新签发的一对令牌。 */
public record TokenPairRespVo(String token, String refreshToken) {
}
