from pathlib import Path
from fastapi import FastAPI, HTTPException, UploadFile, File, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from rapidfuzz import fuzz, process
from datetime import datetime
import json
import time
import logging
import random
from datetime import timedelta, date

from database import engine, SessionLocal, Base
from models import *
from sqlalchemy.orm import joinedload
from typing import Optional

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
    stock_quantity: int = 0
    selling_price: float = 0.0

class SearchResponse(BaseModel):
    query: str
    count: int
    medicines: list[MedicineResponse]
    source: str
    timing_ms: float

class PresetsResponse(BaseModel):
    count: int
    medicines: list[MedicineResponse]

class CorrectionResponse(BaseModel):
    original: str
    corrected: str
    confidence: int

def seed_medicine_master(session, medicines: list[str]) -> int:
    existing_count = session.query(MedicineMaster).count()
    if existing_count > 0:
        logger.info("✓ MedicineMaster already contains %d medicines", existing_count)
        return existing_count

    objects = [MedicineMaster(name=name, manufacturer="", category="") for name in medicines]
    session.bulk_save_objects(objects)
    session.commit()
    logger.info("✓ Seeded %d medicines into MedicineMaster", len(medicines))
    return len(medicines)


def seed_inventory(session) -> int:
    """Seed inventory demo data if inventory table is empty."""
    existing = session.query(Inventory).count()
    if existing > 0:
        logger.info("✓ Inventory already contains %d rows", existing)
        return existing

    ids = [r[0] for r in session.query(MedicineMaster.id).order_by(MedicineMaster.id).all()]
    objects = []
    for mid in ids:
        qty = random.randint(20, 200)
        price = round(random.uniform(5, 500), 2)
        exp = date.today() + timedelta(days=random.randint(90, 365 * 3))
        obj = Inventory(
            medicine_id=mid,
            stock_quantity=qty,
            tablets_per_strip=10,
            selling_price=price,
            expiry_date=exp
        )
        objects.append(obj)

    session.bulk_save_objects(objects)
    session.commit()
    logger.info("✓ Seeded inventory for %d medicines", len(objects))
    return len(objects)


