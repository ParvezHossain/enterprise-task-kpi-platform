package com.parvez.kpi;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.parvez.kpi.service.KpiScoring;
class KpiScoringTest {
    @Test void fixedFormulaHasExactAndRepeatableResults() {
        assertThat(KpiScoring.score(10,6,5,2,3)).isEqualByComparingTo("58.50");
        assertThat(KpiScoring.score(20,20,20,0,4)).isEqualByComparingTo("100.00");
        assertThat(KpiScoring.score(0,0,0,0,0)).isEqualByComparingTo("0.00");
        assertThat(KpiScoring.score(10,0,0,10,0)).isEqualByComparingTo("0.00");
        assertThat(KpiScoring.score(10,6,5,2,3)).isEqualTo(KpiScoring.score(10,6,5,2,3));
    }
    @Test void invalidCountsAndNonFiniteWeightsFail() {
        assertThatThrownBy(()->KpiScoring.score(1,2,0,0,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->KpiScoring.score(1,0,0,0,Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
