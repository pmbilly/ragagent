package com.ragagent.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashesTest {

    @Test
    void sha256HexMatchesKnownVector() {
        assertThat(Hashes.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Base64UrlMatchesKnownVector() {
        assertThat(Hashes.sha256Base64Url("abc"))
                .isEqualTo("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0");
    }

    @Test
    void hexIsLowercaseAndPrefixIsByteAligned() {
        assertThat(Hashes.hex(new byte[] {0x00, (byte) 0xFF, 0x1A})).isEqualTo("00ff1a");
        assertThat(Hashes.hexPrefix(new byte[] {0x00, (byte) 0xFF, 0x1A}, 2)).isEqualTo("00ff");
        assertThat(Hashes.sha256HexPrefix("abc", 8)).isEqualTo("ba7816bf");
    }

    @Test
    void prefixBeyondLengthIsClamped() {
        assertThat(Hashes.sha256HexPrefix("abc", 999)).hasSize(64);
        assertThat(Hashes.hexPrefix(new byte[] {1, 2}, 99)).isEqualTo("0102");
    }
}
