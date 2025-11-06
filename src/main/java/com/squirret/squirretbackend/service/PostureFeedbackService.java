package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.PostureFeedbackDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostureFeedbackService {

    private final FSRDataService fsrDataService;

    // 임계값 설정 (퍼센트)
    private static final float PRESSURE_THRESHOLD_MIN = 5.0f;    // 최소 압력 비율
    private static final float PRESSURE_THRESHOLD_MAX = 40.0f;   // 최대 압력 비율
    private static final float FRONT_BACK_BALANCE_THRESHOLD = 15.0f;  // 앞뒤 균형 임계값
    private static final float INNER_OUTER_BALANCE_THRESHOLD = 15.0f; // 안쪽/바깥쪽 균형 임계값
    private static final float LEFT_RIGHT_BALANCE_THRESHOLD = 20.0f;  // 좌우 균형 임계값

    /**
     * 양발 데이터로부터 종합 피드백 생성 (메인 API)
     */
    public PostureFeedbackDTO getOverallFeedback() {
        var insoleData = fsrDataService.getLatestInsoleData();
        FSRDataDTO leftData = insoleData.get("left");
        FSRDataDTO rightData = insoleData.get("right");

        List<String> feedbacks = new ArrayList<>();

        // 양발 모두 데이터가 있어야 분석 가능
        if (leftData == null || rightData == null || 
            leftData.getSide() == null || rightData.getSide() == null) {
            feedbacks.add("양발 데이터가 모두 필요합니다.");
            return PostureFeedbackDTO.builder()
                    .side("both")
                    .feedbacks(feedbacks)
                    .overallFeedback("좌우 발 데이터를 모두 수집한 후 다시 시도해주세요.")
                    .build();
        }

        // 양발 데이터 종합 분석
        PostureFeedbackDTO.PostureAnalysis leftAnalysis = analyzePosture(leftData);
        PostureFeedbackDTO.PostureAnalysis rightAnalysis = analyzePosture(rightData);

        // 1. 좌우 균형 분석
        float leftTotal = getTotalPressure(leftData);
        float rightTotal = getTotalPressure(rightData);
        float leftRightDiff = Math.abs(leftTotal - rightTotal);

        if (leftRightDiff > LEFT_RIGHT_BALANCE_THRESHOLD) {
            if (leftTotal > rightTotal) {
                feedbacks.add("왼발에 체중이 더 많이 실려있습니다. 오른발에도 체중을 분산시켜주세요.");
            } else {
                feedbacks.add("오른발에 체중이 더 많이 실려있습니다. 왼발에도 체중을 분산시켜주세요.");
            }
        }

        // 2. 양발 평균을 기준으로 앞뒤 균형 분석
        float avgFrontPressure = (leftAnalysis.getFrontPressure() + rightAnalysis.getFrontPressure()) / 2;
        float avgBackPressure = (leftAnalysis.getBackPressure() + rightAnalysis.getBackPressure()) / 2;
        float frontBackDiff = Math.abs(avgFrontPressure - avgBackPressure);

        if (frontBackDiff > FRONT_BACK_BALANCE_THRESHOLD) {
            if (avgFrontPressure > avgBackPressure) {
                feedbacks.add("양발 앞쪽에 압력이 너무 집중되어 있습니다. 발뒤꿈치에도 체중을 분산시켜주세요.");
            } else {
                feedbacks.add("양발 뒤쪽에 압력이 너무 집중되어 있습니다. 앞발에도 체중을 분산시켜주세요.");
            }
        }

        // 3. 양발 평균을 기준으로 안쪽/바깥쪽 균형 분석
        float avgOuterPressure = (leftAnalysis.getOuterPressure() + rightAnalysis.getOuterPressure()) / 2;
        float avgInnerPressure = (leftAnalysis.getInnerPressure() + rightAnalysis.getInnerPressure()) / 2;
        float innerOuterDiff = Math.abs(avgOuterPressure - avgInnerPressure);

        if (innerOuterDiff > INNER_OUTER_BALANCE_THRESHOLD) {
            if (avgOuterPressure > avgInnerPressure) {
                feedbacks.add("양발 바깥쪽에 압력이 너무 쏠려있습니다. 발 안쪽에도 체중을 분산시켜주세요.");
            } else {
                feedbacks.add("양발 안쪽에 압력이 너무 쏠려있습니다. 발 바깥쪽에도 체중을 분산시켜주세요.");
            }
        }

        // 4. 특정 영역 과부하 체크 (양발 중 하나라도 문제가 있으면 피드백)
        checkOverallOverpressure(leftData, rightData, feedbacks);

        // 5. 압력 부족 영역 체크
        checkOverallUnderpressure(leftData, rightData, feedbacks);

        // 6. 정상적인 경우
        if (feedbacks.isEmpty()) {
            feedbacks.add("균형잡힌 자세입니다. 좋은 자세를 유지하세요!");
        }

        // 종합 피드백 생성
        String overallFeedback = generateCombinedOverallFeedback(
                leftAnalysis, rightAnalysis, leftRightDiff, frontBackDiff, innerOuterDiff, feedbacks);

        // 평균 분석 데이터 생성
        PostureFeedbackDTO.PostureAnalysis combinedAnalysis = new PostureFeedbackDTO.PostureAnalysis(
                avgFrontPressure,
                avgBackPressure,
                avgOuterPressure,
                avgInnerPressure,
                (leftAnalysis.getHeelPressure() + rightAnalysis.getHeelPressure()) / 2,
                (leftAnalysis.getForefootPressure() + rightAnalysis.getForefootPressure()) / 2
        );

        return PostureFeedbackDTO.builder()
                .side("both")
                .feedbacks(feedbacks)
                .overallFeedback(overallFeedback)
                .analysis(combinedAnalysis)
                .build();
    }

    /**
     * 개별 발 데이터로부터 피드백 생성
     */
    private PostureFeedbackDTO generateFeedback(String side, FSRDataDTO data) {
        if (data == null || data.getSide() == null) {
            List<String> errorFeedbacks = new ArrayList<>();
            errorFeedbacks.add("데이터가 없습니다.");
            return PostureFeedbackDTO.builder()
                    .side(side)
                    .feedbacks(errorFeedbacks)
                    .overallFeedback(side.equals("left") ? "왼발 데이터를 수집한 후 다시 시도해주세요." : "오른발 데이터를 수집한 후 다시 시도해주세요.")
                    .build();
        }

        List<String> feedbacks = new ArrayList<>();
        PostureFeedbackDTO.PostureAnalysis analysis = analyzePosture(data);

        // 1. 앞뒤 균형 분석
        float frontBackDiff = Math.abs(analysis.getFrontPressure() - analysis.getBackPressure());
        if (frontBackDiff > FRONT_BACK_BALANCE_THRESHOLD) {
            if (analysis.getFrontPressure() > analysis.getBackPressure()) {
                feedbacks.add("발 앞쪽에 압력이 너무 집중되어 있습니다. 발뒤꿈치에도 체중을 분산시켜주세요.");
            } else {
                feedbacks.add("발 뒤쪽에 압력이 너무 집중되어 있습니다. 앞발에도 체중을 분산시켜주세요.");
            }
        }

        // 2. 안쪽/바깥쪽 균형 분석
        float innerOuterDiff = Math.abs(analysis.getInnerPressure() - analysis.getOuterPressure());
        if (innerOuterDiff > INNER_OUTER_BALANCE_THRESHOLD) {
            if (analysis.getOuterPressure() > analysis.getInnerPressure()) {
                feedbacks.add("발 바깥쪽에 압력이 너무 쏠려있습니다. 발 안쪽에도 체중을 분산시켜주세요.");
            } else {
                feedbacks.add("발 안쪽에 압력이 너무 쏠려있습니다. 발 바깥쪽에도 체중을 분산시켜주세요.");
            }
        }

        // 3. 특정 영역 과부하 체크
        checkOverpressure(data, feedbacks);

        // 4. 압력 부족 영역 체크
        checkUnderpressure(data, feedbacks);

        // 5. 정상적인 경우
        if (feedbacks.isEmpty()) {
            feedbacks.add("균형잡힌 자세입니다. 좋은 자세를 유지하세요!");
        }

        String overallFeedback = generateOverallFeedbackForSingle(analysis, feedbacks);

        return PostureFeedbackDTO.builder()
                .side(side)
                .feedbacks(feedbacks)
                .overallFeedback(overallFeedback)
                .analysis(analysis)
                .build();
    }

    /**
     * 자세 분석 수행
     */
    private PostureFeedbackDTO.PostureAnalysis analyzePosture(FSRDataDTO data) {
        // 앞쪽 압력: 4번(새끼발가락), 6번(엄지발가락)
        float frontPressure = data.getRatio4() + data.getRatio6();
        
        // 뒤쪽 압력: 1번(뒤꿈치 바깥쪽), 5번(뒤꿈치 안쪽)
        float backPressure = data.getRatio1() + data.getRatio5();
        
        // 바깥쪽 압력: 1, 2, 3, 4번
        float outerPressure = data.getRatio1() + data.getRatio2() + data.getRatio3() + data.getRatio4();
        
        // 안쪽 압력: 5, 6번
        float innerPressure = data.getRatio5() + data.getRatio6();
        
        // 발뒤꿈치 압력
        float heelPressure = backPressure;
        
        // 앞발 압력
        float forefootPressure = frontPressure;

        return new PostureFeedbackDTO.PostureAnalysis(
                frontPressure,
                backPressure,
                outerPressure,
                innerPressure,
                heelPressure,
                forefootPressure
        );
    }

    /**
     * 과부하 영역 체크
     */
    private void checkOverpressure(FSRDataDTO data, List<String> feedbacks) {
        // 특정 센서가 40% 이상의 압력을 받는 경우
        if (data.getRatio1() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("발뒤꿈치 바깥쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if (data.getRatio4() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("새끼발가락 쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if (data.getRatio6() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("엄지발가락 쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if (data.getRatio2() > PRESSURE_THRESHOLD_MAX || data.getRatio3() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("발 바깥쪽 중간 부분에 과도한 압력이 가해지고 있습니다.");
        }
        if (data.getRatio5() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("발뒤꿈치 안쪽에 과도한 압력이 가해지고 있습니다.");
        }
    }

    /**
     * 압력 부족 영역 체크
     */
    private void checkUnderpressure(FSRDataDTO data, List<String> feedbacks) {
        // 특정 센서가 5% 미만의 압력을 받는 경우
        if (data.getRatio1() < PRESSURE_THRESHOLD_MIN && data.getRatio5() < PRESSURE_THRESHOLD_MIN) {
            feedbacks.add("발뒤꿈치에 충분한 체중이 실리지 않았습니다.");
        }
        if (data.getRatio4() < PRESSURE_THRESHOLD_MIN && data.getRatio6() < PRESSURE_THRESHOLD_MIN) {
            feedbacks.add("앞발에 충분한 체중이 실리지 않았습니다.");
        }
    }

    /**
     * 종합 피드백 생성 (단일 발)
     */
    private String generateOverallFeedbackForSingle(
            PostureFeedbackDTO.PostureAnalysis analysis, 
            List<String> feedbacks) {
        
        if (feedbacks.size() == 1 && feedbacks.get(0).contains("균형잡힌")) {
            return "현재 자세가 매우 좋습니다! 이 자세를 유지해주세요.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("자세 분석 결과: ");
        
        if (analysis.getFrontPressure() > analysis.getBackPressure() + 10) {
            sb.append("앞쪽 중심, ");
        } else if (analysis.getBackPressure() > analysis.getFrontPressure() + 10) {
            sb.append("뒤쪽 중심, ");
        }
        
        if (analysis.getOuterPressure() > analysis.getInnerPressure() + 10) {
            sb.append("바깥쪽 기울어짐, ");
        } else if (analysis.getInnerPressure() > analysis.getOuterPressure() + 10) {
            sb.append("안쪽 기울어짐, ");
        }
        
        sb.append("균형을 맞추기 위해 체중 분산에 주의하세요.");
        
        return sb.toString();
    }

    /**
     * 양발 종합 과부하 체크
     */
    private void checkOverallOverpressure(FSRDataDTO leftData, FSRDataDTO rightData, List<String> feedbacks) {
        // 양발 중 하나라도 특정 센서가 40% 이상의 압력을 받는 경우
        if (leftData.getRatio1() > PRESSURE_THRESHOLD_MAX || rightData.getRatio1() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("발뒤꿈치 바깥쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if (leftData.getRatio4() > PRESSURE_THRESHOLD_MAX || rightData.getRatio4() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("새끼발가락 쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if (leftData.getRatio6() > PRESSURE_THRESHOLD_MAX || rightData.getRatio6() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("엄지발가락 쪽에 과도한 압력이 가해지고 있습니다.");
        }
        if ((leftData.getRatio2() > PRESSURE_THRESHOLD_MAX || leftData.getRatio3() > PRESSURE_THRESHOLD_MAX) ||
            (rightData.getRatio2() > PRESSURE_THRESHOLD_MAX || rightData.getRatio3() > PRESSURE_THRESHOLD_MAX)) {
            feedbacks.add("발 바깥쪽 중간 부분에 과도한 압력이 가해지고 있습니다.");
        }
        if (leftData.getRatio5() > PRESSURE_THRESHOLD_MAX || rightData.getRatio5() > PRESSURE_THRESHOLD_MAX) {
            feedbacks.add("발뒤꿈치 안쪽에 과도한 압력이 가해지고 있습니다.");
        }
    }

    /**
     * 양발 종합 압력 부족 체크
     */
    private void checkOverallUnderpressure(FSRDataDTO leftData, FSRDataDTO rightData, List<String> feedbacks) {
        // 양발 모두 특정 센서가 5% 미만의 압력을 받는 경우
        if ((leftData.getRatio1() < PRESSURE_THRESHOLD_MIN && leftData.getRatio5() < PRESSURE_THRESHOLD_MIN) ||
            (rightData.getRatio1() < PRESSURE_THRESHOLD_MIN && rightData.getRatio5() < PRESSURE_THRESHOLD_MIN)) {
            feedbacks.add("발뒤꿈치에 충분한 체중이 실리지 않았습니다.");
        }
        if ((leftData.getRatio4() < PRESSURE_THRESHOLD_MIN && leftData.getRatio6() < PRESSURE_THRESHOLD_MIN) ||
            (rightData.getRatio4() < PRESSURE_THRESHOLD_MIN && rightData.getRatio6() < PRESSURE_THRESHOLD_MIN)) {
            feedbacks.add("앞발에 충분한 체중이 실리지 않았습니다.");
        }
    }

    /**
     * 양발 종합 피드백 생성
     */
    private String generateCombinedOverallFeedback(
            PostureFeedbackDTO.PostureAnalysis leftAnalysis,
            PostureFeedbackDTO.PostureAnalysis rightAnalysis,
            float leftRightDiff,
            float frontBackDiff,
            float innerOuterDiff,
            List<String> feedbacks) {
        
        if (feedbacks.size() == 1 && feedbacks.get(0).contains("균형잡힌")) {
            return "현재 자세가 매우 좋습니다! 이 자세를 유지해주세요.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("양발 종합 분석: ");
        
        if (leftRightDiff > LEFT_RIGHT_BALANCE_THRESHOLD) {
            sb.append("좌우 균형 불균형, ");
        }
        
        if (frontBackDiff > FRONT_BACK_BALANCE_THRESHOLD) {
            if ((leftAnalysis.getFrontPressure() + rightAnalysis.getFrontPressure()) / 2 > 
                (leftAnalysis.getBackPressure() + rightAnalysis.getBackPressure()) / 2) {
                sb.append("앞쪽 중심, ");
            } else {
                sb.append("뒤쪽 중심, ");
            }
        }
        
        if (innerOuterDiff > INNER_OUTER_BALANCE_THRESHOLD) {
            if ((leftAnalysis.getOuterPressure() + rightAnalysis.getOuterPressure()) / 2 > 
                (leftAnalysis.getInnerPressure() + rightAnalysis.getInnerPressure()) / 2) {
                sb.append("바깥쪽 기울어짐, ");
            } else {
                sb.append("안쪽 기울어짐, ");
            }
        }
        
        if (sb.length() == "양발 종합 분석: ".length()) {
            sb.append("전반적으로 균형잡힌 자세입니다.");
        } else {
            sb.append("위의 피드백을 참고하여 자세를 개선해주세요.");
        }
        
        return sb.toString();
    }

    /**
     * 총 압력 계산
     */
    private float getTotalPressure(FSRDataDTO data) {
        if (data == null) return 0;
        return data.getRatio1() + data.getRatio2() + data.getRatio3() + 
               data.getRatio4() + data.getRatio5() + data.getRatio6();
    }
}

