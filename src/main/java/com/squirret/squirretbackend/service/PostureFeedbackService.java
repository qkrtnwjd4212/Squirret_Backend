package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.FsrFeedbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
                    .feedback("최근 10초 동안 수집된 데이터가 없습니다.")
                    .metrics(Map.of())
                    .build();
        }

        // 양발 데이터를 평균 내어 통합 분석
        CombinedMetrics combined = calculateCombinedMetrics(leftData, rightData);
        StageResult descent = evaluateDescent(combined);
        StageResult ascent = evaluateAscent(combined);

        StageResult finalStage = chooseStage(descent, ascent);

        Map<String, Float> metricMap = new LinkedHashMap<>();
        metricMap.put("front", combined.front);
        metricMap.put("rear", combined.rear);
        metricMap.put("inner", combined.inner);
        metricMap.put("outer", combined.outer);
        metricMap.put("heel", combined.heel);
        metricMap.put("innerOuterDiff", combined.innerOuterDiff);
        metricMap.put("leftRightDiff", combined.leftRightDiff);

        String feedback;
        if (finalStage.messages.isEmpty()) {
            feedback = finalStage.goodMessage;
        } else {
            feedback = String.join(" ", finalStage.messages);
        }

        return FsrFeedbackResponse.builder()
                .stage(finalStage.stage)
                .status(finalStage.messages.isEmpty() ? "GOOD" : "BAD")
                .feedback(feedback)
                .metrics(metricMap)
                .build();
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
            result.goodMessage = "하강 구간 자세가 안정적입니다. 현재 자세를 유지하세요.";
            return result;
        }

        if (m.front > 40f) {
            result.messages.add("체중이 앞쪽으로 쏠렸습니다. 뒤꿈치로 눌러주세요.");
        }
        if (m.inner > 60f) {
            result.messages.add("무릎이 안쪽으로 모이고 있습니다.");
        }
        if (m.outer > 60f) {
            result.messages.add("발 안쪽에도 힘을 주세요.");
        }
        if (!heelOK) {
            result.messages.add("뒤꿈치로 체중을 충분히 실어주세요.");
        }
        if (!withinRear) {
            result.messages.add("엉덩이를 뒤로 보내고 뒤꿈치를 중심으로 내려앉으세요.");
        }
        if (!balanceOK) {
            result.messages.add("좌우 체중 균형을 맞춰주세요.");
        }
        if (!leftRightOK) {
            result.messages.add("양발에 체중을 균등하게 분배하세요.");
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
            result.goodMessage = "상승 구간 자세가 안정적입니다. 그대로 일어나세요.";
            return result;
        }

        if (m.heel < 40f) {
            result.messages.add("뒤꿈치가 들리고 있습니다.");
        }
        if (m.outer > 60f) {
            result.messages.add("발 안쪽에도 힘을 주세요.");
        }
        if (m.inner > 60f) {
            result.messages.add("무릎이 안쪽으로 붕괴되고 있습니다.");
        }
        if (!rearOK || !frontOK) {
            result.messages.add("상체를 곧게 세우고 앞뒤 체중을 균형 있게 분배하세요.");
        }
        if (!balanceOK) {
            result.messages.add("좌우 균형을 맞춰 일어나세요.");
        }
        if (!heelOK) {
            result.messages.add("뒤꿈치를 바닥에 단단히 붙이고 올라오세요.");
        }
        if (!leftRightOK) {
            result.messages.add("양발에 체중을 균등하게 분배하세요.");
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
        String goodMessage = "좋은 자세입니다. 계속 유지하세요.";

        StageResult(String stage) {
            this.stage = stage;
        }
    }
}

