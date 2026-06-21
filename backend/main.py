from pathlib import Path
from fastapi import FastAPI, HTTPException, UploadFile, File, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from rapidfuzz import fuzz, process
from datetime import datetime
import json
import time
import logging

# ==================== LOGGING SETUP ====================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ==================== CONFIG ====================

# In-memory medicines cache (loaded on startup)
MEDICINES: list[str] = []
# Precomputed lowercase medicine names (for faster searching)
MEDICINES_LOWER: list[str] = []

# Preset medicines (used for quick UI lists)
PRESET_MEDICINES = [
    "PARACETAMOL",
    "DOLO 650",
    "CETIRIZINE",
    "AZITHROMYCIN",
    "AMOXICILLIN",
    "METFORMIN",
    "OMEPRAZOLE",
    "PANTOPRAZOLE",
    "ATORVASTATIN",
    "AMLODIPINE"
]

PRESET_SET = {m.lower() for m in PRESET_MEDICINES}

# FastAPI app
app = FastAPI(title="Medicine Search API - Optimized", version="2.0.0")

# CORS for Android app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== PYDANTIC MODELS ====================

class MedicineResponse(BaseModel):
    id: int
    name: str
    description: str = ""
    is_preset: bool = False
    confidence: float = 100.0
    match_type: str = "prefix"

class SearchResponse(BaseModel):
    query: str
    count: int
    medicines: list[MedicineResponse]
    source: str
    timing_ms: float

class PresetsResponse(BaseModel):
    count: int
    medicines: list[MedicineResponse]

# Initialize application on startup
@app.on_event("startup")
async def startup():
    global MEDICINES
    global MEDICINES_LOWER

    load_start = time.time()

    data_path = Path("medicines.json")
    if not data_path.exists():
        logger.critical("medicines.json is missing")
        raise RuntimeError("medicines.json is required")

    try:
        with data_path.open("r", encoding="utf-8") as f:
            MEDICINES = json.load(f)

        if not MEDICINES:
            raise RuntimeError("medicines.json is empty")

        # Precompute lowercase names for efficient searching without allocations
        MEDICINES_LOWER = [m.lower() for m in MEDICINES]

        logger.info(
            f"✓ Loaded {len(MEDICINES):,} medicines in {(time.time() - load_start):.2f}s"
        )
    except Exception as e:
        logger.critical(f"Failed to load medicines.json at startup: {e}")
        # Do not allow the API to start with an empty dataset
        raise RuntimeError(f"Missing or unreadable medicines.json: {e}")

    logger.info("✓ FastAPI server started")

# ==================== API ENDPOINTS ====================

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "medicines_loaded": len(MEDICINES),
        "timestamp": datetime.utcnow().isoformat() + "Z"
    }

def find_fuzzy_results(query: str, limit: int = 5, exclude_ids: set[int] = None) -> list[tuple[int, float]]:
    if exclude_ids is None:
        exclude_ids = set()

    if len(query) < 3 or limit <= 0:
        return []

    results = process.extract(
        query,
        MEDICINES_LOWER,
        scorer=fuzz.partial_ratio,
        processor=lambda x: x,
        limit=limit * 3,
        score_cutoff=60
    )

    best_matches: list[tuple[int, float]] = []
    seen = set()

    for choice, score, idx in results:
        if idx in seen or idx in exclude_ids:
            continue
        seen.add(idx)
        best_matches.append((idx, float(score)))
        if len(best_matches) >= limit:
            break

    return best_matches

@app.get("/api/search", response_model=SearchResponse)
async def search_medicines(
    query: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100)
):
    start_time = time.time()

    query = query.lower().strip()
    if not query:
        raise HTTPException(status_code=422, detail="Query cannot be empty")

    medicines = MEDICINES
    meds_lower = MEDICINES_LOWER
    matches: list[MedicineResponse] = []

    def make_result(index: int, confidence: float = 100.0, match_type: str = "prefix") -> MedicineResponse:
        name = medicines[index]
        return MedicineResponse(
            id=index,
            name=name,
            description="",
            is_preset=meds_lower[index] in PRESET_SET,
            confidence=confidence,
            match_type=match_type
        )

    # 1) Collect prefix matches (startswith) first to preserve priority ranking
    for idx, m_lower in enumerate(meds_lower):
        if m_lower.startswith(query):
            matches.append(make_result(idx, confidence=100.0, match_type="prefix"))
            if len(matches) >= limit:
                break

    # 2) If not enough, collect contains matches (but not those already added)
    if len(matches) < limit:
        existing_ids = {item.id for item in matches}
        for idx, m_lower in enumerate(meds_lower):
            if idx in existing_ids:
                continue
            if query in m_lower:
                matches.append(make_result(idx, confidence=95.0, match_type="contains"))
                if len(matches) >= limit:
                    break

    source = "memory"
    if len(matches) < min(3, limit) and len(query) >= 3:
        fuzzy_candidates = find_fuzzy_results(query, limit - len(matches), exclude_ids={item.id for item in matches})
        for idx, score in fuzzy_candidates:
            matches.append(make_result(idx, confidence=score, match_type="fuzzy"))
        if fuzzy_candidates:
            source = "fuzzy"

    elapsed_ms = round((time.time() - start_time) * 1000, 2)
    logger.debug("Search query=%s results=%d source=%s elapsed=%.2fms", query, len(matches), source, elapsed_ms)

    return SearchResponse(
        query=query,
        count=len(matches),
        medicines=matches,
        source=source,
        timing_ms=elapsed_ms
    )

def make_preset_result(index: int) -> MedicineResponse:
    return MedicineResponse(
        id=index,
        name=PRESET_MEDICINES[index],
        description="",
        is_preset=True,
        confidence=100.0,
        match_type="preset"
    )

@app.get("/api/medicines/presets", response_model=PresetsResponse)
async def get_preset_medicines():
    """Return a small list of preset common medicines."""
    medicines = [make_preset_result(i) for i in range(len(PRESET_MEDICINES))]
    return PresetsResponse(count=len(medicines), medicines=medicines)

@app.post("/api/voice-search")
async def voice_search(file: UploadFile = File(...)):
    """
    Process voice search
    Note: In production, integrate Google Cloud Speech API here
    For now, returns placeholder
    """
    try:
        # TODO: Integrate Google Cloud Speech API
        return {
            "status": "voice_received",
            "file_name": file.filename,
            "note": "Integrate Google Cloud Speech API for transcription"
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/")
async def root():
    """API documentation endpoint"""
    return {
        "name": "Medicine Search API - Optimized v2.0",
        "version": "2.0.0",
        "features": [
            "15K+ medicines loaded in RAM",
            "Startswith-priority search ranking",
            "Sub-10ms search responses",
            "Preset medicine lookup"
        ],
        "endpoints": {
            "health": "GET /health",
            "search": "GET /api/search?query=aspirin&limit=20",
            "presets": "GET /api/medicines/presets",
            "voice_search": "POST /api/voice-search"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
