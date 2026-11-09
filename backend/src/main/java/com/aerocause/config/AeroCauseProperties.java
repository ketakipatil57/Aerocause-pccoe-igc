package com.aerocause.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed configuration for all AeroCause thresholds and weights.
 * All values are HEURISTIC INITIAL VALUES — not scientifically validated.
 * Tune with domain expert guidance before production use.
 */
@Component
@ConfigurationProperties(prefix = "aerocause")
public class AeroCauseProperties {

    private OpenaqProps openaq = new OpenaqProps();
    private OpenMeteoProps openMeteo = new OpenMeteoProps();
    private FirmsProps firms = new FirmsProps();
    private AiServiceProps aiService = new AiServiceProps();
    private IngestionProps ingestion = new IngestionProps();
    private QcProps qc = new QcProps();
    private AnomalyProps anomaly = new AnomalyProps();
    private FingerprintingProps fingerprinting = new FingerprintingProps();
    private AqiProps aqi = new AqiProps();

    // getters/setters
    public OpenaqProps getOpenaq() { return openaq; }
    public void setOpenaq(OpenaqProps v) { openaq = v; }
    public OpenMeteoProps getOpenMeteo() { return openMeteo; }
    public void setOpenMeteo(OpenMeteoProps v) { openMeteo = v; }
    public FirmsProps getFirms() { return firms; }
    public void setFirms(FirmsProps v) { firms = v; }
    public AiServiceProps getAiService() { return aiService; }
    public void setAiService(AiServiceProps v) { aiService = v; }
    public IngestionProps getIngestion() { return ingestion; }
    public void setIngestion(IngestionProps v) { ingestion = v; }
    public QcProps getQc() { return qc; }
    public void setQc(QcProps v) { qc = v; }
    public AnomalyProps getAnomaly() { return anomaly; }
    public void setAnomaly(AnomalyProps v) { anomaly = v; }
    public FingerprintingProps getFingerprinting() { return fingerprinting; }
    public void setFingerprinting(FingerprintingProps v) { fingerprinting = v; }
    public AqiProps getAqi() { return aqi; }
    public void setAqi(AqiProps v) { aqi = v; }

    // ---- nested property classes ----

    public static class OpenaqProps {
        private String baseUrl = "https://api.openaq.org/v3";
        private String apiKey = "";
        private int timeoutSeconds = 10;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { timeoutSeconds = v; }
    }

    public static class OpenMeteoProps {
        private String baseUrl = "https://api.open-meteo.com/v1";
        private int timeoutSeconds = 10;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { timeoutSeconds = v; }
    }

    public static class FirmsProps {
        private boolean enabled = false;
        private String apiKey = "";
        private String baseUrl = "https://firms.modaps.eosdis.nasa.gov/api";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { enabled = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
    }

    public static class AiServiceProps {
        private String baseUrl = "http://localhost:8000";
        private int timeoutSeconds = 30;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { timeoutSeconds = v; }
    }

    public static class IngestionProps {
        private boolean pollingEnabled = false;
        private int pollingIntervalSeconds = 300;
        private String stationIds = "";
        public boolean isPollingEnabled() { return pollingEnabled; }
        public void setPollingEnabled(boolean v) { pollingEnabled = v; }
        public int getPollingIntervalSeconds() { return pollingIntervalSeconds; }
        public void setPollingIntervalSeconds(int v) { pollingIntervalSeconds = v; }
        public String getStationIds() { return stationIds; }
        public void setStationIds(String v) { stationIds = v; }
    }

    public static class QcProps {
        private int flatlineWindow = 5;
        private double flatlineTolerance = 0.001;
        private Map<String, RangeProps> outOfRange = new HashMap<>();
        private int isolatedSpikeMinStations = 2;
        private double isolatedSpikeRatio = 3.0;
        private int staleDataMinutes = 90;

