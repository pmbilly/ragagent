package com.ragagent.platform.security;


import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SSRF 守卫的 scheme 校验。
 *
 * <p>为什么单测值得钉在这里：这是**安全边界**与**功能可用性**的交点，
 * 两者的失效方向相反、且都很安静 ——
 * <ul>
 *   <li>放行太宽：SSRF 防护形同虚设（能打内网、能打云 metadata）；</li>
 *   <li>放行太窄：**IM 长连接整条链路不可用**。2026-10-11 实测踩到后者：
 *       守卫只认 http/https，把飞书长连接的 {@code wss} 挡在门外 ⇒
 *       现象是"渠道配好了、飞书发消息没回复"，日志里只有一行 WARN，
 *       排查时容易一路怀疑凭据/网络/DNS，而真因在第一道 scheme 检查。</li>
 * </ul>
 *
 * <p>白名单是**进程级 static**（见 {@link SsrfGuard} 类注释），测试必须快照/还原，
 * 否则会污染同 JVM 的后续测试（known-issues W5a「SsrfGuard 互踩」家族）。
 */
class SsrfGuardTest {

    private SsrfGuard guard;
    private SsrfGuard.Whitelist snapshot;

    @BeforeEach
    void setUp() {
        snapshot = SsrfGuard.snapshotWhitelist();
        guard = new SsrfGuard();
        // 白名单只放 example.com：让断言聚焦 scheme 本身，不触发 DNS 解析那步。
        guard.reloadWhitelist("example.com");
    }

    @AfterEach
    void tearDown() {
        SsrfGuard.restoreWhitelist(snapshot);
    }

    @Test
    @DisplayName("wss 放行：IM 长连接只能走 wss（此前被 scheme 检查挡死）")
    void wssIsAllowed() {
        // 用白名单内的域名 ⇒ 断言只落在 scheme 这一道（真实场景是 open.feishu.cn
        // 这类飞书长连接端点，域名走的是同一套主机规则，与 scheme 判定无关）。
        assertThatCode(() -> guard.validateURLForSSRF("wss://example.com/callback/ws/endpoint"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ws 放行：与 http 同为明文形态，风险等价")
    void wsIsAllowed() {
        assertThatCode(() -> guard.validateURLForSSRF("ws://example.com/socket"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("http/https 仍放行（回归保护）")
    void httpAndHttpsStillAllowed() {
        assertThatCode(() -> guard.validateURLForSSRF("https://example.com/a")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validateURLForSSRF("http://example.com/a")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("白名单**不放松 scheme**：ftp/file/gopher 即使主机在白名单也拒绝")
    void whitelistDoesNotRelaxScheme() {
        // 关键安全断言：scheme 检查是第一道，早于白名单判定。
        for (String bad : new String[]{"ftp://example.com/x", "file://example.com/x",
                "gopher://example.com/x", "data://example.com/x"}) {
            assertThatThrownBy(() -> guard.validateURLForSSRF(bad))
                    .isInstanceOf(SsrfGuard.SsrfException.class)
                    .hasMessageContaining("invalid scheme");
        }
    }

    @Test
    @DisplayName("wss 不绕过主机限制：直连 IP 仍拒（拿到 wss 也要按同一套主机规则校验）")
    void wssStillSubjectToHostChecks() {
        assertThatThrownBy(() -> guard.validateURLForSSRF("wss://127.0.0.1/socket"))
                .isInstanceOf(SsrfGuard.SsrfException.class);
        assertThatThrownBy(() -> guard.validateURLForSSRF("wss://localhost/socket"))
                .isInstanceOf(SsrfGuard.SsrfException.class);
    }

}
