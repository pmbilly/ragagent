package com.ragagent.platform.prompt;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 组装原语的行为守门：三条链路（agent / 快答 / fallback）共用这套规则，
 * 语义漂移会先在实录上红，这里再钉一层"规则本身"的语义（跳空段、右侧裁剪、尾巴形状）。
 */
class PromptComposerTest {

    @Test
    @DisplayName("joinSections：逐段 trim、跳空段、以空行连接")
    void joinsNonEmptySectionsOnly() {
        assertEquals("a\n\nb", PromptComposer.joinSections(Arrays.asList(" a ", "", null, "\nb\n")));
        assertEquals("", PromptComposer.joinSections(List.of()));
        assertEquals("", PromptComposer.joinSections(null));
    }

    @Test
    @DisplayName("appendProtocol：右侧裁剪后直接接协议段（协议段自带前导换行）")
    void appendsProtocolAfterRightTrim() {
        assertEquals("sys\n\n## P", PromptComposer.appendProtocol("sys\n\n", "\n\n## P"));
        assertEquals("sys", PromptComposer.appendProtocol("sys", null));
    }

    @Test
    @DisplayName("rightTrim：只裁尾部空白；null → 空串")
    void trimsRightWhitespaceOnly() {
        assertEquals("a b", PromptComposer.rightTrim("a b \t\r\n"));
        assertEquals("", PromptComposer.rightTrim(null));
    }

    @Test
    @DisplayName("SOURCES_AND_OUTPUT_SUFFIX：边界段 + 有据作答段，带前导空行（字节形状）")
    void suffixShapeIsByteStable() {
        assertEquals("\n\n" + PromptConstants.SOURCE_DATA_BOUNDARY_PROMPT
                        + "\n\n" + PromptConstants.SOURCED_ANSWER_OUTPUT_PROMPT,
                PromptComposer.SOURCES_AND_OUTPUT_SUFFIX);
    }
}
