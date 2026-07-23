package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import spring.nexusfuture.word_diary.dto.response.CollectEntryDTO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectJsonSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldCollect_whenAnyFeatureSelected() {
        assertTrue(CollectJsonSupport.shouldCollect(List.of("meaning", "word"), null));
        assertTrue(CollectJsonSupport.shouldCollect(List.of("collocation"), null));
        assertTrue(CollectJsonSupport.shouldCollect(null, "word"));
        assertTrue(CollectJsonSupport.shouldCollect(List.of("meaning"), null));
        assertTrue(CollectJsonSupport.shouldCollect(List.of("sentence_pattern"), null));
        assertFalse(CollectJsonSupport.shouldCollect(List.of(), null));
        assertFalse(CollectJsonSupport.shouldCollect(null, null));
        assertFalse(CollectJsonSupport.shouldCollect(null, ""));
    }

    @Test
    void shouldRequireWordPhonetics_alwaysFalse_adminOwnsPrompt() {
        assertFalse(CollectJsonSupport.shouldRequireWordPhonetics(List.of("word"), null));
        assertFalse(CollectJsonSupport.shouldRequireWordPhonetics(List.of("meaning"), null));
        assertFalse(CollectJsonSupport.shouldRequireWordPhonetics(List.of("collocation"), null));
    }

    @Test
    void featureScopeInstruction_isEmpty_adminOwnsText() {
        assertEquals("", CollectJsonSupport.featureScopeInstruction("zh-CN", List.of("meaning"), null));
    }

    @Test
    void normalizeAnalyzeKeyAliases_mapsChineseCollocationKey() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("固定搭配", List.of(Map.of("word", "include examples of", "meaning", "举…的例子")));
        payload.put("翻译", "译文");
        CollectJsonSupport.normalizeAnalyzeKeyAliases(payload);
        assertTrue(payload.containsKey("collocation"));
        assertTrue(payload.containsKey("meaning"));
        assertFalse(payload.containsKey("固定搭配"));
        assertFalse(payload.containsKey("翻译"));
    }

    @Test
    void featuresForModel_dropsWordWithoutTargets() {
        List<String> keys = List.of("word", "meaning", "collocation", "root_affix");
        assertEquals(List.of("meaning", "collocation"),
                CollectJsonSupport.featuresForModel(keys, List.of()));
        assertEquals(keys, CollectJsonSupport.featuresForModel(keys, List.of("run")));
    }

    @Test
    void forceEmptyTargetRequiredArrays_clearsWordWhenNoTargets() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("meaning", "译文");
        payload.put("word", List.of(Map.of("word", "run", "meaning", "v. 经营")));
        payload.put("root_affix", List.of(Map.of("word", "elderly", "rootAffix", "…")));
        CollectJsonSupport.forceEmptyTargetRequiredArrays(
                payload, List.of("word", "meaning", "root_affix"), List.of());
        assertEquals("译文", payload.get("meaning"));
        assertEquals(List.of(), payload.get("word"));
        assertEquals(List.of(), payload.get("root_affix"));
    }

    @Test
    void pruneAnalyzePayload_keepsOnlySelectedKeys_noRename() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("meaning", "译文");
        payload.put("phonetic", "- **reviewer**: /riːˈvjuːər/");
        payload.put("grammar", "主谓结构");
        payload.put("word", List.of(
                Map.of("entryType", "word", "word", "reviewer", "meaning", "n. 评论家")
        ));
        payload.put("collocation", List.of(
                Map.of("entryType", "phrase", "word", "include examples of", "meaning", "举例说明")
        ));

        CollectJsonSupport.pruneAnalyzePayload(payload,
                List.of("meaning", "word", "collocation", "grammar"));

        assertEquals("译文", payload.get("meaning"));
        assertEquals("主谓结构", payload.get("grammar"));
        assertFalse(payload.containsKey("phonetic"), "未勾选连读音标时应删除 phonetic");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> words = (List<Map<String, Object>>) payload.get("word");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phrases = (List<Map<String, Object>>) payload.get("collocation");
        assertEquals(1, words.size());
        assertEquals(1, phrases.size());
    }

    @Test
    void pruneAnalyzePayload_collocationOnlyKeepsThatKey() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("word", List.of(
                Map.of("entryType", "word", "word", "reviewer", "meaning", "n. 评论家")
        ));
        payload.put("collocation", List.of(
                Map.of("entryType", "phrase", "word", "examples of", "meaning", "…的例子")
        ));
        CollectJsonSupport.pruneAnalyzePayload(payload, List.of("collocation"));
        assertFalse(payload.containsKey("word"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phrases = (List<Map<String, Object>>) payload.get("collocation");
        assertEquals(1, phrases.size());
    }

    @Test
    void extractEntries_fromKeyBasedWordAndCollocation() throws Exception {
        String json = """
                {"word":[{"word":"reviewer","meaning":"n. 评论家"}],"collocation":[{"word":"examples of","meaning":"…的例子"}]}
                """;
        var entries = CollectJsonSupport.extractEntriesFromAnalyzeJson(json, mapper);
        assertEquals(2, entries.size());
        assertEquals("word", entries.get(0).getEntryType());
        assertEquals("phrase", entries.get(1).getEntryType());
    }

    @Test
    void stripForDisplay_removesMarkerAndJson() {
        String raw = "讲解内容\n\n" + CollectJsonSupport.MARKER + "\n{\"entries\":[]}";
        assertEquals("讲解内容", CollectJsonSupport.stripForDisplay(raw));
        assertEquals("plain", CollectJsonSupport.stripForDisplay("plain"));
        assertEquals("", CollectJsonSupport.stripForDisplay(null));
    }

    @Test
    void parseEntries_readsWordAndPhrase() throws Exception {
        String raw = """
                这是讲解。
                %s
                {"entries":[{"entryType":"word","wordProto":"strategy","partSpeechMean":"n. 策略"},{"entryType":"phrase","wordProto":"set up","partSpeechMean":"v. 建立"}]}
                """.formatted(CollectJsonSupport.MARKER);

        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntries(raw, mapper);
        assertEquals(2, entries.size());
        assertEquals("word", entries.get(0).getEntryType());
        assertEquals("strategy", entries.get(0).getWordProto());
        assertEquals("n. 策略", entries.get(0).getPartSpeechMean());
        assertEquals("phrase", entries.get(1).getEntryType());
        assertEquals("set up", entries.get(1).getWordProto());
    }

    @Test
    void parseEntries_returnsEmptyOnInvalidJson() {
        String raw = CollectJsonSupport.MARKER + "\n{not-json}";
        assertTrue(CollectJsonSupport.parseEntries(raw, mapper).isEmpty());
        assertTrue(CollectJsonSupport.parseEntries("no marker", mapper).isEmpty());
        assertTrue(CollectJsonSupport.parseEntries(null, mapper).isEmpty());
    }

    @Test
    void collectInstruction_empty_adminOwnsText() {
        assertEquals("", CollectJsonSupport.collectInstruction());
        assertEquals("", CollectJsonSupport.collectInstruction("zh-CN"));
        assertEquals("", CollectJsonSupport.collectInstruction("en"));
    }

    @Test
    void parseEntries_keepsRootAffix() throws Exception {
        String raw = """
                讲解。
                %s
                {"entries":[{"entryType":"word","wordProto":"require","partSpeechMean":"v. 需要","rootAffix":"re- + quire"}]}
                """.formatted(CollectJsonSupport.MARKER);
        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntries(raw, mapper);
        assertEquals(1, entries.size());
        assertEquals("re- + quire", entries.get(0).getRootAffix());
    }

    @Test
    void parseEntriesEnriched_mergesPhrasesFromCollocationSection() {
        String raw = """
                ## 翻译释义
                所选路线不需要拆除许多建筑物。

                ## 固定搭配
                - **require ... to be done**：需要某事被完成
                - **pull down**：拆除（建筑物）

                ## 语法结构
                被动结构说明。

                %s
                {"entries":[{"entryType":"word","wordProto":"require","partSpeechMean":"v. 需要"},{"entryType":"word","wordProto":"pull down","partSpeechMean":"拆除"}]}
                """.formatted(CollectJsonSupport.MARKER);

        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntriesEnriched(raw, mapper);
        assertTrue(entries.stream().anyMatch(e ->
                "phrase".equals(e.getEntryType()) && "pull down".equalsIgnoreCase(e.getWordProto())));
        assertTrue(entries.stream().anyMatch(e ->
                "phrase".equals(e.getEntryType()) && e.getWordProto() != null
                        && e.getWordProto().toLowerCase().contains("require")));
        // JSON 里误标为 word 的短语应纠正为 phrase
        assertEquals("phrase", entries.stream()
                .filter(e -> "pull down".equalsIgnoreCase(e.getWordProto()))
                .findFirst().orElseThrow().getEntryType());
    }

    @Test
    void parseEntriesEnriched_prefersMultiPosMeaningFromProse() {
        String raw = """
                单词：
                - **approach**: 美 /əˈproʊtʃ/, 英 /əˈprəʊtʃ/; n. 方法; 途径; v. 接近; 处理

                %s
                {"entries":[{"entryType":"word","wordProto":"approach","partSpeechMean":"n. 方法；途径","usPhonetic":"/əˈproʊtʃ/","ukPhonetic":"/əˈprəʊtʃ/"}]}
                """.formatted(CollectJsonSupport.MARKER);

        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntriesEnriched(raw, mapper);
        assertEquals(1, entries.size());
        String mean = entries.get(0).getPartSpeechMean();
        assertTrue(mean.contains("n."), mean);
        assertTrue(mean.contains("v."), "应保留正文中的动词义: " + mean);
    }

    @Test
    void preferRicherMeaning_keepsMorePosTags() {
        assertEquals(
                "n. 方法; 途径; v. 接近; 处理",
                CollectJsonSupport.preferRicherMeaning("n. 方法；途径", "n. 方法; 途径; v. 接近; 处理"));
    }

    @Test
    void extractPhrasesFromProse_readsCornerQuotedHeadingAndPlainLines() {
        String prose = """
                「固定搭配：」
                - **arrange it to suit you**：按你的时间安排
                - **keep records of** 做…的记录
                arrange it well：妥善安排
                「语法：」
                主语 + 谓语
                """;
        List<CollectEntryDTO> phrases = CollectJsonSupport.extractPhrasesFromProse(prose);
        assertTrue(phrases.stream().anyMatch(e -> "arrange it to suit you".equalsIgnoreCase(e.getWordProto())));
        assertTrue(phrases.stream().anyMatch(e -> "keep records of".equalsIgnoreCase(e.getWordProto())));
        assertTrue(phrases.stream().anyMatch(e -> "arrange it well".equalsIgnoreCase(e.getWordProto())));
    }

    @Test
    void parseEntriesEnriched_recoversPhrasesWhenJsonOnlyHasWords() {
        String raw = """
                「解析」
                「固定搭配」
                - **arrange it to suit you**：按你的时间安排
                - **keep records of**：做…的记录
                - **keep them up to date**：使它们保持最新
                「语法」
                说明。

                %s
                {"entries":[{"entryType":"word","wordProto":"suit","partSpeechMean":"v. 适合","usPhonetic":"/suːt/","ukPhonetic":"/suːt/"},{"entryType":"word","wordProto":"record","partSpeechMean":"n. 记录","usPhonetic":"/ˈrekərd/","ukPhonetic":"/ˈrekɔːd/"}]}
                """.formatted(CollectJsonSupport.MARKER);

        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntriesEnriched(raw, mapper);
        assertTrue(entries.stream().anyMatch(e ->
                "word".equals(e.getEntryType()) && "suit".equalsIgnoreCase(e.getWordProto())));
        assertTrue(entries.stream().anyMatch(e ->
                "phrase".equals(e.getEntryType()) && "arrange it to suit you".equalsIgnoreCase(e.getWordProto())));
        assertTrue(entries.stream().anyMatch(e ->
                "phrase".equals(e.getEntryType()) && "keep them up to date".equalsIgnoreCase(e.getWordProto())));
        assertTrue(entries.size() >= 5);
    }

    @Test
    void extractPhrasesFromProse_readsBulletsWithoutJson() {
        String prose = """
                ## 固定搭配
                - **pull down**：拆除
                - require sb to do sth: 要求某人做某事
                """;
        List<CollectEntryDTO> phrases = CollectJsonSupport.extractPhrasesFromProse(prose);
        assertEquals(2, phrases.size());
        assertEquals("phrase", phrases.get(0).getEntryType());
        assertEquals("pull down", phrases.get(0).getWordProto());
        assertTrue(phrases.get(0).getPartSpeechMean().contains("拆除"));
        assertEquals("require sb to do sth", phrases.get(1).getWordProto());
    }

    @Test
    void extractWordsFromProse_readsWordSectionWithPhonetics() {
        String prose = """
                翻译：
                通风井的使用未能防止隧道内的污染。

                单词：
                - shafts: 美 /ˈʃæftz/, 英 /ˈʃæfts/; n. 井；竖井；通风井

                固定搭配：
                - use of: 使用……的方法/工具
                - failed to prevent: 未能阻止

                语法：
                主语 + 谓语
                """;
        List<CollectEntryDTO> words = CollectJsonSupport.extractWordsFromProse(prose);
        assertEquals(1, words.size());
        assertEquals("word", words.get(0).getEntryType());
        assertEquals("shafts", words.get(0).getWordProto());
        assertEquals("/ˈʃæftz/", words.get(0).getUsPhonetic());
        assertEquals("/ˈʃæfts/", words.get(0).getUkPhonetic());
        assertTrue(words.get(0).getPartSpeechMean().contains("井"));

        List<CollectEntryDTO> phrases = CollectJsonSupport.extractPhrasesFromProse(prose);
        assertEquals(2, phrases.size());
        assertTrue(phrases.stream().anyMatch(e -> "use of".equalsIgnoreCase(e.getWordProto())));
        assertTrue(phrases.stream().anyMatch(e -> "failed to prevent".equalsIgnoreCase(e.getWordProto())));
    }

    @Test
    void parseEntriesEnriched_mergesWordsFromWordSectionWhenJsonMissesThem() {
        String raw = """
                单词：
                - **shafts**: 美 /ˈʃæftz/, 英 /ˈʃæfts/; n. 井；竖井；通风井

                固定搭配：
                - **use of**：使用
                - **failed to prevent**：未能阻止

                %s
                {"entries":[{"entryType":"phrase","wordProto":"use of","partSpeechMean":"短语. 使用"},{"entryType":"phrase","wordProto":"failed to prevent","partSpeechMean":"短语. 未能阻止"}]}
                """.formatted(CollectJsonSupport.MARKER);

        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntriesEnriched(raw, mapper);
        assertTrue(entries.stream().anyMatch(e ->
                "word".equals(e.getEntryType()) && "shafts".equalsIgnoreCase(e.getWordProto())));
        assertTrue(entries.stream().anyMatch(e ->
                "phrase".equals(e.getEntryType()) && "use of".equalsIgnoreCase(e.getWordProto())));
        assertEquals(3, entries.size());
    }

    @Test
    void stripForDisplay_keepsParseableJsonInRaw() {
        String raw = "讲解\n" + CollectJsonSupport.MARKER + "\n{\"entries\":[{\"entryType\":\"word\",\"wordProto\":\"a\",\"partSpeechMean\":\"甲\"}]}";
        assertEquals("讲解", CollectJsonSupport.stripForDisplay(raw));
        assertEquals(1, CollectJsonSupport.parseEntries(raw, mapper).size());
    }

    @Test
    void phoneticAndCollectInstruction_empty_adminOwnsText() {
        assertEquals("", CollectJsonSupport.phoneticDisplayInstruction("zh-CN"));
        assertEquals("", CollectJsonSupport.phoneticDisplayInstruction("en"));
        assertEquals("", CollectJsonSupport.phoneticFormatInstruction("zh-CN"));
        assertEquals("", CollectJsonSupport.collectInstruction("zh-CN"));
    }
}
