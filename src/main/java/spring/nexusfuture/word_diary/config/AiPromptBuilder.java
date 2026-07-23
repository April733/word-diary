package spring.nexusfuture.word_diary.config;

import org.springframework.stereotype.Component;
import spring.nexusfuture.word_diary.common.AiFeatureI18nSupport;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.entity.SysAiFeature;
import spring.nexusfuture.word_diary.entity.SysAiScenePrompt;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.SysAiFeatureMapper;
import spring.nexusfuture.word_diary.mapper.SysAiScenePromptMapper;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;

import java.util.List;
import java.util.Map;

/**
 * AI 提示词构建器。
 * 阅读解析：场景壳来自 sys_ai_scene_prompt(feature)，功能细节来自 sys_ai_feature.prompt（均后台可配）。
 */
@Component
public class AiPromptBuilder {

    private final WdUserMapper userMapper;
    private final SysAiFeatureMapper featureMapper;
    private final SysAiScenePromptMapper scenePromptMapper;

    public AiPromptBuilder(WdUserMapper userMapper,
                           SysAiFeatureMapper featureMapper,
                           SysAiScenePromptMapper scenePromptMapper) {
        this.userMapper = userMapper;
        this.featureMapper = featureMapper;
        this.scenePromptMapper = scenePromptMapper;
    }

