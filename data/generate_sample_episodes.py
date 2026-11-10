#!/usr/bin/env python3
"""
AeroCause Synthetic Episode Generator

Generates 5 SYNTHETIC air-quality episodes for a fictional city "Vayu Nagar"
with 4 monitoring stations. All data is clearly marked synthetic.

Episodes:
  1. Dust-like event (PM10 >> PM2.5, high wind from desert direction)
  2. Combustion/traffic-like (PM2.5 + NO2/CO elevated, evening rush)
  3. Biomass-burning-like (PM2.5 regional rise, upwind fire hotspot flag)
  4. Sensor fault (single station spike while neighbours stay normal)
  5. Ambiguous / INCONCLUSIVE (conflicting evidence, weak signals)

NOTE: All values, thresholds, and patterns are SYNTHETIC HEURISTICS for
demonstration purposes only. They are NOT validated against scientific literature
or official monitoring data.
"""

import json
import math
import os
import random
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

random.seed(42)
np.random.seed(42)

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "episodes")

# ---------------------------------------------------------------------------
# Station definitions (imaginary city "Vayu Nagar")
# ---------------------------------------------------------------------------
STATIONS = [
    {"id": "STN-001", "name": "Central Monitor", "lat": 18.5204, "lon": 73.8567},
    {"id": "STN-002", "name": "North Station",   "lat": 18.5500, "lon": 73.8400},
    {"id": "STN-003", "name": "East Station",    "lat": 18.5100, "lon": 73.9000},
    {"id": "STN-004", "name": "South Station",   "lat": 18.4900, "lon": 73.8600},
]

POLLUTANTS = ["PM25", "PM10", "NO2", "SO2", "CO", "O3"]


def make_timestamps(start: datetime, hours: int, interval_minutes: int = 60):
    """Generate a list of hourly timestamps."""
    return [start + timedelta(minutes=i * interval_minutes)
            for i in range(int(hours * 60 / interval_minutes))]


def diurnal_factor(hour: int, pollutant: str) -> float:
    """
    Simple diurnal cycle factor (multiplicative).
    Traffic pollutants peak in morning and evening rush.
    PM peaks slightly mid-morning and after sunset.
    These are illustrative patterns, NOT scientific baselines.
    """
    if pollutant in ("NO2", "CO"):
        # Rush hours ~8am and ~6pm
        morning = math.exp(-0.5 * ((hour - 8) / 2) ** 2)
        evening = math.exp(-0.5 * ((hour - 18) / 2) ** 2)
        return 0.6 + 0.4 * max(morning, evening)
    elif pollutant in ("PM25", "PM10"):
        # Slightly elevated mid-morning and overnight
        return 0.7 + 0.3 * math.exp(-0.5 * ((hour - 10) / 4) ** 2)
    elif pollutant == "O3":
        # Peak afternoon
        return 0.4 + 0.6 * math.exp(-0.5 * ((hour - 14) / 3) ** 2)
    else:
        return 1.0


def add_noise(value: float, noise_pct: float = 0.08) -> float:
    """Add Gaussian noise to a value. Clamp to >= 0."""
    return max(0.0, value * (1 + np.random.normal(0, noise_pct)))


def build_readings(timestamps, station_id: str, pollutant_values: dict) -> list[dict]:
    """Build reading rows for one station."""
    rows = []
    for ts in timestamps:
        hour = ts.hour
        for pollutant, base_value in pollutant_values.items():
            factor = diurnal_factor(hour, pollutant)
            value = add_noise(base_value * factor)
            unit = "ppm" if pollutant == "CO" else "µg/m³"
            rows.append({
                "station_external_id": station_id,
                "pollutant": pollutant,
                "value": round(value, 2),
                "unit": unit,
                "observed_at": ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "source": "SYNTHETIC",
            })
    return rows


def baseline_timestamps(start: datetime, hours: int, days: int = 5):
    """Provide a same-hour baseline for each event hour on preceding days."""
    event_hours = make_timestamps(start, hours)
    return [
        event_time - timedelta(days=day)
        for day in range(days, 0, -1)
        for event_time in event_hours
    ]