# Initialize application on startup
@app.on_event("startup")
async def startup():
    Base.metadata.create_all(bind=engine)

    global MEDICINES
    global MEDICINES_LOWER

    load_start = time.time()

    data_path = Path("medicines.json")
    if not data_path.exists():
        logger.critical("medicines.json is missing")
        raise RuntimeError("medicines.json is required")

    try:
        with data_path.open("r", encoding="utf-8") as f:
            loaded_medicines = json.load(f)

        if not loaded_medicines:
            raise RuntimeError("medicines.json is empty")

        with SessionLocal() as session:
            seed_medicine_master(session, loaded_medicines)
            # Seed inventory demo data (one row per medicine) if empty
            try:
                seed_inventory(session)
            except Exception as se:
                logger.error("Failed to seed inventory: %s", se)

        MEDICINES = loaded_medicines
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

    with SessionLocal() as session:
        rows = session.query(MedicineMaster.id, MedicineMaster.name).order_by(MedicineMaster.id).all()
        names_lower = [name.lower() for _, name in rows]

    results = process.extract(
        query,
        names_lower,
        scorer=fuzz.partial_ratio,
        processor=lambda x: x,
        limit=limit * 3,
        score_cutoff=60
    )

    best_matches: list[tuple[int, float]] = []
    seen = set()

    for _, score, idx in results:
        row_id = rows[idx][0]
        if row_id in seen or row_id in exclude_ids:
            continue
        seen.add(row_id)
        best_matches.append((row_id, float(score)))
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

    matches: list[MedicineResponse] = []

    def make_result(row: MedicineMaster, confidence: float = 100.0, match_type: str = "prefix") -> MedicineResponse:
        inv = getattr(row, "inventory", None)
        return MedicineResponse(
            id=row.id,
            name=row.name,
            description="",
            is_preset=row.name.lower() in PRESET_SET,
            confidence=confidence,
            match_type=match_type,
            stock_quantity=inv.stock_quantity if inv else 0,
            selling_price=inv.selling_price if inv else 0.0
        )

    with SessionLocal() as session:
        prefix_query = (
            session.query(MedicineMaster)
            .options(joinedload(MedicineMaster.inventory))
            .filter(MedicineMaster.name.ilike(f"{query}%"))
            .order_by(MedicineMaster.id)
            .limit(limit)
            .all()
        )

        for row in prefix_query:
            matches.append(make_result(row, confidence=100.0, match_type="prefix"))

        if len(matches) < limit:
            existing_ids = {item.id for item in matches}
            contains_query = session.query(MedicineMaster).options(joinedload(MedicineMaster.inventory))
            if existing_ids:
                contains_query = contains_query.filter(~MedicineMaster.id.in_(existing_ids))
            contains_query = (
                contains_query
                .filter(MedicineMaster.name.ilike(f"%{query}%"))
                .order_by(MedicineMaster.id)
                .limit(limit - len(matches))
                .all()
            )
            for row in contains_query:
                matches.append(make_result(row, confidence=95.0, match_type="contains"))

    source = "memory"
    if len(matches) < min(3, limit) and len(query) >= 3:
        fuzzy_candidates = find_fuzzy_results(query, limit - len(matches), exclude_ids={item.id for item in matches})
        if fuzzy_candidates:
            with SessionLocal() as session:
                id_to_row = {row.id: row for row in session.query(MedicineMaster).options(joinedload(MedicineMaster.inventory)).filter(MedicineMaster.id.in_([item[0] for item in fuzzy_candidates])).all()}
            for row_id, score in fuzzy_candidates:
                if row_id in id_to_row:
                    matches.append(make_result(id_to_row[row_id], confidence=score, match_type="fuzzy"))
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


class MedicineDetailResponse(BaseModel):
    id: int
    name: str
    manufacturer: str = ""
    category: str = ""
    stock_quantity: int = 0
    tablets_per_strip: int = 10
    selling_price: float = 0.0
    expiry_date: Optional[str] = None


@app.get("/api/medicine/{medicine_id}", response_model=MedicineDetailResponse)
async def get_medicine_detail(medicine_id: int):
    with SessionLocal() as session:
        row = (
            session.query(MedicineMaster)
            .options(joinedload(MedicineMaster.inventory))
            .filter(MedicineMaster.id == medicine_id)
            .first()
        )

        if not row:
            raise HTTPException(status_code=404, detail="Medicine not found")

        inv = row.inventory
        expiry = None
        if inv and inv.expiry_date:
            expiry = inv.expiry_date.isoformat()

        return MedicineDetailResponse(
            id=row.id,
            name=row.name,
            manufacturer=row.manufacturer or "",
            category=row.category or "",
            stock_quantity=inv.stock_quantity if inv else 0,
            tablets_per_strip=inv.tablets_per_strip if inv else 10,
            selling_price=inv.selling_price if inv else 0.0,
            expiry_date=expiry
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

@app.get("/api/correct", response_model=CorrectionResponse)
async def correct_medicine(query: str = Query(..., min_length=1)):
    query_value = query.strip()
    if not query_value:
        raise HTTPException(status_code=422, detail="Query cannot be empty")

    best_match = process.extractOne(
        query_value.lower(),
        MEDICINES_LOWER,
        scorer=fuzz.partial_ratio,
        processor=lambda x: x
    )

    if best_match is None:
        return CorrectionResponse(
            original=query_value,
            corrected=query_value,
            confidence=0
        )

    _, score, idx = best_match
    return CorrectionResponse(
        original=query_value,
        corrected=MEDICINES[idx],
        confidence=int(round(score))
    )

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
            "voice_search": "POST /api/voice-search",
            "correct": "GET /api/correct?query=aspirin"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
