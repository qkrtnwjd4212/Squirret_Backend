package com.squirret.squirretbackend.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class AiStateStore {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Map<String, String> latest = new HashMap<>(); // keys: lumbar, knee, ankle -> values: good|bad|null

    public void update(String lumbar, String knee, String ankle) {
        lock.writeLock().lock();
        try {
            Map<String, String> m = new HashMap<>();
            if (lumbar != null) m.put("lumbar", lumbar);
            if (knee != null) m.put("knee", knee);
            if (ankle != null) m.put("ankle", ankle);
            latest = m;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, String> snapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(latest));
        } finally {
            lock.readLock().unlock();
        }
    }
}


