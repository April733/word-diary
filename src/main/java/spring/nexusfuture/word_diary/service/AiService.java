package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.AiParseResult;
import reactor.core.publisher.Flux;
import java.util.List;

public interface AiService {
    /**
     * 调用 DeepSeek API 批量补全单词信息
     */
    List<AiParseResult> parseWords(Long userId, List<String> words);

    /**
     * 流式调用 DeepSeek API，返回文本片段的 Flux
     */
    Flux<String> parseWordsStream(Long userId, List<String> words);

    /**
     * AI 阅读助手流式对话（自动记录调用日志）
     */
    Flux<String> chatStream(Long userId, String systemPrompt, String userPrompt);
}