    private record PromptTemplate(
            String localeName,
            String globalConstraint,
            String readingAssistantSystem,
            String readingAssistantContext,
            String readingAssistantUserPrefix,
            String wordParseSystem,
            String wordParseUserPrefix,
            String featureAssistantIntro,
            String multiFeatureIntro,
            String featureDirectInstruction,
            String featureUserFormat,
            String multiFeatureUserFormat
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
        String globalConstraint = "【语言】中文（原文与音标除外）。";
        String readingAssistantSystem = "你是一个专业的英语阅读与语言学习助手。用户正在阅读英文材料，请用中文回答与语言学习相关的问题。";
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
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix,
                "你是英语阅读助手。",
                "你是英语阅读助手。仅分析下列功能：\n",
                "直接输出结果，勿开场/总结。",
                "请对以下选中的文本进行「%s」分析：\n%s",
                "请对以下选中的文本进行「%s」分析：\n%s");
    }

    // ==================== English ====================

    private static PromptTemplate buildEnTemplate() {
        String globalConstraint = "[Important Global Rule] Always respond to the user in English. Regardless of what language the user inputs, all explanations, translations, and analyses must be in English. Original English text and phonetic transcriptions are exceptions.";
        String readingAssistantSystem = "You are a professional English reading and language-learning assistant. The user is reading English material. Answer language-learning questions in English.";
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
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix,
                "You are a professional English reading assistant. ",
                "You are a professional English reading assistant. The user selected these analysis features; cover **only** them and do not add unselected ones:\n",
                "Give the analysis directly, without openings, explanations, or summaries. Keep it concise. Only cover the selected features. Use Markdown bold for section titles (e.g. **Collocations**) and for lemma terms in lists (e.g. - **proposed**: …).",
                "Please analyze the selected text with 「%s」:\n%s",
                "Please analyze the selected text with 「%s」:\n%s");
    }

    // ==================== Bahasa Indonesia ====================

    private static PromptTemplate buildIdTemplate() {
        String globalConstraint = "[Aturan Global Penting] Selalu balas pengguna dalam Bahasa Indonesia. Meskipun pengguna menulis dalam bahasa Inggris atau bahasa lain, semua penjelasan, terjemahan contoh kalimat, dan analisis harus dalam Bahasa Indonesia. Kecuali teks asli bahasa Inggris dan transkripsi fonetik.";
        String readingAssistantSystem = "Anda adalah asisten belajar bahasa & membaca bahasa Inggris. Jawab hanya pertanyaan terkait pembelajaran bahasa dalam Bahasa Indonesia.";
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
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix,
                "Anda adalah asisten membaca bahasa Inggris profesional. ",
                "Anda adalah asisten membaca bahasa Inggris profesional. Pengguna memilih beberapa fitur analisis berikut; berikan analisis gabungan:\n",
                "Berikan hasil analisis secara langsung, tanpa pembuka, penjelasan, atau ringkasan. Tetap ringkas.",
                "Harap analisis teks terpilih dengan 「%s」:\n%s",
                "Harap analisis teks terpilih dengan 「%s」:\n%s");
    }

    // ==================== Tiếng Việt ====================

    private static PromptTemplate buildViTemplate() {
        String globalConstraint = "[Quy Tắc Toàn Cầu Quan Trọng] Luôn trả lời người dùng bằng Tiếng Việt. Dù người dùng nhập bằng tiếng Anh hay bất kỳ ngôn ngữ nào, tất cả giải thích, bản dịch câu ví dụ và phân tích phải bằng Tiếng Việt. Trừ văn bản gốc tiếng Anh và phiên âm.";
        String readingAssistantSystem = "Bạn là trợ lý học ngôn ngữ & đọc tiếng Anh. Chỉ trả lời câu hỏi liên quan đến học ngôn ngữ bằng Tiếng Việt.";
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
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix,
                "Bạn là trợ lý đọc tiếng Anh chuyên nghiệp. ",
                "Bạn là trợ lý đọc tiếng Anh chuyên nghiệp. Người dùng đã chọn nhiều chức năng phân tích sau; hãy đưa ra phân tích tổng hợp:\n",
                "Đưa ra kết quả phân tích trực tiếp, không thêm lời mở đầu, giải thích hoặc tóm tắt. Giữ ngắn gọn.",
                "Vui lòng phân tích đoạn văn đã chọn với 「%s」:\n%s",
                "Vui lòng phân tích đoạn văn đã chọn với 「%s」:\n%s");
    }

    // ==================== ภาษาไทย ====================

    private static PromptTemplate buildThTemplate() {
        String globalConstraint = "[กฎระดับโลกที่สำคัญ] ตอบผู้ใช้เสมอด้วยภาษาไทย ไม่ว่าผู้ใช้จะพิมพ์เป็นภาษาอังกฤษหรือภาษาใดก็ตาม คำอธิบาย การแปลประโยคตัวอย่าง และการวิเคราะห์ทั้งหมดต้องเป็นภาษาไทย ยกเว้นข้อความต้นฉบับภาษาอังกฤษและสัญลักษณ์เสียง";
        String readingAssistantSystem = "คุณคือผู้ช่วยเรียนภาษาและการอ่านภาษาอังกฤษ ตอบเฉพาะคำถามที่เกี่ยวกับการเรียนภาษาเป็นภาษาไทย";
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
                readingAssistantContext, readingAssistantUserPrefix, wordParseSystem, wordParseUserPrefix,
                "คุณคือผู้ช่วยการอ่านภาษาอังกฤษมืออาชีพ ",
                "คุณคือผู้ช่วยการอ่านภาษาอังกฤษมืออาชีพ ผู้ใช้เลือกฟีเจอร์วิเคราะห์หลายรายการดังนี้ กรุณาวิเคราะห์แบบรวม:\n",
                "ให้ผลการวิเคราะห์โดยตรง โดยไม่ต้องมีคำเปิด การอธิบาย หรือสรุป ให้สั้นกระชับ",
                "กรุณาวิเคราะห์ข้อความที่เลือกด้วย 「%s」:\n%s",
                "กรุณาวิเคราะห์ข้อความที่เลือกด้วย 「%s」:\n%s");
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
        String locale = getUserLocale(userId);
        String fromDb = loadScenePrompt(SysConfigKeys.AI_SCENE_WORD_PARSE, locale);
        if (fromDb != null) {
            return applyPlaceholders(fromDb, "", "", locale);
        }
        return getTemplateByLocale(locale).wordParseSystem();
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
        String fromDb = loadScenePrompt(SysConfigKeys.AI_SCENE_READING_CHAT, locale);
        if (fromDb != null) {
            return applyPlaceholders(fromDb, context, "", locale) + replyLanguageReinforcement(locale);
        }
        PromptTemplate t = getTemplateByLocale(locale);
        String base = t.globalConstraint() + "\n" + t.readingAssistantSystem();
        if (context != null && !context.isEmpty()) {
            base += "\n" + String.format(t.readingAssistantContext(), context);
        }
        return base + replyLanguageReinforcement(locale);
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
     * 雅思写作批改 system prompt。优先 sys_ai_scene_prompt(writing_grade)；占位符 {task_type}。
     */
    public String buildWritingGradeSystemPrompt(Long userId, String taskType) {
        String locale = getUserLocale(userId);
        String task = taskType == null ? "task2" : taskType;
        String fromDb = loadScenePrompt(SysConfigKeys.AI_SCENE_WRITING_GRADE, locale);
        if (fromDb != null) {
            return applyPlaceholders(fromDb, "", "", locale)
                    .replace("{task_type}", task)
                    + replyLanguageReinforcement(locale);
        }
        return defaultWritingGradeSystem(locale, task);
    }

    public String buildWritingGradeUserPrompt(Long userId, String taskType, String promptText,
                                              String bodyText, int wordCount) {
        String locale = getUserLocale(userId);
        PromptTemplate t = getTemplateByLocale(locale);
        String taskLabel = "task1".equalsIgnoreCase(taskType) ? "Task 1" : "Task 2";
        return """
                %sWriting type: %s
                Word count (approx): %d
                Question:
                ---
                %s
                ---
                Student essay:
                ---
                %s
                ---
                Return ONE JSON object only.
                """.formatted(
                AiFeatureI18nSupport.isChinese(locale) ? "请用" + t.localeName() + "写评语与批注。\n" : "",
                taskLabel,
                wordCount,
                promptText == null ? "" : promptText,
                bodyText == null ? "" : bodyText
        );
    }

    private String defaultWritingGradeSystem(String locale, String taskType) {
        PromptTemplate t = getTemplateByLocale(locale);
        String taskCriterion = "task1".equalsIgnoreCase(taskType)
                ? "taskAchievement (Task Achievement)"
                : "taskResponse (Task Response)";
        return t.globalConstraint() + "\n" + """
                You are an IELTS Academic Writing examiner and writing coach for {task_type}.
                Grade the essay and produce a polished rewrite that keeps the student's ideas.
                Also provide a high-band model essay for the same question (mark it as a model, not a rewrite).

                Return ONLY one JSON object (no Markdown fences) with keys:
                - overallBand (number, 0-9, 0.5 steps)
                - %s (number)
                - coherence (number)
                - lexical (number)
                - grammar (number)
                - summary (short string)
                - priorities (array of 3-5 actionable improvement strings, highest impact first)
                - annotations (array of {excerpt, issue, fix})
                - polishedEssay (string: improved version of the student's essay)
                - modelEssay (string: high-band sample for the same question)
                Do not invent scores without reading the essay. Be specific and actionable.
                """.formatted(taskCriterion).replace("{task_type}", taskType);
    }

    /**
     * 功能预设 system prompt。
     * 场景壳 = sys_ai_scene_prompt(feature)；功能细节 = sys_ai_feature.prompt。不追加任何代码内文案。
     */
    public String buildFeaturePrompt(String locale, String featureKey, String context) {
        return buildMultiFeaturePrompt(locale,
                featureKey == null ? List.of() : List.of(featureKey), context, null);
    }

    /**
     * 多选功能 system prompt。
     * 严格按 featureKeys 顺序读取库表 prompt，填入场景壳 {feature_prompts}。
     */
    public String buildMultiFeaturePrompt(String locale, List<String> featureKeys, String context) {
        return buildMultiFeaturePrompt(locale, featureKeys, context, null);
    }

    public String buildMultiFeaturePrompt(String locale, List<String> featureKeys, String context,
                                          List<String> targetWords) {
        String featureBlock = assembleFeaturePromptsFromDb(locale, featureKeys);
        String fromDb = loadScenePrompt(SysConfigKeys.AI_SCENE_FEATURE, locale);
        if (fromDb != null) {
            return applyPlaceholders(fromDb, context, featureBlock, locale, targetWords);
        }
        // 未配置场景壳时：只下发后台功能提示词，不再拼代码里的中文壳
        return featureBlock;
    }

    /**
     * 功能 / 多选 user prompt：仅为用户选中原文。
     * 无目标词时 word/root_affix 不会进入 featureKeys（由接口层剥离），此处不再解释「空」。
     */
    public String buildMultiFeatureUserPrompt(String locale, List<String> featureKeys, String context) {
        return context == null ? "" : context.trim();
    }

    /**
     * 功能预设 user prompt：仅为选中原文。
     */
    public String buildFeatureUserPrompt(String locale, String featureKey, String context) {
        return buildMultiFeatureUserPrompt(locale,
                featureKey == null ? List.of() : List.of(featureKey), context);
    }

    /** 按勾选顺序拼接 sys_ai_feature.prompt（后台「AI 功能」里配置的原文）。 */
    private String assembleFeaturePromptsFromDb(String locale, List<String> featureKeys) {
        if (featureKeys == null || featureKeys.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String key : featureKeys) {
            if (key == null || key.isBlank()) continue;
            String k = key.trim();
            SysAiFeature feature = loadFeatureForLocale(k, locale);
            if (feature == null) continue; // 停用或不存在 → 不进提示词
            String name = feature.getFeatureName() != null ? feature.getFeatureName() : k;
            String prompt = feature.getPrompt() != null ? feature.getPrompt().trim() : "";
            sb.append(i++).append('.').append(name).append('(').append(k).append("):\n");
            if (!prompt.isEmpty()) {
                sb.append(prompt).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 语言学习范围约束：拒答与阅读/词汇/语法等无关的问题，并给出友好提示文案。
     */
    public String languageLearningScopeInstruction(String locale) {
        String loc = AiFeatureI18nSupport.normalizeLocale(locale);
        if (AiFeatureI18nSupport.isChinese(loc)) {
            return "\n【范围】仅回答语言学习相关问题；无关则只回复：抱歉，我是 Aiko 语言学习助手，请换个英语学习相关问题～\n";
        }
        return "\n[Scope] Language-learning only. If off-topic, reply briefly that Aiko only helps with language learning.\n";
    }

    /** 按 locale 取功能行并解析展示名；不修改 Mapper 缓存对象。 */
    private SysAiFeature loadFeatureForLocale(String featureKey, String locale) {
        SysAiFeature feature = featureMapper.selectByFeatureKey(featureKey);
        if (feature == null) return null;
        SysAiFeature copy = copyFeature(feature);
        AiFeatureI18nSupport.resolveInPlace(copy, locale);
        return copy;
    }

    private static SysAiFeature copyFeature(SysAiFeature src) {
        SysAiFeature f = new SysAiFeature();
        f.setId(src.getId());
        f.setFeatureKey(src.getFeatureKey());
        f.setFeatureName(src.getFeatureName());
        f.setDescription(src.getDescription());
        f.setNameI18n(src.getNameI18n());
        f.setDescriptionI18n(src.getDescriptionI18n());
        f.setPrompt(src.getPrompt());
        f.setSortNum(src.getSortNum());
        f.setStatus(src.getStatus());
        return f;
    }

    /** 放在 prompt 末尾，优先于功能细节里残留的语言要求。 */
    private String replyLanguageReinforcement(String locale) {
        String loc = AiFeatureI18nSupport.normalizeLocale(locale);
        PromptTemplate t = getTemplateByLocale(loc);
        if (AiFeatureI18nSupport.isChinese(loc)) {
            return "\n【回复语言】所有释义、翻译、讲解必须使用" + t.localeName() + "（英文原文与音标除外）。\n";
        }
        return "\n[Reply language] All glosses, translations, and explanations MUST be in "
                + t.localeName()
                + ". Do NOT use Chinese unless the user language is Chinese. English source text and IPA are exceptions.\n";
    }

    /**
     * 仅按精确 locale 加载库配置；未配置时返回 null，由调用方回退。
     */
    private String loadScenePrompt(String sceneKey, String locale) {
        try {
            String loc = AiFeatureI18nSupport.normalizeLocale(locale);
            SysAiScenePrompt row = scenePromptMapper.selectEnabled(sceneKey, loc);
            if (row != null && row.getSystemPrompt() != null && !row.getSystemPrompt().isBlank()) {
                return row.getSystemPrompt();
            }
        } catch (Exception ignored) {
            // 表未建或查询失败时回退
        }
        return null;
    }

    /**
     * 替换场景提示词占位符。
     * 支持：{context}、{feature_prompts}、{target_words}、{language}、{locale}、{global_constraint}。
     */
    private String applyPlaceholders(String template, String context, String featurePrompts, String locale) {
        return applyPlaceholders(template, context, featurePrompts, locale, null);
    }

    private String applyPlaceholders(String template, String context, String featurePrompts, String locale,
                                     List<String> targetWords) {
        String loc = AiFeatureI18nSupport.normalizeLocale(locale);
        PromptTemplate t = getTemplateByLocale(loc);
        String ctx = context == null ? "" : context;
        String feats = featurePrompts == null ? "" : featurePrompts;
        // 有目标词才填入；没有则留空。无目标词时 word 功能不会下发给模型，不必在此解释。
        String targets = (targetWords != null && !targetWords.isEmpty())
                ? String.join(", ", targetWords)
                : "";
        return template
                .replace("{context}", ctx)
                .replace("{feature_prompts}", feats)
                .replace("{target_words}", targets)
                .replace("{language}", t.localeName())
                .replace("{locale}", loc)
                .replace("{global_constraint}", t.globalConstraint());
    }

    private PromptTemplate getTemplate(Long userId) {
        return getTemplateByLocale(getUserLocale(userId));
    }

    private String getUserLocale(Long userId) {
        if (userId == null) return "zh-CN";
        try {
            WdUser user = userMapper.selectById(userId);
            if (user != null && user.getPreferredLocale() != null) {
                return AiFeatureI18nSupport.normalizeLocale(user.getPreferredLocale());
            }
        } catch (Exception ignored) {}
        return "zh-CN";
    }

    private PromptTemplate getTemplateByLocale(String locale) {
        String loc = AiFeatureI18nSupport.normalizeLocale(locale);
        return TEMPLATES.getOrDefault(loc, TEMPLATES.get("zh-CN"));
    }
}
