package com.squirret.squirretbackend.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squirret.squirretbackend.dto.DifficultyType;
import com.squirret.squirretbackend.dto.UserFitnessDataDto;
import com.squirret.squirretbackend.dto.WorkoutRecordDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest
@DisplayName("FitnessDataController 통합 테스트")
@AutoConfigureMockMvc(addFilters = false)
public class FitnessDataControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // JSON 직렬화/역직렬화를 위한 객체

    // 테스트에 사용할 샘플 사용자 ID
    private UUID testUserId;
    private UUID testWorkoutId1;
    private UUID testWorkoutId2;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        testWorkoutId1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        testWorkoutId2 = UUID.fromString("00000000-0000-0000-0000-000000000012");
    }

    // create test
    @Test
    @DisplayName("새 피트니스 데이터를 성공적으로 생성해야 한다")
    void shouldCreateFitnessDataSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(
                testWorkoutId1,
                Instant.parse("2024-06-20T10:00:00Z"),
                50, 350.5, DifficultyType.MEDIUM, 30
        );
        UserFitnessDataDto newUserFitnessData = new UserFitnessDataDto(
                testUserId,
                150,
                List.of(workout1)
        );

        // POST 요청 수행
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUserFitnessData)))
                .andExpect(status().isCreated()) // HTTP 201 Created 응답 기대
                .andExpect(content().string(containsString("사용자 " + testUserId + "에 대한 피트니스 데이터가 성공적으로 저장되었습니다!"))); // 응답 본문 검증
    }

    @Test
    @DisplayName("userID가 null인 경우 피트니스 데이터 생성에 실패해야 한다")
    void shouldFailToCreateFitnessDataWhenUserIdIsNull() throws Exception {
        UserFitnessDataDto invalidUserFitnessData = new UserFitnessDataDto(
                null,
                100,
                List.of()
        );

        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUserFitnessData)))
                .andExpect(status().isBadRequest()) // HTTP 400 Bad Request 응답 기대
                .andExpect(content().string(containsString("사용자 ID는 null일 수 없습니다."))); // 응답 본문 검증
    }

    // resd test
    @Test
    @DisplayName("사용자 ID로 피트니스 데이터를 성공적으로 조회해야 한다")
    void shouldGetFitnessDataByUserIdSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of(workout1));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        // GET 요청 수행
        mockMvc.perform(get("/api/fitness/{userId}", testUserId))
                .andExpect(status().isOk()) // HTTP 200 OK 응답 기대
                .andExpect(jsonPath("$.userID").value(testUserId.toString()))
                .andExpect(jsonPath("$.acornCount").value(150))
                .andExpect(jsonPath("$.workoutRecordList[0].id").value(testWorkoutId1.toString()))
                .andExpect(jsonPath("$.workoutRecordList[0].allCount").value(50));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 피트니스 데이터를 조회할 때 404를 반환해야 한다")
    void shouldReturn404WhenGettingNonExistentFitnessData() throws Exception {
        UUID nonExistentUserId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        mockMvc.perform(get("/api/fitness/{userId}", nonExistentUserId))
                .andExpect(status().isNotFound()); // HTTP 404 Not Found 응답을 기대
    }

    @Test
    @DisplayName("특정 운동 기록을 사용자 ID와 운동 ID로 성공적으로 조회해야 한다")
    void shouldGetSpecificWorkoutRecordSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of(workout1));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/fitness/{userId}/workouts/{workoutId}", testUserId, testWorkoutId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testWorkoutId1.toString()))
                .andExpect(jsonPath("$.allCount").value(50))
                .andExpect(jsonPath("$.difficultyType").value("MEDIUM"));
    }

    @Test
    @DisplayName("존재하지 않는 운동 기록을 조회할 때 404를 반환해야 한다")
    void shouldReturn404WhenGettingNonExistentWorkoutRecord() throws Exception {
        // 먼저 사용자 데이터만 저장
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of());
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        UUID nonExistentWorkoutId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        mockMvc.perform(get("/api/fitness/{userId}/workouts/{workoutId}", testUserId, nonExistentWorkoutId))
                .andExpect(status().isNotFound());
    }


    // update test
    @Test
    @DisplayName("사용자 ID로 피트니스 데이터를 성공적으로 업데이트해야 한다")
    void shouldUpdateFitnessDataByUserIdSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of(workout1));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        WorkoutRecordDto updatedWorkout = new WorkoutRecordDto(testWorkoutId2, Instant.now(), 100, 700.0, DifficultyType.HARD, 60);
        UserFitnessDataDto updatedUserFitnessData = new UserFitnessDataDto(
                testUserId,
                200,
                List.of(updatedWorkout)
        );

        // PUT 요청 수행
        mockMvc.perform(put("/api/fitness/{userId}", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedUserFitnessData)))
                .andExpect(status().isOk()) // HTTP 200 OK 응답 기대
                .andExpect(content().string(containsString("사용자 " + testUserId + "에 대한 피트니스 데이터가 성공적으로 업데이트되었습니다.")));

        mockMvc.perform(get("/api/fitness/{userId}", testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acornCount").value(200))
                .andExpect(jsonPath("$.workoutRecordList[0].id").value(testWorkoutId2.toString()))
                .andExpect(jsonPath("$.workoutRecordList[0].allCount").value(100));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 피트니스 데이터 업데이트 시 404를 반환해야 한다")
    void shouldReturn404WhenUpdatingNonExistentFitnessData() throws Exception {
        UUID nonExistentUserId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        UserFitnessDataDto updatedData = new UserFitnessDataDto(nonExistentUserId, 200, List.of());

        mockMvc.perform(put("/api/fitness/{userId}", nonExistentUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedData)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("사용자를 찾을 수 없습니다.")));
    }

    @Test
    @DisplayName("특정 운동 기록을 성공적으로 업데이트해야 한다")
    void shouldUpdateSpecificWorkoutRecordSuccessfully() throws Exception {
        WorkoutRecordDto originalWorkout = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of(originalWorkout));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        WorkoutRecordDto updatedWorkout = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 60, 400.0, DifficultyType.HARD, 35);

        mockMvc.perform(put("/api/fitness/{userId}/workouts/{workoutId}", testUserId, testWorkoutId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedWorkout)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("운동 기록이 성공적으로 업데이트되었습니다.")));

        // 업데이트 확인
        mockMvc.perform(get("/api/fitness/{userId}/workouts/{workoutId}", testUserId, testWorkoutId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allCount").value(60))
                .andExpect(jsonPath("$.totalCalories").value(400.0))
                .andExpect(jsonPath("$.difficultyType").value("HARD"));
    }

    @Test
    @DisplayName("존재하지 않는 운동 기록 업데이트 시 404를 반환해야 한다")
    void shouldReturn404WhenUpdatingNonExistentWorkoutRecord() throws Exception {
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of());
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        UUID nonExistentWorkoutId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        WorkoutRecordDto updatedWorkout = new WorkoutRecordDto(nonExistentWorkoutId, Instant.now(), 10, 100.0, DifficultyType.EASY, 10);

        mockMvc.perform(put("/api/fitness/{userId}/workouts/{workoutId}", testUserId, nonExistentWorkoutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedWorkout)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("운동 기록 ID " + nonExistentWorkoutId + "를 찾을 수 없습니다.")));
    }


    // delete
    @Test
    @DisplayName("사용자 ID로 피트니스 데이터를 성공적으로 삭제해야 한다")
    void shouldDeleteFitnessDataByUserIdSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of(workout1));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        // DELETE 요청 수행
        mockMvc.perform(delete("/api/fitness/{userId}", testUserId))
                .andExpect(status().isOk()) // HTTP 200 OK 응답을 기대
                ;

        // 삭제 후 조회하여 404 응답 확인
        mockMvc.perform(get("/api/fitness/{userId}", testUserId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 피트니스 데이터 삭제 시 404를 반환해야 한다")
    void shouldReturn404WhenDeletingNonExistentFitnessData() throws Exception {
        UUID nonExistentUserId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        mockMvc.perform(delete("/api/fitness/{userId}", nonExistentUserId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("사용자를 찾을 수 없습니다.")));
    }

    @Test
    @DisplayName("특정 운동 기록을 성공적으로 삭제해야 한다")
    void shouldDeleteSpecificWorkoutRecordSuccessfully() throws Exception {
        WorkoutRecordDto workout1 = new WorkoutRecordDto(testWorkoutId1, Instant.now(), 50, 350.5, DifficultyType.MEDIUM, 30);
        WorkoutRecordDto workout2 = new WorkoutRecordDto(testWorkoutId2, Instant.now().plusSeconds(3600), 60, 400.0, DifficultyType.HARD, 40);
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, Arrays.asList(workout1, workout2));
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        // 특정 운동 기록 삭제
        mockMvc.perform(delete("/api/fitness/{userId}/workouts/{workoutId}", testUserId, testWorkoutId1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("운동 기록이 성공적으로 삭제되었습니다.")));

        // 삭제 후 사용자 데이터를 다시 조회하여 남은 운동 기록 확인
        MvcResult result = mockMvc.perform(get("/api/fitness/{userId}", testUserId))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UserFitnessDataDto retrievedData = objectMapper.readValue(responseBody, UserFitnessDataDto.class);

        // 남은 운동 기록이 하나이고, 그게 workout2인지 확인
        assertThat(retrievedData.getWorkoutRecordList(), hasSize(1));
        assertThat(retrievedData.getWorkoutRecordList().get(0).getId(), is(testWorkoutId2));
    }

    @Test
    @DisplayName("존재하지 않는 운동 기록 삭제 시 404를 반환해야 한다")
    void shouldReturn404WhenDeletingNonExistentWorkoutRecord() throws Exception {
        UserFitnessDataDto initialData = new UserFitnessDataDto(testUserId, 150, List.of()); // 빈 운동 기록
        mockMvc.perform(post("/api/fitness/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialData)))
                .andExpect(status().isCreated());

        UUID nonExistentWorkoutId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        mockMvc.perform(delete("/api/fitness/{userId}/workouts/{workoutId}", testUserId, nonExistentWorkoutId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("운동 기록 ID " + nonExistentWorkoutId + "를 찾을 수 없습니다.")));
    }
}