        public int getFlatlineWindow() { return flatlineWindow; }
        public void setFlatlineWindow(int v) { flatlineWindow = v; }
        public double getFlatlineTolerance() { return flatlineTolerance; }
        public void setFlatlineTolerance(double v) { flatlineTolerance = v; }
        public Map<String, RangeProps> getOutOfRange() { return outOfRange; }
        public void setOutOfRange(Map<String, RangeProps> v) { outOfRange = v; }
        public int getIsolatedSpikeMinStations() { return isolatedSpikeMinStations; }
        public void setIsolatedSpikeMinStations(int v) { isolatedSpikeMinStations = v; }
        public double getIsolatedSpikeRatio() { return isolatedSpikeRatio; }
        public void setIsolatedSpikeRatio(double v) { isolatedSpikeRatio = v; }
        public int getStaleDataMinutes() { return staleDataMinutes; }
        public void setStaleDataMinutes(int v) { staleDataMinutes = v; }
    }

    public static class RangeProps {
        private double min = 0;
        private double max = 1000;
        public double getMin() { return min; }
        public void setMin(double v) { min = v; }
        public double getMax() { return max; }
        public void setMax(double v) { max = v; }
    }

    public static class AnomalyProps {
        private int baselineDays = 14;
        private int baselineMinReadings = 5;
        private double robustZThreshold = 3.5;
        private Map<String, Double> minAbsoluteIncrease = new HashMap<>();
        private int consecutiveReadings = 2;

        public int getBaselineDays() { return baselineDays; }
        public void setBaselineDays(int v) { baselineDays = v; }
        public int getBaselineMinReadings() { return baselineMinReadings; }
        public void setBaselineMinReadings(int v) { baselineMinReadings = v; }
        public double getRobustZThreshold() { return robustZThreshold; }
        public void setRobustZThreshold(double v) { robustZThreshold = v; }
        public Map<String, Double> getMinAbsoluteIncrease() { return minAbsoluteIncrease; }
        public void setMinAbsoluteIncrease(Map<String, Double> v) { minAbsoluteIncrease = v; }
        public int getConsecutiveReadings() { return consecutiveReadings; }
        public void setConsecutiveReadings(int v) { consecutiveReadings = v; }
    }

    public static class FingerprintingProps {
        private double inconclusiveMarginThreshold = 0.15;
        private double inconclusiveMissingPenalty = 0.1;
        private RulesProps rules = new RulesProps();

        public double getInconclusiveMarginThreshold() { return inconclusiveMarginThreshold; }
        public void setInconclusiveMarginThreshold(double v) { inconclusiveMarginThreshold = v; }
        public double getInconclusiveMissingPenalty() { return inconclusiveMissingPenalty; }
        public void setInconclusiveMissingPenalty(double v) { inconclusiveMissingPenalty = v; }
        public RulesProps getRules() { return rules; }
        public void setRules(RulesProps v) { rules = v; }
    }

    public static class RulesProps {
        private PmRatioRuleProps pmRatio = new PmRatioRuleProps();
        private No2CoRuleProps no2Co = new No2CoRuleProps();
        private So2RuleProps so2 = new So2RuleProps();
        private WindRuleProps wind = new WindRuleProps();
        private FireHotspotRuleProps fireHotspot = new FireHotspotRuleProps();
        private SensorFaultRuleProps sensorFault = new SensorFaultRuleProps();

        public PmRatioRuleProps getPmRatio() { return pmRatio; }
        public void setPmRatio(PmRatioRuleProps v) { pmRatio = v; }
        public No2CoRuleProps getNo2Co() { return no2Co; }
        public void setNo2Co(No2CoRuleProps v) { no2Co = v; }
        public So2RuleProps getSo2() { return so2; }
        public void setSo2(So2RuleProps v) { so2 = v; }
        public WindRuleProps getWind() { return wind; }
        public void setWind(WindRuleProps v) { wind = v; }
        public FireHotspotRuleProps getFireHotspot() { return fireHotspot; }
        public void setFireHotspot(FireHotspotRuleProps v) { fireHotspot = v; }
        public SensorFaultRuleProps getSensorFault() { return sensorFault; }
        public void setSensorFault(SensorFaultRuleProps v) { sensorFault = v; }
    }

