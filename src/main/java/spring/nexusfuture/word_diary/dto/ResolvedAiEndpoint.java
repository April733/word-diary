package spring.nexusfuture.word_diary.dto;

/** 场景解析出的调用模型（模型名 + 供应商，供应商决定走哪条 base-url） */
public record ResolvedAiEndpoint(String modelKey, String provider) {}