def build_weather(timestamps, station_id: str, wind_speed: float,
                  wind_dir_deg: float, blh: float | None = None,
                  wind_jitter: float = 1.0,
                  fire_hotspot: bool | None = False) -> list[dict]:
    """Build weather rows for one station."""
    rows = []
    for ts in timestamps:
        rows.append({
            "station_external_id": station_id,
            "observed_at": ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "wind_speed": round(add_noise(wind_speed, noise_pct=0.15), 2),
            "wind_dir_deg": round((wind_dir_deg + np.random.normal(0, wind_jitter * 5)) % 360, 1),
            "boundary_layer_height": round(add_noise(blh, 0.1), 0) if blh else "",
            "fire_hotspot_upwind": "" if fire_hotspot is None else fire_hotspot,
            "source": "SYNTHETIC",
        })
    return rows


def save_episode(ep_dir: str, readings: list[dict], weather: list[dict]):
    os.makedirs(ep_dir, exist_ok=True)
    pd.DataFrame(readings).to_csv(os.path.join(ep_dir, "readings.csv"), index=False)
    pd.DataFrame(weather).to_csv(os.path.join(ep_dir, "weather.csv"), index=False)
    print(f"  Saved {len(readings)} reading rows and {len(weather)} weather rows to {ep_dir}")


# ---------------------------------------------------------------------------
# Episode 1: Dust-like event
# ---------------------------------------------------------------------------
def generate_episode_01():
    """
    Dust-like pollution event.
    Signature: PM10 >> PM2.5 (ratio < 0.4), high wind speed from NW direction,
    all 4 stations affected (regional). NO2/CO stay near baseline.
    Expected top hypothesis: DUST
    """
    print("Generating Episode 1: Dust-like event...")
    start = datetime(2024, 3, 15, 0, 0, 0, tzinfo=timezone.utc)
    timestamps = make_timestamps(start, hours=6)
    baseline = baseline_timestamps(start, hours=6)
    all_readings, all_weather = [], []

    for stn in STATIONS:
        baseline_values = {"PM25": 25, "PM10": 42, "NO2": 18, "SO2": 6, "CO": 0.4, "O3": 32}
        # PM10 strongly elevated, PM2.5 moderately elevated (low ratio)
        poll_vals = {"PM25": 80, "PM10": 280, "NO2": 32, "SO2": 10, "CO": 0.6, "O3": 55}
        all_readings.extend(build_readings(baseline, stn["id"], baseline_values))
        all_readings.extend(build_readings(timestamps, stn["id"], poll_vals))
        all_weather.extend(build_weather(baseline, stn["id"],
                                         wind_speed=4.0, wind_dir_deg=270, blh=900))
        # High wind from NW (~315 degrees)
        all_weather.extend(build_weather(timestamps, stn["id"],
                                         wind_speed=8.5, wind_dir_deg=315,
                                         blh=1500, wind_jitter=1.5))

    ep_dir = os.path.join(OUTPUT_DIR, "episode_01_dust")
    save_episode(ep_dir, all_readings, all_weather)
    return {
        "id": 1, "name": "Dust Storm Event",
        "description": (
            "Synthetic dust-like event: PM10 strongly elevated relative to PM2.5 "
            "(ratio ~0.29), high NW wind across all 4 stations. "
            "No significant NO2/CO elevation. "
            "SAMPLE DATA (synthetic) — not based on real incident."
        ),
        "synthetic": True,
        "expected_top_hypothesis": "DUST",
        "expected_fault": False,
        "stations": [s["id"] for s in STATIONS],
        "duration_hours": 6,
        "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "readings_file": "episodes/episode_01_dust/readings.csv",
        "weather_file": "episodes/episode_01_dust/weather.csv",
    }


