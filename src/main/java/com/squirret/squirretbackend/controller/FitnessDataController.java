package com.squirret.squirretbackend.controller;
import com.squirret.squirretbackend.dto.UserFitnessDataDto;
import com.squirret.squirretbackend.dto.WorkoutRecordDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fitness") // 이 컨트롤러의 엔드포인트에 대한 기본 경로
public class FitnessDataController {
    // 테스트용 인메모리 저장소
    private final Map<UUID, UserFitnessDataDto> userFitnessDataStore = new ConcurrentHashMap<>();

    public FitnessDataController() {
        // 테스트 데이터
        UUID sampleUserId1 = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
        UUID sampleWorkoutId1 = UUID.fromString("1a2b3c4d-5e6f-7890-1234-567890abc001");
        UUID sampleWorkoutId2 = UUID.fromString("1a2b3c4d-5e6f-7890-1234-567890abc002");

        UserFitnessDataDto initialData = new UserFitnessDataDto(
                sampleUserId1,
                100,
                List.of(
                        new WorkoutRecordDto(sampleWorkoutId1, Instant.now().minusSeconds(3600), 45, 300.0, com.squirret.squirretbackend.dto.DifficultyType.MEDIUM, 40),
                        new WorkoutRecordDto(sampleWorkoutId2, Instant.now().minusSeconds(7200), 60, 450.0, com.squirret.squirretbackend.dto.DifficultyType.HARD, 50)
                )
        );
        userFitnessDataStore.put(sampleUserId1, initialData);

        System.out.println("ID " + sampleUserId1 + "에 대한 샘플 사용자 데이터로 초기화되었습니다.");
    }

    // CREATE
    @PostMapping("/save")
    public ResponseEntity<String> saveFitnessData(@RequestBody UserFitnessDataDto userFitnessDataDto) {
        if (userFitnessDataDto.getUserID() == null) {
            return ResponseEntity.badRequest().body("사용자 ID는 null일 수 없습니다.");
        }
        userFitnessDataStore.put(userFitnessDataDto.getUserID(), userFitnessDataDto);
        System.out.println("수신된 사용자 피트니스 데이터: " + userFitnessDataDto);

        // 성공 응답 반환
        return ResponseEntity.status(HttpStatus.CREATED).body("사용자 " + userFitnessDataDto.getUserID() + "에 대한 피트니스 데이터가 성공적으로 저장되었습니다!");
    }

