# Amped

**Your on-device AR circuit coach.** Amped uses a custom YOLO object detector and Google's Gemma LLM — both running entirely on-device — to guide beginners through building real Raspberry Pi breadboard circuits, step by step.

Point your phone camera at your workspace and Amped will recognize your components, tell you what to do next, and draw smooth AR overlays showing how everything connects.

---

## Features

- **Real-time component detection** — A custom-trained YOLOv8 TFLite model (`circuit_detector.tflite`) identifies breadboards, Raspberry Pis, GPIO breakouts, LEDs, resistors, ribbon cables, and more at ~10+ FPS on-device.

- **On-device Gemma coaching** — Gemma 4 E2B runs locally via LiteRT-LM with NPU-first acceleration (falls back to GPU/CPU). A structured prompt pipeline feeds YOLO detections + blueprint context to Gemma, which responds with short, actionable coaching messages.

- **Smooth AR overlay** — Detected components are tracked with exponential smoothing (lerp) so AR boxes glide instead of flickering. Dashed bezier connection lines are drawn between components required for the current step.

- **Step-by-step blueprints** — JSON-defined circuit projects (e.g. "Blink an LED") break builds into 7 guided steps, each with required-visible components, breadboard hole highlights, and resistor value checks.

- **Resistor color reader** — A classical-CV heuristic decodes resistor color bands to verify the user has the right value (e.g. 330 ohm).

- **Benchmark mode** — A dedicated screen for measuring YOLO latency/FPS, Gemma inference time, and memory usage.

- **Celebration screen** — Confetti animation with build stats when the user completes a circuit.

- **100% offline** — No cloud APIs, no internet required. Everything runs on the phone.

---

## Architecture

```
Camera Frame
    |
    v
FrameAnalyzer (CameraX ImageAnalysis)
    |
    v
ObjectDetector (TFLite, 640x640 letterbox)
    |
    v
YoloPostProcessor (NMS, per-class thresholds)
    |
    +---> liveDetections (StateFlow) ---> YOLO Overlay (CoachDetectionOverlay)
    |                                 \--> AR Overlay (ArOverlay, smoothed)
    |
    +---> SceneState (temporal merge, 5-frame window)
              |
              v
         StepEngine (blueprint step + detections --> status)
              |
              v
         CoachViewModel
              |
              +---> PromptBuilder.buildUserPrompt(...)
              |         |
              |         v
              |    GemmaReasoningEngine.sendMessage(prompt)
              |         |
              |         v
              |    Streaming coach response
              |
              +---> CoachPanel UI (instructions, status, controls)
```

---

## Project Structure

```
app/
  src/main/
    java/com/npusensei/app/
      MainActivity.kt              # Single-activity Compose app, navigation routing
      NpuSenseiApplication.kt      # App singleton: ObjectDetector, Gemma engine, blueprints
      GemmaReasoningEngine.kt      # LiteRT-LM Engine/Conversation wrapper (NPU/GPU/CPU)
      GemmaModelConfig.kt          # Model file paths and backend configs
      ml/
        ObjectDetector.kt          # TFLite interpreter, letterbox, float buffer
        YoloPostProcessor.kt       # YOLO output parsing, NMS, box mapping
        DetectionResult.kt         # Detection, SceneState, CircuitClasses
        ResistorColorReader.kt     # Color-band heuristic for resistor values
      camera/
        FrameAnalyzer.kt           # CameraX analyzer: detect + publish flows
        CameraController.kt        # Binds Preview + ImageAnalysis to lifecycle
      circuit/
        CircuitBlueprint.kt        # Blueprint/step data classes (kotlinx.serialization)
        BlueprintRepository.kt     # Loads blueprint JSON from assets
        StepEngine.kt              # Evaluates step completion from SceneState
        BreadboardMapper.kt        # Maps logical row/col to pixel coordinates
      viewmodel/
        CoachViewModel.kt          # Orchestrates detection, step eval, Gemma coaching
      gemma/
        PromptBuilder.kt           # System prompt + structured user prompt builder
      ui/
        home/CoachHomeScreen.kt    # Blueprint selection screen
        camera/
          CoachCameraScreen.kt     # Camera preview + overlays + controls
          CoachDetectionOverlay.kt # Raw YOLO detection box overlay
          ArOverlay.kt             # Smooth AR overlay with connection lines
        coach/CoachPanel.kt        # Bottom sheet: instructions, coach text, nav
        benchmark/BenchmarkScreen.kt # YOLO + Gemma performance metrics
        theme/                     # Material3 color, type, theme
    assets/
      models/circuit_detector.tflite   # Custom YOLOv8 circuit detector (9 classes)
      blueprints/pi_led_blink.json     # "Blink an LED" guided project
    res/
      mipmap-*/                        # App launcher icons
      values/strings.xml               # App name: "Amped"
  icon/
    logo-no-text.png                   # App icon source
    logo-text.png                      # Splash screen logos
    logo-text-only-2.png               # Home screen header
```

