# AgriKosh AI 🌾

**Empowering Indian Agriculture through Conversational Intelligence, Satellites, and On-Device AI**

AgriKosh AI is a comprehensive, multilingual agricultural intelligence platform built to address critical smallholder farmer challenges in India. Backed by government-grade models, satellite EO data, and edge AI, AgriKosh AI bridges the information gap between agricultural research institutions and marginal farmers.

---

## 🚨 The Problem
Smallholder and marginal farmers across India face severe information bottlenecks:
- **Agronomic Advisory Gaps**: Generic AI assistants(like ChatGPT, Gemini, Grok, etc.) lack localized, domain-specific crop management knowledge.
- **Water Scarcity & Depleting Aquifers**: Uninformed crop selection (such as water-intensive paddy in rapidly depleting groundwater zones) leads to severe economic loss and ecological distress.
- **Crop Disease Devastation**: Unidentified plant diseases spread rapidly before symptoms are diagnosed.
- **Market Price Exploitation**: Lack of real-time mandi price transparency exposes farmers to intermediaries.
- **Low Welfare Scheme Awareness**: Navigating complex Central and State government welfare schemes remains an uphill battle.

---

## 💡 The Solution: AgriKosh AI Ecosystem

### 1. 🤖 Dhenu2 AI Agronomy Chatbot (6B Parameter SLM)
- **Model**: Powered by **Dhenu2 (Llama 3.1 8B / 6B Instruct)**, fine-tuned specifically on Indian agricultural datasets derived from over **1.5 million farmer call center logs**.
- **Expertise**: Comprehensive knowledge across **4,000+ agricultural topics** (crop cycles, pest control, soil health, and market strategies).
- **Multilingual Voice & Text**: Fully supports natural voice interaction and text in **Hindi, Punjabi, Marathi, Bengali, and English**, with automatic session localization.

### 2. 🛰️ Prithvi EO Satellite Groundwater Prediction
- **Technology**: Trained on NASA/IBM **Prithvi EO Satellite Earth Observation data** with an astounding **91.6% accuracy**.
- **Impact**: Predicts water table depths (current vs. 3-6 month forecasts). Advises farmers against planting water-intensive crops (like paddy/rice) during predicted water scarcity periods.

### 3. 🌿 Crop Disease Scanner (Edge & Cloud AI)
- **Capacity**: Detects **387 diseases across 59 crops**.
- **Dual-Layer Architecture**:
  - **On-Device TFLite Gatekeeper**: Instantly validates whether the uploaded image is a valid leaf locally before uploading, preventing wasted compute and bandwidth.
  - **India Multi-Crop Classifier**: Cloud-based computer vision model delivering top predictions with confidence scores and organic/chemical remedial advisories.

### 4. 📈 Live Mandi / Market Prices (AGMARKNET Integration)
- **Source**: Directly interfaces with the **AGMARKNET / data.gov.in** live portal.
- **Functionality**: Provides district- and market-specific live commodity modal prices, price trends, and AI-driven market advisories.

### 5. 🏛️ Offline-First Govt Scheme Matcher (150+ Schemes)
- **Coverage**: Includes over **150+ Central and State government welfare schemes**.
- **Offline Capability**: Runs completely offline using local structured JSON criteria engines, guiding farmers through simple eligibility questions to instantly match eligible financial and subsidy schemes.

### 6. 🌦️ 7-Day Precision Weather Forecast & Irrigation Planner
- **Functionality**: Real-time GPS-localized 7-day weather forecasts, hourly precipitation probability charts, and automated advisories on whether to irrigate crops or withhold pesticide/fertilizer application during expected rainfall.

### 7. 🚜 Hyper-Local Machinery Sharing (Custom Hiring Center Network)
- **Functionality**: Connects smallholders with nearby machinery providers (Tractors, Rotavators, Drones, Harvesters) using GPS coordinates, Haversine distance calculations, and one-tap **Call** & **Google Maps Navigation** actions.

---

## 🛠️ Tech Stack

- **Android Client**: Kotlin, Jetpack Compose / XML Views, Material3, SpeechRecognizer, TextToSpeech, CameraX, TensorFlow Lite.
- **Backend Service**: FastAPI (Python), PyTorch, Timm, Llama-cpp-python, Deep Translator, Requests, Uvicorn.
- **AI / ML Models**: Dhenu2-In-Llama3.1-8B-Instruct (GGUF), Prithvi EO Groundwater Regression Engine, MobileNet TFLite Gatekeeper, India Multi-Crop Plant Disease Classifier.

---
 
## 🔄 Application Workflow

1. **Onboarding & Language Selection**: Farmer selects their preferred regional language (Hindi, Punjabi, Marathi, Bengali, English).
2. **AI Chat**: Voice or text prompts are processed by Dhenu2 with instant regional translation caches.
3. **Disease Detection**: Capture leaf photo -> local TFLite gatekeeper validation -> FastAPI multi-crop analysis -> immediate advisory & Text-to-Speech output.
4. **Market & Groundwater Check**: Select district & crop for live AGMARKNET prices, or scan aquifers for groundwater depletion advisories.
5. **Scheme Matching**: Answer interactive eligibility questions to discover applicable government subsidies.

---

## 🏆 Social Implication & Hackathon Theme
AgriKosh AI was created with a core focus on the **Social Implication of AI**—uplifting marginal and smallholder farmers by democratizing access to cutting-edge satellite intelligence, government schemes, and expert agronomy in their native mother tongue.
