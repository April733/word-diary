package spring.nexusfuture.word_diary.dto;

/** 场景解析出的调用端点：模型 + 供应商 + 场景调用参数 */
public record ResolvedAiEndpoint(String modelKey, String provider, AiSceneCallParams params) {
    public ResolvedAiEndpoint(String modelKey, String provider) {
        this(modelKey, provider, AiSceneCallParams.empty());
    }

    public AiSceneCallParams paramsOrEmpty() {
        return params != null ? params : AiSceneCallParams.empty();
    }
}
