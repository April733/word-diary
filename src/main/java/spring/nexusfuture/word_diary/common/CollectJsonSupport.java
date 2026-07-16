package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public final class CollectJsonSupport {

    public static final String MARKER = "---COLLECT_JSON---";

    private CollectJsonSupport() {}

    public static String collectInstruction() {
        return """

                【收录数据（必须）】
                在完成上述中文讲解后，另起一行输出标记 %s，紧接着输出一行 JSON（不要 markdown 代码块），格式：
                {"entries":[{"entryType":"word或phrase","wordProto":"英文原形","partSpeechMean":"词性+中文释义","usPhonetic":"美音可空","ukPhonetic":"英音可空","rootAffix":"该词的词根词缀分析","sentenceExample":"含该词的英文例句"}]}
                硬性要求：
                1. 每个「单词讲解」中的英文单词单独一条 entryType=word。
                2. 每个「固定搭配 / 固搭 / 短语用法」中出现的搭配必须单独一条 entryType=phrase（固定搭配=短语）；wordProto 写搭配本身（如 pull down、require sb to do sth），不要拆成单词漏收。
                3. partSpeechMean 必填，须与上文讲解一致；短语可写「短语. 含义」。
                4. rootAffix：单词写词根词缀；短语写组成说明（如 pull + down）。
                5. sentenceExample 必填：优先用用户选中的原文句子；若原文不含该词/搭配，再用上文「例句」中包含它的完整英文句。
                6. entries 必须覆盖上文已讲解的全部单词 + 全部固定搭配，禁止只收单词不收搭配。
                """.formatted(MARKER);
    }

    public static boolean shouldCollect(List<String> featureKeys, String featureKey) {
        // 任一阅读 AI 功能（单词/释义/词根/搭配等）都应产出可收录词条
        if (featureKeys != null && !featureKeys.isEmpty()) {
            return true;
        }
        return featureKey != null && !featureKey.isBlank();
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
}
