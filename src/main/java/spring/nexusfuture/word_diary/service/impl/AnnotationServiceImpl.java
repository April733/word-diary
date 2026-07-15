package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.entity.WdAnnotation;
import spring.nexusfuture.word_diary.mapper.WdAnnotationMapper;
import spring.nexusfuture.word_diary.service.AnnotationService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private final WdAnnotationMapper annotationMapper;

    @Override
    @Transactional
    public WdAnnotation addAnnotation(Long userId, WdAnnotation annotation) {
        annotation.setUserId(userId);
        annotation.setCreateBy(userId);
        annotation.setCreateTime(LocalDateTime.now());
        annotation.setUpdateBy(userId);
        annotation.setUpdateTime(LocalDateTime.now());
        annotation.setIsDeleted(0);
        if (annotation.getColor() == null || annotation.getColor().isEmpty()) {
            annotation.setColor("#FFD700");
        }
        annotationMapper.insert(annotation);
        return annotation;
    }

    @Override
    @Transactional
    public WdAnnotation updateAnnotation(Long userId, WdAnnotation annotation) {
        WdAnnotation existing = annotationMapper.selectById(annotation.getId());
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException("无权操作");
        }
        annotation.setUserId(userId);
        annotation.setUpdateBy(userId);
        annotationMapper.updateById(annotation);
        return annotationMapper.selectById(annotation.getId());
    }

    @Override
    @Transactional
    public void deleteAnnotation(Long id, Long userId) {
        WdAnnotation existing = annotationMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException("无权操作");
        }
        annotationMapper.deleteById(id, userId);
    }

    @Override
    public List<WdAnnotation> listByBook(Long bookId, Long userId) {
        return annotationMapper.selectByBookId(bookId, userId);
    }

    @Override
    public List<WdAnnotation> listByChapter(Long bookId, Long userId, Integer chapterIndex) {
        return annotationMapper.selectByChapter(bookId, userId, chapterIndex);
    }
}
