package com.squirret.squirretbackend.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FSRDataDTO {
    private String side;
    private float voltage1;
    private float voltage2;
    private float voltage3;
    private float voltage4;
    private float voltage5;
    private float voltage6;
    private float ratio1;
    private float ratio2;
    private float ratio3;
    private float ratio4;
    private float ratio5;
    private float ratio6;
    private Long timestamp; // 데이터 업데이트 시간 (밀리초)
}
