package com.ragagent.platform.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ImageRefRewriter 纯逻辑：命中才替换、只换路径本体、按源文件名去重落盘、
 * 图标过滤开关、落盘失败与无内联字节时原样保留。
 */
class ImageRefRewriterTest {

    private final List<String> saved = new ArrayList<>();

    private ImageRefRewriter.Saver saver() {
        return (data, fileName) -> {
            saved.add(fileName);
            return "local://1/exports/" + fileName;
        };
    }

    @Test
    void rewritesMatchedTargetsAndKeepsTitleAndUnmatchedRefs() {
        byte[] png = pngBytes(200, 150);
        String markdown = "![图片](images/chart.png \"标题\")\n"
                + "![other](images/unknown.png)\n"
                + "![空格目标](<images/第 1 页 (测试).png>)";

        ImageRefRewriter.Result result = ImageRefRewriter.rewrite(markdown, List.of(
                new ImageRefRewriter.RefRow("images/chart.png", "chart.png", "image/png", png),
                new ImageRefRewriter.RefRow("images/第 1 页 (测试).png", "第 1 页 (测试).png",
                        "image/png", png)), true, saver());

        assertThat(result.stored()).hasSize(2);
        String chartRef = result.stored().get(0).servingRef();
        String spacedRef = result.stored().get(1).servingRef();
        // 只换路径本体：title / 右括号 / 尖括号 / 未命中的引用原样保留
        assertThat(result.markdown()).isEqualTo("![图片](" + chartRef + " \"标题\")\n"
                + "![other](images/unknown.png)\n"
                + "![空格目标](<" + spacedRef + ">)");
        assertThat(saved).hasSize(2);
        assertThat(chartRef).matches("local://1/exports/.+\\.png");
    }

    @Test
    void repeatedReferencesSaveOnceAndShareServingRef() {
        byte[] png = pngBytes(200, 150);
        ImageRefRewriter.RefRow ref =
                new ImageRefRewriter.RefRow("images/x.png", "x.png", "image/png", png);

        ImageRefRewriter.Result result = ImageRefRewriter.rewrite(
                "![a](images/x.png)\n![b](images/x.png)", List.of(ref, ref), true, saver());

        // 落盘一次、正文两处同引用、记录两条（按出现序）
        assertThat(saved).hasSize(1);
        assertThat(result.stored()).hasSize(2);
        String servingRef = result.stored().get(0).servingRef();
        assertThat(result.stored().get(1).servingRef()).isEqualTo(servingRef);
        assertThat(result.markdown().split(java.util.regex.Pattern.quote(servingRef), -1)).hasSize(3);
    }

    @Test
    void iconFilterAppliesOnlyWhenRequested() {
        byte[] icon = pngBytes(48, 48);
        ImageRefRewriter.RefRow ref =
                new ImageRefRewriter.RefRow("images/icon.png", "icon.png", "image/png", icon);
        String markdown = "![icon](images/icon.png)";

        assertThat(ImageRefRewriter.rewrite(markdown, List.of(ref), true, saver()).markdown())
                .isEqualTo(markdown);
        assertThat(ImageRefRewriter.rewrite(markdown, List.of(ref), false, saver()).stored())
                .hasSize(1);
    }

    @Test
    void keepsRefWhenSaveFailsOrNoInlineBytes() {
        byte[] png = pngBytes(200, 150);
        String markdown = "![a](images/a.png) ![b](images/b.png)";

        ImageRefRewriter.Result result = ImageRefRewriter.rewrite(markdown, List.of(
                new ImageRefRewriter.RefRow("images/a.png", "a.png", "image/png", png),
                new ImageRefRewriter.RefRow("images/b.png", "b.png", "image/png", new byte[0])),
                true, (data, fileName) -> {
                    throw new IllegalStateException("disk full");
                });

        assertThat(result.stored()).isEmpty();
        assertThat(result.markdown()).isEqualTo(markdown);
    }

    @Test
    void emptyRefsLeaveMarkdownUntouched() {
        assertThat(ImageRefRewriter.rewrite("![a](images/a.png)", List.of(), true, saver()).markdown())
                .isEqualTo("![a](images/a.png)");
        assertThat(ImageRefRewriter.rewrite("![a](images/a.png)", null, true, saver()).stored())
                .isEmpty();
    }

    /** 生成可解码的 PNG（图标过滤需要真实尺寸）。 */
    private static byte[] pngBytes(int width, int height) {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
