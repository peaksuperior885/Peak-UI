package com.peak885.ui.animation;

public interface Animation {

    float update(float dt);

    float value();

    boolean isFinished();

    void reset();
}