# ---------------------------------------------------------------------------
# Episode 2: Combustion / traffic-like event
# ---------------------------------------------------------------------------
def generate_episode_02():
    """
    Combustion/traffic-like event.
    Signature: PM2.5 and NO2 elevated (ratio PM2.5/PM10 > 0.6),
    CO elevated, low wind speed, event mainly at central + south stations
    during evening hours (not all 4 stations equally — more localised).
    Expected top hypothesis: COMBUSTION_TRAFFIC
    """
    print("Generating Episode 2: Combustion/traffic-like event...")
    start = datetime(2024, 2, 20, 14, 0, 0, tzinfo=timezone.utc)  # 7:30pm IST
    timestamps = make_timestamps(start, hours=6)
    baseline = baseline_timestamps(start, hours=6)
    all_readings, all_weather = [], []

    for i, stn in enumerate(STATIONS):
        if stn["id"] in ("STN-001", "STN-004"):  # central + south more affected
            baseline_values = {"PM25": 35, "PM10": 58, "NO2": 22, "SO2": 7, "CO": 0.5, "O3": 32}
            poll_vals = {"PM25": 145, "PM10": 200, "NO2": 95, "SO2": 18, "CO": 2.8, "O3": 20}
        else:
            baseline_values = {"PM25": 28, "PM10": 46, "NO2": 17, "SO2": 6, "CO": 0.4, "O3": 38}
            poll_vals = {"PM25": 70, "PM10": 100, "NO2": 48, "SO2": 10, "CO": 1.2, "O3": 35}
        all_readings.extend(build_readings(baseline, stn["id"], baseline_values))
        all_readings.extend(build_readings(timestamps, stn["id"], poll_vals))
        all_weather.extend(build_weather(baseline, stn["id"],
                                         wind_speed=3.0, wind_dir_deg=190, blh=900))
        all_weather.extend(build_weather(timestamps, stn["id"],
                                         wind_speed=1.8, wind_dir_deg=200,
                                         blh=400, wind_jitter=2.0))

    ep_dir = os.path.join(OUTPUT_DIR, "episode_02_combustion")
    save_episode(ep_dir, all_readings, all_weather)
    return {
        "id": 2, "name": "Combustion / Traffic Event",
        "description": (
            "Synthetic combustion-like event: PM2.5/PM10 ratio ~0.73 at affected stations, "
            "NO2 and CO elevated, low wind speed, low BLH. "
            "Central and south stations more affected than north/east. "
            "SAMPLE DATA (synthetic)."
        ),
        "synthetic": True,
        "expected_top_hypothesis": "COMBUSTION_TRAFFIC",
        "expected_fault": False,
        "stations": [s["id"] for s in STATIONS],
        "duration_hours": 6,
        "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "readings_file": "episodes/episode_02_combustion/readings.csv",
        "weather_file": "episodes/episode_02_combustion/weather.csv",
    }


# ---------------------------------------------------------------------------
# Episode 3: Biomass-burning-like event
# ---------------------------------------------------------------------------
def generate_episode_03():
    """
    Biomass-burning-like event.
    Signature: PM2.5 elevated regionally across all stations,
    fire_hotspot_upwind=True, moderate-high wind from NE (upwind direction),
    SO2 slightly elevated, NO2 moderately elevated.
    Expected top hypothesis: BIOMASS_BURNING
    """
    print("Generating Episode 3: Biomass-burning-like event...")
    start = datetime(2024, 10, 25, 2, 0, 0, tzinfo=timezone.utc)  # post-harvest season
    timestamps = make_timestamps(start, hours=6)
    baseline = baseline_timestamps(start, hours=6)
    all_readings, all_weather = [], []

    for stn in STATIONS:
        baseline_values = {"PM25": 32, "PM10": 48, "NO2": 20, "SO2": 7, "CO": 0.5, "O3": 30}
        poll_vals = {"PM25": 180, "PM10": 230, "NO2": 55, "SO2": 28, "CO": 1.9, "O3": 25}
        all_readings.extend(build_readings(baseline, stn["id"], baseline_values))
        all_readings.extend(build_readings(timestamps, stn["id"], poll_vals))
        all_weather.extend(build_weather(baseline, stn["id"],
                                         wind_speed=3.0, wind_dir_deg=90, blh=900))
        all_weather.extend(build_weather(timestamps, stn["id"],
                                         wind_speed=4.2, wind_dir_deg=45,  # NE
                                         blh=800, wind_jitter=1.0,
                                         fire_hotspot=True))

    ep_dir = os.path.join(OUTPUT_DIR, "episode_03_biomass")
    save_episode(ep_dir, all_readings, all_weather)
    return {
        "id": 3, "name": "Biomass Burning Event",
        "description": (
            "Synthetic biomass-burning-like event: elevated PM2.5 across all stations, "
            "fire hotspot flag set for upwind direction (NE), moderate wind. "
            "Post-harvest season timing. "
            "SAMPLE DATA (synthetic)."
        ),
        "synthetic": True,
        "expected_top_hypothesis": "BIOMASS_BURNING",
        "expected_fault": False,
        "stations": [s["id"] for s in STATIONS],
        "duration_hours": 6,
        "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "readings_file": "episodes/episode_03_biomass/readings.csv",
        "weather_file": "episodes/episode_03_biomass/weather.csv",
    }


