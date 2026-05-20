# Jiangnan TT — ECU & OBD_Pro Research Document

## 1. ECU Hardware Identification

| Field | Value |
|---|---|
| **ECU Manufacturer** | WHLD / 武汉菱电 (Wuhan LinControl / Lingdian) |
| **ECU Model** | LEC3A |
| **Part Number** | JNJ7082AF-3610100 |
| **Hardware Code** | 0 103 022 101 (Delphi format) |
| **Serial Number** | SN.DE020535 |
| **Engine Calibration** | ENG-JN-368-S201 |
| **Emissions Standard** | EOBD |

### Vehicle Data (from nameplate photo — verified 2026-05-20)

| Field | Value |
|---|---|
| **Brand** | JIANGNAN-TT (江南TT) |
| **Type** | JNJ7082AF |
| **Engine Type** | JN368QA (based on Suzuki F8B) |
| **Engine Displacement** | 0.796 L (796cc, NOT 368cc — "368" is the engine code) |
| **Power** | 27.5 kW (~37 hp) |
| **Cylinders** | 3 |
| **G/W** | 1000 kg |
| **VIN** | LJ811B5A2D0000616 |
| **Engine No** | 121230509 |
| **Passengers** | 4 |
| **Manufacture Date** | 2013-01-03 |
| **Manufacturer** | Hunan Jiangnan Automobile Manufacture Co., Ltd |

> [!IMPORTANT]
> Despite the Delphi-format part numbers, this is a **Wuhan LinControl LEC3A** unit — a Chinese-market ECU that uses Delphi silicon but proprietary firmware.
> **DiagZone diagnostic scanner connects successfully when selecting "Suzuki"** — confirming protocol compatibility with Suzuki Alto F8B ECU architecture.

---

## 2. Dual-Bus Architecture

The ECU has **two complete diagnostic buses** built into the hardware:

| Bus | ECU Pin | OBD Pin | Protocol | Factory Wiring Status |
|---|---|---|---|---|
| CAN-H | Pin 50 | OBD Pin 6 | ISO 15765-4 | ✅ Connected |
| CAN-L | Pin 32 | OBD Pin 14 | ISO 15765-4 | ❌ **NOT WIRED** |
| K-Line | Pin 55 | OBD Pin 7 | KWP2000 (ISO 14230-4) | ✅ Connected |
| L-Line (Wake) | Pin 51 | OBD Pin 15 | KWP2000 Wake-up | ✅ Connected |
| Ground | — | OBD Pin 4, 5 | — | ✅ Connected |
| +12V Battery | — | OBD Pin 16 | — | ✅ Connected |

### The Factory Wiring Defect

CAN-L (ECU pin 32 → OBD pin 14) was **never connected at the factory**. This means:
- CAN bus communication is **physically impossible** without adding a wire
- The hanging CAN-H signal creates electrical noise that **interferes with K-Line**
- Emissions inspection equipment tries CAN first, fails, and the noise corrupts the K-Line fallback

### Why Our KWP2000 Approach Works

Our ELM327 adapter connects via **K-Line only** (OBD pins 7/15). By using `ATSP 5` (ISO 14230-4 KWP Fast Init), we bypass the broken CAN bus entirely. The ECU responds perfectly to KWP2000 Service `21 01`.

---

## 3. Data Stream Byte Map (Service 21 01 → Response 61 01)

The ECU returns a **64-byte proprietary data block**. Confirmed mappings:

### ✅ Confirmed Sensors (Verified by Physical Tests)

| Byte(s) | Sensor | Formula | Unit | Verification Method |
|---|---|---|---|---|
| 24, 25 | **Engine RPM** | `(d[24]*256) + d[25]` | rpm | Engine on vs off |
| 8 | **Battery Voltage** | `d[8] * 0.1` | V | Matches multimeter |
| 17 | **Vehicle Speed** | `d[17]` | km/h | ❌ Driving test: always 0x00 |
| 9 | **MAP (Manifold Abs Pressure)** | `d[9] / 2.0` | kPa | Engine vacuum vs atmo |
| 10 | **IAT (Intake Air Temp)** | `d[10] - 40` | °C | Stable ~46°C |
| 36 | **Coolant Temperature** | `d[36] - 40` | °C | ⚠️ STATIC 0x93 (107°C) — see §8 |
| 21 | **Throttle Position** | `d[21] * 100.0 / 255.0` | % | Gas pedal test: 0x10→0xDA |

### 🔶 High-Confidence (From Data Analysis)

| Byte(s) | Likely Sensor | Evidence |
|---|---|---|
| 14 | **O2 Sensor Voltage** | Rapidly oscillates when engine running (0x4F→0xB1→0x0D) |
| 54 | **Engine Runtime Counter** | Starts at 0, counts up steadily (0x00→0x04→0x12→0x51) |
| 22 | **TPS Secondary / Raw ADC** | Moves in sync with Byte 21 during throttle test |

### 🔴 Identified But Unmapped (Need More Testing)

| Byte(s) | Observations |
|---|---|
| 12 | Drops when electrical loads are turned on (lights, AC) — likely **alternator load / electrical demand** |
| 33 | Changed during throttle test (0x74→0x82) — possibly **ignition timing advance** |
| 39 | Jumped 0x00→0x20 during WOT — possibly **fuel enrichment flag** |

