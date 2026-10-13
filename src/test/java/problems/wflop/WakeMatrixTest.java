package problems.wflop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WakeMatrixTest {

    // Both directions of the sparse matrix hold exactly the nonzero squared deficits of the wake model, in order
    @Test
    void entriesAreTheNonzeroSquaredDeficits() {
        for (WakeMode mode : WakeMode.values()) {
            WindFarmParameters parameters = WindFarmParameters.builder().farmSize(1000).build();   // 5 x 5 cells
            WindFarm farm = new WindFarm(parameters, mode, WindScenario.condition2());
            WakeMatrix m = farm.wakeMatrix();
            List<WindScenario.State> states = farm.wind().states();
            int cells = farm.cellCount();
            assertEquals(cells, m.cells());
            assertEquals(states.size(), m.states());

            int nonzero = 0;
            for (int i = 0; i < cells; i++) {
                int e = m.outBegin(i);
                for (int j = 0; j < cells; j++)
                    for (int s = 0; s < states.size(); s++) {
                        double d = i == j ? 0 : farm.wakeModel().deficit(farm.centerX(j) - farm.centerX(i),
                                farm.centerY(j) - farm.centerY(i), states.get(s).flowX, states.get(s).flowY);
                        if (d == 0)
                            continue;
                        nonzero++;
                        assertTrue(e < m.outEnd(i));
                        assertEquals(j, m.outTarget(e));
                        assertEquals(s, m.outState(e));
                        assertEquals(d * d, m.outSquaredDeficit(e));
                        assertEquals(d * d, incoming(m, i, j, s));
                        e++;
                    }
                assertEquals(m.outEnd(i), e);
            }
            assertEquals(nonzero / ((double) cells * (cells - 1) * states.size()), m.density(), 1e-15);
        }
    }

    private static double incoming(WakeMatrix m, int i, int j, int s) {
        for (int e = m.inBegin(j, s); e < m.inEnd(j, s); e++)
            if (m.inSource(e) == i)
                return m.inSquaredDeficit(e);
        return 0;
    }

    @Test
    void stateWeightsSumToOne() {
        WindFarm farm = WFLOPCase.CASE_1.createDiscrete(WindScenario.condition2()).farm();
        double sum = 0;
        for (int s = 0; s < farm.stateCount(); s++)
            sum += farm.stateWeight(s);
        assertEquals(1, sum, 1e-12);
    }
}
