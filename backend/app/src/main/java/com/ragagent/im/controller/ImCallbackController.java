package com.ragagent.im.controller;

import com.ragagent.im.callback.ImCallbackHandler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IM 平台回调面（路由 GET/POST /api/v1/im/callback/{channelId}）。
 *
 * <p>鉴权与响应形态由 {@link ImCallbackHandler} 承担：平台自带验签、plain JSON 直写、
 * 404/503/403/400 文案与 ACK 形状保持原样。本类只做路由绑定。</p>
 */
@RestController
@Tag(name = "IM")
public class ImCallbackController {

    private final ImCallbackHandler handler;

    public ImCallbackController(ImCallbackHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/api/v1/im/callback/{channelId}")
    public void callbackGet(@PathVariable("channelId") String channelId,
            HttpServletRequest request, HttpServletResponse response) {
        handler.handle(channelId, request, response);
    }

    @PostMapping("/api/v1/im/callback/{channelId}")
    public void callbackPost(@PathVariable("channelId") String channelId,
            HttpServletRequest request, HttpServletResponse response) {
        handler.handle(channelId, request, response);
    }
}
