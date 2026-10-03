package com.ragagent.datasource.connector.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * {@link JdkXmlFeedParser} 的字段级对等测试。
 *
 * <p>两条只看代码容易猜错的语义，用例单独钉住：</p>
 * <ol>
 *   <li><b>RSS 的 {@code updatedParsed} 只来自 {@code dc:date}</b>，{@code pubDate} 进的是
 *       {@code publishedParsed}——所以只有 {@code pubDate} 的条目在该字段上是 {@code null}；</li>
 *   <li><b>Atom 的 {@code <link>} 必须带 {@code rel="alternate"}</b>，不带 {@code rel}
 *       时取不到值（rel 是精确匹配）。</li>
 * </ol>
 *
 * <h2>未声明实体的处理</h2>
 * <p>{@code &nbsp;} 这类未声明的命名实体在严格 XML 解析下是致命错误；
 * {@link HtmlEntities#makeXmlSafe} 把它收窄成"表里没有的实体原样保留字面量"。</p>
 */
class JdkXmlFeedParserTest {

    private final JdkXmlFeedParser parser = new JdkXmlFeedParser();

    private FeedParser.ParsedFeed parse(String xml) {
        return parser.parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    // ── RSS 2.0 ──────────────────────────────────────────────────────────

    @Test
    void parsesRss20Fields() {
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                <title>Test Feed</title>
                <link>http://example.com/</link>
                <description>A test feed</description>
                <lastBuildDate>Tue, 03 Jan 2006 15:04:05 GMT</lastBuildDate>
                <item>
                  <title>Article One</title>
                  <link>http://example.com/a1</link>
                  <guid>guid-1</guid>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 GMT</pubDate>
                  <description>summary fallback</description>
                </item>
                </channel></rss>
                """);
        assertThat(feed.title()).isEqualTo("Test Feed");
        assertThat(feed.description()).isEqualTo("A test feed");
        assertThat(feed.link()).isEqualTo("http://example.com/");
        // 实测：feed 级的 UpdatedParsed 来自 <lastBuildDate>，且被 .UTC() 归一
        assertThat(feed.updatedParsed().toInstant()).isEqualTo(instant("2006-01-03T15:04:05Z"));
        assertThat(feed.items()).hasSize(1);

        FeedParser.ParsedItem item = feed.items().get(0);
        assertThat(item.guid()).isEqualTo("guid-1");
        assertThat(item.link()).isEqualTo("http://example.com/a1");
        assertThat(item.title()).isEqualTo("Article One");
        assertThat(item.description()).isEqualTo("summary fallback");
        assertThat(item.content()).isEmpty();
        // ⚠️ pubDate 只进 PublishedParsed；UpdatedParsed 在没有 dc:date 时是 null
        assertThat(item.updatedParsed()).isNull();
        assertThat(item.publishedParsed().toInstant()).isEqualTo(instant("2006-01-02T15:04:05Z"));
        assertThat(item.authorName()).isNull();
    }

    @Test
    void parsesContentEncodedAndDublinCore() {
        // content = "<p>encoded <em>body</em></p>"，
        // author 来自 dc:creator = "Dave"，UpdatedParsed 来自 dc:date。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"
                     xmlns:content="http://purl.org/rss/1.0/modules/content/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
                <channel>
                <title>Ext Feed</title><link>http://ext.example/</link>
                <description>ext desc</description>
                <item>
                  <title>Ext One</title>
                  <link>http://ext.example/x1</link>
                  <guid isPermaLink="false">ext-1</guid>
                  <dc:date>2006-01-04T02:03:04Z</dc:date>
                  <dc:creator>Dave</dc:creator>
                  <content:encoded><![CDATA[<p>encoded <em>body</em></p>]]></content:encoded>
                  <description>plain desc</description>
                </item>
                </channel></rss>
                """);
        FeedParser.ParsedItem item = feed.items().get(0);
        assertThat(item.guid()).isEqualTo("ext-1");
        assertThat(item.content()).isEqualTo("<p>encoded <em>body</em></p>");
        assertThat(item.description()).isEqualTo("plain desc");
        assertThat(item.updatedParsed().toInstant()).isEqualTo(instant("2006-01-04T02:03:04Z"));
        assertThat(item.authorName()).isEqualTo("Dave");
    }

    @Test
    void parsesNakedMarkupInsideDescription() {
        // description 取的是"内层 XML"：裸标签会被保留（不是被当子元素吃掉）。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>T</title>
                <item><guid>g</guid><description><p>a &amp; b</p></description></item>
                </channel></rss>
                """);
        assertThat(feed.items().get(0).description()).isEqualTo("<p>a & b</p>");
    }

    @Test
    void parsesAuthorNameAddressForms() {
        // 作者字段按四条解析模式依序尝试：
        assertThat(parseNameAddressViaFeed("joe@example.com (Joe)")).isEqualTo("Joe");
        assertThat(parseNameAddressViaFeed("Joe (joe@example.com)")).isEqualTo("Joe");
        assertThat(parseNameAddressViaFeed("Joe")).isEqualTo("Joe");
        assertThat(parseNameAddressViaFeed("joe@example.com")).isEmpty();
        // "a@b (not-an-email)" 命中的是**第一条**正则（邮箱 + 括号里的名字），名字 = 括号内容
        assertThat(parseNameAddressViaFeed("a@b (not-an-email)")).isEqualTo("not-an-email");
        // 括号里也是邮箱（含 @）→ 四条正则都不命中 → 名字为空
        assertThat(parseNameAddressViaFeed("a@b (c@d)")).isEmpty();
    }

    private String parseNameAddressViaFeed(String author) {
        FeedParser.ParsedFeed feed = parse("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>"
                + "<title>T</title><item><guid>g</guid><author>" + author
                + "</author></item></channel></rss>");
        return feed.items().get(0).authorName();
    }

    // ── RSS 1.0（RDF） ───────────────────────────────────────────────────

    @Test
    void parsesRss10ItemsAtRootLevel() {
        // RSS 1.0 的 <item> 挂在根下而不是 channel 下——解析器两种形状都收。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns="http://purl.org/rss/1.0/">
                  <channel rdf:about="http://rss10.example/">
                    <title>RSS 1.0 Feed</title>
                    <link>http://rss10.example/</link>
                    <description>rdf desc</description>
                  </channel>
                  <item rdf:about="http://rss10.example/i1">
                    <title>RDF One</title>
                    <link>http://rss10.example/i1</link>
                    <description>rdf item</description>
                  </item>
                </rdf:RDF>
                """);
        assertThat(feed.title()).isEqualTo("RSS 1.0 Feed");
        assertThat(feed.description()).isEqualTo("rdf desc");
        assertThat(feed.link()).isEqualTo("http://rss10.example/");
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).title()).isEqualTo("RDF One");
    }

    // ── Atom ─────────────────────────────────────────────────────────────

    /**
     * 期望值（同一个 feed）：
     * <pre>
     *   feed.title="Atom Feed" description="atom sub" link="http://atom.example/"
     *   updatedParsed=2024-05-06T07:08:09Z
     *   entry1: id="tag:atom.example,2024:e1" link="http://atom.example/e1"
     *           title="Entry One" updated=2024-05-07T10:00:00Z published=2024-05-01T09:00:00Z
     *           summary="entry summary" content="<p>hello <b>world</b></p>" author="Alice"
     *   entry2: published 缺失 → PublishedParsed 回落 UpdatedParsed = 2024-05-08T10:00:00Z
     * </pre>
     */
    @Test
    void parsesAtomFields() {
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <title>Atom Feed</title>
                  <subtitle>atom sub</subtitle>
                  <link rel="alternate" href="http://atom.example/"/>
                  <link rel="self" href="http://atom.example/feed.atom"/>
                  <updated>2024-05-06T07:08:09Z</updated>
                  <entry>
                    <title>Entry One</title>
                    <link rel="alternate" href="http://atom.example/e1"/>
                    <id>tag:atom.example,2024:e1</id>
                    <updated>2024-05-07T10:00:00Z</updated>
                    <published>2024-05-01T09:00:00Z</published>
                    <summary>entry summary</summary>
                    <content type="html">&lt;p&gt;hello &lt;b&gt;world&lt;/b&gt;&lt;/p&gt;</content>
                    <author><name>Alice</name></author>
                  </entry>
                  <entry>
                    <title>Entry Two</title>
                    <link rel="alternate" href="http://atom.example/e2"/>
                    <id>tag:atom.example,2024:e2</id>
                    <updated>2024-05-08T10:00:00Z</updated>
                    <content type="html">&lt;p&gt;second&lt;/p&gt;</content>
                  </entry>
                </feed>
                """);
        assertThat(feed.title()).isEqualTo("Atom Feed");
        assertThat(feed.description()).isEqualTo("atom sub");
        assertThat(feed.link()).isEqualTo("http://atom.example/");
        assertThat(feed.updatedParsed().toInstant()).isEqualTo(instant("2024-05-06T07:08:09Z"));

        FeedParser.ParsedItem one = feed.items().get(0);
        assertThat(one.guid()).isEqualTo("tag:atom.example,2024:e1");
        assertThat(one.link()).isEqualTo("http://atom.example/e1");
        assertThat(one.title()).isEqualTo("Entry One");
        assertThat(one.description()).isEqualTo("entry summary");
        assertThat(one.content()).isEqualTo("<p>hello <b>world</b></p>");
        assertThat(one.updatedParsed().toInstant()).isEqualTo(instant("2024-05-07T10:00:00Z"));
        assertThat(one.publishedParsed().toInstant()).isEqualTo(instant("2024-05-01T09:00:00Z"));
        assertThat(one.authorName()).isEqualTo("Alice");

        FeedParser.ParsedItem two = feed.items().get(1);
        assertThat(two.publishedParsed().toInstant()).isEqualTo(instant("2024-05-08T10:00:00Z"));
        assertThat(two.authorName()).isNull();
        assertThat(two.description()).isEmpty();
    }

    @Test
    void atomLinkWithoutRelIsNotPickedUp() {
        // ⚠️ link 的选择是精确匹配 rel == "alternate"，
        // 没有 rel 属性的 <link href="…"/> 取不到值。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>No Rel</title>
                  <link href="http://norel.example/"/>
                  <entry>
                    <title>E</title>
                    <id>e1</id>
                    <link href="http://norel.example/e1"/>
                    <updated>2024-05-06T07:08:09Z</updated>
                  </entry>
                </feed>
                """);
        assertThat(feed.link()).isEmpty();
        assertThat(feed.items().get(0).link()).isEmpty();
    }

    @Test
    void atomXhtmlContentIsSerializedAsInnerXml() {
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>T</title>
                  <entry><id>e</id><title>E</title><updated>2024-05-06T07:08:09Z</updated>
                  <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">hi</div></content>
                  </entry>
                </feed>
                """);
        assertThat(feed.items().get(0).content()).contains("hi");
    }

    // ── 日期（有界子集） ─────────────────────────────────────────────────

    @Test
    void dateParsingCoversCommonLayouts() {
        assertThat(JdkXmlFeedParser.parseDate("Mon, 02 Jan 2006 15:04:05 GMT"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 15, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("Mon, 02 Jan 2006 15:04:05 -0700"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 22, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("Mon, 2 Jan 2006 15:04:05 +0800"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 7, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("2006-01-02T15:04:05Z"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 15, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("2006-01-02T15:04:05+08:00"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 7, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("2006-01-02 15:04:05"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 15, 4, 5, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("2006-01-02"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("Jan 2, 2006"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("2 January 2006"))
                .isEqualTo(OffsetDateTime.of(2006, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(JdkXmlFeedParser.parseDate("  ")).isNull();
        assertThat(JdkXmlFeedParser.parseDate("")).isNull();
        // 有界子集之外：解析不出来时返回 null（单条解析失败只跳过，不中断同步）
        assertThat(JdkXmlFeedParser.parseDate("6/1/2 15:04")).isNull();
        assertThat(JdkXmlFeedParser.parseDate("02 Monday, Jan 2006 15:04")).isNull();
    }

    @Test
    void unparseableItemDateLeavesNullAndDoesNotFailTheFeed() {
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>T</title>
                <item><guid>g</guid><pubDate>not a date at all</pubDate></item>
                </channel></rss>
                """);
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).publishedParsed()).isNull();
        assertThat(feed.items().get(0).updatedParsed()).isNull();
    }

    // ── 格式识别 ─────────────────────────────────────────────────────────

    @Test
    void rejectsNonFeedDocumentsWithGoMessage() {
        assertThatThrownBy(() -> parse("not a feed at all"))
                .isInstanceOf(FeedParseException.class)
                .hasMessage("Failed to detect feed type");
        assertThatThrownBy(() -> parse("<?xml version=\"1.0\"?><html><body>x</body></html>"))
                .isInstanceOf(FeedParseException.class)
                .hasMessage("Failed to detect feed type");
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(FeedParseException.class)
                .hasMessage("Failed to detect feed type");
    }

    @Test
    void jsonFeedIsExplicitlyUnsupported() {
        // ⚠️ 已知缺口：JSON Feed 格式不支持——这里明确失败（不静默当成空 feed）。
        assertThatThrownBy(() -> parse("{\"version\":\"https://jsonfeed.org/version/1\","
                + "\"title\":\"t\",\"items\":[]}"))
                .isInstanceOf(FeedParseException.class)
                .hasMessageContaining("Failed to detect feed type")
                .hasMessageContaining("JSON Feed is not supported by this build");
    }

    @Test
    void malformedXmlFailsWithParserMessage() {
        assertThatThrownBy(() -> parse("<?xml version=\"1.0\"?><rss><channel><title>x</title>"))
                .isInstanceOf(FeedParseException.class);
    }

    // ── 宽松度：比严格 XML 解析更宽容的输入 ──────────────────────────────

    @Test
    void toleratesUndeclaredNamedEntities() {
        // &nbsp; 在严格 XML 下是致命错误；这里预解码成 U+00A0。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>T</title>
                <item><guid>g</guid><description>a&nbsp;b &mdash; c</description></item>
                </channel></rss>
                """);
        assertThat(feed.items().get(0).description()).isEqualTo("a\u00A0b \u2014 c");
    }

    @Test
    void toleratesBareAmpersand() {
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>AT&amp;T News</title>
                <item><guid>g</guid><description>AT&T</description></item>
                </channel></rss>
                """);
        assertThat(feed.title()).isEqualTo("AT&T News");
        assertThat(feed.items().get(0).description()).isEqualTo("AT&T");
    }

    @Test
    void unknownNamedEntityStaysLiteral() {
        // 未知实体原样保留。
        FeedParser.ParsedFeed feed = parse("""
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>T</title>
                <item><guid>g</guid><description>x&bogus;y</description></item>
                </channel></rss>
                """);
        assertThat(feed.items().get(0).description()).isEqualTo("x&bogus;y");
    }

    @Test
    void skipsUtf8BomBeforeRootElement() {
        FeedParser.ParsedFeed feed = parser.parse(("﻿" + """
                <?xml version="1.0"?>
                <rss version="2.0"><channel><title>Bom</title>
                <item><guid>g</guid></item></channel></rss>
                """).getBytes(StandardCharsets.UTF_8));
        assertThat(feed.title()).isEqualTo("Bom");
    }

    @Test
    void emptyItemsListIsEmptyNotNull() {
        FeedParser.ParsedFeed feed = parse("<?xml version=\"1.0\"?><rss version=\"2.0\">"
                + "<channel><title>T</title></channel></rss>");
        assertThat(feed.items()).isNotNull().isEmpty();
    }
}
