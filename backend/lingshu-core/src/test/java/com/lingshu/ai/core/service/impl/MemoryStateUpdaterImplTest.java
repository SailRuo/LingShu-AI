package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.MemoryStateUpdater;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStateUpdaterImplTest {

    private final MemoryStateUpdater updater = new MemoryStateUpdaterImpl();

    @Test
    void applySupported_shouldInitializeStateFromQueryVectorWhenEmpty() {
        MemoryStateUpdater.MemoryStateDelta delta = updater.applySupported(
                null,
                null,
                null,
                new double[]{0.3d, 0.4d},
                0.90d
        );

        assertThat(delta.updateCount()).isEqualTo(1);
        assertThat(delta.uncertainty()).isLessThan(1.0d);
        assertThat(delta.serializedVector()).isNotBlank();
        assertThat(delta.gain()).isBetween(0.05d, 0.35d);
    }

    @Test
    void applySupported_shouldMergeExistingVectorAndIncreaseUpdateCount() {
        MemoryStateUpdater.MemoryStateDelta delta = updater.applySupported(
                "[1.0,0.0]",
                0.60d,
                3,
                new double[]{0.0d, 1.0d},
                0.85d
        );

        assertThat(delta.updateCount()).isEqualTo(4);
        assertThat(delta.uncertainty()).isLessThan(0.60d);
        assertThat(delta.serializedVector()).contains(",");
        assertThat(delta.serializedVector()).isNotEqualTo("[1.0,0.0]");
    }

    @Test
    void applyUnsupportedUncertainty_shouldOnlyIncreaseUncertaintyWithinCap() {
        double next = updater.applyUnsupportedUncertainty(0.95d, 0.80d);
        double capped = updater.applyUnsupportedUncertainty(1.19d, 1.00d);

        assertThat(next).isGreaterThan(0.95d);
        assertThat(capped).isEqualTo(1.20d);
    }
}
