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
                {"entries":[{"entryType":"word或phrase","wordProto":"英文","partSpeechMean":"中文释义","usPhonetic":"","ukPhonetic":"","rootAffix":""}]}
                要求：每个英文单词单独一条 entryType=word；固定短语/搭配单独一条 entryType=phrase；禁止把多个无关词合并成一条；partSpeechMean 必填。
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
        if (text == null) return "";
        int idx = text.indexOf(MARKER);
        return idx >= 0 ? text.substring(0, idx).trim() : text;
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
