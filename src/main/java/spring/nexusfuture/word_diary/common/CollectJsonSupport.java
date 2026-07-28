package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import spring.nexusfuture.word_diary.dto.response.CollectEntryDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class CollectJsonSupport {

    public static final String MARKER = "---COLLECT_JSON---";

    /**
     * @deprecated 解析契约由后台场景壳 / 功能 prompt 约定；代码不再下发输出说明。
     */
    public static String structuredAnalyzeInstruction(String locale) {
        return "";
    }

    /** 从解析 JSON 取出可收录词条：扫所有值为「词条对象数组」的键，不按 key 名硬编码。 */
    @SuppressWarnings("unchecked")
    public static List<CollectEntryDTO> extractEntriesFromAnalyzeJson(String text, ObjectMapper mapper) {
        Map<String, Object> root = parseAnalyzeJson(text, mapper);
        if (root == null) return Collections.emptyList();

        List<CollectEntryDTO> out = new ArrayList<>();
        for (Object raw : root.values()) {
            if (!(raw instanceof List<?> list) || list.isEmpty()) continue;
            List<Map<String, Object>> maps = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
                if (!copy.containsKey("word") && !copy.containsKey("wordProto")) continue;
                maps.add(copy);
            }
            if (!maps.isEmpty()) {
                out.addAll(normalizeEntryMaps(maps));
            }
        }
        if (!out.isEmpty()) return out;

        if (text != null && text.trim().startsWith("[")) {
            try {
                List<Map<String, Object>> arr = mapper.readValue(text.trim(),
                        new TypeReference<List<Map<String, Object>>>() {});
                return normalizeEntryMaps(arr);
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    public static Map<String, Object> parseAnalyzeJson(String text, ObjectMapper mapper) {
        if (text == null || text.isBlank()) return null;
        String json = extractJsonObject(text);
        if (json == null) return null;
        Map<String, Object> parsed = tryReadAnalyzeMap(mapper, json);
        if (parsed != null) return parsed;
        // 容忍尾逗号
        String noTrail = json.replaceAll(",\\s*([}\\]])", "$1");
        parsed = tryReadAnalyzeMap(mapper, noTrail);
        if (parsed != null) return parsed;
        if (text.contains(MARKER)) {
            try {
                String part = text.substring(text.indexOf(MARKER) + MARKER.length()).trim();
                String sub = extractJsonObject(part);
                if (sub != null) {
                    parsed = tryReadAnalyzeMap(mapper, sub);
                    if (parsed != null) return parsed;
                }
            } catch (Exception ignored) {
                /* fall through */
            }
        }
        log.warn("解析结构化 analyze JSON 失败, head={}", json.length() > 120 ? json.substring(0, 120) : json);
        return null;
    }

    private static String extractJsonObject(String text) {
        if (text == null) return null;
        String json = text.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) return json.substring(start, end + 1);
            return null;
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) return json.substring(start, end + 1);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryReadAnalyzeMap(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Object tree = mapper.readValue(json, Object.class);
            if (tree instanceof String s) {
                String inner = extractJsonObject(s);
                if (inner != null) tree = mapper.readValue(inner, Object.class);
            }
            if (tree instanceof Map<?, ?> map && !map.isEmpty()) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {
            /* try next strategy */
        }
        return null;
    }

    private static List<CollectEntryDTO> normalizeEntryMaps(List<Map<String, Object>> maps) {
        List<CollectEntryDTO> out = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            if (m == null) continue;
            String word = firstNonBlank(m.get("word"), m.get("wordProto"));
            String meaning = firstNonBlank(m.get("meaning"), m.get("partSpeechMean"));
            if (word == null || meaning == null) continue;
            String type = m.get("entryType") != null ? String.valueOf(m.get("entryType")).trim() : "word";
            if (!"phrase".equalsIgnoreCase(type) && word.contains(" ")) {
                type = "phrase";
            }
            out.add(CollectEntryDTO.builder()
                    .entryType("phrase".equalsIgnoreCase(type) ? "phrase" : "word")
                    .wordProto(word.trim())
                    .partSpeechMean(WordPosParser.canonicalize(meaning.trim()))
                    .usPhonetic(asStr(m.get("usPhonetic")))
                    .ukPhonetic(asStr(m.get("ukPhonetic")))
                    .rootAffix(asStr(m.get("rootAffix")))
                    .build());
        }
        return out;
    }

    private static String firstNonBlank(Object a, Object b) {
        String s = asStr(a);
        if (s != null && !s.isBlank()) return s;
        s = asStr(b);
        return s != null && !s.isBlank() ? s : null;
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    /** 「固定搭配 / Collocations」等小节标题（允许「固定搭配：」直角引号版式） */
    private static final Pattern COLLOCATION_HEADING = Pattern.compile(
            "(?i)^#{0,6}\\s*[「\"“]?(?:\\*\\*)?(固定搭配|固搭|短语(?:用法|讲解|解析)?|常用搭配|词组|分解解释|搭配讲解|"
                    + "collocations?|fixed\\s+phrases?|phrasal\\s+verbs?)(?:\\*\\*)?\\s*[:：]?[」\"”]?\\s*$");

    /** 「单词 / Words」等小节标题 */
    private static final Pattern WORD_HEADING = Pattern.compile(
            "(?i)^#{0,6}\\s*[「\"“]?(?:\\*\\*)?(单词|单词讲解|词汇|words?|vocabulary)(?:\\*\\*)?\\s*[:：]?[」\"”]?\\s*$");

    /** 任一已知小节标题（用于结束当前小节；含无 ## 的「翻译：」） */
    private static final Pattern ANY_SECTION_HEADING = Pattern.compile(
            "(?i)^#{0,6}\\s*[「\"“]?(?:\\*\\*)?(翻译|释义|整体释义|解析|单词|单词讲解|词汇|固搭|固定搭配|语法|语法结构|句式|词根词缀|例句|"
                    + "分解解释|短语讲解|短语解析|搭配讲解|"
                    + "words?|vocabulary|collocations?|fixed\\s+phrases?|phrasal\\s+verbs?|grammar|translation|analysis|breakdown)"
                    + "(?:\\*\\*)?\\s*[:：]?[」\"”]?\\s*$");

    /** 其它 markdown 标题（用于结束搭配/单词小节） */
    private static final Pattern OTHER_HEADING = Pattern.compile("^#{1,6}\\s+\\S");

    /**
     * 列表行：- **lemma**：释义（单词/搭配共用）
     */
    private static final Pattern LEMMA_BULLET = Pattern.compile(
            "^\\s*(?:[-*•]|\\d+[.)])\\s*(?:\\*\\*)?([^:*：—–\\n]{1,120}?)(?:\\*\\*)?\\s*[:：—–\\-]\\s*(.{1,300})$");

    /** 无列表符：get involved in：参与… */
    private static final Pattern LEMMA_PLAIN = Pattern.compile(
            "(?i)^(?:\\*\\*)?([a-z][a-z0-9'’./…\\-]*(?:\\s+(?:\\.\\.\\.|…|[a-z][a-z0-9'’./…\\-]*)){0,8})(?:\\*\\*)?\\s*[:：—–\\-]\\s*(.{1,300})$");

    /** 无冒号：- **keep records of** 做…的记录 */
    private static final Pattern LEMMA_SPACE_CJK = Pattern.compile(
            "(?i)^\\s*(?:[-*•]|\\d+[.)])?\\s*(?:\\*\\*)?([a-z][a-z0-9'’./…\\-]*(?:\\s+(?:\\.\\.\\.|…|[a-z][a-z0-9'’./…\\-]*)){0,8})(?:\\*\\*)?\\s+([\\u4e00-\\u9fff].{0,300})$");

    /** 美/英音标 + 词性释义 */
    private static final Pattern WORD_PHONETIC_MEANING = Pattern.compile(
            "(?i)^(?:美|US)\\s*(/[^/\\n]{1,40}/)\\s*[,，]?\\s*(?:英|UK)\\s*(/[^/\\n]{1,40}/)\\s*[;；]?\\s*(.*)$");

    private static final int MAX_PROSE_LINE_LEN = 400;

    private CollectJsonSupport() {}

    /** @deprecated 收录说明应由后台 prompt 配置；代码不再下发。 */
    @Deprecated
    public static String collectInstruction() {
        return "";
    }

    /** @deprecated 收录说明应由后台 prompt 配置；代码不再下发。 */
    public static String collectInstruction(String locale) {
        return "";
    }

    /** @deprecated 单词筛选/音标格式应由后台功能 prompt 配置。 */
    public static String phoneticDisplayInstruction(String locale) {
        return "";
    }

    /** @deprecated 音标格式应由后台功能 prompt 配置。 */
    public static String phoneticFormatInstruction(String locale) {
        return "";
    }

    /**
     * 是否尝试从解析结果抽词条：有勾选功能即尝试；能否抽到由 JSON 形状决定，不按 key 白名单。
     */
    public static boolean shouldCollect(List<String> featureKeys, String featureKey) {
        List<String> keys = normalizeFeatureKeys(featureKeys, featureKey);
        return !keys.isEmpty();
    }

    /**
     * @deprecated 音标要求应由后台功能 prompt 约定；代码不再按 key 硬编码。
     */
    public static boolean shouldRequireWordPhonetics(List<String> featureKeys, String featureKey) {
        return false;
    }

    /** @deprecated 收录能力不再绑死在特定 feature_key 名上。 */
    public static boolean isCollectableFeature(String key) {
        return key != null && !key.isBlank();
    }

    /** @deprecated 音标要求应由后台功能 prompt 约定。 */
    public static boolean isPhoneticFeature(String key) {
        return false;
    }

    /** 已废弃：范围说明应由后台场景壳配置；保留空实现以免旧调用报错。 */
    public static String featureScopeInstruction(String locale, List<String> featureKeys, String featureKey) {
        return "";
    }

    /** 只保留本次勾选的 feature_key；不改名、不合并字段。 */
    public static void pruneAnalyzePayload(Map<String, Object> payload, List<String> featureKeys) {
        if (payload == null) return;
        normalizeAnalyzeKeyAliases(payload);
        List<String> keys = normalizeFeatureKeys(featureKeys, null);
        if (keys.isEmpty()) return;
        LinkedHashSet<String> allowed = new LinkedHashSet<>(keys);
        payload.keySet().removeIf(k -> !"raw".equals(k) && !allowed.contains(k));
    }

    /**
     * 模型偶发用中文键/复数键；归一到标准 feature_key，避免固搭有数据却展示不出来。
     */
    public static void normalizeAnalyzeKeyAliases(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return;
        remapAlias(payload, "collocation",
                "collocations", "phrase", "phrases", "fixed_phrase", "固搭", "固定搭配", "搭配");
        remapAlias(payload, "word", "words", "vocabulary", "单词", "词汇");
        remapAlias(payload, "meaning", "translation", "译文", "翻译", "释义");
        remapAlias(payload, "grammar", "语法");
        remapAlias(payload, "sentence_pattern", "sentencePattern", "句式");
        remapAlias(payload, "modifier_attachment", "modifierAttachment", "modifiers", "modification", "修饰挂接", "修饰");
        remapAlias(payload, "root_affix", "rootAffix", "词根词缀");
        remapAlias(payload, "phonetic", "连读音标", "音标");
    }

    private static void remapAlias(Map<String, Object> payload, String canonical, String... aliases) {
        if (payload.containsKey(canonical)) {
            Object cur = payload.get(canonical);
            if (cur != null && !(cur instanceof List<?> list && list.isEmpty())
                    && !(cur instanceof String s && s.isBlank())) {
                return; // 已有有效 canonical
            }
        }
        for (String alt : aliases) {
            if (!payload.containsKey(alt)) continue;
            Object v = payload.remove(alt);
            if (v == null) continue;
            if (!payload.containsKey(canonical) || isBlankAnalyzeValue(payload.get(canonical))) {
                payload.put(canonical, v);
            }
            return;
        }
    }

    private static boolean isBlankAnalyzeValue(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof List<?> list) return list.isEmpty();
        return false;
    }

    /**
     * 需要用户双击指定目标词的功能：无目标词时不应让模型自行扫原文抽词。
     */
    public static boolean requiresTargetWords(String featureKey) {
        if (featureKey == null || featureKey.isBlank()) return false;
        String k = featureKey.trim();
        return "word".equals(k) || "root_affix".equals(k);
    }

    /** 无目标词时，从下发给模型的功能列表中去掉 word/root_affix（省 token、避免乱抽词）。 */
    public static List<String> featuresForModel(List<String> featureKeys, List<String> targetWords) {
        List<String> keys = normalizeFeatureKeys(featureKeys, null);
        if (targetWords != null && !targetWords.isEmpty()) {
            return keys;
        }
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (!requiresTargetWords(k)) out.add(k);
        }
        return out;
    }

    /**
     * 无目标词时，把本次勾选的 word/root_affix 强制写成空数组（即使模型仍返回了词条）。
     */
    public static void forceEmptyTargetRequiredArrays(Map<String, Object> payload,
                                                      List<String> selectedKeys,
                                                      List<String> targetWords) {
        if (payload == null) return;
        if (targetWords != null && !targetWords.isEmpty()) return;
        List<String> keys = normalizeFeatureKeys(selectedKeys, null);
        for (String k : keys) {
            if (requiresTargetWords(k)) {
                payload.put(k, Collections.emptyList());
            }
        }
    }

    public static List<String> normalizeFeatureKeys(List<String> featureKeys, String featureKey) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (featureKeys != null) {
            for (String k : featureKeys) {
                if (k == null || k.isBlank()) continue;
                for (String part : k.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        }
        if (featureKey != null && !featureKey.isBlank()) {
            for (String part : featureKey.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * @deprecated 目标词说明应由后台场景壳 {target_words} 配置；代码不再下发硬编码字段名文案。
     */
    public static String targetWordsInstruction(String locale, List<String> targetWords) {
        return "";
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
     * 解析 COLLECT_JSON，并用讲解正文中的「单词 / 固定搭配」列表补全漏收词条。
     * 含空格的词条若被标成 word，也会纠正为 phrase。
     */
    public static List<CollectEntryDTO> parseEntriesEnriched(String text, ObjectMapper mapper) {
        List<CollectEntryDTO> fromJson = parseEntries(text, mapper);
        String display = stripForDisplay(text);
        List<CollectEntryDTO> fromProse = new ArrayList<>();
        fromProse.addAll(extractWordsFromProse(display));
        fromProse.addAll(extractPhrasesFromProse(display));
        fromProse.addAll(extractLooseAnalysisEntries(display));

        Map<String, CollectEntryDTO> merged = new LinkedHashMap<>();
        for (CollectEntryDTO e : fromJson) {
            CollectEntryDTO normalized = normalizeEntry(e);
            if (normalized == null) continue;
            merged.put(entryKey(normalized), normalized);
        }
        for (CollectEntryDTO e : fromProse) {
            CollectEntryDTO normalized = normalizeEntry(e);
            if (normalized == null) continue;
            String key = entryKey(normalized);
            CollectEntryDTO existing = merged.get(key);
            if (existing == null) {
                merged.put(key, normalized);
            } else {
                // JSON 常只留语境义；正文多词性时用更完整释义补全
                merged.put(key, enrichFromProse(existing, normalized));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 同词条合并：释义取词性更全/更长的一方，音标与词根缺则补。
     */
    static CollectEntryDTO enrichFromProse(CollectEntryDTO fromJson, CollectEntryDTO fromProse) {
        if (fromJson == null) return fromProse;
        if (fromProse == null) return fromJson;
        return CollectEntryDTO.builder()
                .entryType(fromJson.getEntryType() != null ? fromJson.getEntryType() : fromProse.getEntryType())
                .wordProto(fromJson.getWordProto())
                .partSpeechMean(preferRicherMeaning(fromJson.getPartSpeechMean(), fromProse.getPartSpeechMean()))
                .usPhonetic(blankToNull(fromJson.getUsPhonetic()) != null
                        ? fromJson.getUsPhonetic() : fromProse.getUsPhonetic())
                .ukPhonetic(blankToNull(fromJson.getUkPhonetic()) != null
                        ? fromJson.getUkPhonetic() : fromProse.getUkPhonetic())
                .sentenceExample(blankToNull(fromJson.getSentenceExample()) != null
                        ? fromJson.getSentenceExample() : fromProse.getSentenceExample())
                .rootAffix(blankToNull(fromJson.getRootAffix()) != null
                        ? fromJson.getRootAffix() : fromProse.getRootAffix())
                .appendExample(fromJson.getAppendExample() != null
                        ? fromJson.getAppendExample() : fromProse.getAppendExample())
                .build();
    }

    /** 优先保留含更多词性标记的释义（如 n.…; v.… 优于仅 n.…） */
    static String preferRicherMeaning(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        int ca = countPosMarkers(a);
        int cb = countPosMarkers(b);
        if (cb > ca) return b.trim();
        if (ca > cb) return a.trim();
        return b.trim().length() > a.trim().length() ? b.trim() : a.trim();
    }

    private static int countPosMarkers(String meaning) {
        if (meaning == null || meaning.isBlank()) return 0;
        Matcher m = Pattern.compile(
                "(?i)\\b(?:n|v|adj|adv|prep|conj|pron|det|num|int|phr|vt|vi)\\.|短语\\.?|\\bphrase\\.?")
                .matcher(meaning);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 从讲解正文「单词」等小节提取单词（兜底，模型漏写 COLLECT_JSON 时用） */
    public static List<CollectEntryDTO> extractWordsFromProse(String displayText) {
        if (displayText == null || displayText.isBlank()) {
            return Collections.emptyList();
        }
        List<CollectEntryDTO> out = new ArrayList<>();
        boolean inSection = false;
        for (String rawLine : displayText.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.length() > MAX_PROSE_LINE_LEN) continue;
            if (WORD_HEADING.matcher(line).matches()) {
                inSection = true;
                continue;
            }
            if (inSection && isSectionExit(line, true)) {
                inSection = false;
                continue;
            }
            if (!inSection) continue;

            LemmaHit hit = matchLemmaLine(line);
            if (hit == null) continue;
            String proto = hit.proto;
            String rawMeaning = hit.meaning;
            if (proto.isEmpty() || rawMeaning.isEmpty() || !looksLikeEnglishWord(proto)) continue;

            String us = null;
            String uk = null;
            String meaning = rawMeaning;
            Matcher ph = WORD_PHONETIC_MEANING.matcher(rawMeaning);
            if (ph.matches()) {
                us = ph.group(1);
                uk = ph.group(2);
                meaning = cleanMeaning(ph.group(3));
            }
            if (meaning.isEmpty()) continue;

            out.add(CollectEntryDTO.builder()
                    .entryType("word")
                    .wordProto(proto)
                    .partSpeechMean(meaning)
                    .usPhonetic(us)
                    .ukPhonetic(uk)
                    .build());
        }
        return out;
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
            if (inSection && isSectionExit(line, false)) {
                inSection = false;
                continue;
            }
            if (!inSection) continue;

            LemmaHit hit = matchLemmaLine(line);
            if (hit == null) continue;
            String proto = hit.proto;
            String meaning = hit.meaning;
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

    /**
     * 无标准小节时的兜底：从「lemma：中文」行抽取单词/短语。
     */
    public static List<CollectEntryDTO> extractLooseAnalysisEntries(String displayText) {
        if (displayText == null || displayText.isBlank()) {
            return Collections.emptyList();
        }
        List<CollectEntryDTO> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String rawLine : displayText.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.length() > MAX_PROSE_LINE_LEN) continue;
            if (ANY_SECTION_HEADING.matcher(line).matches() || OTHER_HEADING.matcher(line).matches()) continue;
            LemmaHit hit = matchLemmaLine(line);
            if (hit == null || !hasCjkMeaning(hit.meaning)) continue;
            String proto = hit.proto;
            String meaning = hit.meaning;
            if (proto.isEmpty() || meaning.isEmpty()) continue;
            if (looksLikeEnglishPhrase(proto)) {
                String key = "phrase:" + proto.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                out.add(CollectEntryDTO.builder()
                        .entryType("phrase")
                        .wordProto(proto)
                        .partSpeechMean(meaning.startsWith("短语") || meaning.toLowerCase(Locale.ROOT).startsWith("phrase")
                                ? meaning
                                : "短语. " + meaning)
                        .build());
            } else if (looksLikeEnglishWord(proto)) {
                String key = "word:" + proto.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                out.add(CollectEntryDTO.builder()
                        .entryType("word")
                        .wordProto(proto)
                        .partSpeechMean(meaning)
                        .build());
            }
        }
        return out;
    }

    private record LemmaHit(String proto, String meaning) {}

    private static LemmaHit matchLemmaLine(String line) {
        Matcher m = LEMMA_BULLET.matcher(line);
        if (m.matches()) {
            return new LemmaHit(cleanProto(m.group(1)), cleanMeaning(m.group(2)));
        }
        m = LEMMA_PLAIN.matcher(line);
        if (m.matches()) {
            return new LemmaHit(cleanProto(m.group(1)), cleanMeaning(m.group(2)));
        }
        m = LEMMA_SPACE_CJK.matcher(line);
        if (m.matches()) {
            return new LemmaHit(cleanProto(m.group(1)), cleanMeaning(m.group(2)));
        }
        return null;
    }

    private static boolean hasCjkMeaning(String s) {
        return s != null && s.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }

    /** 离开当前单词/搭配小节：遇到其它小节标题或 ## 标题 */
    private static boolean isSectionExit(String line, boolean inWordSection) {
        if (OTHER_HEADING.matcher(line).matches()) {
            if (inWordSection) return !WORD_HEADING.matcher(line).matches();
            return !COLLOCATION_HEADING.matcher(line).matches();
        }
        if (!ANY_SECTION_HEADING.matcher(line).matches()) return false;
        if (inWordSection) return !WORD_HEADING.matcher(line).matches();
        return !COLLOCATION_HEADING.matcher(line).matches();
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

    /** 单词小节里的单词语条（不含空格短语） */
    private static boolean looksLikeEnglishWord(String proto) {
        String p = proto.trim();
        if (p.isEmpty() || looksLikeEnglishPhrase(p)) return false;
        return p.matches("(?i)^[a-z][a-z0-9'’]*$");
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
