package spring.nexusfuture.word_diary.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本计量：用于成本分析（用户原始输入字数 / 词数）。
 * 中英混合：英文按单词计，中日韩字符按字计。
 */
public final class TextMetrics {

    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");
    private static final Pattern CJK_CHAR = Pattern.compile("[\\u3400-\\u9FFF\\uF900-\\uFAFF]");

    private TextMetrics() {}

    public static int charCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.codePointCount(0, text.length());
    }

    /** 中英混合词数：拉丁单词 + CJK 单字 */
    public static int wordCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        Matcher latin = LATIN_WORD.matcher(text);
        while (latin.find()) count++;
        Matcher cjk = CJK_CHAR.matcher(text);
        while (cjk.find()) count++;
        return count;
    }
}
