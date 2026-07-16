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

    private static CollectEntryDTO entry(String proto, String mean, boolean append) {
        CollectEntryDTO dto = new CollectEntryDTO();
        dto.setEntryType("word");
        dto.setWordProto(proto);
        dto.setPartSpeechMean(mean);
        dto.setAppendExample(append);
        return dto;
    }
}
