package com.squirret.squirretbackend.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FSRDataDTO {
    private String side;
    private Float voltage1;
    private Float voltage2;
    private Float voltage3;
    private Float voltage4;
    private Float voltage5;
    private Float voltage6;
    private float ratio1;
    private float ratio2;
    private float ratio3;
    private float ratio4;
    private float ratio5;
    private float ratio6;
}
