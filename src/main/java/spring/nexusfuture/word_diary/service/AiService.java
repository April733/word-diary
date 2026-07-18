package spring.nexusfuture.word_diary.service;

import org.springframework.ai.chat.messages.Message;
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

    /**
     * AI 阅读助手流式对话，带场景与原始输入计量
     *
     * @param callScene  场景标识，如 reading_chat / feature
     * @param inputText  用户原始输入（选中文本或提问），用于字数/词数统计
     */
    Flux<String> chatStream(Long userId, String callScene, String inputText,
                            String systemPrompt, String userPrompt);

    /**
     * 流式对话，可附带多轮历史（按时间正序的 user/assistant 消息）。
     * history 为空时等价于无记忆单轮调用。
     */
    Flux<String> chatStream(Long userId, String callScene, String inputText,
                            String systemPrompt, String userPrompt,
                            List<Message> history);
}
