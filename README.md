# AuthSense – Behavioral Continuous Authentication for Mobile Banking

AuthSense is an AI-powered mobile security system that enhances mobile banking protection through **behavior-based continuous authentication**. Instead of verifying users only during login, AuthSense continuously monitors behavioral patterns such as typing rhythm, touch gestures, and device motion to detect unauthorized access in real time.

The system uses lightweight deep learning models running directly on Android devices to provide secure, privacy-preserving, and energy-efficient authentication without interrupting the user experience.

---

## Problem Statement

Traditional authentication methods such as passwords, PINs, OTPs, and biometrics verify users only once during login. After authentication, sessions remain vulnerable to:

* Session hijacking
* Stolen credentials
* Unauthorized access
* Fraudulent banking activities

Existing systems fail to continuously validate user identity during active sessions.

AuthSense addresses this challenge using continuous behavioral monitoring and AI-based anomaly detection to identify suspicious behavior dynamically and trigger adaptive security responses.

---

# Features

## Continuous Authentication

* Monitors user behavior throughout the session
* Detects anomalies in real time
* Provides dynamic risk scoring

## Behavioral Biometrics

The system analyzes:

* Keystroke dynamics
* Touch gestures
* Swipe patterns
* Accelerometer data
* Gyroscope motion
* Navigation behavior

## AI-Based Anomaly Detection

* Uses an LSTM Autoencoder model
* Learns normal behavioral patterns
* Detects deviations using reconstruction error

## Adaptive Security Response

Depending on risk level:

* Low Risk → Silent monitoring
* Medium Risk → OTP/Biometric re-authentication
* High Risk → Session lock + alerts

## Lightweight Mobile Deployment

* ONNX/TensorFlow Lite optimized model
* Final model size: ~37.9 KB
* Designed for real-time Android execution

## Privacy-Focused

* On-device inference
* Minimal data sharing
* Anonymous risk scoring

---

# System Architecture

## 1. Behavioral Data Collection

The app continuously captures:

* Typing speed and rhythm
* Tap/swipe pressure
* Device tilt and handling
* Motion sensor patterns
* App navigation flow
* Context-aware signals

## 2. Data Preprocessing

The collected sensor data is:

* Segmented into 30-second windows
* Downsampled from 100Hz to 10Hz
* Normalized to a 0–1 range

## 3. Model Training

An LSTM Autoencoder is trained using:

* HMOG dataset
* Touchalytics dataset

The model learns the user’s normal behavior and identifies anomalies using reconstruction loss.

## 4. Real-Time Inference

The trained ONNX/TFLite model runs directly on Android devices and computes:

* Mean Squared Error (MSE)
* Dynamic anomaly score
* Risk classification

## 5. Adaptive Response Engine

Based on anomaly severity:

* Continue monitoring
* Trigger re-authentication
* Lock sensitive operations

---

# Tech Stack

## Mobile Application

* Kotlin
* Android Studio

## Machine Learning

* Python
* TensorFlow
* LSTM Autoencoder
* ONNX Runtime
* TensorFlow Lite

## Sensors & Data

* Accelerometer
* Gyroscope
* Touch interaction APIs

## Datasets

* HMOG Dataset
* Touchalytics Dataset

---

# Machine Learning Workflow

## Training Strategy

### Phase 1 – Generalized Training

The model is initially trained using public datasets containing behavioral patterns from 100+ users.

Behavioral features learned:

* Keystroke latency
* Swipe gestures
* Motion dynamics
* Device handling patterns

### Phase 2 – Personalization

The app collects limited user-specific data (20–30 minutes) and fine-tunes the model locally for personalized authentication.

This improves:

* Accuracy
* Adaptability
* User-specific behavior recognition

---

# Model Details

## LSTM Autoencoder

The model:

* Learns sequential behavioral patterns
* Captures temporal dependencies
* Uses reconstruction error for anomaly detection

### Input Features

* Accelerometer X/Y/Z
* Gyroscope X/Y/Z

### Detection Logic

If reconstruction MSE exceeds threshold:

MSE > 0.005

→ User behavior is flagged as anomalous.

### Performance

* ~89% authentication accuracy
* Lightweight edge deployment
* Real-time inference support

---

# Project Workflow

1. User logs into banking application
2. App continuously monitors behavioral signals
3. Sensor windows are processed every 30 seconds
4. AI model calculates anomaly score
5. Risk level is generated
6. Security response is triggered dynamically

---

# Mobile Application Features

## Real-Time Monitoring

Continuously tracks:

* Motion patterns
* Typing behavior
* Gesture interactions

## Anomaly Alert Screen

When suspicious activity is detected:

* User receives a full-screen warning
* Can secure the account immediately
* Can dismiss false positives

## Background Detection Engine

* Lightweight execution
* Minimal CPU usage
* Battery-efficient adaptive sampling

---

# Performance Optimization

## Battery Efficiency

* Adaptive sensor sampling
* 100Hz → 10Hz downsampling during low-risk states

## Resource Usage

* < 3% CPU usage
* Compact ONNX/TFLite model
* Optimized for mid-range Android devices

---

# Privacy & Security

* On-device behavioral processing
* No raw behavioral data uploaded
* Anonymous risk scoring
* Privacy-preserving architecture

---

# Edge Cases Considered

## Elderly & Differently-Abled Users

* Adjustable sensitivity thresholds
* Support for assistive input methods
* Reduced false positives

## Duress Scenarios

* Hidden panic gestures
* Silent security triggers

---

# Future Enhancements

* Personalized continual learning
* Federated learning integration
* Multi-device behavioral synchronization
* Advanced contextual authentication
* Cloud-assisted threat intelligence

---

# Research Inspiration

The project builds upon recent research in:

* Reinforcement learning-based authentication
* Behavioral biometrics
* Continuous mobile authentication
* Deep learning anomaly detection systems

---

# Installation

## Clone the Repository

```bash
git clone https://github.com/UnisysUIP/2026-AuthSense-An-Intelligent-Adaptive-Continuous-Behavior-Based-Authentication-System-for-Secure
cd AuthSense
```

## Open in Android Studio

* Import the project
* Sync Gradle dependencies
* Run on Android device/emulator

---

# Requirements

## Android

* Android Studio
* Android SDK
* Minimum Android version: Android 8+

## Python Environment

```bash
pip install tensorflow numpy pandas scikit-learn onnxruntime
```

---

# Demo Scenario

### Legitimate User

* Normal behavioral patterns detected
* Low risk score
* Session continues normally

### Unauthorized User

* Behavioral mismatch detected
* High anomaly score
* Security alert triggered

---

# Contributors

* Team AuthSense

---

# License

This project is developed for research and educational purposes.