# ---------------------------------------------------------------------------
# Episode 4: Sensor fault
# ---------------------------------------------------------------------------
def generate_episode_04():
    """
    Sensor fault at STN-001 (flatline then spike).
    STN-002, STN-003, STN-004 stay at normal background levels.
    Expected top hypothesis: SENSOR_FAULT
    """
    print("Generating Episode 4: Sensor fault...")
    start = datetime(2024, 5, 10, 6, 0, 0, tzinfo=timezone.utc)
    timestamps = make_timestamps(start, hours=8)
    baseline = baseline_timestamps(start, hours=8)
    all_readings, all_weather = [], []

    for stn in STATIONS:
        if stn["id"] == "STN-001":
            baseline_values = {"PM25": 34, "PM10": 54, "NO2": 20, "SO2": 6, "CO": 0.45, "O3": 35}
            all_readings.extend(build_readings(baseline, stn["id"], baseline_values))
            # First 6 hours: flatline at exactly 42.0 (no noise — suspicious)
            # Next 6 hours: extreme spike then flatline at 0
            rows = []
            for i, ts in enumerate(timestamps):
                hour = ts.hour
                for pollutant in POLLUTANTS:
                    if i < 6:
                        value = 42.0  # exact flatline — no variation
                    elif i < 9:
                        value = 998.0 if pollutant == "PM25" else 42.0  # extreme spike
                    else:
                        value = 0.0   # drops to zero (sensor died)
                    unit = "ppm" if pollutant == "CO" else "µg/m³"
                    rows.append({
                        "station_external_id": stn["id"],
                        "pollutant": pollutant,
                        "value": value,
                        "unit": unit,
                        "observed_at": ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
                        "source": "SYNTHETIC",
                    })
            all_readings.extend(rows)
        else:
            # Normal background at other stations
            poll_vals = {"PM25": 35, "PM10": 55, "NO2": 28, "SO2": 8, "CO": 0.5, "O3": 60}
            all_readings.extend(build_readings(baseline, stn["id"], poll_vals))
            all_readings.extend(build_readings(timestamps, stn["id"], poll_vals))

        all_weather.extend(build_weather(baseline, stn["id"],
                                         wind_speed=3.0, wind_dir_deg=180, blh=1200))
        all_weather.extend(build_weather(timestamps, stn["id"],
                                         wind_speed=3.0, wind_dir_deg=180,
                                         blh=1200))

    ep_dir = os.path.join(OUTPUT_DIR, "episode_04_sensor_fault")
    save_episode(ep_dir, all_readings, all_weather)
    return {
        "id": 4, "name": "Sensor Fault (STN-001)",
        "description": (
            "Synthetic sensor fault: STN-001 flatlines for 6 hours, then shows an "
            "extreme spike (PM2.5=998), then drops to zero. "
            "Neighbouring stations remain at normal background levels. "
            "QC rules: FLATLINE + ISOLATED_SPIKE + OUT_OF_RANGE. "
            "SAMPLE DATA (synthetic)."
        ),
        "synthetic": True,
        "expected_top_hypothesis": "SENSOR_FAULT",
        "expected_fault": True,
        "stations": [s["id"] for s in STATIONS],
        "duration_hours": 8,
        "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "readings_file": "episodes/episode_04_sensor_fault/readings.csv",
        "weather_file": "episodes/episode_04_sensor_fault/weather.csv",
    }