    public static class PmRatioRuleProps {
        private double lowRatioThreshold = 0.45;
        private double highRatioThreshold = 0.60;
        private double dustWeight = 0.30;
        private double combustionWeight = 0.25;
        public double getLowRatioThreshold() { return lowRatioThreshold; }
        public void setLowRatioThreshold(double v) { lowRatioThreshold = v; }
        public double getHighRatioThreshold() { return highRatioThreshold; }
        public void setHighRatioThreshold(double v) { highRatioThreshold = v; }
        public double getDustWeight() { return dustWeight; }
        public void setDustWeight(double v) { dustWeight = v; }
        public double getCombustionWeight() { return combustionWeight; }
        public void setCombustionWeight(double v) { combustionWeight = v; }
    }

    public static class No2CoRuleProps {
        private double no2ThresholdUgm3 = 60.0;
        private double coThresholdPpm = 1.5;
        private double combustionWeight = 0.30;
        public double getNo2ThresholdUgm3() { return no2ThresholdUgm3; }
        public void setNo2ThresholdUgm3(double v) { no2ThresholdUgm3 = v; }
        public double getCoThresholdPpm() { return coThresholdPpm; }
        public void setCoThresholdPpm(double v) { coThresholdPpm = v; }
        public double getCombustionWeight() { return combustionWeight; }
        public void setCombustionWeight(double v) { combustionWeight = v; }
    }

    public static class So2RuleProps {
        private double thresholdUgm3 = 40.0;
        private double industrialWeight = 0.35;
        public double getThresholdUgm3() { return thresholdUgm3; }
        public void setThresholdUgm3(double v) { thresholdUgm3 = v; }
        public double getIndustrialWeight() { return industrialWeight; }
        public void setIndustrialWeight(double v) { industrialWeight = v; }
    }

    public static class WindRuleProps {
        private double lowWindThreshold = 2.0;
        private double stagnantBlhThreshold = 500.0;
        private double stagnantWeight = 0.25;
        private double regionalSpreadWeight = 0.15;
        public double getLowWindThreshold() { return lowWindThreshold; }
        public void setLowWindThreshold(double v) { lowWindThreshold = v; }
        public double getStagnantBlhThreshold() { return stagnantBlhThreshold; }
        public void setStagnantBlhThreshold(double v) { stagnantBlhThreshold = v; }
        public double getStagnantWeight() { return stagnantWeight; }
        public void setStagnantWeight(double v) { stagnantWeight = v; }
        public double getRegionalSpreadWeight() { return regionalSpreadWeight; }
        public void setRegionalSpreadWeight(double v) { regionalSpreadWeight = v; }
    }

    public static class FireHotspotRuleProps {
        private double biomassWeight = 0.40;
        public double getBiomassWeight() { return biomassWeight; }
        public void setBiomassWeight(double v) { biomassWeight = v; }
    }

    public static class SensorFaultRuleProps {
        private double qcFlagWeight = 0.50;
        private double isolationWeight = 0.30;
        public double getQcFlagWeight() { return qcFlagWeight; }
        public void setQcFlagWeight(double v) { qcFlagWeight = v; }
        public double getIsolationWeight() { return isolationWeight; }
        public void setIsolationWeight(double v) { isolationWeight = v; }
    }

    public static class AqiProps {
        private Map<String, List<BreakpointProps>> breakpoints = new HashMap<>();
        public Map<String, List<BreakpointProps>> getBreakpoints() { return breakpoints; }
        public void setBreakpoints(Map<String, List<BreakpointProps>> v) { breakpoints = v; }
    }

    public static class BreakpointProps {
        private double clow;
        private double chigh;
        private double ilow;
        private double ihigh;
        public double getClow() { return clow; }
        public void setClow(double v) { clow = v; }
        public double getChigh() { return chigh; }
        public void setChigh(double v) { chigh = v; }
        public double getIlow() { return ilow; }
        public void setIlow(double v) { ilow = v; }
        public double getIhigh() { return ihigh; }
        public void setIhigh(double v) { ihigh = v; }
    }
}
