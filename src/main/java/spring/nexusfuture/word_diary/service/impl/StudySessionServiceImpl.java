package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.common.StudyAlgorithm;
import spring.nexusfuture.word_diary.common.StudyDay;
import spring.nexusfuture.word_diary.common.StudyFeedback;
import spring.nexusfuture.word_diary.dto.*;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.StudySessionService;
import spring.nexusfuture.word_diary.service.SysBaseConfigService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudySessionServiceImpl implements StudySessionService {

    private static final String PHASE_REVIEW = "review";
    private static final String PHASE_NEW = "new";
    /** 一天内每个词至少背完这么多遍才算「已完成」 */
    private static final int REQUIRED_DAILY_PASSES = 2;
    private static final String REASON_CONFIRM_PASS = "confirm_pass";

    private final WdLearnPlanMapper planMapper;
    private final WdLearnRecordMapper recordMapper;
    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final WdStudyDailyMapper dailyMapper;
    private final WdStudyQueueMapper queueMapper;
    private final WdUserStudyPrefMapper prefMapper;
    private final SysBaseConfigService configService;

    public StudySessionServiceImpl(WdLearnPlanMapper planMapper, WdLearnRecordMapper recordMapper,
                                   WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                                   WdStudyDailyMapper dailyMapper, WdStudyQueueMapper queueMapper,
                                   WdUserStudyPrefMapper prefMapper, SysBaseConfigService configService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.dailyMapper = dailyMapper;
        this.queueMapper = queueMapper;
        this.prefMapper = prefMapper;
        this.configService = configService;
    }

    @Override
    public LocalDate currentStudyDate(Long userId) {
        return StudyDay.current(getUnlockHour(userId));
    }

    @Override
    public LocalDateTime currentStudyDayStart(Long userId) {
        return StudyDay.startOf(currentStudyDate(userId), getUnlockHour(userId));
    }

    @Override
    public LocalDateTime currentStudyDayEnd(Long userId) {
        return StudyDay.endOf(currentStudyDate(userId), getUnlockHour(userId));
    }

    private LocalDateTime windowStart(Long userId, LocalDate studyDate) {
        return StudyDay.startOf(studyDate, getUnlockHour(userId));
    }

    private LocalDateTime windowEnd(Long userId, LocalDate studyDate) {
        return StudyDay.endOf(studyDate, getUnlockHour(userId));
    }

    @Override
    @Transactional
    public StudyTodayVO getToday(Long userId) {
        LocalDate today = currentStudyDate(userId);
        planMapper.migrateForgottenToNotLearned(userId);
        pruneStaleQueue(userId, today);
        WdStudyDaily daily = dailyMapper.selectByUserAndDate(userId, today);
        if (daily == null) {
            int reviewCount = dedupByWordId(planMapper.selectReviewTasks(userId, today)).size();
            allocateNewWords(userId, today);
            int newCount = Math.min(getDailyNewLimit(userId),
                    dedupByWordId(planMapper.selectTodayNewWords(userId, today)).size());
            return buildTodayVO(userId, today, reviewCount, 0, newCount, 0, reviewCount == 0);
        }
        // 今日已「完成」后用户又加了新词：补进队列并重新打开
        supplementTodayWork(userId, today, daily);
        daily = dailyMapper.selectByUserAndDate(userId, today);
        refreshDailyProgress(daily, userId, today);
        return toTodayVO(daily, userId, today);
    }

    @Override
    @Transactional
    public StudySessionVO openSession(Long userId) {
        LocalDate today = currentStudyDate(userId);
        planMapper.migrateForgottenToNotLearned(userId);
        pruneStaleQueue(userId, today);

        WdStudyDaily daily = dailyMapper.selectByUserAndDate(userId, today);
        if (daily != null) {
            supplementTodayWork(userId, today, daily);
            daily = dailyMapper.selectByUserAndDate(userId, today);
        }
        int queueCount = queueMapper.countByUserAndDate(userId, today);

        if (daily != null && daily.getStatus() != null && daily.getStatus() == 1
                && queueMapper.countAllPending(userId, today) == 0) {
            refreshDailyProgress(daily, userId, today);
            return buildSessionVO(userId, today, daily);
        }

        if (queueCount == 0) {
            allocateNewWords(userId, today);
            List<Long> completedToday = queueMapper.selectWordIdsWithMinDonePasses(
                    userId, windowStart(userId, today), windowEnd(userId, today), REQUIRED_DAILY_PASSES);
            List<WdLearnPlan> reviewPlans = filterStudiedToday(
                    dedupByWordId(planMapper.selectReviewTasks(userId, today)), completedToday);
            List<WdLearnPlan> newPlans = filterStudiedToday(
                    dedupByWordId(planMapper.selectTodayNewWords(userId, today)), completedToday);
            int newLimit = getDailyNewLimit(userId);
            if (newPlans.size() > newLimit) {
                newPlans = newPlans.subList(0, newLimit);
            }

            if (daily == null) {
                daily = WdStudyDaily.builder()
                        .userId(userId)
                        .studyDate(today)
                        .reviewTotal(reviewPlans.size())
                        .reviewDone(0)
                        .newTotal(newPlans.size())
                        .newDone(0)
                        .reviewPhaseDone(reviewPlans.isEmpty() ? 1 : 0)
                        .status(reviewPlans.isEmpty() && newPlans.isEmpty() ? 1 : 0)
                        .build();
                dailyMapper.insert(daily);
            } else {
                daily.setReviewTotal(reviewPlans.size());
                daily.setReviewDone(0);
                daily.setNewTotal(newPlans.size());
                daily.setNewDone(0);
                daily.setReviewPhaseDone(reviewPlans.isEmpty() ? 1 : 0);
                daily.setStatus(reviewPlans.isEmpty() && newPlans.isEmpty() ? 1 : 0);
                dailyMapper.update(daily);
            }

            buildInitialQueue(userId, today, reviewPlans, newPlans);
        } else if (daily == null) {
            daily = WdStudyDaily.builder()
                    .userId(userId)
                    .studyDate(today)
                    .reviewTotal(queueMapper.countPendingByPhase(userId, today, PHASE_REVIEW))
                    .reviewDone(0)
                    .newTotal(queueMapper.countPendingByPhase(userId, today, PHASE_NEW))
                    .newDone(0)
                    .reviewPhaseDone(0)
                    .status(0)
                    .build();
            dailyMapper.insert(daily);
        } else {
            refreshDailyProgress(daily, userId, today);
        }

        return buildSessionVO(userId, today, daily);
    }

    @Override
    @Transactional
    public StudyAnswerVO submitAnswer(Long userId, StudyAnswerReq req) {
        if (req == null || req.getQueueItemId() == null || req.getFeedback() == null) {
            throw new BizException("参数不完整");
        }
        if (!StudyFeedback.isValid(req.getFeedback())) {
            throw new BizException("无效的反馈类型");
        }

        LocalDate today = currentStudyDate(userId);
        WdStudyQueue queueItem = queueMapper.selectById(req.getQueueItemId());
        if (queueItem == null || !queueItem.getUserId().equals(userId)
                || !queueItem.getStudyDate().equals(today)) {
            throw new BizException("队列项不存在");
        }
        if (queueItem.getState() != WdStudyQueue.STATE_PENDING) {
            throw new BizException("该题已完成");
        }

        WdStudyDaily daily = dailyMapper.selectByUserAndDate(userId, today);
        if (daily == null) {
            throw new BizException("请先开启今日学习会话");
        }

        String phase = queueItem.getPhase();
        if (PHASE_NEW.equals(phase) && (daily.getReviewPhaseDone() == null || daily.getReviewPhaseDone() == 0)) {
            throw new BizException("请先完成今日复习");
        }

        WdLearnPlan plan = planMapper.selectById(queueItem.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException("学习计划不存在");
        }

        boolean isNewPhase = PHASE_NEW.equals(phase);
        int feedback = req.getFeedback();
        boolean confirmPass = WdStudyQueue.TYPE_INTRADAY.equals(queueItem.getQueueType())
                && REASON_CONFIRM_PASS.equals(queueItem.getInsertReason());
        // 第二遍「认识」只巩固，不再改长期排期
        boolean applySchedule = !(confirmPass && feedback == StudyFeedback.KNOW);

        if (applySchedule) {
            StudyAlgorithm.applyFeedback(plan, feedback, today, isNewPhase);
            if (feedback == StudyFeedback.FORGET) {
                rescheduleForgotten(userId, today, plan);
            }
            planMapper.updatePlan(plan);
        }

        saveRecord(userId, plan, isNewPhase, feedback);

        queueMapper.updateState(queueItem.getId(), WdStudyQueue.STATE_DONE);

        int donePasses = queueMapper.countDoneByPlan(
                userId, plan.getId(), windowStart(userId, today), windowEnd(userId, today));
        if (feedback == StudyFeedback.FUZZY || feedback == StudyFeedback.FORGET) {
            insertIntraday(userId, today, plan, phase, feedback, queueItem.getSortOrder());
        } else if (donePasses < REQUIRED_DAILY_PASSES) {
            // 「认识」也要再背一遍才算今日完成
            insertConfirmPass(userId, today, plan, phase, queueItem.getSortOrder());
        }

        boolean wasReviewPhaseDone = daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1;
        refreshDailyProgress(daily, userId, today);
        boolean phaseSwitched = !wasReviewPhaseDone && daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1;

        WdStudyQueue nextItem = pickNextQueueItem(userId, today, daily);
        StudyTaskVO nextTask = nextItem != null ? toTaskVO(planMapper.selectById(nextItem.getPlanId())) : null;

        if (nextItem == null) {
            markDailyCompleteIfNeeded(daily, userId, today);
        }

        String currentPhase = resolveCurrentPhase(daily, nextItem);
        return StudyAnswerVO.builder()
                .next(nextTask)
                .today(toTodayVO(daily, userId, today))
                .phaseSwitched(phaseSwitched)
                .phase(currentPhase)
                .queueItemId(nextItem != null ? nextItem.getId() : null)
                .build();
    }

    @Override
    public StudySettingsVO getSettings(Long userId) {
        WdUserStudyPref pref = ensurePref(userId);
        return StudySettingsVO.builder()
                .dailyNewLimit(pref.getDailyNewLimit())
                .reviewUnlockHour(pref.getReviewUnlockHour())
                .autoSpreadNew(pref.getAutoSpreadNew() != null && pref.getAutoSpreadNew() == 1)
                .build();
    }

    @Override
    @Transactional
    public void updateSettings(Long userId, StudySettingsReq req) {
        WdUserStudyPref pref = ensurePref(userId);
        if (req.getDailyNewLimit() != null) {
            if (req.getDailyNewLimit() < 1 || req.getDailyNewLimit() > 200) {
                throw new BizException("每日新词上限需在 1-200 之间");
            }
            pref.setDailyNewLimit(req.getDailyNewLimit());
        }
        if (req.getReviewUnlockHour() != null) {
            if (req.getReviewUnlockHour() < 0 || req.getReviewUnlockHour() > 23) {
                throw new BizException("复习开放时刻需在 0-23 之间");
            }
            pref.setReviewUnlockHour(req.getReviewUnlockHour());
        }
        if (req.getAutoSpreadNew() != null) {
            pref.setAutoSpreadNew(req.getAutoSpreadNew() ? 1 : 0);
        }
        prefMapper.update(pref);
    }

    private void allocateNewWords(Long userId, LocalDate today) {
        WdUserStudyPref pref = ensurePref(userId);
        int limit = pref.getDailyNewLimit();
        List<WdLearnPlan> alreadyDue = dedupByWordId(planMapper.selectTodayNewWords(userId, today));
        int room = Math.max(0, limit - alreadyDue.size());
        int nextSeq = alreadyDue.stream()
                .map(WdLearnPlan::getNewAllocSeq)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;

        // 1) 未排期 → 今日（填满上限为止）
        List<WdLearnPlan> unscheduled = dedupByWordId(planMapper.selectUnscheduledNew(userId));
        int take = Math.min(room, unscheduled.size());
        for (int i = 0; i < take; i++) {
            WdLearnPlan plan = unscheduled.get(i);
            plan.setScheduledNewDate(today);
            plan.setNewAllocSeq(nextSeq++);
            planMapper.updateSchedule(plan);
        }
        room -= take;

        // 2) 上限上调后：把原先排到未来的词提前到今日
        if (room > 0) {
            List<WdLearnPlan> future = dedupByWordId(planMapper.selectFutureScheduledNew(userId, today));
            int pull = Math.min(room, future.size());
            for (int i = 0; i < pull; i++) {
                WdLearnPlan plan = future.get(i);
                plan.setScheduledNewDate(today);
                plan.setNewAllocSeq(nextSeq++);
                planMapper.updateSchedule(plan);
            }
        }

        // 3) 自动均摊：剩余未排期摊到后续学习日
        boolean autoSpread = pref.getAutoSpreadNew() == null || pref.getAutoSpreadNew() == 1;
        if (!autoSpread) {
            return;
        }
        unscheduled = dedupByWordId(planMapper.selectUnscheduledNew(userId));
        if (unscheduled.isEmpty()) {
            return;
        }
        for (int i = 0; i < unscheduled.size(); i++) {
            WdLearnPlan plan = unscheduled.get(i);
            int dayOffset = 1 + i / limit;
            plan.setScheduledNewDate(today.plusDays(dayOffset));
            plan.setNewAllocSeq(i);
            planMapper.updateSchedule(plan);
        }
    }

    /**
     * 今日会话已结束后又出现新词/复习：补进队列并重新打开会话。
     */
    private void supplementTodayWork(Long userId, LocalDate today, WdStudyDaily daily) {
        allocateNewWords(userId, today);
        List<Long> completedToday = queueMapper.selectWordIdsWithMinDonePasses(
                userId, windowStart(userId, today), windowEnd(userId, today), REQUIRED_DAILY_PASSES);
        List<WdLearnPlan> reviewPlans = filterStudiedToday(
                dedupByWordId(planMapper.selectReviewTasks(userId, today)), completedToday);
        List<WdLearnPlan> newPlans = filterStudiedToday(
                dedupByWordId(planMapper.selectTodayNewWords(userId, today)), completedToday);

        List<WdStudyQueue> existing = queueMapper.selectByUserAndDate(userId, today);
        Set<Long> alreadyQueued = existing.stream()
                .map(WdStudyQueue::getPlanId)
                .collect(Collectors.toSet());

        List<WdLearnPlan> missingReview = reviewPlans.stream()
                .filter(p -> !alreadyQueued.contains(p.getId()))
                .collect(Collectors.toList());

        int newLimit = getDailyNewLimit(userId);
        // 只按「待学」新词占名额，已完成遍次不占今日新词上限
        int existingNewPending = queueMapper.countPendingByPhase(userId, today, PHASE_NEW);
        int room = Math.max(0, newLimit - existingNewPending);
        List<WdLearnPlan> missingNew = newPlans.stream()
                .filter(p -> !alreadyQueued.contains(p.getId()))
                .limit(room)
                .collect(Collectors.toList());

        if (missingReview.isEmpty() && missingNew.isEmpty()) {
            ensureSecondPassForPartialWords(userId, today);
            return;
        }

        int order = queueMapper.maxSortOrder(userId, today) + 1;
        for (WdLearnPlan plan : missingReview) {
            insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_REVIEW, PHASE_REVIEW, order++, "supplement");
        }
        for (WdLearnPlan plan : missingNew) {
            // 逾期未学新词滚入今日后，排期日期对齐，避免次日再被当成「昨天的」
            if (plan.getScheduledNewDate() != null && plan.getScheduledNewDate().isBefore(today)) {
                plan.setScheduledNewDate(today);
                planMapper.updateSchedule(plan);
            }
            insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_NEW, PHASE_NEW, order++, "supplement");
        }

        int reviewTotal = (daily.getReviewTotal() == null ? 0 : daily.getReviewTotal()) + missingReview.size();
        int newTotal = (daily.getNewTotal() == null ? 0 : daily.getNewTotal()) + missingNew.size();
        daily.setReviewTotal(reviewTotal);
        daily.setNewTotal(newTotal);
        if (!missingReview.isEmpty()) {
            daily.setReviewPhaseDone(0);
        } else if (reviewTotal == 0) {
            daily.setReviewPhaseDone(1);
        }
        daily.setStatus(0);
        dailyMapper.update(daily);
        ensureSecondPassForPartialWords(userId, today);
    }

    /** 只背过 1 遍且无待做项：补第二遍（兼容旧会话 / 规则升级） */
    private void ensureSecondPassForPartialWords(Long userId, LocalDate today) {
        List<WdStudyQueue> items = queueMapper.selectByUserAndDate(userId, today);
        Map<Long, Long> doneCount = new HashMap<>();
        Map<Long, Long> pendingCount = new HashMap<>();
        Map<Long, String> phaseByPlan = new HashMap<>();
        for (WdStudyQueue q : items) {
            Long planId = q.getPlanId();
            if (planId == null) continue;
            if (q.getPhase() != null) {
                phaseByPlan.putIfAbsent(planId, q.getPhase());
            }
            if (q.getState() != null && q.getState() == WdStudyQueue.STATE_DONE) {
                doneCount.merge(planId, 1L, Long::sum);
            } else if (q.getState() != null && q.getState() == WdStudyQueue.STATE_PENDING) {
                pendingCount.merge(planId, 1L, Long::sum);
            }
        }
        for (Map.Entry<Long, Long> e : doneCount.entrySet()) {
            Long planId = e.getKey();
            if (e.getValue() < REQUIRED_DAILY_PASSES
                    && pendingCount.getOrDefault(planId, 0L) == 0) {
                WdLearnPlan plan = planMapper.selectById(planId);
                if (plan == null) continue;
                WdWordBook notebook = bookMapper.selectById(plan.getNotebookId());
                // 已关闭背诵的单词本不再补第二遍
                if (notebook == null || (notebook.getStatus() != null && notebook.getStatus() == 0)) {
                    continue;
                }
                String phase = phaseByPlan.getOrDefault(planId, PHASE_NEW);
                int sortOrder = queueMapper.maxSortOrder(userId, today) + 1;
                insertQueueItem(userId, today, planId, WdStudyQueue.TYPE_INTRADAY, phase, sortOrder,
                        REASON_CONFIRM_PASS);
            }
        }
    }

    private void rescheduleForgotten(Long userId, LocalDate today, WdLearnPlan plan) {
        int limit = getDailyNewLimit(userId);
        List<WdLearnPlan> unscheduled = dedupByWordId(planMapper.selectUnscheduledNew(userId));
        int dayOffset = Math.max(1, (unscheduled.size() + 1) / limit);
        plan.setScheduledNewDate(today.plusDays(dayOffset));
        plan.setNewAllocSeq(unscheduled.size());
        planMapper.updateSchedule(plan);
    }

    private void buildInitialQueue(Long userId, LocalDate today, List<WdLearnPlan> reviewPlans,
                                   List<WdLearnPlan> newPlans) {
        int order = 0;
        for (WdLearnPlan plan : reviewPlans) {
            insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_REVIEW, PHASE_REVIEW, order++, "initial");
        }
        for (WdLearnPlan plan : newPlans) {
            insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_NEW, PHASE_NEW, order++, "initial");
        }
    }

    private void insertQueueItem(Long userId, LocalDate today, Long planId, String queueType,
                                 String phase, int sortOrder, String reason) {
        WdStudyQueue item = WdStudyQueue.builder()
                .userId(userId)
                .studyDate(today)
                .planId(planId)
                .queueType(queueType)
                .phase(phase)
                .sortOrder(sortOrder)
                .state(WdStudyQueue.STATE_PENDING)
                .insertReason(reason)
                .build();
        queueMapper.insert(item);
    }

    private void insertIntraday(Long userId, LocalDate today, WdLearnPlan plan, String phase,
                                int feedback, int currentSortOrder) {
        int sortOrder = feedback == StudyFeedback.FUZZY
                ? currentSortOrder + 3
                : queueMapper.maxSortOrder(userId, today) + 1;
        insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_INTRADAY, phase, sortOrder,
                feedback == StudyFeedback.FUZZY ? "fuzzy_repeat" : "forget_repeat");
    }

    private void insertConfirmPass(Long userId, LocalDate today, WdLearnPlan plan, String phase,
                                   int currentSortOrder) {
        int sortOrder = currentSortOrder + 5;
        insertQueueItem(userId, today, plan.getId(), WdStudyQueue.TYPE_INTRADAY, phase, sortOrder,
                REASON_CONFIRM_PASS);
    }

    /** 删除单词本/单词后，清掉仍挂在今日队列里的幽灵任务；关闭背诵的本也一并清掉 */
    private void pruneStaleQueue(Long userId, LocalDate today) {
        int removedDisabled = queueMapper.deletePendingFromDisabledNotebooks(userId, today);
        List<WdStudyQueue> items = queueMapper.selectByUserAndDate(userId, today);
        boolean changed = removedDisabled > 0;
        for (WdStudyQueue item : items) {
            if (item.getState() == null || item.getState() != WdStudyQueue.STATE_PENDING) {
                continue;
            }
            WdLearnPlan plan = planMapper.selectById(item.getPlanId());
            if (plan == null) {
                queueMapper.deleteById(item.getId());
                changed = true;
                continue;
            }
            WdWord word = wordMapper.selectById(plan.getWordId());
            WdWordBook notebook = bookMapper.selectById(plan.getNotebookId());
            // 单词本关闭参与学习后，清掉仍挂在今日队列的待学项（status=0；null 视为开启）
            boolean studyDisabled = notebook != null
                    && notebook.getStatus() != null
                    && notebook.getStatus() == 0;
            if (word == null || notebook == null || studyDisabled) {
                queueMapper.deleteById(item.getId());
                changed = true;
            }
        }
        if (changed) {
            WdStudyDaily daily = dailyMapper.selectByUserAndDate(userId, today);
            if (daily != null) {
                int reviewPending = queueMapper.countPendingByPhase(userId, today, PHASE_REVIEW);
                int newPending = queueMapper.countPendingByPhase(userId, today, PHASE_NEW);
                int reviewDone = daily.getReviewDone() == null ? 0 : daily.getReviewDone();
                int newDone = daily.getNewDone() == null ? 0 : daily.getNewDone();
                daily.setReviewTotal(reviewDone + reviewPending);
                daily.setNewTotal(newDone + newPending);
                if (reviewPending == 0) {
                    daily.setReviewPhaseDone(1);
                }
                if (queueMapper.countAllPending(userId, today) == 0) {
                    daily.setStatus(1);
                }
                dailyMapper.update(daily);
            }
        }
    }

    private WdStudyQueue pickNextQueueItem(Long userId, LocalDate today, WdStudyDaily daily) {
        if (daily.getReviewPhaseDone() == null || daily.getReviewPhaseDone() == 0) {
            WdStudyQueue reviewNext = queueMapper.selectNextPendingByPhase(userId, today, PHASE_REVIEW);
            if (reviewNext != null) {
                return reviewNext;
            }
            daily.setReviewPhaseDone(1);
            dailyMapper.update(daily);
        }
        return queueMapper.selectNextPending(userId, today);
    }

    private void refreshDailyProgress(WdStudyDaily daily, Long userId, LocalDate today) {
        int reviewPending = queueMapper.countPendingByPhase(userId, today, PHASE_REVIEW);
        int newPending = queueMapper.countPendingByPhase(userId, today, PHASE_NEW);
        // 按「词」计完成：学习日时间窗内至少背完 REQUIRED_DAILY_PASSES 遍
        int reviewDone = queueMapper.countWordsWithMinDonePassesByPhase(
                userId, PHASE_REVIEW, windowStart(userId, today), windowEnd(userId, today), REQUIRED_DAILY_PASSES);
        int newDone = queueMapper.countWordsWithMinDonePassesByPhase(
                userId, PHASE_NEW, windowStart(userId, today), windowEnd(userId, today), REQUIRED_DAILY_PASSES);

        if (reviewPending == 0 && daily.getReviewTotal() != null && daily.getReviewTotal() > 0) {
            daily.setReviewPhaseDone(1);
        } else if (daily.getReviewTotal() != null && daily.getReviewTotal() == 0) {
            daily.setReviewPhaseDone(1);
        }
        daily.setReviewDone(Math.min(reviewDone, daily.getReviewTotal() != null ? daily.getReviewTotal() : reviewDone));
        daily.setNewDone(Math.min(newDone, daily.getNewTotal() != null ? daily.getNewTotal() : newDone));
        markDailyCompleteIfNeeded(daily, userId, today);
        dailyMapper.update(daily);
    }

    private void markDailyCompleteIfNeeded(WdStudyDaily daily, Long userId, LocalDate today) {
        if (daily.getStatus() != null && daily.getStatus() == 1) {
            return;
        }
        int allPending = queueMapper.countAllPending(userId, today);
        if (allPending == 0 && daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1) {
            daily.setStatus(1);
        }
    }

    private List<WdLearnPlan> filterStudiedToday(List<WdLearnPlan> plans, List<Long> studiedToday) {
        if (studiedToday == null || studiedToday.isEmpty()) {
            return plans;
        }
        Set<Long> studied = new HashSet<>(studiedToday);
        return plans.stream().filter(p -> !studied.contains(p.getWordId())).collect(Collectors.toList());
    }

    private StudySessionVO buildSessionVO(Long userId, LocalDate today, WdStudyDaily daily) {
        WdStudyQueue next = pickNextQueueItem(userId, today, daily);
        StudyTaskVO current = null;
        if (next != null) {
            WdLearnPlan plan = planMapper.selectById(next.getPlanId());
            current = toTaskVO(plan);
        }
        int reviewRemaining = queueMapper.countPendingByPhase(userId, today, PHASE_REVIEW);
        int newRemaining = queueMapper.countPendingByPhase(userId, today, PHASE_NEW);
        String phase = resolveCurrentPhase(daily, next);

        return StudySessionVO.builder()
                .dailyId(daily.getId())
                .phase(phase)
                .current(current)
                .queueItemId(next != null ? next.getId() : null)
                .reviewRemaining(reviewRemaining)
                .newRemaining(newRemaining)
                .reviewPhaseDone(daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1)
                .today(toTodayVO(daily, userId, today))
                .build();
    }

    private String resolveCurrentPhase(WdStudyDaily daily, WdStudyQueue next) {
        if (next == null) {
            return daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1 ? PHASE_NEW : PHASE_REVIEW;
        }
        return next.getPhase() != null ? next.getPhase() : PHASE_REVIEW;
    }

    private StudyTodayVO toTodayVO(WdStudyDaily daily, Long userId, LocalDate today) {
        int reviewPending = queueMapper.countPendingByPhase(userId, today, PHASE_REVIEW);
        int newPending = queueMapper.countPendingByPhase(userId, today, PHASE_NEW);
        boolean reviewPhaseDone = daily.getReviewPhaseDone() != null && daily.getReviewPhaseDone() == 1;
        return buildTodayVO(userId, today,
                reviewPending,
                daily.getReviewDone() != null ? daily.getReviewDone() : 0,
                newPending,
                daily.getNewDone() != null ? daily.getNewDone() : 0,
                reviewPhaseDone);
    }

    private StudyTodayVO buildTodayVO(Long userId, LocalDate today, int reviewPending, int reviewDone,
                                      int newPending, int newDone, boolean reviewPhaseDone) {
        return StudyTodayVO.builder()
                .studyDate(today)
                .reviewUnlockHour(getUnlockHour(userId))
                .reviewPending(reviewPending)
                .reviewDone(reviewDone)
                .newPending(newPending)
                .newDone(newDone)
                .reviewPhaseDone(reviewPhaseDone)
                .newPhaseUnlocked(reviewPhaseDone)
                .currentPhase(reviewPhaseDone ? PHASE_NEW : PHASE_REVIEW)
                .build();
    }

    private int getUnlockHour(Long userId) {
        if (userId != null) {
            WdUserStudyPref pref = prefMapper.selectByUserId(userId);
            if (pref != null && pref.getReviewUnlockHour() != null) {
                return pref.getReviewUnlockHour();
            }
        }
        return configService.getReviewUnlockHour();
    }

    private int getDailyNewLimit(Long userId) {
        return ensurePref(userId).getDailyNewLimit();
    }

    private WdUserStudyPref ensurePref(Long userId) {
        WdUserStudyPref pref = prefMapper.selectByUserId(userId);
        if (pref != null) {
            return pref;
        }
        int defaultLimit = configService.getInt("study.daily_new_limit_default", 20);
        pref = WdUserStudyPref.builder()
                .userId(userId)
                .dailyNewLimit(defaultLimit)
                .reviewUnlockHour(configService.getReviewUnlockHour())
                .autoSpreadNew(1)
                .timezone("Asia/Shanghai")
                .build();
        prefMapper.insert(pref);
        return pref;
    }

    private List<WdLearnPlan> dedupByWordId(List<WdLearnPlan> plans) {
        return plans.stream()
                .collect(Collectors.toMap(WdLearnPlan::getWordId, p -> p,
                        (a, b) -> a.getId() < b.getId() ? a : b,
                        LinkedHashMap::new))
                .values().stream()
                .collect(Collectors.toList());
    }

    private void saveRecord(Long userId, WdLearnPlan plan, boolean isNewPhase, int feedback) {
        int legacyResult = feedback == StudyFeedback.KNOW ? LearnStatus.RESULT_REMEMBER : LearnStatus.RESULT_FORGET;
        WdLearnRecord record = WdLearnRecord.builder()
                .userId(userId)
                .notebookId(plan.getNotebookId())
                .wordId(plan.getWordId())
                .studyType(isNewPhase ? LearnStatus.STUDY_TYPE_FIRST : LearnStatus.STUDY_TYPE_REVIEW)
                .result(legacyResult)
                .feedback(feedback)
                .studyMode(LearnStatus.STUDY_MODE_DAILY)
                .affectsSchedule(1)
                .studyTime(LocalDateTime.now())
                .build();
        recordMapper.insert(record);
    }

    private StudyTaskVO toTaskVO(WdLearnPlan plan) {
        if (plan == null) {
            return null;
        }
        WdWord word = wordMapper.selectById(plan.getWordId());
        WdWordBook book = bookMapper.selectById(plan.getNotebookId());
        return StudyTaskVO.builder()
                .planId(plan.getId())
                .wordId(plan.getWordId())
                .wordProto(word != null ? word.getWordProto() : "")
                .usPhonetic(word != null ? word.getUsPhonetic() : "")
                .ukPhonetic(word != null ? word.getUkPhonetic() : "")
                .partSpeechMean(word != null ? word.getPartSpeechMean() : "")
                .sentenceExample(word != null ? word.getSentenceExample() : "")
                .rootAffix(word != null ? word.getRootAffix() : "")
                .learnStatus(plan.getLearnStatus())
                .currentLevel(plan.getCurrentLevel())
                .reviewTimes(plan.getReviewTimes())
                .nextReviewTime(plan.getNextReviewTime())
                .notebookName(book != null ? book.getBookName() : "")
                .build();
    }

    @Override
    @Transactional
    public List<StudyTaskVO> listTodayQueue(Long userId, String phase) {
        // 确保今日队列已生成
        openSession(userId);
        LocalDate today = currentStudyDate(userId);
        String normalized = phase == null ? "" : phase.trim().toLowerCase();
        List<WdStudyQueue> items;
        if (PHASE_REVIEW.equals(normalized) || PHASE_NEW.equals(normalized)) {
            items = queueMapper.selectByUserDateAndPhase(userId, today, normalized);
        } else {
            items = queueMapper.selectByUserAndDate(userId, today);
        }
        List<StudyTaskVO> result = new ArrayList<>();
        for (WdStudyQueue item : items) {
            if (item.getState() != null && item.getState() != WdStudyQueue.STATE_PENDING) {
                continue;
            }
            WdLearnPlan plan = planMapper.selectById(item.getPlanId());
            StudyTaskVO vo = toTaskVO(plan);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }
}
