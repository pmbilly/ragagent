package com.ragagent.platform.persistence;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link LikeEscapes}：三条元字符各走一遍 + 组合与"不退化成匹配一切"的断言。 */
class LikeEscapesTest {

    @Test
    @DisplayName("B263㉚：反斜杠 / % / _ 各自转义（PG LIKE 默认 ESCAPE 是反斜杠）")
    void escapesAllThreeMetaCharacters() {
        assertThat(LikeEscapes.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(LikeEscapes.escape("100%")).isEqualTo("100\\%");
        assertThat(LikeEscapes.escape("a_b")).isEqualTo("a\\_b");
    }

    @Test
    @DisplayName("B263㉚：纯 % 不再退化成\"匹配一切\"；普通文本原样返回")
    void percentDoesNotDegenerateAndPlainTextIsUntouched() {
        assertThat(LikeEscapes.escape("%")).isEqualTo("\\%");
        assertThat(LikeEscapes.escape("plain text")).isEqualTo("plain text");
        assertThat("%" + LikeEscapes.escape("%") + "%").isEqualTo("%\\%%");
    }
}