---

## Prerequisites

- **Android phone** with arm64 (arm64-v8a). Tested on Samsung Galaxy S25 Ultra.
- **Android 12+** (API 31+)
- **Gemma model file**: `gemma-4-E2B-it_qualcomm_sm8750.litertlm` (~3 GB) — must be pushed to the device separately (not bundled in the APK).

---

## Setup

### 1. Clone

```bash
git clone https://github.com/wishruth/Google-Hackathon.git
cd Google-Hackathon
```

### 2. Build

```bash
./gradlew assembleRelease
```

The signed release APK will be at `app/build/outputs/apk/release/app-release.apk`.

### 3. Install

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### 4. Push the Gemma model

Download the Gemma 4 E2B LiteRT-LM model from [HuggingFace](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/) and push it to the device:

```bash
adb shell mkdir -p /sdcard/Android/data/com.npusensei.app/files/
adb push gemma-4-E2B-it_qualcomm_sm8750.litertlm /sdcard/Android/data/com.npusensei.app/files/
```

### 5. Launch

Open **Amped** from your app drawer. The splash screen will show while Gemma initializes on the NPU.

---

## Detected Components (YOLO Classes)

| Index | Class | Description |
|-------|-------|-------------|
| 0 | `gpio_breakout` | T-type GPIO cobbler |
| 1 | `completed_circuit` | Lit circuit (legacy) |
| 2 | `blue_cable` | Blue jumper cable |
| 3 | `breadboard` | Half-size breadboard |
| 4 | `red_led` | Red LED |
| 5 | `raspberry_pi` | Raspberry Pi board |
| 6 | `red_wire` | Red jumper wire |
| 7 | `resistor` | Resistor (with color band reader) |
| 8 | `ribbon_cable` | 40-pin ribbon cable |

---

## How It Works

1. **Choose a project** from the home screen (e.g. "Raspberry Pi — Blink an LED").

2. **Point your camera** at your workspace. YOLO detects components in real time.

3. **Follow the steps.** Each step tells you what to do and highlights where components should go on the breadboard. The AR overlay draws smooth boxes around required components and connects them with dashed lines.

4. **Ask for help.** Tap the refresh button to get Gemma's assessment of your current progress, or open the chat to ask a specific question. Tap "Grade" to have Gemma evaluate if your current step is complete.

5. **Complete the circuit.** On the final step, tap "It lit up!" when your LED lights to trigger the celebration screen.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX (camera2 backend) |
| Object Detection | YOLOv8 via TFLite (custom trained) |
| LLM | Gemma 4 E2B via LiteRT-LM (NPU/GPU/CPU) |
| AR Overlay | Custom Compose Canvas with exponential smoothing |
| Serialization | kotlinx.serialization |
| Permissions | Accompanist Permissions |
| Target | Android 12+ (API 31), arm64-v8a |

---

## Team
Gandharva Naveen, Mohamed Farghally, Shaan Patel, Surya Jagarlamudi, Vishruth Narasimhan
Built for the Google X Qualcomm Hackathon 2026.

---

## License

See repository for license details.
