package com.ragagent.im.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.platform.security.CryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** IM 凭据加密：写前包装、读时宽容解密、无密钥降级、密钥轮换不冒充满值。 */
class ImCredentialsTypeHandlerTest {

    private static final String KEY_A = "0123456789abcdef0123456789abcdef";
    private static final String KEY_B = "fedcba9876543210fedcba9876543210";
    private static final String PLAINTEXT = "{\"bot_token\":\"7654321:AAEmbtoken123\"}";

    private String originalKey;

    @BeforeEach
    void installKey() {
        originalKey = CryptoService.rawAesKey();
        CryptoService.installAesKey(KEY_A);
    }

    @AfterEach
    void restoreKey() {
        CryptoService.installAesKey(originalKey);
    }

    @Test
    void wrapsPlaintextIntoJsonbEnvelope() {
        String stored = new ImCredentialsTypeHandler().wrap(PLAINTEXT);

        assertThat(stored).startsWith("{\"enc\":\"enc:v1:");
        assertThat(stored).doesNotContain("AAEmbtoken123");
    }

    @Test
    void roundTripsThroughEnvelope() {
        ImCredentialsTypeHandler handler = new ImCredentialsTypeHandler();

        assertThat(handler.unwrap(handler.wrap(PLAINTEXT))).isEqualTo(PLAINTEXT);
    }

    @Test
    void readsLegacyPlaintextAsIs() {
        assertThat(new ImCredentialsTypeHandler().unwrap(PLAINTEXT)).isEqualTo(PLAINTEXT);
    }

    @Test
    void degradesToPlaintextWithoutKey() {
        CryptoService.installAesKey(null);
        ImCredentialsTypeHandler handler = new ImCredentialsTypeHandler();

        assertThat(handler.wrap(PLAINTEXT)).isEqualTo(PLAINTEXT);
        assertThat(handler.unwrap(PLAINTEXT)).isEqualTo(PLAINTEXT);
    }

    @Test
    void returnsEmptyObjectWhenKeyRotated() {
        String stored = new ImCredentialsTypeHandler().wrap(PLAINTEXT);

        CryptoService.installAesKey(KEY_B);

        assertThat(new ImCredentialsTypeHandler().unwrap(stored)).isEqualTo("{}");
    }

    @Test
    void normalizesBlankToEmptyObject() {
        ImCredentialsTypeHandler handler = new ImCredentialsTypeHandler();

        assertThat(handler.wrap("")).isEqualTo("{}");
        assertThat(handler.wrap("   ")).isEqualTo("{}");
        assertThat(handler.unwrap(null)).isNull();
    }

    @Test
    void doesNotDoubleWrap() {
        ImCredentialsTypeHandler handler = new ImCredentialsTypeHandler();
        String once = handler.wrap(PLAINTEXT);

        assertThat(handler.wrap(once)).isEqualTo(once);
    }

    @Test
    void treatsJsonWithOtherKeysAsPlaintext() {
        assertThat(ImCredentialsTypeHandler.isWrapped("{\"enc\":\"x\",\"bot_token\":\"y\"}")).isFalse();
    }
}
