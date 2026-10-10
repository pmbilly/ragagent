package com.ragagent.common.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /health 探活：{"status":"ok"}（无需认证）。
 */
// B200：/health 是**基础设施探针**（k8s/docker healthcheck、外部监控 ✓）⇒ 保持原样
// `{"status":"ok"}`，**不进统一外壳**（包壳会破坏探针 ✓）。已知例外，见 docs/api-response-convention.md
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
