package spring.nexusfuture.word_diary.config;

import org.springframework.stereotype.Component;
import spring.nexusfuture.word_diary.entity.SysAiFeature;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.SysAiFeatureMapper;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;

import java.util.List;
import java.util.Map;

/**
 * 全局 AI 提示词构建器。
 * 所有 AI 调用统一通过此类构建 system prompt，确保语言等全局约束一致。
 * 为避免模型被 system prompt 本身的语种带偏，所有指令均使用用户目标语言撰写。
 */
@Component
public class AiPromptBuilder {

    private final WdUserMapper userMapper;
    private final SysAiFeatureMapper featureMapper;

    public AiPromptBuilder(WdUserMapper userMapper, SysAiFeatureMapper featureMapper) {
        this.userMapper = userMapper;
        this.featureMapper = featureMapper;
    }

    /**
     * 每个语言对应一套完整提示词模板。使用目标语言撰写指令，可最大限度让模型按目标语言输出。
     */
    private record PromptTemplate(
            String localeName,
            String globalConstraint,
            String readingAssistantSystem,
            String readingAssistantContext,
            String readingAssistantUserPrefix,
            String wordParseSystem,
            String wordParseUserPrefix
    ) {}

    private static final Map<String, PromptTemplate> TEMPLATES = Map.of(
            "zh-CN", buildZhCnTemplate(),
            "en", buildEnTemplate(),
            "id", buildIdTemplate(),
            "vi", buildViTemplate(),
            "th", buildThTemplate()
    );

    // ==================== 中文 ====================

    private static PromptTemplate buildZhCnTemplate() {
        String globalConstraint = "【重要全局规则】请始终使用中文回复用户。无论用户输入何种语言，所有释义、例句翻译、解析说明都必须用中文。英文原文和音标除外。";
        String readingAssistantSystem = "你是一个专业的英语阅读助手。用户正在阅读英文原著，请用中文回答用户的问题。";
        String readingAssistantContext = "用户当前选中的上下文是：\"%s\"";
        String readingAssistantUserPrefix = "请用中文回答以下用户问题：\n%s";
        String wordParseSystem = globalConstraint + "\n" + """
                你是一个专业的英语词典助手。用户会给你一个英文单词/短语列表（JSON数组），请为每个词条补全以下信息，并以严格的 JSON 数组格式返回。

                返回格式要求：
                [
                  {
                    "wordProto": "原词",
                    "usPhonetic": "美式音标，如 /ˈeksəmpəl/",
                    "ukPhonetic": "英式音标，如 /ɪɡˈzɑːmpəl/",
                    "partSpeechMean": "词性+中文释义，如 n. 例子；v. 举例",
                    "sentenceExample": "为每个词性各提供一个例句，格式：词性标记. 英文例句 中文翻译。多个词性用换行符\\n分隔，如 n. This is an example. 这是一个例子。\\nv. He exemplified the rule. 他举例说明了这个规则。",
                    "rootAffix": "词根词缀解析，如 ex-（出）+ ample（拿）→ 拿出来做示范 → 例子。短语，无词根词缀"
                  }
                ]

                要求：
                1. 必须返回完整的 JSON 数组，不要有任何额外的解释文字
                2. 字段不能为空，如果确实没有信息，写\"暂无\"
                3. 音标使用 IPA 国际音标
                4. 例句要自然实用，每个词性都要有一个对应的例句，用\"词性标记. 英文例句 中文翻译\"格式，换行符\\n分隔
                5. 词根词缀解析要准确，包含词源拆解和语义演变
                6. 对于短语（如 set up, look forward to），partSpeechMean 标注为\"短语\"而非单一词性，usPhonetic/ukPhonetic 写完整短语的音标
                """;
        String wordParseUserPrefix = "请补全以下单词/短语的信息：\n";
        return new PromptTemplate("中文", globalConstraint, readingAssistantSystem,
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix);
    }

    // ==================== English ====================

