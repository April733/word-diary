package spring.nexusfuture.word_diary.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spring.nexusfuture.word_diary.dto.BookCollectReq;
import spring.nexusfuture.word_diary.dto.BookCollectResultVO;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookCollectServiceImplTest {

    @Mock WdBookMapper bookMapper;
    @Mock WdWordBookMapper notebookMapper;
    @Mock WdWordMapper wordMapper;
    @Mock WdLearnPlanMapper planMapper;

    BookCollectServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookCollectServiceImpl(bookMapper, notebookMapper, wordMapper, planMapper);
        when(bookMapper.selectById(1L)).thenReturn(WdBook.builder().id(1L).userId(9L).build());
        when(notebookMapper.selectById(2L)).thenReturn(
                WdWordBook.builder().id(2L).userId(9L).bookId(1L).build());
    }

    @Test
    void collect_skipsDuplicateAlreadyInNotebook() {
        when(wordMapper.selectByNotebookAndWordProto(2L, "strategy"))
                .thenReturn(WdWord.builder().id(10L).wordProto("strategy").sentenceExample("old").build());

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setItems(List.of(entry("strategy", "n. 策略", false)));

        BookCollectResultVO result = service.collect(9L, 1L, req);
        assertEquals(0, result.getAdded());
        assertEquals(1, result.getSkipped());
        verify(wordMapper, never()).insert(any());
    }

    @Test
    void collect_skipsDuplicateWithinSameRequest() {
        when(wordMapper.selectByNotebookAndWordProto(eq(2L), anyString())).thenReturn(null);

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setItems(List.of(
                entry("Strategy", "n. 策略", false),
                entry("strategy", "n. 策略（重复）", false)
        ));

        BookCollectResultVO result = service.collect(9L, 1L, req);
        assertEquals(1, result.getAdded());
        assertEquals(1, result.getSkipped());
        ArgumentCaptor<WdWord> captor = ArgumentCaptor.forClass(WdWord.class);
        verify(wordMapper, times(1)).insert(captor.capture());
        assertEquals("Strategy", captor.getValue().getWordProto());
    }

    @Test
    void collect_usesOriginalSnippetAsExample() {
        when(wordMapper.selectByNotebookAndWordProto(eq(2L), anyString())).thenReturn(null);

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setSourceSnippet("The streets were full of horse-drawn vehicles.");
        req.setItems(List.of(entry("street", "n. 街道", false)));

        service.collect(9L, 1L, req);
        ArgumentCaptor<WdWord> captor = ArgumentCaptor.forClass(WdWord.class);
        verify(wordMapper).insert(captor.capture());
        assertEquals("【原文】The streets were full of horse-drawn vehicles.",
                captor.getValue().getSentenceExample());
    }

    @Test
    void collect_picksOnlySentenceContainingLemma() {
        when(wordMapper.selectByNotebookAndWordProto(eq(2L), anyString())).thenReturn(null);

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setSourceSnippet(
                "Charles Pearson, a solicitor, suggested building an underground railway. "
                        + "Building the railway would make it possible to move people.");
        req.setItems(List.of(
                entry("solicitor", "n. 律师", false),
                entry("possible", "adj. 可能的", false)
        ));

        service.collect(9L, 1L, req);
        ArgumentCaptor<WdWord> captor = ArgumentCaptor.forClass(WdWord.class);
        verify(wordMapper, times(2)).insert(captor.capture());
        List<WdWord> words = captor.getAllValues();
        assertEquals("【原文】Charles Pearson, a solicitor, suggested building an underground railway.",
                words.get(0).getSentenceExample());
        assertTrue(words.get(1).getSentenceExample().contains("Building the railway would make it possible"));
        assertFalse(words.get(1).getSentenceExample().toLowerCase().contains("solicitor"));
    }

    @Test
    void collect_repairsOcrSeparatorsThenPicksSentence() {
        when(wordMapper.selectByNotebookAndWordProto(eq(2L), anyString())).thenReturn(null);

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setSourceSnippet(
                "The construction e. The chosen route did not require many buildings to be pulled down e "
                        + "The 'cut and cover' method was used to construct the tunnels ° "
                        + "on the completion of the brick arch, the tunnel was covered with");
        req.setItems(List.of(entry("arch", "n. 拱", false)));

        service.collect(9L, 1L, req);
        ArgumentCaptor<WdWord> captor = ArgumentCaptor.forClass(WdWord.class);
        verify(wordMapper).insert(captor.capture());
        String example = captor.getValue().getSentenceExample();
        assertTrue(example.contains("arch"));
        assertFalse(example.toLowerCase().contains("require"));
        assertFalse(example.toLowerCase().contains("cut and cover"));
        assertEquals(
                "pulled down. The tunnels. on the completion",
                BookCollectServiceImpl.repairSentenceBoundaries("pulled down e The tunnels ° on the completion"));
    }

    @Test
    void collect_overwritesWhenFlagSet() {
        when(wordMapper.selectByNotebookAndWordProto(2L, "approach"))
                .thenReturn(WdWord.builder().id(10L).wordProto("approach")
                        .partSpeechMean("n. 方法").sentenceExample("【原文】old").build());

        CollectEntryDTO dto = entry("approach", "n. 方法; 途径; v. 接近; 处理", false);
        dto.setOverwrite(true);
        dto.setUsPhonetic("/əˈproʊtʃ/");
        dto.setRootAffix("approach (词根)");
        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setSourceSnippet("A different approach from the cut and cover technique.");
        req.setItems(List.of(dto));

        BookCollectResultVO result = service.collect(9L, 1L, req);
        assertEquals(0, result.getAdded());
        assertEquals(0, result.getSkipped());
        assertEquals(1, result.getUpdated());
        ArgumentCaptor<WdWord> captor = ArgumentCaptor.forClass(WdWord.class);
        verify(wordMapper).update(captor.capture());
        WdWord updated = captor.getValue();
        assertEquals("n. 方法; 途径; v. 接近; 处理", updated.getPartSpeechMean());
        assertEquals("/əˈproʊtʃ/", updated.getUsPhonetic());
        assertEquals("approach (词根)", updated.getRootAffix());
        assertEquals(1L, updated.getSourceBookId());
        assertTrue(updated.getSentenceExample().contains("approach"));
        verify(wordMapper, never()).insert(any());
    }

    @Test
    void collect_overwritesWhenRequestFlagSet() {
        when(wordMapper.selectByNotebookAndWordProto(2L, "approach"))
                .thenReturn(WdWord.builder().id(10L).wordProto("approach")
                        .partSpeechMean("n. 方法").build());

        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setOverwriteExisting(true);
        req.setItems(List.of(entry("approach", "n. 方法; v. 接近", false)));

        BookCollectResultVO result = service.collect(9L, 1L, req);
        assertEquals(1, result.getUpdated());
        assertEquals(0, result.getSkipped());
        verify(wordMapper).update(any());
    }

    @Test
    void collect_ignoresAiSentenceExampleAndDoesNotAppend() {
        when(wordMapper.selectByNotebookAndWordProto(2L, "strategy"))
                .thenReturn(WdWord.builder().id(10L).wordProto("strategy")
                        .sentenceExample("【原文】old").build());

        CollectEntryDTO dto = entry("strategy", "n. 策略", true);
        dto.setSentenceExample("AI invented example");
        BookCollectReq req = new BookCollectReq();
        req.setNotebookId(2L);
        req.setSourceSnippet("new snippet");
        req.setItems(List.of(dto));

        BookCollectResultVO result = service.collect(9L, 1L, req);
        assertEquals(0, result.getAdded());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getUpdated());
        verify(wordMapper, never()).update(any());
    }

    private static CollectEntryDTO entry(String proto, String mean, boolean append) {
        CollectEntryDTO dto = new CollectEntryDTO();
        dto.setEntryType("word");
        dto.setWordProto(proto);
        dto.setPartSpeechMean(mean);
        dto.setAppendExample(append);
        return dto;
    }
}