### Static / Unknown Bytes
Bytes 0-7, 13, 15-16, 18-20, 23, 26, 28, 31, 34-35, 37-38, 40-53, 55-63 showed no significant change during any test.

---

## 4. Sensor Discovery Test Results

### Test: Throttle Position Sensor (Ignition ON, Engine OFF)
```
[Byte 21] : 10 -> DA  (Diff: 202)  ← PRIMARY TPS
[Byte 22] : 01 -> CB  (Diff: 202)  ← TPS Secondary
[Byte 39] : 00 -> 20  (Diff: 32)   ← Fuel enrichment flag?
[Byte 33] : 74 -> 82  (Diff: 14)   ← Ignition timing?
```

### Test: Brake Pedal
```
[Byte 12] : 66 -> 65  (Diff: 1)   ← Minor voltage drop only
[Byte 36] : 58 -> 57  (Diff: 1)   ← Noise
```
> Result: **ECU does NOT monitor brake pedal switch** via this data stream.

### Test: AC Switch
```
[Byte 12] : 65 -> 62  (Diff: 3)   ← Electrical load indicator
```

### Test: Headlights
```
[Byte 12] : 65 -> 61  (Diff: 4)   ← Electrical load indicator
```

### Test: Reverse Gear
```
No bytes changed — ECU does not monitor reverse gear.
```

---

## 5. Bug Fix: False NRC Disconnections

### Problem
The `_check_negative_response()` method used `re.search()` which found `7F` patterns **inside valid 61 01 data bytes**. Example: data bytes `...67 FF 79...` stripped to `...67FF79...` matched as `7F F7 9...` → falsely interpreted as NRC from Service 0xF7.

### Fix Applied
1. Skip NRC check entirely if response contains `6101` (valid positive response header)
2. Changed `re.search()` to `re.match()` — only match `7F` at the **start** of the response

---

## 6. App Improvement Roadmap

### 🟢 Quick Wins (Can Do Now)

| # | Improvement | Details |
|---|---|---|
| 1 | **Expand DTC Database** | Import 3,585 DTC codes from AndrOBD `codes.properties` file into our `COMMON_DTCS` dictionary |
| 2 | **Add Vehicle Info Display** | Show ECU model (LEC3A), calibration (ENG-JN-368-S201), engine (368cc) in sidebar |
| 3 | **Data Logging to CSV** | Record timestamped raw 21 01 hex + decoded values to CSV for drive analysis |
| 4 | **Gauge Range Limits** | Add color thresholds (e.g., coolant > 105°C = red, battery < 11V = red) |

### 🟡 Medium Effort

| # | Improvement | Details |
|---|---|---|
| 5 | **CAN Bus Support** | If user wires CAN-L (ECU pin 32 → OBD pin 14), add `ATSP 6/7/8` CAN protocol mode |
| 6 | **Inspection Helper Mode** | Step-by-step guide to disconnect OBD pin 6 for emissions testing |
| 7 | **Config File for Byte Map** | Move all byte offsets/formulas to a JSON config so users can adjust without editing code |
| 8 | **Freeze Frame on DTC** | When reading DTCs, also request freeze frame data (Service 12) |

### 🔴 Advanced (Future)

| # | Improvement | Details |
|---|---|---|
| 9 | **Full 64-Byte Live Hex View** | A debug tab showing all 64 bytes with color-coded change highlighting |
| 10 | **Service 21 02+ Discovery** | Scan other data blocks (21 02, 21 03, etc.) for additional sensor pages |
| 11 | **Actuator Testing** | KWP2000 Service 30 (InputOutputControlByLocalIdentifier) for relay/injector tests |
| 12 | **ECU Identification** | KWP2000 Service 1A (readEcuIdentification) to auto-detect ECU model |

---

## 7. Source References

- **ECU Label Photo**: LEC3A JNJ7082AF-3610100 EOBD ECU, SN.DE020535
- **Autohome Forum Post**: [江南tt obd年检方案](https://club.autohome.com.cn/bbs/thread/b5b5218adf071566/114912469-1.html) by joker2925 (2026-04-20)
- **AndrOBD Source**: DTC codes database at `library/src/main/java/com/fr3ts0n/ecu/prot/obd/res/codes.properties`
- **Physical Testing**: Sensor discovery wizard results from 2026-05-08
- **Real-World Driving Test**: 2026-05-20, 35 screenshots (idle + driving)

---

## 8. 2026-05-20 Driving Test Findings

### Test Conditions
- Date: 2026-05-20, 07:08-07:20 (35 screenshots captured)
- Vehicle was driven on road (RPM 800-2574, MAP/TPS changing)
- ELM327 v1.5 adapter, Bluetooth SPP, KWP2000 Fast Init

### Speed (Byte 17) NOT AVAILABLE
Byte 17 was always 0x00 in ALL screenshots, including during active driving.
The LEC3A ECU does NOT output vehicle speed through KWP2000 Service 21 01.
The VSS signal goes directly to the instrument cluster, bypassing the ECU.

### Coolant Temperature (Byte 36) SUSPECT MAPPING
Byte 36 was always 0x93 (=147, formula: 147-40=107C) across ALL test conditions:
- Before engine start (ignition ON only): 107C
- After engine start (idle): 107C
- During driving: 107C

A real coolant temp sensor should read ambient (~25-35C) before start, then rise.
Possible causes: wrong byte mapping, broken CTS, or sensor wire fault.
Action needed: cold-start test with Explorer to find the real coolant byte.