# ---------------------------------------------------------------------------
# Episode 5: Ambiguous / INCONCLUSIVE
# ---------------------------------------------------------------------------
def generate_episode_05():
    """
    Ambiguous episode with conflicting evidence.
    Signals: PM2.5 moderately elevated (not dramatic), PM10 also elevated,
    NO2 slightly up, SO2 slightly up, low wind BUT fire hotspot provider is OFF
    so biomass cannot be confirmed. Only 2 stations affected.
    Margin between top hypotheses is small -> INCONCLUSIVE.
    Expected top hypothesis: INCONCLUSIVE
    """
    print("Generating Episode 5: Ambiguous / INCONCLUSIVE...")
    start = datetime(2024, 7, 8, 10, 0, 0, tzinfo=timezone.utc)
    timestamps = make_timestamps(start, hours=6)
    baseline = baseline_timestamps(start, hours=6)
    all_readings, all_weather = [], []

    for stn in STATIONS:
        if stn["id"] in ("STN-001", "STN-002"):
            baseline_values = {"PM25": 35, "PM10": 55, "NO2": 21, "SO2": 7, "CO": 0.5, "O3": 38}
            # Moderate mixed signals
            poll_vals = {
                "PM25": 95,   # elevated but not extreme
                "PM10": 140,  # PM2.5/PM10 ratio = 0.68 -> could be combustion
                "NO2": 55,    # slightly elevated -> combustion signal
                "SO2": 22,    # slightly elevated -> industrial signal
                "CO": 1.4,    # moderate
                "O3": 30,
            }
        else:
            # Other stations near background
            baseline_values = {"PM25": 32, "PM10": 50, "NO2": 19, "SO2": 6, "CO": 0.45, "O3": 40}
            poll_vals = {"PM25": 42, "PM10": 65, "NO2": 30, "SO2": 10, "CO": 0.7, "O3": 55}
        all_readings.extend(build_readings(baseline, stn["id"], baseline_values))
        all_readings.extend(build_readings(timestamps, stn["id"], poll_vals))
        all_weather.extend(build_weather(baseline, stn["id"],
                                         wind_speed=3.0, wind_dir_deg=270, blh=900,
                                         fire_hotspot=None))
        # Low wind — could be stagnant weather
        all_weather.extend(build_weather(timestamps, stn["id"],
                                         wind_speed=1.2, wind_dir_deg=270,
                                         blh=350, wind_jitter=3.0,
                                         fire_hotspot=None))

    ep_dir = os.path.join(OUTPUT_DIR, "episode_05_inconclusive")
    save_episode(ep_dir, all_readings, all_weather)
    return {
        "id": 5, "name": "Ambiguous Episode (INCONCLUSIVE)",
        "description": (
            "Synthetic ambiguous episode: moderate PM2.5+PM10, slightly elevated NO2 and SO2, "
            "low wind speed, only 2 of 4 stations affected. "
            "No fire hotspot data. Evidence conflicts between COMBUSTION_TRAFFIC, "
            "STAGNANT_WEATHER, and INDUSTRIAL. "
            "Margin between top two hypotheses is small -> expected INCONCLUSIVE. "
            "SAMPLE DATA (synthetic)."
        ),
        "synthetic": True,
        "expected_top_hypothesis": "INCONCLUSIVE",
        "expected_fault": False,
        "stations": [s["id"] for s in STATIONS],
        "duration_hours": 6,
        "start_time": start.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "readings_file": "episodes/episode_05_inconclusive/readings.csv",
        "weather_file": "episodes/episode_05_inconclusive/weather.csv",
    }


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    episodes = [
        generate_episode_01(),
        generate_episode_02(),
        generate_episode_03(),
        generate_episode_04(),
        generate_episode_05(),
    ]

    metadata = {
        "data_notice": "SAMPLE DATA (synthetic) — All values are artificially generated for demonstration purposes. Not based on real monitoring data.",
        "city": "Vayu Nagar (imaginary)",
        "stations": STATIONS,
        "episodes": episodes,
    }

    meta_path = os.path.join(os.path.dirname(__file__), "episodes_metadata.json")
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"\nGenerated {len(episodes)} episodes.")
    print(f"   Metadata: {meta_path}")
    print(f"   Episode data: {OUTPUT_DIR}/")
    print("\nAll data is SAMPLE DATA (synthetic). Clearly label it everywhere it is shown.")


if __name__ == "__main__":
    main()
