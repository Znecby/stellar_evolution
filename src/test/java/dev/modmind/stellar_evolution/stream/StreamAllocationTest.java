package dev.modmind.stellar_evolution.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link StreamAllocation} 的 max-min 公平分配口径。 */
class StreamAllocationTest {

    private static final double EPS = 1.0E-9D;

    @Test
    void supplyCoversEveryDemand() {
        assertArrayEquals(new double[] {5.0D, 5.0D},
                StreamAllocation.allocate(10.0D, new double[] {5.0D, 5.0D}), EPS);
    }

    @Test
    void smallDemandIsSatisfiedFirst() {
        assertArrayEquals(new double[] {2.0D, 8.0D},
                StreamAllocation.allocate(10.0D, new double[] {2.0D, 10.0D}), EPS);
    }

    @Test
    void idleConsumerTakesNothing() {
        assertArrayEquals(new double[] {0.0D, 10.0D},
                StreamAllocation.allocate(10.0D, new double[] {0.0D, 10.0D}), EPS);
    }

    @Test
    void scarceSupplyIsSplitEvenly() {
        assertArrayEquals(new double[] {1.0D, 1.0D, 1.0D},
                StreamAllocation.allocate(3.0D, new double[] {10.0D, 10.0D, 10.0D}), EPS);
    }

    @Test
    void leftoverGoesToTheBigConsumer() {
        assertArrayEquals(new double[] {1.0D, 1.0D, 5.0D},
                StreamAllocation.allocate(7.0D, new double[] {1.0D, 1.0D, 10.0D}), EPS);
    }

    @Test
    void noSupplyMeansNoShare() {
        assertArrayEquals(new double[] {0.0D, 0.0D},
                StreamAllocation.allocate(0.0D, new double[] {5.0D, 5.0D}), EPS);
        assertArrayEquals(new double[] {0.0D, 0.0D},
                StreamAllocation.allocate(-3.0D, new double[] {5.0D, 5.0D}), EPS);
    }

    @Test
    void emptyDemandGivesEmptyResult() {
        assertEquals(0, StreamAllocation.allocate(5.0D, new double[0]).length);
    }

    @Test
    void grantNeverExceedsSupply() {
        double[] demands = {3.0D, 7.0D, 1.0D, 40.0D, 0.0D};
        for (double supply : new double[] {0.5D, 4.0D, 11.0D, 51.0D}) {
            double[] granted = StreamAllocation.allocate(supply, demands);
            double total = 0.0D;
            for (int i = 0; i < granted.length; i++) {
                total += granted[i];
                assertTrue(granted[i] <= demands[i] + EPS, "不得超发");
            }
            assertTrue(total <= supply + EPS, "总和不得超过供给");
        }
    }
}