    private static PromptTemplate buildEnTemplate() {
        String globalConstraint = "[Important Global Rule] Always respond to the user in English. Regardless of what language the user inputs, all explanations, translations, and analyses must be in English. Original English text and phonetic transcriptions are exceptions.";
        String readingAssistantSystem = "You are a professional English reading assistant. The user is reading an English original book. Please answer the user's questions in English.";
        String readingAssistantContext = "The user's currently selected context is: \"%s\"";
        String readingAssistantUserPrefix = "Please answer the following user question in English:\n%s";
        String wordParseSystem = globalConstraint + "\n" + """
                You are a professional English dictionary assistant. The user will give you a list of English words/phrases (JSON array). Please complete the information for each entry and return it in a strict JSON array format.

                Return format:
                [
                  {
                    "wordProto": "original word",
                    "usPhonetic": "American phonetic, e.g. /ˈeksəmpəl/",
                    "ukPhonetic": "British phonetic, e.g. /ɪɡˈzɑːmpəl/",
                    "partSpeechMean": "part of speech + English meaning, e.g. n. example; v. to exemplify",
                    "sentenceExample": "Provide one example sentence for each part of speech, format: part-of-speech tag. English sentence English explanation. If multiple parts of speech, separate with newline \\n, e.g. n. This is an example. This is an example.\\nv. He exemplified the rule. He exemplified the rule.",
                    "rootAffix": "Root/affix analysis, e.g. ex- (out) + ample (take) → take out as a demonstration → example. Phrase, no root/affix"
                  }
                ]

                Requirements:
                1. Must return a complete JSON array with no extra explanatory text
                2. Fields cannot be empty; if there is no information, write \"N/A\"
                3. Use IPA for phonetic transcriptions
                4. Example sentences should be natural and practical; each part of speech should have one example sentence in the format \"part-of-speech tag. English sentence English explanation\", separated by newline \\n
                5. Root/affix analysis should be accurate, including etymological breakdown and semantic evolution
                6. For phrases (e.g. set up, look forward to), mark partSpeechMean as \"phrase\" rather than a single part of speech, and write the full phrase phonetic for usPhonetic/ukPhonetic
                """;
        String wordParseUserPrefix = "Please complete the information for the following words/phrases:\n";
        return new PromptTemplate("English", globalConstraint, readingAssistantSystem,
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix);
    }

    // ==================== Bahasa Indonesia ====================