    // READ
    @GetMapping("/{userId}")
    public ResponseEntity<UserFitnessDataDto> getFitnessDataByUserId(@PathVariable UUID userId) {
        UserFitnessDataDto data = userFitnessDataStore.get(userId);
        if (data != null) {
            System.out.println("사용자 " + userId + "의 피트니스 데이터를 검색했습니다.");
            return ResponseEntity.ok(data);
        } else {
            System.out.println("사용자 " + userId + "의 피트니스 데이터를 찾을 수 없습니다.");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{userId}/workouts/{workoutId}")
    public ResponseEntity<WorkoutRecordDto> getWorkoutRecord(@PathVariable UUID userId, @PathVariable UUID workoutId) {
        UserFitnessDataDto userData = userFitnessDataStore.get(userId);
        if (userData == null) {
            return ResponseEntity.notFound().build(); // 사용자 찾을 수 없음
        }

        Optional<WorkoutRecordDto> workout = userData.getWorkoutRecordList().stream()
                .filter(w -> w.getId().equals(workoutId))
                .findFirst();

        if (workout.isPresent()) {
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 검색했습니다.");
            return ResponseEntity.ok(workout.get());
        } else {
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 찾을 수 없습니다.");
            return ResponseEntity.notFound().build(); // 운동 기록 찾을 수 없음
        }
    }

    // UPDATE
    @PutMapping("/{userId}")
    public ResponseEntity<String> updateFitnessData(@PathVariable UUID userId, @RequestBody UserFitnessDataDto updatedData) {
        if (!userFitnessDataStore.containsKey(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ID " + userId + "인 사용자를 찾을 수 없습니다."); // 사용자가 존재하지 않으면 업데이트할 수 없음
        }
        if (!userId.equals(updatedData.getUserID())) {
            return ResponseEntity.badRequest().body("경로의 사용자 ID (" + userId + ")가 본문의 사용자 ID (" + updatedData.getUserID() + ")와 일치하지 않습니다.");
        }

        userFitnessDataStore.put(userId, updatedData); // 기존 항목을 덮어씁니다.
        System.out.println("사용자 " + userId + "에 대한 피트니스 데이터를 업데이트했습니다. 새 데이터: " + updatedData);
        return ResponseEntity.ok("사용자 " + userId + "에 대한 피트니스 데이터가 성공적으로 업데이트되었습니다.");
    }

    @PutMapping("/{userId}/workouts/{workoutId}")
    public ResponseEntity<String> updateWorkoutRecord(@PathVariable UUID userId, @PathVariable UUID workoutId, @RequestBody WorkoutRecordDto updatedWorkout) {
        UserFitnessDataDto userData = userFitnessDataStore.get(userId);
        if (userData == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ID " + userId + "인 사용자를 찾을 수 없습니다.");
        }
        if (!workoutId.equals(updatedWorkout.getId())) {
            return ResponseEntity.badRequest().body("경로의 운동 ID (" + workoutId + ")가 본문의 운동 ID (" + updatedWorkout.getId() + ")와 일치하지 않습니다.");
        }

        List<WorkoutRecordDto> workoutList = userData.getWorkoutRecordList();
        boolean found = false;
        for (int i = 0; i < workoutList.size(); i++) {
            if (workoutList.get(i).getId().equals(workoutId)) {
                workoutList.set(i, updatedWorkout); // 기존 기록을 업데이트된 기록으로 대체합니다.
                found = true;
                break;
            }
        }

        if (found) {
            userFitnessDataStore.put(userId, userData); // 사용자 맵 항목 업데이트
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 업데이트했습니다.");
            return ResponseEntity.ok("사용자 " + userId + "에 대한 운동 기록이 성공적으로 업데이트되었습니다.");
        } else {
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 찾을 수 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자 " + userId + "에 대한 운동 기록 ID " + workoutId + "를 찾을 수 없습니다.");
        }
    }

    // DELETE
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteFitnessData(@PathVariable UUID userId) {
        UserFitnessDataDto removedData = userFitnessDataStore.remove(userId);
        if (removedData != null) {
            System.out.println("사용자 " + userId + "의 피트니스 데이터를 삭제했습니다.");
            return ResponseEntity.ok("사용자 " + userId + "에 대한 피트니스 데이터가 성공적으로 삭제되었습니다.");
        } else {
            System.out.println("사용자 " + userId + "의 피트니스 데이터를 찾을 수 없습니다. 삭제할 내용이 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ID " + userId + "인 사용자를 찾을 수 없습니다.");
        }
    }

    @DeleteMapping("/{userId}/workouts/{workoutId}")
    public ResponseEntity<String> deleteWorkoutRecord(@PathVariable UUID userId, @PathVariable UUID workoutId) {
        UserFitnessDataDto userData = userFitnessDataStore.get(userId);
        if (userData == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ID " + userId + "인 사용자를 찾을 수 없습니다.");
        }

        List<WorkoutRecordDto> originalList = userData.getWorkoutRecordList();
        List<WorkoutRecordDto> updatedList = originalList.stream()
                .filter(w -> !w.getId().equals(workoutId))
                .collect(Collectors.toList());

        if (updatedList.size() < originalList.size()) {
            userData.setWorkoutRecordList(updatedList); // 사용자 운동 목록 업데이트
            userFitnessDataStore.put(userId, userData); // 사용자 맵 항목 업데이트
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 삭제했습니다.");
            return ResponseEntity.ok("사용자 " + userId + "에 대한 운동 기록이 성공적으로 삭제되었습니다.");
        } else {
            System.out.println("사용자 " + userId + "에 대한 운동 기록 " + workoutId + "를 찾을 수 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자 " + userId + "에 대한 운동 기록 ID " + workoutId + "를 찾을 수 없습니다.");
        }
    }
}
