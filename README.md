# AgriKosh AI 🌾
> *Empowering Indian Agriculture through Voice-First Multilingual AI, Localized Intelligence, and Computer Vision.*

---

## 📌 The Problem
Despite rapid digital advancements, millions of smallholder farmers across India face severe barriers to adopting modern agricultural technology:
1. **The Language & Literacy Barrier:** Most ag-tech solutions are text-heavy and predominantly in English or complex Hindi, making them unusable for rural farmers who speak regional dialects (Punjabi, Marathi, Bengali, etc.).
2. **Delayed Disease Diagnostics:** Crop infections spread rapidly. Waiting days for agricultural extension officers often results in catastrophic crop loss before treatment can be administered.
3. **Information Asymmetry:** Farmers struggle to access real-time, localized market pricing (Mandi rates), accurate groundwater depletion warnings, and fragmented government welfare schemes that match their exact land and income criteria.

---

## 🌍 Solving the Social Theme (Impact & Vision)
AgriKosh AI directly addresses sustainable farming, rural economic security, and food security under national development goals:
* **Financial Inclusion & Fair Pricing:** By integrating live AGMARKNET mandi rates, farmers can negotiate better prices, preventing exploitation by local middlemen.
* **Proactive Resource Management:** Groundwater depletion predictions guide farmers toward sustainable crop planning, safeguarding local water tables (especially vital in agrarian states like Punjab and Haryana).
* **Democratizing Government Welfare:** The dynamic government scheme matching engine eliminates bureaucratic hurdles, instantly filtering and pairing eligible farmers with financial subsidies and support programs.
* **Zero-Cost Accessibility:** Built as a lightweight, voice-first companion, it requires zero technical literacy—farmers can simply speak naturally in their native tongue to get answers.

---

## 🛠️ System Architecture & Workflow

```text
[ Farmer Voice / Image Capture ] 
       │
       ├──> (Voice Input) ──> Android SpeechRecognizer ──> FastAPI Backend ──> Dhenu2 8B LLM (Multilingual SLM) ──> TTS & UI Output
       │
       └──> (Leaf Photo) ───> Local TFLite Gatekeeper ──> FastAPI Backend (/predict-disease) ──> PyTorch Multi-Crop Model ──> Actionable Advisory