    private static PromptTemplate buildIdTemplate() {
        String globalConstraint = "[Aturan Global Penting] Selalu balas pengguna dalam Bahasa Indonesia. Meskipun pengguna menulis dalam bahasa Inggris atau bahasa lain, semua penjelasan, terjemahan contoh kalimat, dan analisis harus dalam Bahasa Indonesia. Kecuali teks asli bahasa Inggris dan transkripsi fonetik.";
        String readingAssistantSystem = "Anda adalah asisten membaca bahasa Inggris profesional. Pengguna sedang membaca buku asli bahasa Inggris. Harap jawab pertanyaan pengguna dalam Bahasa Indonesia.";
        String readingAssistantContext = "Konteks yang sedang dipilih pengguna adalah: \"%s\"";
        String readingAssistantUserPrefix = "Harap jawab pertanyaan pengguna berikut dalam Bahasa Indonesia:\n%s";
        String wordParseSystem = globalConstraint + "\n" + """
                Anda adalah asisten kamus bahasa Inggris profesional. Pengguna akan memberikan daftar kata/frasa bahasa Inggris (array JSON). Harap lengkapi informasi untuk setiap entri dan kembalikan dalam format array JSON yang ketat.

                Format pengembalian:
                [
                  {
                    "wordProto": "kata asli",
                    "usPhonetic": "transkripsi fonetik Amerika, misalnya /ˈeksəmpəl/",
                    "ukPhonetic": "transkripsi fonetik Britania, misalnya /ɪɡˈzɑːmpəl/",
                    "partSpeechMean": "jenis kata + arti dalam Bahasa Indonesia, misalnya n. contoh; v. memberi contoh",
                    "sentenceExample": "Berikan satu contoh kalimat untuk setiap jenis kata, format: tanda jenis kata. Kalimat bahasa Inggris Terjemahan Bahasa Indonesia. Jika beberapa jenis kata, pisahkan dengan baris baru \\n, misalnya n. This is an example. Ini adalah contoh.\\nv. He exemplified the rule. Dia memberi contoh aturan itu.",
                    "rootAffix": "Analisis akar kata/afiks, misalnya ex- (keluar) + ample (ambil) → diambil sebagai contoh → contoh. Frasa, tidak ada akar/afiks"
                  }
                ]

                Persyaratan:
                1. Harus mengembalikan array JSON lengkap, tanpa teks penjelasan tambahan
                2. Kolom tidak boleh kosong, jika memang tidak ada informasi, tulis \"N/A\"
                3. Transkripsi fonetik menggunakan IPA
                4. Contoh kalimat harus natural dan praktis, setiap jenis kata harus memiliki satu contoh kalimat, format \"tanda jenis kata. Kalimat bahasa Inggris Terjemahan Bahasa Indonesia\", dipisahkan dengan baris baru \\n
                5. Analisis akar kata/afiks harus akurat, mencakup pemisahan etimologi dan evolusi makna
                6. Untuk frasa (misalnya set up, look forward to), partSpeechMean ditandai sebagai \"frasa\" bukan jenis kata tunggal, usPhonetic/ukPhonetic tulis transkripsi fonetik frasa lengkap
                """;
        String wordParseUserPrefix = "Harap lengkapi informasi untuk kata/frasa berikut:\n";
        return new PromptTemplate("Bahasa Indonesia", globalConstraint, readingAssistantSystem,
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix);
    }

    // ==================== Tiếng Việt ====================

    private static PromptTemplate buildViTemplate() {
        String globalConstraint = "[Quy Tắc Toàn Cầu Quan Trọng] Luôn trả lời người dùng bằng Tiếng Việt. Dù người dùng nhập bằng tiếng Anh hay bất kỳ ngôn ngữ nào, tất cả giải thích, bản dịch câu ví dụ và phân tích phải bằng Tiếng Việt. Trừ văn bản gốc tiếng Anh và phiên âm.";
        String readingAssistantSystem = "Bạn là trợ lý đọc tiếng Anh chuyên nghiệp. Người dùng đang đọc sách gốc tiếng Anh. Vui lòng trả lời câu hỏi của người dùng bằng Tiếng Việt.";
        String readingAssistantContext = "Ngữ cảnh hiện tại người dùng đã chọn là: \"%s\"";
        String readingAssistantUserPrefix = "Vui lòng trả lời câu hỏi của người dùng sau bằng Tiếng Việt:\n%s";
        String wordParseSystem = globalConstraint + "\n" + """
                Bạn là trợ lý từ điển tiếng Anh chuyên nghiệp. Người dùng sẽ cung cấp danh sách từ/cụm từ tiếng Anh (mảng JSON). Vui lòng bổ sung thông tin cho mỗi mục và trả về định dạng mảng JSON nghiêm ngặt.

                Định dạng trả về:
                [
                  {
                    "wordProto": "từ gốc",
                    "usPhonetic": "phiên âm tiếng Anh Mỹ, ví dụ /ˈeksəmpəl/",
                    "ukPhonetic": "phiên âm tiếng Anh Anh, ví dụ /ɪɡˈzɑːmpəl/",
                    "partSpeechMean": "từ loại + nghĩa tiếng Việt, ví dụ n. ví dụ; v. minh họa",
                    "sentenceExample": "Cung cấp một câu ví dụ cho mỗi từ loại, định dạng: nhãn từ loại. Câu tiếng Anh Bản dịch tiếng Việt. Nếu có nhiều từ loại, phân cách bằng dòng mới \\n, ví dụ n. This is an example. Đây là một ví dụ.\\nv. He exemplified the rule. Ông ấy đã minh họa quy tắc.",
                    "rootAffix": "Phân tích gốc từ/áp đuôi, ví dụ ex- (ra ngoài) + ample (lấy) → lấy ra làm mẫu → ví dụ. Cụm từ, không có gốc/áp đuôi"
                  }
                ]

                Yêu cầu:
                1. Phải trả về mảng JSON đầy đủ, không có văn bản giải thích thêm
                2. Các trường không được để trống; nếu thực sự không có thông tin, hãy ghi \"N/A\"
                3. Phiên âm sử dụng IPA
                4. Câu ví dụ phải tự nhiên và thực tế; mỗi từ loại phải có một câu ví dụ, định dạng \"nhãn từ loại. Câu tiếng Anh Bản dịch tiếng Việt\", phân cách bằng dòng mới \\n
                5. Phân tích gốc từ/áp đuôi phải chính xác, bao gồm sự phân tích từ nguyên và sự tiến hóa nghĩa
                6. Đối với cụm từ (ví dụ set up, look forward to), partSpeechMean đánh dấu là \"cụm từ\" thay vì một từ loại đơn lẻ, usPhonetic/ukPhonetic ghi phiên âm đầy đủ của cụm từ
                """;
        String wordParseUserPrefix = "Vui lòng bổ sung thông tin cho các từ/cụm từ sau:\n";
        return new PromptTemplate("Tiếng Việt", globalConstraint, readingAssistantSystem,
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix);
    }

