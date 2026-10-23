package com.ragagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code <currentTime>} 的取值契约：本地时间（带偏移）+ 同一瞬间的 UTC。
 *
 * <p>只给日期，模型处理不了“现在/半小时后”；只给 UTC，中文用户的“明天下午三点”会错算时差。</p>
 */
class ConversationAssemblerCurrentTimeTest {

    private static final Pattern CURRENT_TIME = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) ([+-]\\d{2}:\\d{2})"
                    + " \\(UTC (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z)\\)");

    @Test
    void currentTimeTextCarriesLocalOffsetAndUtcInstant() {
        String text = ConversationAssembler.currentTimeText();
        Matcher m = CURRENT_TIME.matcher(text);
        assertThat(m.matches()).as("currentTime 文本格式：%s", text).isTrue();

        Instant localInstant = OffsetDateTime
                .parse(m.group(1).replace(' ', 'T') + m.group(2))
                .toInstant();
        Instant utcInstant = Instant.parse(m.group(3));

        assertThat(Math.abs(Duration.between(localInstant, utcInstant).toSeconds()))
                .as("本地时间与 UTC 必须指同一瞬间：%s", text)
                .isLessThanOrEqualTo(1);
    }

    @Test
    void runtimeContextBlockEmbedsFullCurrentTime() {
        String block = ConversationAssembler.buildRuntimeContextBlock("s-1", null, null);

        assertThat(block).startsWith("<runtimeContext scope=\"this_turn\">");
        Matcher m = Pattern.compile("<currentTime>([^<]*)</currentTime>").matcher(block);
        assertThat(m.find()).as("必须注入 currentTime 元素：%s", block).isTrue();
        assertThat(CURRENT_TIME.matcher(m.group(1)).matches())
                .as("currentTime 内容格式：%s", m.group(1))
                .isTrue();
    }
}
