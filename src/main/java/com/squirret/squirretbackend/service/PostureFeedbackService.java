package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.FSRMetricsDTO;
import com.squirret.squirretbackend.dto.FsrFeedbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostureFeedbackService {

    private static final Duration FEEDBACK_WINDOW = Duration.ofSeconds(10);

    private final FSRDataService fsrDataService;

    public FsrFeedbackResponse getOverallFeedback() {
        Map<String, FSRDataDTO> averaged = fsrDataService.getAveragedInsoleData(FEEDBACK_WINDOW);
        FSRDataDTO leftData = averaged.get("left");
        FSRDataDTO rightData = averaged.get("right");

        // 양발 데이터가 모두 없으면 NO_DATA 반환
        if ((leftData == null || leftData.getSide() == null) && 
            (rightData == null || rightData.getSide() == null)) {
            return FsrFeedbackResponse.builder()
                    .stage("UNKNOWN")
                    .status("NO_DATA")
                    .feedback("데이터 수집 중입니다")
                    .metrics(null)
                    .build();
        }

        // 양발 데이터를 평균 내어 통합 분석
        CombinedMetrics combined = calculateCombinedMetrics(leftData, rightData);
        StageResult descent = evaluateDescent(combined);
        StageResult ascent = evaluateAscent(combined);

        StageResult finalStage = chooseStage(descent, ascent);

        FSRMetricsDTO metrics = FSRMetricsDTO.builder()
                .front(combined.front)
                .rear(combined.rear)
                .inner(combined.inner)
                .outer(combined.outer)
                .heel(combined.heel)
                .innerOuterDiff(combined.innerOuterDiff)
                .leftRightDiff(combined.leftRightDiff)
                .build();

        String feedback;
        if (finalStage.messages.isEmpty()) {
            feedback = finalStage.goodMessage;
        } else {
            // 여러 메시지가 있는 경우 첫 번째 메시지만 사용하고, 25자로 제한
            feedback = finalStage.messages.get(0);
        }
        
        // 피드백을 25자 이내로 제한
        feedback = limitFeedbackLength(feedback, 25);

        return FsrFeedbackResponse.builder()
                .stage(finalStage.stage)
                .status(finalStage.messages.isEmpty() ? "GOOD" : "BAD")
                .feedback(feedback)
                .metrics(metrics)
                .build();
    }
    
    /**
     * 피드백 텍스트를 지정된 길이로 제한
     * 한글, 영문 모두 문자 수로 계산 (바이트가 아닌 문자 수)
     * 
     * @param text 원본 텍스트
     * @param maxLength 최대 길이
     * @return 제한된 텍스트 (길이가 maxLength를 초과하면 말줄임표 없이 자름)
     */
    private String limitFeedbackLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        // 25자 초과 시 앞에서부터 자름 (말줄임표 없이)
        return text.substring(0, maxLength);
    }

    private CombinedMetrics calculateCombinedMetrics(FSRDataDTO leftData, FSRDataDTO rightData) {
        CombinedMetrics combined = new CombinedMetrics();
        
        Metrics leftMetrics = leftData != null && leftData.getSide() != null 
                ? calculateMetrics(leftData) : new Metrics();
        Metrics rightMetrics = rightData != null && rightData.getSide() != null 
                ? calculateMetrics(rightData) : new Metrics();

        // 양발 평균 계산
        combined.front = (leftMetrics.front + rightMetrics.front) / 2f;
        combined.rear = (leftMetrics.rear + rightMetrics.rear) / 2f;
        combined.inner = (leftMetrics.inner + rightMetrics.inner) / 2f;
        combined.outer = (leftMetrics.outer + rightMetrics.outer) / 2f;
        combined.heel = (leftMetrics.heel + rightMetrics.heel) / 2f;
        combined.innerOuterDiff = Math.abs(combined.inner - combined.outer);
        
        // 좌우 균형 차이 계산
        float leftTotal = leftMetrics.front + leftMetrics.rear;
        float rightTotal = rightMetrics.front + rightMetrics.rear;
        combined.leftRightDiff = Math.abs(leftTotal - rightTotal);

        return combined;
    }

    private Metrics calculateMetrics(FSRDataDTO data) {
        Metrics metrics = new Metrics();
        metrics.front = data.getRatio3() + data.getRatio4() + data.getRatio6();
        metrics.rear = data.getRatio1() + data.getRatio5();
        metrics.outer = data.getRatio1() + data.getRatio2() + data.getRatio3() + data.getRatio4();
        metrics.inner = data.getRatio5() + data.getRatio6();
        metrics.heel = data.getRatio1() + data.getRatio5();
        metrics.innerOuterDiff = Math.abs(metrics.inner - metrics.outer);
        return metrics;
    }

    private StageResult evaluateDescent(CombinedMetrics m) {
        StageResult result = new StageResult("DESCENT");

        boolean withinRear = between(m.rear, 55f, 70f);
        boolean withinFront = m.front <= 35f;
        boolean heelOK = m.heel >= 55f;
        boolean balanceOK = m.innerOuterDiff <= 10f;
        boolean leftRightOK = m.leftRightDiff <= 15f;

        if (withinRear && withinFront && heelOK && balanceOK && leftRightOK) {
            result.goodMessage = "하강 자세 안정적입니다";
            return result;
        }

        if (m.front > 40f) {
            result.messages.add("뒤꿈치로 체중을 이동하세요");
        }
        if (m.inner > 60f) {
            result.messages.add("무릎 정렬을 유지하세요");
        }
        if (m.outer > 60f) {
            result.messages.add("발 안쪽에 힘을 주세요");
        }
        if (!heelOK) {
            result.messages.add("뒤꿈치에 체중을 실으세요");
        }
        if (!withinRear) {
            result.messages.add("뒤꿈치 중심으로 내려앉으세요");
        }
        if (!balanceOK) {
            result.messages.add("좌우 균형을 맞추세요");
        }
        if (!leftRightOK) {
            result.messages.add("양발에 균등하게 체중 배분");
        }
        return result;
    }

    private StageResult evaluateAscent(CombinedMetrics m) {
        StageResult result = new StageResult("ASCENT");

        boolean rearOK = between(m.rear, 45f, 55f);
        boolean frontOK = between(m.front, 45f, 55f);
        boolean heelOK = m.heel >= 45f;
        boolean balanceOK = m.innerOuterDiff <= 10f;
        boolean leftRightOK = m.leftRightDiff <= 15f;

        if (rearOK && frontOK && heelOK && balanceOK && leftRightOK) {
            result.goodMessage = "상승 자세 안정적입니다";
            return result;
        }

        if (m.heel < 40f) {
            result.messages.add("뒤꿈치를 바닥에 붙이세요");
        }
        if (m.outer > 60f) {
            result.messages.add("발 안쪽에 힘을 주세요");
        }
        if (m.inner > 60f) {
            result.messages.add("무릎 정렬을 유지하세요");
        }
        if (!rearOK || !frontOK) {
            result.messages.add("상체를 곧게 세우세요");
        }
        if (!balanceOK) {
            result.messages.add("좌우 균형을 맞추세요");
        }
        if (!heelOK) {
            result.messages.add("뒤꿈치를 바닥에 붙이세요");
        }
        if (!leftRightOK) {
            result.messages.add("양발에 균등하게 체중 배분");
        }
        return result;
    }

    private StageResult chooseStage(StageResult descent, StageResult ascent) {
        if (descent.messages.isEmpty() && !ascent.messages.isEmpty()) {
            return descent;
        }
        if (ascent.messages.isEmpty() && !descent.messages.isEmpty()) {
            return ascent;
        }
        if (descent.messages.size() < ascent.messages.size()) {
            return descent;
        }
        if (ascent.messages.size() < descent.messages.size()) {
            return ascent;
        }
        // 동일한 경우 기본적으로 하강 구간을 우선
        return descent;
    }

    private boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }

    private static class Metrics {
        float front;
        float rear;
        float inner;
        float outer;
        float heel;
        float innerOuterDiff;
    }

    private static class CombinedMetrics {
        float front;
        float rear;
        float inner;
        float outer;
        float heel;
        float innerOuterDiff;
        float leftRightDiff;
    }

    private static class StageResult {
        final String stage;
        final List<String> messages = new ArrayList<>();
        String goodMessage = "좋은 자세입니다";

        StageResult(String stage) {
            this.stage = stage;
        }
    }
}