    // ==================== ภาษาไทย ====================

    private static PromptTemplate buildThTemplate() {
        String globalConstraint = "[กฎระดับโลกที่สำคัญ] ตอบผู้ใช้เสมอด้วยภาษาไทย ไม่ว่าผู้ใช้จะพิมพ์เป็นภาษาอังกฤษหรือภาษาใดก็ตาม คำอธิบาย การแปลประโยคตัวอย่าง และการวิเคราะห์ทั้งหมดต้องเป็นภาษาไทย ยกเว้นข้อความต้นฉบับภาษาอังกฤษและสัญลักษณ์เสียง";
        String readingAssistantSystem = "คุณคือผู้ช่วยการอ่านภาษาอังกฤษมืออาชีพ ผู้ใช้กำลังอ่านหนังสือต้นฉบับภาษาอังกฤษ กรุณาตอบคำถามของผู้ใช้เป็นภาษาไทย";
        String readingAssistantContext = "บริบทที่ผู้ใช้เลือกในปัจจุบันคือ: \"%s\"";
        String readingAssistantUserPrefix = "กรุณาตอบคำถามของผู้ใช้ต่อไปนี้เป็นภาษาไทย:\n%s";
        String wordParseSystem = globalConstraint + "\n" + """
                คุณคือผู้ช่วยพจนานุกรมภาษาอังกฤษมืออาชีพ ผู้ใช้จะให้รายการคำ/วลีภาษาอังกฤษ (อาร์เรย์ JSON) กรุณาเติมข้อมูลให้ครบถ้วนสำหรับแต่ละรายการ และส่งคืนในรูปแบบอาร์เรย์ JSON ที่เข้มงวด

                รูปแบบการส่งคืน:
                [
                  {
                    "wordProto": "คำต้นฉบับ",
                    "usPhonetic": "สัญลักษณ์เสียงอเมริกัน เช่น /ˈeksəmpəl/",
                    "ukPhonetic": "สัญลักษณ์เสียงบริติช เช่น /ɪɡˈzɑːmpəl/",
                    "partSpeechMean": "ชนิดของคำ + ความหมายภาษาไทย เช่น n. ตัวอย่าง; v. เป็นตัวอย่าง",
                    "sentenceExample": "ให้ตัวอย่างประโยคสำหรับแต่ละชนิดคำ รูปแบบ: ป้ายชนิดคำ. ประโยคภาษาอังกฤษ การแปลภาษาไทย หากมีหลายชนิดคำ ให้คั่นด้วยบรรทัดใหม่ \\n เช่น n. This is an example. นี่คือตัวอย่าง\\nv. He exemplified the rule. เขาได้เป็นตัวอย่างของกฎ",
                    "rootAffix": "การวิเคราะห์รากคำ/อุปสรรค เช่น ex- (ออก) + ample (เอา) → เอาออกมาเป็นตัวอย่าง → ตัวอย่าง วลี ไม่มีราก/อุปสรรค"
                  }
                ]

                ข้อกำหนด:
                1. ต้องส่งคืนอาร์เรย์ JSON ที่สมบูรณ์ โดยไม่มีข้อความอธิบายเพิ่มเติม
                2. ฟิลด์ต้องไม่ว่างเปล่า หากไม่มีข้อมูลจริง ๆ ให้เขียน \"N/A\"
                3. ใช้ IPA สำหรับสัญลักษณ์เสียง
                4. ตัวอย่างประโยคต้องเป็นธรรมชาติและใช้งานได้จริง แต่ละชนิดคำต้องมีตัวอย่างประโยคหนึ่งประโยค ในรูปแบบ \"ป้ายชนิดคำ. ประโยคภาษาอังกฤษ การแปลภาษาไทย\" คั่นด้วยบรรทัดใหม่ \\n
                5. การวิเคราะห์รากคำ/อุปสรรคต้องถูกต้อง รวมถึงการแยกส่วนกำเนิดและวิวัฒนาการความหมาย
                6. สำหรับวลี (เช่น set up, look forward to) ให้ partSpeechMean ระบุเป็น \"วลี\" แทนที่จะเป็นชนิดคำเดียว usPhonetic/ukPhonetic เขียนสัญลักษณ์เสียงวลีเต็ม
                """;
        String wordParseUserPrefix = "กรุณาเติมข้อมูลให้ครบถ้วนสำหรับคำ/วลีต่อไปนี้:\n";
        return new PromptTemplate("ภาษาไทย", globalConstraint, readingAssistantSystem,
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix);
    }

