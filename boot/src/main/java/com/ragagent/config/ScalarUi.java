package com.ragagent.config;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Scalar API Reference 页面：读取 classpath 里的单页 HTML，指向 {@code /v3/api-docs}。
 *
 * <p>只在 {@code springdoc.api-docs.enabled=true} 时注册；默认关闭时 {@code /scalar}
 * 由 DispatcherServlet 返回 404，与 OpenAPI JSON 的开关一致。</p>
 */
@Controller
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
final class ScalarUi {

    private final Resource page = new ClassPathResource("scalar/index.html");

    @GetMapping(value = {"/scalar", "/scalar/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> page() throws IOException {
        try (var in = page.getInputStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(StreamUtils.copyToByteArray(in));
        }
    }
}
