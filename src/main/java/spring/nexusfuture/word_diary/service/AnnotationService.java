package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdAnnotation;

import java.util.List;

/** 阅读划线与文字笔记 CRUD。 */
public interface AnnotationService {

    /**
     * 新增划线笔记。
     *
     * @param userId 当前用户 ID
     * @param annotation 笔记内容（书籍、章节、选区、正文等）
     * @return 持久化后的笔记实体
     */
    WdAnnotation addAnnotation(Long userId, WdAnnotation annotation);

    /**
     * 更新笔记内容（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param annotation 含 ID 的笔记实体
     * @return 更新后的笔记实体
     */
    WdAnnotation updateAnnotation(Long userId, WdAnnotation annotation);

    /**
     * 删除笔记（校验归属）。
     *
     * @param id 笔记 ID
     * @param userId 当前用户 ID
     */
    void deleteAnnotation(Long id, Long userId);

    /**
     * 获取某本书的全部笔记。
     *
     * @param bookId 书籍 ID
     * @param userId 当前用户 ID
     * @return 笔记列表
     */
    List<WdAnnotation> listByBook(Long bookId, Long userId);

    /**
     * 获取某章节的笔记。
     *
     * @param bookId 书籍 ID
     * @param userId 当前用户 ID
     * @param chapterIndex 章节索引
     * @return 该章节下的笔记列表
     */
    List<WdAnnotation> listByChapter(Long bookId, Long userId, Integer chapterIndex);
}