    // ==================== 公共方法 ====================

    /**
     * 获取全局语言约束前缀（使用用户目标语言）。
     */
    public String buildGlobalLocaleConstraint(Long userId) {
        return getTemplate(userId).globalConstraint();
    }

    /**
     * 单词解析 system prompt（使用用户目标语言）。
     */
    public String buildWordParsePrompt(Long userId) {
        return getTemplate(userId).wordParseSystem();
    }

    /**
     * 单词解析 user prompt 前缀（使用用户目标语言）。
     */
    public String buildWordParseUserPrompt(Long userId, String wordsList) {
        return getTemplate(userId).wordParseUserPrefix() + wordsList;
    }

    /**
     * 阅读 AI 助手 system prompt（基于 locale 字符串，不依赖用户表查询）。
     */
    public String buildReadingAssistantPrompt(String locale, String context) {
        PromptTemplate t = getTemplateByLocale(locale);
        String base = t.globalConstraint() + "\n" + t.readingAssistantSystem();
        if (context != null && !context.isEmpty()) {
            base += "\n" + String.format(t.readingAssistantContext(), context);
        }
        return base;
    }

    /**
     * 阅读 AI 助手 system prompt（基于 userId，兼容旧调用）。
     */
    public String buildReadingAssistantPrompt(Long userId, String context) {
        return buildReadingAssistantPrompt(getUserLocale(userId), context);
    }

    /**
     * 阅读 AI 助手 user prompt（基于 locale 字符串包装）。
     */
    public String buildReadingAssistantUserPrompt(String locale, String question) {
        PromptTemplate t = getTemplateByLocale(locale);
        return String.format(t.readingAssistantUserPrefix(), question);
    }

    /**
     * 阅读 AI 助手 user prompt（基于 userId，兼容旧调用）。
     */
    public String buildReadingAssistantUserPrompt(Long userId, String question) {
        return buildReadingAssistantUserPrompt(getUserLocale(userId), question);
    }

