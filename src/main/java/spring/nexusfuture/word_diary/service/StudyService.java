package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.StatsVO;
import spring.nexusfuture.word_diary.dto.response.StudyTaskVO;
import java.util.List;
import java.util.Map;

/** 学习计划编排：今日进度、统计与计划词表。 */
public interface StudyService {

    /**
     * 获取今日已完成学习的词列表（复习+新词，满两遍「认识」）。
     *
     * @param userId 当前用户 ID
     * @return 已完成任务 VO 列表
     */
    List<StudyTaskVO> getTodayCompleted(Long userId);

    /**
     * 获取用户学习统计数据（总量、连续天数、今日进度等）。
     *
     * @param userId 当前用户 ID
     * @return 统计 VO
     */
    StatsVO getStats(Long userId);

    /**
     * 按计划状态筛选词表。
     * status: pending(=0) | learning(NOT IN 0,6) | mastered(=6) | all（默认 all）
     *
     * @param userId 当前用户 ID
     * @param status 筛选状态，可为空表示 all
     * @return 匹配的学习任务 VO 列表
     */
    List<StudyTaskVO> listPlanWords(Long userId, String status);

    /**
     * 调试：导出用户全部词的 SRS 字段快照。
     *
     * @param userId 当前用户 ID
     * @return 每词一条 Map（含 wordId、status、interval 等键）
     */
    List<Map<String, Object>> debugWordSrs(Long userId);
}
