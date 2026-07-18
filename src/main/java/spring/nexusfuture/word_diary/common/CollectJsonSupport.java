package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class CollectJsonSupport {

    public static final String MARKER = "---COLLECT_JSON---";

    /** 「固定搭配 / Collocations」等小节标题 */
    private static final Pattern COLLOCATION_HEADING = Pattern.compile(
            "(?i)^#{0,6}\\s*(?:\\*\\*)?(固定搭配|固搭|短语(?:用法)?|常用搭配|词组|collocations?|fixed\\s+phrases?|phrasal\\s+verbs?)(?:\\*\\*)?\\s*[:：]?\\s*$");

    /** 其它 markdown 标题（用于结束搭配小节） */
    private static final Pattern OTHER_HEADING = Pattern.compile("^#{1,6}\\s+\\S");

    /**
     * 搭配列表行（避免嵌套量词 ReDoS）：先抓「冒号前英文 + 冒号后释义」。
     * 例：- **pull down**：拆除
     */
    private static final Pattern COLLOCATION_BULLET = Pattern.compile(
            "^\\s*(?:[-*•]|\\d+[.)])\\s*(?:\\*\\*)?([^:*：—–\\n]{1,120}?)(?:\\*\\*)?\\s*[:：—–\\-]\\s*(.{1,300})$");

    private static final int MAX_PROSE_LINE_LEN = 400;

    private CollectJsonSupport() {}

    /** @deprecated 使用 {@link #collectInstruction(String)}，按用户语言输出收录说明 */
    @Deprecated
    public static String collectInstruction() {
        return collectInstruction("zh-CN");
    }

    public static String collectInstruction(String locale) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale;
        return switch (loc) {
            case "en" -> collectInstructionEn();
            case "id" -> collectInstructionId();
            case "vi" -> collectInstructionVi();
            case "th" -> collectInstructionTh();
            default -> collectInstructionZh();
        };
    }

    private static String collectInstructionZh() {
        return """

                【收录】讲解结束后另起一行输出 %s，再输出一行 JSON（勿用代码块）：
                {"entries":[{"entryType":"word|phrase","wordProto":"原形","partSpeechMean":"n. 释义","usPhonetic":"/…/","ukPhonetic":"/…/","rootAffix":"词根或组成"}]}
                规则：仅收录正文实际讲解的单词/搭配（勿为未讲解的常见词造条）；word 须含美英 IPA；partSpeechMean 用 n./v./adj. 等缩写；勿输出 sentenceExample。
                """.formatted(MARKER);
    }

    /**
     * 单词讲解：只讲难点 + 音标释义格式（追加到 system prompt）。
     * 与「把选段里每个词都列一遍」相反——宁少勿滥。
     */
    public static String phoneticDisplayInstruction(String locale) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale;
        return switch (loc) {
            case "en" -> """

                    [Words — selection + format]
                    Explain ONLY hard/low-frequency words a B1–B2 learner may not know (e.g. solicitor).
                    SKIP: proper names, numbers, and common words (make, move, people, better, building, railway, possible…).
                    Prefer 1–5 words for a short passage; do not dump every token.
                    Format: - **word**: US /…/, UK /…/; n. meaning
                    """;
            case "id", "vi", "th" -> """

                    [Words] Only difficult words (skip names & high-frequency words). Prefer 1–5 items.
                    Format: - **word**: US /…/, UK /…/; n. meaning
                    """;
            default -> """

                    【单词·筛选+格式】只讲学习者可能不认识的重难点词（如 solicitor）。
                    禁止讲解：专有名词（人名/地名）、基础高频词（make/move/people/better/building/railway/possible/suggested 等常见词）。
                    短段落通常 1～5 个即可，宁少勿滥，不要把选段里每个词都列一遍。
                    格式：- **solicitor**: 美 /…/，英 /…/；n. 律师。禁「名词，意为…」。
                    """;
        };
    }

    /** 仅音标/词性格式（已有明确目标词时用，不再重复「筛词」规则） */
    public static String phoneticFormatInstruction(String locale) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale;
        return switch (loc) {
            case "en" -> """

                    [Word format] - **word**: US /…/, UK /…/; n. meaning
                    """;
            default -> """

                    【单词·格式】- **word**: 美 /…/，英 /…/；n. 释义。禁「名词，意为…」。
                    """;
        };
    }

    private static String collectInstructionEn() {
        return """

                [Collect] After the explanation, output marker %s on a new line, then one JSON line (no code fence):
                {"entries":[{"entryType":"word|phrase","wordProto":"lemma","partSpeechMean":"n. meaning","usPhonetic":"/…/","ukPhonetic":"/…/","rootAffix":"root or composition"}]}
                Rules: one entry per explained word/collocation; words need US+UK IPA; use POS abbreviations; no sentenceExample.
                """.formatted(MARKER);
    }

    private static String collectInstructionId() {
        return collectInstructionEn();
    }

    private static String collectInstructionVi() {
        return collectInstructionEn();
    }

    private static String collectInstructionTh() {
        return collectInstructionEn();
    }

    public static boolean shouldCollect(List<String> featureKeys, String featureKey) {
        if (featureKeys != null && !featureKeys.isEmpty()) {
            return featureKeys.stream().anyMatch(CollectJsonSupport::isCollectableFeature);
        }
        return isCollectableFeature(featureKey);
    }

    /** 需要在讲解中强制输出英美音标（单词/词根类功能） */
    public static boolean shouldRequireWordPhonetics(List<String> featureKeys, String featureKey) {
        if (featureKeys != null && !featureKeys.isEmpty()) {
            return featureKeys.stream().anyMatch(CollectJsonSupport::isPhoneticFeature);
        }
        return isPhoneticFeature(featureKey);
    }

    /**
     * 可收录词条的功能：单词、搭配、词根、例句等。
     * 释义 / 句式是句子级分析，不应强制 COLLECT_JSON，否则模型会被带去做单词讲解。
     */
    public static boolean isCollectableFeature(String key) {
        if (key == null || key.isBlank()) return false;
        return switch (key.trim()) {
            case "word", "collocation", "root_affix", "root", "example" -> true;
            default -> false;
        };
    }

    public static boolean isPhoneticFeature(String key) {
        if (key == null || key.isBlank()) return false;
        return switch (key.trim()) {
            case "word", "root_affix", "root", "phonetic" -> true;
            default -> false;
        };
    }

    /** 约束：只做用户勾选的功能（顺序已在上方功能块体现，此处不重复长文） */
    public static String featureScopeInstruction(String locale, List<String> featureKeys, String featureKey) {
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale;
        if ((featureKeys == null || featureKeys.isEmpty()) && (featureKey == null || featureKey.isBlank())) {
            return "";
        }
        return switch (loc) {
            case "en" -> "\n[Scope] Only selected features; section order must match the feature list above. No extras.\n";
            default -> "\n【范围】只输出上方已列功能对应小节，顺序与列表一致，勿增删调换。\n";
        };
    }

    /**
     * 用户点选的目标词：单词/搭配/词根仅针对这些词讲解。
     * 翻译/句式仍可用全文语境。
     */
    public static String targetWordsInstruction(String locale, List<String> targetWords) {
        if (targetWords == null || targetWords.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", targetWords);
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale;
        return switch (loc) {
            case "en" -> """

                    [Target words] For word / collocation / root sections, explain ONLY: %s.
                    Do NOT explain any other words. Translation / grammar may use the full passage.
                    Collect JSON must only include these targets (and collocations built from them).
                    """.formatted(joined);
            default -> """

                    【目标词】单词/搭配/词根小节仅针对用户指定词：%s。
                    禁止讲解未列出的词。翻译/句式可使用全文语境。
                    收录 JSON 也仅含这些词（及其相关搭配）。
                    """.formatted(joined);
        };
    }

    public static List<String> normalizeTargetWords(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            String s = o.toString().trim();
            if (s.isEmpty()) continue;
            boolean dup = false;
            for (String existing : out) {
                if (existing.equalsIgnoreCase(s)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(s);
        }
        return out;
    }

    public static String stripForDisplay(String text) {
        if (text == null || text.isEmpty()) return "";
        int idx = text.indexOf(MARKER);
        if (idx >= 0) return text.substring(0, idx).trim();

        int soft = text.indexOf("---COLLECT_JSON");
        if (soft >= 0) return text.substring(0, soft).trim();

        for (int len = Math.min(MARKER.length() - 1, text.length()); len >= 3; len--) {
            if (text.endsWith(MARKER.substring(0, len))) {
                return text.substring(0, text.length() - len).trim();
            }
        }
        return text;
    }

    public static List<CollectEntryDTO> parseEntries(String text, ObjectMapper mapper) {
        if (text == null || !text.contains(MARKER)) {
            return Collections.emptyList();
        }
        String jsonPart = text.substring(text.indexOf(MARKER) + MARKER.length()).trim();
        int start = jsonPart.indexOf('{');
        int end = jsonPart.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Collections.emptyList();
        }
        jsonPart = jsonPart.substring(start, end + 1);
        try {
            Map<String, List<CollectEntryDTO>> root = mapper.readValue(jsonPart,
                    new TypeReference<Map<String, List<CollectEntryDTO>>>() {});
            List<CollectEntryDTO> entries = root.get("entries");
            return entries != null ? entries : Collections.emptyList();
        } catch (Exception e) {
            log.warn("解析 COLLECT_JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 COLLECT_JSON，并用讲解正文中的「固定搭配」列表补全漏收的短语。
     * 含空格的词条若被标成 word，也会纠正为 phrase。
     */
    public static List<CollectEntryDTO> parseEntriesEnriched(String text, ObjectMapper mapper) {
        List<CollectEntryDTO> fromJson = parseEntries(text, mapper);
        List<CollectEntryDTO> fromProse = extractPhrasesFromProse(stripForDisplay(text));

        Map<String, CollectEntryDTO> merged = new LinkedHashMap<>();
        for (CollectEntryDTO e : fromJson) {
            CollectEntryDTO normalized = normalizeEntry(e);
            if (normalized == null) continue;
            merged.put(entryKey(normalized), normalized);
        }
        for (CollectEntryDTO e : fromProse) {
            CollectEntryDTO normalized = normalizeEntry(e);
            if (normalized == null) continue;
            merged.putIfAbsent(entryKey(normalized), normalized);
        }
        return new ArrayList<>(merged.values());
    }

    /** 从讲解正文「固定搭配」等小节提取短语（兜底，模型漏写 COLLECT_JSON 时用） */
    public static List<CollectEntryDTO> extractPhrasesFromProse(String displayText) {
        if (displayText == null || displayText.isBlank()) {
            return Collections.emptyList();
        }
        List<CollectEntryDTO> out = new ArrayList<>();
        boolean inSection = false;
        for (String rawLine : displayText.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.length() > MAX_PROSE_LINE_LEN) continue;
            if (COLLOCATION_HEADING.matcher(line).matches()) {
                inSection = true;
                continue;
            }
            if (inSection && OTHER_HEADING.matcher(line).matches() && !COLLOCATION_HEADING.matcher(line).matches()) {
                inSection = false;
                continue;
            }
            if (!inSection) continue;

            Matcher m = COLLOCATION_BULLET.matcher(line);
            if (!m.matches()) continue;
            String proto = cleanProto(m.group(1));
            String meaning = cleanMeaning(m.group(2));
            if (proto.isEmpty() || meaning.isEmpty() || !looksLikeEnglishPhrase(proto)) continue;
            out.add(CollectEntryDTO.builder()
                    .entryType("phrase")
                    .wordProto(proto)
                    .partSpeechMean(meaning.startsWith("短语") || meaning.toLowerCase(Locale.ROOT).startsWith("phrase")
                            ? meaning
                            : "短语. " + meaning)
                    .build());
        }
        return out;
    }

    private static CollectEntryDTO normalizeEntry(CollectEntryDTO e) {
        if (e == null || e.getWordProto() == null || e.getWordProto().isBlank()) return null;
        if (e.getPartSpeechMean() == null || e.getPartSpeechMean().isBlank()) return null;
        String proto = cleanProto(e.getWordProto());
        if (proto.isEmpty()) return null;
        String type = e.getEntryType();
        if (looksLikeEnglishPhrase(proto) && (type == null || !"phrase".equalsIgnoreCase(type.trim()))) {
            type = "phrase";
        } else if (type == null || type.isBlank()) {
            type = looksLikeEnglishPhrase(proto) ? "phrase" : "word";
        } else {
            type = "phrase".equalsIgnoreCase(type.trim()) ? "phrase" : "word";
        }
        return CollectEntryDTO.builder()
                .entryType(type)
                .wordProto(proto)
                .partSpeechMean(e.getPartSpeechMean().trim())
                .usPhonetic(e.getUsPhonetic())
                .ukPhonetic(e.getUkPhonetic())
                .sentenceExample(e.getSentenceExample())
                .rootAffix(e.getRootAffix())
                .appendExample(e.getAppendExample())
                .build();
    }

    private static String entryKey(CollectEntryDTO e) {
        String type = e.getEntryType() == null ? "word" : e.getEntryType().trim().toLowerCase(Locale.ROOT);
        return type + ":" + e.getWordProto().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeEnglishPhrase(String proto) {
        String p = proto.trim();
        if (!p.matches("(?i)^[a-z][a-z0-9'’./…\\-]*(?:\\s+(?:\\.\\.\\.|…|[a-z][a-z0-9'’./…\\-]*))*$")) {
            return false;
        }
        return p.contains(" ") || p.contains("...") || p.contains("…")
                || (p.indexOf('-') > 0 && p.matches("(?i)[a-z]+(?:-[a-z]+)+"));
    }

    private static String cleanProto(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\*+", "")
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String cleanMeaning(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\*+", "").trim();
        // 去掉末尾多余标点堆叠
        while (s.endsWith("…") || s.endsWith("...")) {
            // keep ellipsis as part of meaning ok
            break;
        }
        return s;
    }
}