    /**
     * 功能预设 system prompt（基于 locale 和 featureKey）。
     * 从数据库读取功能描述作为提示词。
     */
    public String buildFeaturePrompt(String locale, String featureKey, String context) {
        PromptTemplate t = getTemplateByLocale(locale);
        SysAiFeature feature = featureMapper.selectByFeatureKey(featureKey);
        String featurePrompt = feature != null && feature.getPrompt() != null ? feature.getPrompt() : "";

        StringBuilder prompt = new StringBuilder();
        prompt.append(t.globalConstraint()).append("\n");
        prompt.append("你是一个专业的英语阅读助手。").append(featurePrompt).append("\n");
        if (context != null && !context.isEmpty()) {
            prompt.append(String.format(t.readingAssistantContext(), context)).append("\n");
        }
        prompt.append("请直接给出分析结果，不要添加开场白、解释说明或总结。保持简洁。");

        return prompt.toString();
    }

    /**
     * 功能预设 user prompt（基于 locale 和 featureKey）。
     */
    public String buildFeatureUserPrompt(String locale, String featureKey, String context) {
        PromptTemplate t = getTemplateByLocale(locale);
        SysAiFeature feature = featureMapper.selectByFeatureKey(featureKey);
        String featureName = feature != null ? feature.getFeatureName() : featureKey;

        return String.format("请对以下选中的文本进行「%s」分析：\n%s", featureName, context);
    }

    /**
     * 多选功能 system prompt（基于 locale 和 featureKeys 列表）。
     * 从数据库读取多个功能描述，合并为综合提示词。
     */
    public String buildMultiFeaturePrompt(String locale, List<String> featureKeys, String context) {
        PromptTemplate t = getTemplateByLocale(locale);
        StringBuilder featureDescs = new StringBuilder();
        for (String key : featureKeys) {
            SysAiFeature feature = featureMapper.selectByFeatureKey(key);
            if (feature != null && feature.getPrompt() != null) {
                featureDescs.append(feature.getPrompt()).append("\n");
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(t.globalConstraint()).append("\n");
        prompt.append("你是一个专业的英语阅读助手。用户选择了以下多个分析功能，请综合给出分析结果：\n");
        prompt.append(featureDescs);
        if (context != null && !context.isEmpty()) {
            prompt.append(String.format(t.readingAssistantContext(), context)).append("\n");
        }
        prompt.append("请直接给出分析结果，不要添加开场白、解释说明或总结。保持简洁。");

        return prompt.toString();
    }

    /**
     * 多选功能 user prompt（基于 locale 和 featureKeys 列表）。
     */
    public String buildMultiFeatureUserPrompt(String locale, List<String> featureKeys, String context) {
        PromptTemplate t = getTemplateByLocale(locale);
        StringBuilder featureNames = new StringBuilder();
        for (String key : featureKeys) {
            SysAiFeature feature = featureMapper.selectByFeatureKey(key);
            if (feature != null && feature.getFeatureName() != null) {
                if (featureNames.length() > 0) featureNames.append(" + ");
                featureNames.append(feature.getFeatureName());
            }
        }

        return String.format("请对以下选中的文本进行「%s」分析：\n%s", featureNames, context);
    }

    private PromptTemplate getTemplate(Long userId) {
        return getTemplateByLocale(getUserLocale(userId));
    }

    private String getUserLocale(Long userId) {
        if (userId == null) return "zh-CN";
        try {
            WdUser user = userMapper.selectById(userId);
            if (user != null && user.getPreferredLocale() != null) {
                return user.getPreferredLocale();
            }
        } catch (Exception ignored) {}
        return "zh-CN";
    }

    private PromptTemplate getTemplateByLocale(String locale) {
        if (locale == null || locale.isEmpty()) return TEMPLATES.get("zh-CN");
        return TEMPLATES.getOrDefault(locale, TEMPLATES.get("zh-CN"));
    }
}
