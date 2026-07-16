package spring.nexusfuture.word_diary.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectJsonSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldCollect_whenAnyFeaturePresent() {
        assertTrue(CollectJsonSupport.shouldCollect(List.of("meaning", "word"), null));
        assertTrue(CollectJsonSupport.shouldCollect(List.of("root", "example"), null));
        assertTrue(CollectJsonSupport.shouldCollect(null, "word"));
        assertTrue(CollectJsonSupport.shouldCollect(null, "meaning"));
        assertFalse(CollectJsonSupport.shouldCollect(List.of(), null));
        assertFalse(CollectJsonSupport.shouldCollect(null, null));
        assertFalse(CollectJsonSupport.shouldCollect(null, ""));
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
    void collectInstruction_containsMarker() {
        String instruction = CollectJsonSupport.collectInstruction();
        assertTrue(instruction.contains(CollectJsonSupport.MARKER));
        assertTrue(instruction.contains("entryType"));
        assertTrue(instruction.contains("wordProto"));
        assertTrue(instruction.contains("rootAffix"));
        assertTrue(instruction.contains("sentenceExample"));
        assertTrue(instruction.contains("固定搭配") || instruction.contains("phrase"));
    }

    @Test
    void parseEntries_keepsRootAffixAndExample() throws Exception {
        String raw = """
                讲解。
                %s
                {"entries":[{"entryType":"word","wordProto":"require","partSpeechMean":"v. 需要","rootAffix":"re- + quire","sentenceExample":"This task requires care."}]}
                """.formatted(CollectJsonSupport.MARKER);
        List<CollectEntryDTO> entries = CollectJsonSupport.parseEntries(raw, mapper);
        assertEquals(1, entries.size());
        assertEquals("re- + quire", entries.get(0).getRootAffix());
        assertEquals("This task requires care.", entries.get(0).getSentenceExample());
    }

    @Test
    void stripForDisplay_keepsParseableJsonInRaw() {
        String raw = "讲解\n" + CollectJsonSupport.MARKER + "\n{\"entries\":[{\"entryType\":\"word\",\"wordProto\":\"a\",\"partSpeechMean\":\"甲\"}]}";
        assertEquals("讲解", CollectJsonSupport.stripForDisplay(raw));
        assertEquals(1, CollectJsonSupport.parseEntries(raw, mapper).size());
    }
}
