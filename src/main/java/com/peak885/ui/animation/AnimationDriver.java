package com.peak885.ui.animation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

public final class AnimationDriver {

    private final Map<String, Animation> map = new HashMap<>();

    public void put(String id, Animation animation) {
        if (id == null || animation == null) {
            throw new IllegalArgumentException("id and animation must be non-null");
        }
        map.put(id, animation);
    }

    public Animation get(String id) {
        return map.get(id);
    }

    public Animation computeIfAbsent(String id, Supplier<? extends Animation> factory) {
        Animation existing = map.get(id);
        if (existing != null) {
            return existing;
        }
        Animation created = factory.get();
        map.put(id, created);
        return created;
    }

    public boolean remove(String id) {
        return map.remove(id) != null;
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    public void update(float dt) {
        for (Animation a : map.values()) {
            a.update(dt);
        }
    }

    public float value(String id, float fallback) {
        Animation a = map.get(id);
        return a != null ? a.value() : fallback;
    }

    public float value(String id) {
        return value(id, 0f);
    }

    public boolean isFinished(String id) {
        Animation a = map.get(id);
        return a == null || a.isFinished();
    }

    public int pruneFinished() {
        int removed = 0;
        Iterator<Map.Entry<String, Animation>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isFinished()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }
}
