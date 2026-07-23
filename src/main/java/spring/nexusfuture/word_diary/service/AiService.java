package spring.nexusfuture.word_diary.service;

import org.springframework.ai.chat.messages.Message;
import spring.nexusfuture.word_diary.dto.response.AiParseResult;
import reactor.core.publisher.Flux;
import java.util.List;

/** AI 能力门面：单词解析、阅读助手流式/同步对话。 */
public interface AiService {

    /**
     * 批量调用 AI 补全单词释义、音标等信息。
     *
     * @param userId 当前用户 ID
     * @param words 待解析的单词列表
     * @return 解析结果列表
     */
    List<AiParseResult> parseWords(Long userId, List<String> words);

    /**
     * 流式批量解析单词，逐段返回文本片段。
     *
     * @param userId 当前用户 ID
     * @param words 待解析的单词列表
     * @return 文本片段 Flux
     */
    Flux<String> parseWordsStream(Long userId, List<String> words);

    /**
     * AI 阅读助手流式对话（自动记录调用日志）。
     *
     * @param userId 当前用户 ID
     * @param systemPrompt 系统 Prompt
     * @param userPrompt 用户 Prompt
     * @return 助手回复文本片段 Flux
     */
    Flux<String> chatStream(Long userId, String systemPrompt, String userPrompt);

    /**
     * AI 阅读助手流式对话，带场景与原始输入计量。
     *
     * @param userId 当前用户 ID
     * @param callScene 场景标识，如 reading_chat / feature
     * @param inputText 用户原始输入（选中文本或提问），用于字数/词数统计
     * @param systemPrompt 系统 Prompt
     * @param userPrompt 用户 Prompt
     * @return 助手回复文本片段 Flux
     */
    Flux<String> chatStream(Long userId, String callScene, String inputText,
                            String systemPrompt, String userPrompt);

    /**
     * 流式对话，可附带多轮历史（按时间正序的 user/assistant 消息）。
     * history 为空时等价于无记忆单轮调用。
     *
     * @param userId 当前用户 ID
     * @param callScene 场景标识
     * @param inputText 用户原始输入
     * @param systemPrompt 系统 Prompt
     * @param userPrompt 用户 Prompt
     * @param history 历史消息列表，可为空
     * @return 助手回复文本片段 Flux
     */
    Flux<String> chatStream(Long userId, String callScene, String inputText,
                            String systemPrompt, String userPrompt,
                            List<Message> history);

    /**
     * 同步单轮调用（语法对照等需解析 JSON 的场景）。
     *
     * @param userId 当前用户 ID
     * @param callScene 场景标识
     * @param inputText 用户原始输入
     * @param systemPrompt 系统 Prompt
     * @param userPrompt 用户 Prompt
     * @return 助手完整回复文本
     */
    String chatOnce(Long userId, String callScene, String inputText,
                    String systemPrompt, String userPrompt);
}
