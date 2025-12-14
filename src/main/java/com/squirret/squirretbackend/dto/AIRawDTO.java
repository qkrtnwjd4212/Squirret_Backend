package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AI 분석 결과 원시 데이터")
public class AIRawDTO {
    @Schema(description = "허리 상태 (good/bad/null)", nullable = true, example = "good")
    private String lumbar;
    
    @Schema(description = "무릎 상태 (good/bad/null)", nullable = true, example = "bad")
    private String knee;
    
    @Schema(description = "발목 상태 (good/bad/null)", nullable = true, example = "good")
    private String ankle;
}

