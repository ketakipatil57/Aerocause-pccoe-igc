package com.aerocause.service;

import com.aerocause.config.AeroCauseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AqiServiceTest {

    AqiService aqiService;
    AeroCauseProperties props;

    @BeforeEach
    void setUp() {
        props = new AeroCauseProperties();
        // PM25 breakpoints (CPCB-style)
        List<AeroCauseProperties.BreakpointProps> pm25 = List.of(
                bp(0, 30, 0, 50),
                bp(31, 60, 51, 100),
                bp(61, 90, 101, 200),
                bp(91, 120, 201, 300),
                bp(121, 250, 301, 400),
                bp(251, 500, 401, 500)
        );
        List<AeroCauseProperties.BreakpointProps> pm10 = List.of(
                bp(0, 50, 0, 50),
                bp(51, 100, 51, 100),
                bp(101, 250, 101, 200),
                bp(251, 350, 201, 300),
                bp(351, 430, 301, 400),
                bp(431, 600, 401, 500)
        );
        List<AeroCauseProperties.BreakpointProps> no2 = List.of(
                bp(0, 40, 0, 50),
                bp(41, 80, 51, 100),
                bp(81, 180, 101, 200),
                bp(181, 280, 201, 300),
                bp(281, 400, 301, 400),
                bp(401, 800, 401, 500)
        );
        props.getAqi().getBreakpoints().put("PM25", pm25);
        props.getAqi().getBreakpoints().put("PM10", pm10);
        props.getAqi().getBreakpoints().put("NO2", no2);

        aqiService = new AqiService(props);
    }

    // -------------------------------------------------------------------------
    // Category tests
    // -------------------------------------------------------------------------

    @Test
    void category_good() {
        assertThat(aqiService.category(25)).isEqualTo("Good");
        assertThat(aqiService.category(50)).isEqualTo("Good");
    }

    @Test
    void category_satisfactory() {
        assertThat(aqiService.category(51)).isEqualTo("Satisfactory");
        assertThat(aqiService.category(100)).isEqualTo("Satisfactory");
    }

    @Test
    void category_moderate() {
        assertThat(aqiService.category(101)).isEqualTo("Moderate");
        assertThat(aqiService.category(200)).isEqualTo("Moderate");
    }

    @Test
    void category_poor() {
        assertThat(aqiService.category(201)).isEqualTo("Poor");
        assertThat(aqiService.category(300)).isEqualTo("Poor");
    }

    @Test
    void category_veryPoor() {
        assertThat(aqiService.category(301)).isEqualTo("Very Poor");
        assertThat(aqiService.category(400)).isEqualTo("Very Poor");
    }

    @Test
    void category_severe() {
        assertThat(aqiService.category(401)).isEqualTo("Severe");
        assertThat(aqiService.category(500)).isEqualTo("Severe");
    }

    // -------------------------------------------------------------------------
    // Sub-index computation
    // -------------------------------------------------------------------------

    @Test
    void subIndex_PM25_lowerBound() {
        List<AeroCauseProperties.BreakpointProps> bps =
                props.getAqi().getBreakpoints().get("PM25");
        int index = aqiService.computeSubIndex(0, bps);
        assertThat(index).isEqualTo(0);
    }

    @Test
    void subIndex_PM25_midFirstBand() {
        List<AeroCauseProperties.BreakpointProps> bps =
                props.getAqi().getBreakpoints().get("PM25");
        // C=15 in band [0,30] -> I=25
        int index = aqiService.computeSubIndex(15, bps);
        assertThat(index).isCloseTo(25, within(1));
    }

    @Test
    void subIndex_PM25_secondBand() {
        List<AeroCauseProperties.BreakpointProps> bps =
                props.getAqi().getBreakpoints().get("PM25");
        // C=45 in band [31,60] -> proportional between 51 and 100
        int index = aqiService.computeSubIndex(45, bps);
        assertThat(index).isGreaterThan(50).isLessThan(100);
    }

    @Test
    void subIndex_aboveAllBands_capsAtMax() {
        List<AeroCauseProperties.BreakpointProps> bps =
                props.getAqi().getBreakpoints().get("PM25");
        int index = aqiService.computeSubIndex(999, bps);
        assertThat(index).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // Overall AQI
    // -------------------------------------------------------------------------

    @Test
    void computeAqi_dominantPollutant() {
        // PM25=300 gives AQI ~400, PM10=60 gives ~56
        AqiService.AqiResult result = aqiService.computeAqi(Map.of("PM25", 300.0, "PM10", 60.0));
        assertThat(result.dominantPollutant()).isEqualTo("PM25");
        assertThat(result.aqi()).isGreaterThan(300);
    }

    @Test
    void computeAqi_emptyConcentrations() {
        AqiService.AqiResult result = aqiService.computeAqi(Map.of());
        assertThat(result.category()).isEqualTo("No Data");
        assertThat(result.aqi()).isEqualTo(0);
    }

    @Test
    void computeAqi_unknownPollutant_ignored() {
        AqiService.AqiResult result = aqiService.computeAqi(Map.of("UNKNOWN_GAS", 999.0));
        assertThat(result.category()).isEqualTo("No Data");
    }

    @Test
    void computeAqi_ignoresInvalidConcentrationsAndNormalizesPollutant() {
        Map<String, Double> concentrations = new LinkedHashMap<>();
        concentrations.put(" pm25 ", 30.5);
        concentrations.put("PM10", Double.NaN);
        concentrations.put("NO2", null);

        AqiService.AqiResult result = aqiService.computeAqi(concentrations);

        assertThat(result.subIndexes()).containsOnlyKeys("PM25");
        assertThat(result.subIndexes().get("PM25")).isEqualTo(51);
        assertThat(result.dominantPollutant()).isEqualTo("PM25");
    }

    @Test
    void subIndex_negativeOrNonFiniteConcentrationsAreUnavailable() {
        List<AeroCauseProperties.BreakpointProps> bps =
                props.getAqi().getBreakpoints().get("PM25");

        assertThat(aqiService.computeSubIndex(-1, bps)).isEqualTo(-1);
        assertThat(aqiService.computeSubIndex(Double.POSITIVE_INFINITY, bps)).isEqualTo(-1);
        assertThat(aqiService.computeSubIndex(10, null)).isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AeroCauseProperties.BreakpointProps bp(double clow, double chigh,
                                                    double ilow, double ihigh) {
        AeroCauseProperties.BreakpointProps p = new AeroCauseProperties.BreakpointProps();
        p.setClow(clow); p.setChigh(chigh);
        p.setIlow(ilow); p.setIhigh(ihigh);
        return p;
    }
}
