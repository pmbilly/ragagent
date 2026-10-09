package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A14 守卫：逐字段 {@code @JsonProperty} 只允许出现在「外部协议面」或被证明必要的位置。
 *
 * <p>依据 {@code docs/knowledge-api-contract-v1.md} §1.1：<b>JSON 字段名 = Java 字段名（camelCase）；
 * 禁止逐字段 {@code @JsonProperty}</b>。键与隐式属性名相同时注解是纯噪音，而且注解<b>优先于</b>
 * 字段名——两者一旦漂移就是静默换键（B132/B149 的同类教训）。</p>
 *
 * <p>但「键 = 字段名」<b>不充分</b>：注解有时是<b>唯一让 Jackson 看得见该属性</b>的东西。所以本守卫
 * 只在<b>同时满足</b>两条时才判红：</p>
 * <ol>
 *   <li>注解的键 = 该成员的隐式属性名（Jackson 规则）；</li>
 *   <li>Jackson <b>本来就会自动探测</b>该成员——record 分量 / {@code public} 字段 / 有 {@code public}
 *       访问器（{@code getX}/{@code isX}/{@code setX} 或同名 fluent 方法）。否则删掉注解会让属性直接
 *       消失（B151 实测踩过：{@code CreateTenantRequest} 的包级私有字段 → 请求体 400
 *       「field 'name' is required」）。</li>
 * </ol>
 *
 * <p>必要形态因此只剩四类，规则见下：键≠隐式名（关键字冲突等）· 键形如 {@code isXxx} 的布尔属性
 * （getter 的隐式名会丢 {@code is}）· Jackson 探测不到的成员 · 外部协议面（{@link #ALLOWED_FACES} /
 * {@link #ALLOWED_FILES}，每条带理由）。另有一类"看似冗余实为字段序锚"的：见
 * {@link #hasRenamedAnnotation}（混用改名注解的类型，整文件跳过）。</p>
 *
 * <p>基线 <b>0</b>（不是棘轮——B151 已把我们自己的面清零，从第一天起就是"新增即红"）。</p>
 */
class JsonPropertyHygieneTest {

    /**
     * 允许逐字段 {@code @JsonProperty} 的面（都带理由）。清理某面后请同步收紧本表——留着不疼，
     * 但下一批人就不知道那面其实已经干净了。
     */
    private static final Map<String, String> ALLOWED_FACES = allowedFaces();

    private static Map<String, String> allowedFaces() {
        Map<String, String> faces = new LinkedHashMap<>();
        faces.put("datasource/connector/", "连接器：对方 API 的蛇形键（§14.6 冻结面）");
        faces.put("llm/chat/Anthropic", "Anthropic 线格式（外部协议）");
        faces.put("llm/ollama/", "Ollama 线格式（外部协议）");
        faces.put("llm/provider/", "provider 线格式（外部协议）");
        faces.put("rerank/", "RankResult 供 models/{id}/debug 的 raw_response（§14.6）");
        return faces;
    }

    /**
     * 逐文件放行：{@code llm/domain} 下「provider 线格式的类型化视图」——它们的键就是要原样进
     * provider 请求体/响应解析的（§14.6「provider 请求体」边界），其中部分还是蛇形冻结键
     * （{@code multi_content}/{@code tool_call_id}）。面内新旧注解一律不动。
     *
     * <p>同包的 {@code StreamResponse} <b>不在</b>此表：它是我们自己的 SSE 事件体（键已全 camel），
     * 冗余注解已在 B151 清掉，故继续受本守卫约束（防回流）。</p>
     */
    private static final Map<String, String> ALLOWED_FILES = allowedFiles();

    private static Map<String, String> allowedFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        String why = "provider 线格式视图（OpenAI/Ollama 兼容，§14.6 边界）";
        for (String f : new String[] {"ChatMessage", "ChatOptions", "ChatResponse", "ChatTool",
                "FunctionCall", "FunctionDef", "ImageUrl", "MessageContentPart", "ToolCall"}) {
            files.put("llm/domain/" + f + ".java", why);
        }
        return files;
    }

    /**
     * 短形式与<b>全限定</b>形式都要认——HANDOFF §14.6 的口径警告：只扫短名会漏（{@code QaRequests} 那批用
     * {@code @com.fasterxml…JsonProperty}；当前仓里 {@code chatpipeline/Rec46cSupport} 仍是这种写法）。
     */
    private static final String ANN =
            "@(?:com\\.fasterxml\\.jackson\\.annotation\\.)?JsonProperty";

    private static final Pattern ANNOTATION = Pattern.compile(ANN + "\\s*\\(");
    private static final Pattern KEY =
            Pattern.compile(ANN + "\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");
    /** 键形如 isXxx（布尔）：getter 的隐式名会丢 is ⇒ 注解必要，整族豁免。 */
    private static final Pattern IS_PREFIXED_KEY = Pattern.compile("is[A-Z].*");
    private static final Pattern METHOD_NAME = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern RECORD_HEADER = Pattern.compile("\\brecord\\s+\\w+\\s*\\(");

    @Test
    @DisplayName("A14：我们的面里不得有「键=隐式属性名」且「Jackson 本可自动探测」的逐字段 @JsonProperty")
    void redundantJsonPropertyIsForbidden() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : ArchitectureRulesTest.backendSourceRoots("main/java")) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    String rel = relative(file);
                    if (ALLOWED_FACES.keySet().stream().anyMatch(rel::startsWith)
                            || ALLOWED_FILES.containsKey(rel)) {
                        continue;
                    }
                    collect(file, rel, offenders);
                }
            }
        }
        assertThat(offenders)
                .as("@JsonProperty 只是噪音的情形：键=隐式属性名 **且** Jackson 本会自己探测到它"
                        + "（record 分量 / public 字段 / public 访问器）。必要形态见类注释；外部面 %s",
                        ALLOWED_FACES.keySet())
                .isEmpty();
    }

    /** 把 {@code .../java/com/ragagent/xxx} 归一成 {@code xxx}（与 ALLOWED_FACES 的写法一致）。 */
    private static String relative(Path file) {
        String s = file.toString().replace('\\', '/');
        int i = s.indexOf("com/ragagent/");
        return i < 0 ? s : s.substring(i + "com/ragagent/".length());
    }

    private static void collect(Path file, String rel, List<String> offenders) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String body = String.join("\n", lines);
        boolean[] inRecordHeader = recordComponentLines(lines);
        if (hasRenamedAnnotation(lines, inRecordHeader)) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher ann = ANNOTATION.matcher(line);
            if (!ann.find() || isComment(line)) {
                continue;
            }
            Matcher key = KEY.matcher(line);
            if (!key.find()) {
                continue;
            }
            String keyName = key.group(1);
            if (IS_PREFIXED_KEY.matcher(keyName).matches()) {
                continue;
            }
            Member member = memberAfter(lines, i, ann.start());
            if (member == null || !member.name.equals(keyName)) {
                continue;
            }
            if (!jacksonAutoDetects(member, inRecordHeader[i], body)) {
                continue;   // 注解是唯一可见性来源 ⇒ 必要（B151 实测：删了会 400）
            }
            offenders.add(rel + ":" + (i + 1) + "  键 " + keyName
                    + " = 隐式属性名且 Jackson 自会探测（删掉注解即可，零行为变化）");
        }
    }

    private static boolean isComment(String line) {
        String t = line.stripLeading();
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*");
    }

    /**
     * 该类型是否混用「键 ≠ 隐式名」的<b>改名注解</b>。混用时不得再删「键 = 隐式名」的注解：那会把它从
     * <b>显式命名</b>降级成<b>隐式命名</b>，Jackson 据此可能改变属性顺序 ⇒ 线格式<b>字段序</b>漂移
     * （B151 实测：Go 队列载荷断言期望 {@code tenant_id…chat_model_id,language}，删后 {@code language}
     * 跳到最前）。这类冗余注解实为「字段序锚」，一并保留。
     */
    private static boolean hasRenamedAnnotation(List<String> lines, boolean[] inRecordHeader) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher ann = ANNOTATION.matcher(line);
            if (!ann.find() || isComment(line)) {
                continue;
            }
            Matcher key = KEY.matcher(line);
            if (!key.find()) {
                continue;
            }
            Member member = memberAfter(lines, i, ann.start());
            if (member != null && !member.name.equals(key.group(1))) {
                return true;
            }
        }
        return false;
    }

    /** 注解所修饰成员：隐式属性名 + 是否方法声明 + 声明原文。 */
    private record Member(String name, boolean method, String decl) {}

    /**
     * 求注解所修饰成员的<b>隐式属性名</b>（Jackson 实际规则）：字段/记录分量 = 名字本身；
     * getter/setter 去 {@code get}/{@code set} 并首字母小写，布尔 {@code isXxx()} ⇒ {@code xxx}。
     * 解析不出返回 {@code null}（宁可漏报也不误报）。
     */
    private static Member memberAfter(List<String> lines, int i, int start) {
        String rest = lines.get(i).substring(start);
        int end = closingParen(rest);
        List<String> candidates = new ArrayList<>();
        if (end >= 0 && !rest.substring(end).isBlank()) {
            String tail = rest.substring(end);
            if (!tail.contains("@")) {
                candidates.add(tail);
            }
        }
        for (int k = i + 1; k < Math.min(i + 8, lines.size()); k++) {
            String s = lines.get(k).strip();
            if (s.isEmpty() || isComment(s)) {
                continue;
            }
            if (stripLeadingAnnotations(s).isEmpty()) {
                continue;                                  // 纯注解行（如 @JsonInclude(...)）⇒ 继续往后
            }
            candidates.add(s);
            break;
        }
        for (String raw : candidates) {
            String s = stripLeadingAnnotations(raw).split("//")[0].strip();
            if (s.isEmpty()) {
                continue;
            }
            int paren = s.indexOf('(');
            int eq = s.indexOf('=');
            if (eq >= 0 && (paren < 0 || eq < paren)) {
                s = s.substring(0, eq).strip();             // private String issuer = ""; ⇒ private String issuer
            }
            if (s.indexOf('(') >= 0) {
                Matcher m = METHOD_NAME.matcher(s);
                if (!m.find()) {
                    continue;
                }
                String method = m.group(1);
                return new Member(stripAccessorPrefix(method), true, raw.strip());
            }
            String t = s.replaceAll("[;,){}\\s]+$", "").strip();
            int sp = t.lastIndexOf(' ');
            String name = sp < 0 ? t : t.substring(sp + 1).strip();
            if (!name.isEmpty()) {
                return new Member(name, false, raw.strip());
            }
        }
        return null;
    }

    /**
     * Jackson 是否会<b>自己</b>发现该成员（默认可见性：字段要 public、访问器要 public）：
     * record 分量 ✓ · {@code public} 声明 ✓ · 同类里有 public 访问器（{@code getX}/{@code isX}/{@code setX}
     * 或同名 fluent）✓。
     */
    private static boolean jacksonAutoDetects(Member member, boolean inRecordHeader, String body) {
        if (inRecordHeader || member.method && member.decl.startsWith("public ")) {
            return true;
        }
        if (!member.method && member.decl.startsWith("public ")) {
            return true;
        }
        String cap = Character.toUpperCase(member.name.charAt(0)) + member.name.substring(1);
        Pattern accessor = Pattern.compile("public\\s+[\\w<>\\[\\], .?]+\\s+(?:get|is|set)?"
                + Pattern.quote(cap) + "\\s*\\(");
        return accessor.matcher(body).find();
    }

    private static String stripAccessorPrefix(String method) {
        for (String pre : new String[] {"get", "set"}) {
            if (method.startsWith(pre) && method.length() > 3) {
                return Character.toLowerCase(method.charAt(3)) + method.substring(4);
            }
        }
        if (method.startsWith("is") && method.length() > 2 && Character.isUpperCase(method.charAt(2))) {
            return Character.toLowerCase(method.charAt(2)) + method.substring(3);
        }
        return method;
    }

    /** 剥掉行首的一串注解（{@code @JsonInclude(...) String language,} ⇒ {@code String language,}）。 */
    private static String stripLeadingAnnotations(String line) {
        String s = line.strip();
        while (s.startsWith("@")) {
            int end = closingParen(s);
            if (end < 0) {
                return "";
            }
            s = s.substring(end).strip();
        }
        return s;
    }

    /** 标记「落在 record 头括号内」的行（= 记录分量所在行）。 */
    private static boolean[] recordComponentLines(List<String> lines) {
        boolean[] inside = new boolean[lines.size()];
        int depth = 0;
        boolean inHeader = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!inHeader) {
                Matcher m = RECORD_HEADER.matcher(l);
                if (!m.find()) {
                    continue;
                }
                inHeader = true;
                depth = 0;
                for (int j = m.end() - 1; j < l.length(); j++) {
                    char c = l.charAt(j);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        depth--;
                        if (depth == 0) {
                            inHeader = false;
                            break;
                        }
                    }
                }
                inside[i] = true;
                continue;
            }
            inside[i] = true;
            for (int j = 0; j < l.length(); j++) {
                char c = l.charAt(j);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        inHeader = false;
                        break;
                    }
                }
            }
        }
        return inside;
    }

    /** 返回第一个 '(' 对应的 ')' 的<b>后一位</b>；无括号返回 -1。 */
    private static int closingParen(String s) {
        int depth = 0;
        boolean seen = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
                seen = true;
            } else if (c == ')') {
                depth--;
                if (seen && depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }
}
