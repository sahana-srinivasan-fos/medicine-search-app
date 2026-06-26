# 💊 RxOra

**RxOra** is a lightweight Android-based pharmacy management system built for pharmacists and small medical stores. It combines fast medicine search, inventory management, and billing into a single application designed for day-to-day pharmacy operations.

The project began as a medicine search application and has gradually evolved into a complete pharmacy point-of-sale (POS) system.

---

## Features

### Fast Medicine Search

* Search through 15,000+ medicines
* Prefix-priority search
* Substring matching
* RapidFuzz-powered typo correction
* Search latency measurement
* Optimized in-memory search

### Inventory Management

* Medicine master catalogue
* Inventory tracking
* Stock quantity management
* Tablet strip tracking
* Selling price management
* Batch information
* Expiry dates

### Medicine Details

* Detailed medicine information
* Current stock availability
* Selling price
* Inventory status

### Cart System

* Add medicines to cart
* Quantity management
* Automatic price calculation
* Running total
* Remove items
* Checkout preparation

### Orders & Billing *(In Progress)*

* Checkout workflow
* Order creation
* Order history
* Invoice generation
* Inventory deduction
* Pharmacy billing workflow

### Voice Search *(Work in Progress)*

Current

* Android SpeechRecognizer

Upcoming

* Offline Whisper.cpp transcription
* Faster and more accurate medicine recognition
* Voice-powered medicine lookup
* Hybrid fuzzy search

---

# Tech Stack

## Android

* Kotlin
* Android SDK
* RecyclerView
* Retrofit
* ViewBinding
* Material Design
* Coroutines

## Backend

* FastAPI
* SQLAlchemy
* SQLite
* Pydantic
* Uvicorn

## AI & Search

* RapidFuzz
* Prefix search
* Substring search
* Offline Whisper.cpp *(under development)*

## Deployment

* Render
* GitHub

---

# Project Structure

```text
RxOra
│
├── frontend/
│   ├── Android App
│   ├── Search
│   ├── Inventory
│   ├── Cart
│   ├── Billing
│   └── Voice Search
│
├── backend/
│   ├── FastAPI
│   ├── Database
│   ├── Inventory APIs
│   ├── Search APIs
│   └── Checkout APIs
│
└── database/
```

---

# Architecture

```text
                   Android App
                        │
                        ▼
              Retrofit HTTP Client
                        │
                        ▼
                  FastAPI Backend
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
   SQLite Database               Medicine Search
                                        │
                              RapidFuzz Correction
```

Future voice pipeline

```text
Microphone
      │
      ▼
Whisper.cpp
      │
      ▼
RapidFuzz
      │
      ▼
Medicine Search
      │
      ▼
Inventory
      │
      ▼
Cart
      │
      ▼
Checkout
```

---

# Database

The backend currently contains the following tables:

* MedicineMaster
* Inventory
* Cart
* CartItem
* Orders
* OrderItems

These tables form the foundation for inventory management, billing, and order history.

---

# API Endpoints

| Method | Endpoint                 | Description                  |
| ------ | ------------------------ | ---------------------------- |
| GET    | `/health`                | Health check                 |
| GET    | `/api/search`            | Search medicines             |
| GET    | `/api/medicines/presets` | Preset medicines             |
| GET    | `/api/medicine/{id}`     | Medicine details             |
| GET    | `/api/correct`           | Medicine correction          |
| POST   | `/api/voice-search`      | Voice search *(placeholder)* |

---

# Current Status

### Completed

* Android frontend
* FastAPI backend
* SQLite integration
* Medicine catalogue
* Inventory models
* Fast medicine search
* Fuzzy medicine correction
* Medicine details
* Cart foundation
* Render deployment
* Dark healthcare UI

### In Progress

* Billing
* Checkout workflow
* Order history
* Invoice generation
* Stock deduction
* Offline Whisper.cpp integration

### Planned

* Barcode scanner
* PDF invoice export
* Customer management
* Analytics dashboard
* Purchase history
* Medicine expiry alerts
* Multi-store support
* User authentication

---

# Screens

* Home
* Search
* Medicine Details
* Cart
* Checkout *(In Progress)*
* Invoice *(In Progress)*
* Order History *(In Progress)*

---

# Running the Project

## Backend

```bash
cd backend

python -m venv .venv

source .venv/bin/activate

pip install -r requirements.txt

uvicorn main:app --reload
```

Backend runs on

```
http://localhost:8000
```

---

## Android

Open the `frontend` folder in Android Studio.

Build and install the application on an Android device or emulator.

---

# Roadmap

* Complete billing workflow
* Inventory CRUD operations
* Finish checkout
* Invoice generation
* Order history
* Offline Whisper.cpp voice search
* Barcode scanner
* Production deployment
* Pharmacy ERP features

---

# License

This project is currently under active development and is intended for educational, research, and demonstration purposes.

---

# Author

**Sahana Srinivasan**

Final Year, IIT Madras BS Degree (Data Science)

Building practical AI-powered healthcare software with a focus on usability, performance, and real-world deployment.
