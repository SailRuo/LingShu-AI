package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.MemoryStateProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStateProjectorImplTest {

    private final MemoryStateProjector projector = new MemoryStateProjectorImpl();

    @Test
    void computeStateBonus_shouldRewardAlignedVectorAndLowUncertainty() {
        double bonus = projector.computeStateBonus("[1.0,0.0]", 0.10d, new double[]{1.0d, 0.0d}, 0.12d);

        assertThat(bonus).isGreaterThan(0.0d);
    }

    @Test
    void computeStateBonus_shouldReturnZeroForAntiAlignedOrInvalidState() {
        double antiAligned = projector.computeStateBonus("[-1.0,0.0]", 0.10d, new double[]{1.0d, 0.0d}, 0.12d);
        double invalidVector = projector.computeStateBonus("[1.0,0.0,0.0]", 0.10d, new double[]{1.0d, 0.0d}, 0.12d);

        assertThat(antiAligned).isEqualTo(0.0d);
        assertThat(invalidVector).isEqualTo(0.0d);
    }
}
