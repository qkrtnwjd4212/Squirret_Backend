package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.FSRLatestResponse;
import com.squirret.squirretbackend.handler.FSRWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class FSRDataService {

    private static final long WINDOW_MILLIS = 10_000;

    private final FSRWebSocketHandler fsrWebSocketHandler;

    public FSRDataService(@Lazy FSRWebSocketHandler fsrWebSocketHandler) {
        this.fsrWebSocketHandler = fsrWebSocketHandler;
    }

    private final AtomicReference<FSRDataDTO> latestLeftData = new AtomicReference<>();
    private final AtomicReference<FSRDataDTO> latestRightData = new AtomicReference<>();

    private final Deque<FsrSample> leftHistory = new ArrayDeque<>();
    private final Deque<FsrSample> rightHistory = new ArrayDeque<>();

    public void updateData(FSRDataDTO newData) {
        if (newData == null || newData.getSide() == null) {
            return;
        }

        FSRDataDTO copy = copyOf(newData);
        long now = System.currentTimeMillis();

        if ("left".equalsIgnoreCase(copy.getSide())) {
            latestLeftData.set(copy);
            appendSample(leftHistory, copy, now);
        } else if ("right".equalsIgnoreCase(copy.getSide())) {
            latestRightData.set(copy);
            appendSample(rightHistory, copy, now);
        } else {
            log.warn("알 수 없는 side 값: {}", copy.getSide());
        }

        Map<String, FSRDataDTO> latestData = getLatestInsoleData(true);
        fsrWebSocketHandler.broadcastFSRData(latestData);
        log.debug("FSR 데이터 업데이트 및 웹소켓 브로드캐스트: {}", copy.getSide());
    }

    public Map<String, FSRDataDTO> getLatestInsoleData() {
        return getLatestInsoleData(false);
    }

    public FSRLatestResponse getLatestInsoleDataAsResponse() {
        Map<String, FSRDataDTO> data = getLatestInsoleData(false);
        return new FSRLatestResponse(data.get("left"), data.get("right"));
    }

    public Map<String, FSRDataDTO> getLatestInsoleData(boolean fillEmptyWithZero) {
        Map<String, FSRDataDTO> insoleData = new HashMap<>();

        FSRDataDTO leftData = latestLeftData.get();
        FSRDataDTO rightData = latestRightData.get();

        insoleData.put("left", leftData != null ? leftData : (fillEmptyWithZero ? emptyWithSide("left") : null));
        insoleData.put("right", rightData != null ? rightData : (fillEmptyWithZero ? emptyWithSide("right") : null));

        return insoleData;
    }

    public Map<String, FSRDataDTO> getAveragedInsoleData(Duration window) {
        long windowMillis = window != null ? window.toMillis() : WINDOW_MILLIS;
        long threshold = System.currentTimeMillis() - windowMillis;

        Map<String, FSRDataDTO> averaged = new HashMap<>();
        averaged.put("left", averageHistory(leftHistory, threshold, "left"));
        averaged.put("right", averageHistory(rightHistory, threshold, "right"));
        return averaged;
    }

    private void appendSample(Deque<FsrSample> history, FSRDataDTO data, long timestamp) {
        synchronized (history) {
            history.addLast(new FsrSample(timestamp, copyOf(data)));
            prune(history, timestamp - WINDOW_MILLIS);
        }
    }

    private void prune(Deque<FsrSample> history, long threshold) {
        while (!history.isEmpty()) {
            FsrSample first = history.peekFirst();
            if (first.timestamp < threshold) {
                history.removeFirst();
            } else {
                break;
            }
        }
    }

    private FSRDataDTO averageHistory(Deque<FsrSample> history, long threshold, String side) {
        float sum1 = 0, sum2 = 0, sum3 = 0, sum4 = 0, sum5 = 0, sum6 = 0;
        int count = 0;

        synchronized (history) {
            prune(history, threshold);
            for (FsrSample sample : history) {
                if (sample.timestamp < threshold) continue;
                FSRDataDTO d = sample.data;
                sum1 += d.getRatio1();
                sum2 += d.getRatio2();
                sum3 += d.getRatio3();
                sum4 += d.getRatio4();
                sum5 += d.getRatio5();
                sum6 += d.getRatio6();
                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        FSRDataDTO avg = new FSRDataDTO();
        avg.setSide(side);
        avg.setRatio1(sum1 / count);
        avg.setRatio2(sum2 / count);
        avg.setRatio3(sum3 / count);
        avg.setRatio4(sum4 / count);
        avg.setRatio5(sum5 / count);
        avg.setRatio6(sum6 / count);
        return avg;
    }

    private FSRDataDTO emptyWithSide(String side) {
        FSRDataDTO dto = new FSRDataDTO();
        dto.setSide(side);
        return dto;
    }

    private FSRDataDTO copyOf(FSRDataDTO source) {
        FSRDataDTO copy = new FSRDataDTO();
        copy.setSide(source.getSide());
        copy.setRatio1(source.getRatio1());
        copy.setRatio2(source.getRatio2());
        copy.setRatio3(source.getRatio3());
        copy.setRatio4(source.getRatio4());
        copy.setRatio5(source.getRatio5());
        copy.setRatio6(source.getRatio6());
        return copy;
    }

    private static class FsrSample {
        private final long timestamp;
        private final FSRDataDTO data;

        FsrSample(long timestamp, FSRDataDTO data) {
            this.timestamp = timestamp;
            this.data = data;
        }
    }
}
