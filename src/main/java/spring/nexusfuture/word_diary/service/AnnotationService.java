package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdAnnotation;

import java.util.List;

public interface AnnotationService {

    /** 新增划线笔记 */
    WdAnnotation addAnnotation(Long userId, WdAnnotation annotation);

    /** 更新笔记内容 */
    WdAnnotation updateAnnotation(Long userId, WdAnnotation annotation);

    /** 删除笔记 */
    void deleteAnnotation(Long id, Long userId);

    /** 获取某本书的全部笔记 */
    List<WdAnnotation> listByBook(Long bookId, Long userId);

    /** 获取某章节的笔记 */
    List<WdAnnotation> listByChapter(Long bookId, Long userId, Integer chapterIndex);
}
