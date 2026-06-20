from fastapi import FastAPI, HTTPException, UploadFile, File, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from datetime import datetime, timedelta
import redis
import json
import os
import time
import logging
from typing import List, Optional
from dotenv import load_dotenv

# ==================== LOAD .ENV ====================
load_dotenv()
password = os.getenv("PASSWORD")

# ==================== LOGGING SETUP ====================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ==================== CONFIG ====================
REDIS_URL = "redis://localhost:6379"

# Redis connection
redis_client = redis.Redis.from_url(REDIS_URL, decode_responses=True)

# In-memory medicines cache (loaded on startup)
MEDICINES = []

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
    description: Optional[str]
    is_preset: bool

    class Config:
        from_attributes = True

class RecentSearchResponse(BaseModel):
    id: int
    query: str
    voice_search: bool
    searched_at: datetime

    class Config:
        from_attributes = True

class SearchRequest(BaseModel):
    query: str
    limit: int = 20

class TrackSearchRequest(BaseModel):
    user_id: str
    query: str
    voice_search: bool = False

# Initialize application on startup
@app.on_event("startup")
async def startup():
    global MEDICINES

    try:
        with open("medicines.json", "r", encoding="utf-8") as f:
            MEDICINES = json.load(f)
        logger.info(f"✓ Loaded {len(MEDICINES):,} medicines")
    except Exception as e:
        logger.warning(f"Could not load medicines.json into memory: {e}")

    logger.info("✓ FastAPI server started (v2.0 with caching)")
    logger.info("✓ Redis connected")

# ==================== API ENDPOINTS ====================

@app.get("/health")
async def health_check():
    """Health check endpoint with performance metrics"""
    try:
        redis_start = time.time()
        redis_client.ping()
        redis_time = (time.time() - redis_start) * 1000

        return {
            "status": "healthy",
            "database": "none",
            "redis": "connected",
            "performance": {
                "redis_ms": f"{redis_time:.2f}"
            },
            "timestamp": datetime.utcnow()
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/search")
async def search_medicines(
    query: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=100)
):
    start_time = time.time()

    query = query.lower().strip()

    cache_key = f"search:{query}"

    try:
        cached = redis_client.get(cache_key)

        if cached:
            result = json.loads(cached)
            result["source"] = "redis"
            return result
    except:
        pass

    matches = []

    for idx, medicine in enumerate(MEDICINES):
        if query in medicine.lower():

            matches.append({
                "id": idx,
                "name": medicine,
                "description": "",
                "is_preset": False
            })

            if len(matches) >= limit:
                break

    response = {
        "query": query,
        "count": len(matches),
        "medicines": matches,
        "source": "memory",
        "timing_ms": round((time.time() - start_time) * 1000, 2)
    }

    try:
        redis_client.setex(
            cache_key,
            300,
            json.dumps(response)
        )
    except:
        pass

    return response

@app.get("/api/medicines/presets")
async def get_preset_medicines():
    """Return a small list of preset common medicines (from memory)."""
    try:
        cache_key = "presets:all"

        try:
            cached = redis_client.get(cache_key)
            if cached:
                result = json.loads(cached)
                result["source"] = "cache"
                return result
        except Exception:
            pass

        medicines = [
            {"id": i, "name": name, "description": "", "is_preset": True}
            for i, name in enumerate(PRESET_MEDICINES)
        ]

        result = {"count": len(medicines), "medicines": medicines}

        try:
            redis_client.setex(cache_key, 86400, json.dumps(result))
        except Exception:
            pass

        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

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

@app.get("/api/recent-searches")
async def get_recent_searches(user_id: str = Query(...), limit: int = 10):
    """Get recent searches for a user from Redis list."""
    try:
        cache_key = f"recent_searches:{user_id}"
        try:
            items = redis_client.lrange(cache_key, 0, limit - 1)
            searches = [json.loads(i) for i in items]
        except Exception:
            searches = []

        return {
            "user_id": user_id,
            "count": len(searches),
            "searches": searches
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/track-search")
async def track_search(request: TrackSearchRequest):
    """Track user search by pushing it to Redis list (no Postgres required)."""
    try:
        cache_key = f"recent_searches:{request.user_id}"
        try:
            redis_client.lpush(cache_key, json.dumps({
                "query": request.query,
                "timestamp": datetime.utcnow().isoformat(),
                "voice_search": request.voice_search
            }))
            redis_client.ltrim(cache_key, 0, 99)  # Keep last 100
            redis_client.expire(cache_key, 7 * 24 * 60 * 60)  # 7 days TTL
        except Exception as e:
            logger.warning(f"Failed to write recent search to Redis: {e}")

        return {
            "status": "tracked",
            "user_id": request.user_id,
            "query": request.query,
            "timestamp": datetime.utcnow()
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/user-analytics")
async def get_user_analytics(user_id: str = Query(...)):
    """Get user search analytics from Redis."""
    try:
        user_key = f"user:{user_id}"
        activity = redis_client.hgetall(user_key)

        if not activity:
            return {
                "user_id": user_id,
                "search_count": 0,
                "searches_today": 0,
                "last_search_at": None
            }

        return {
            "user_id": user_id,
            "search_count": int(activity.get("search_count", 0)),
            "searches_today": int(activity.get("searches_today", 0)),
            "last_search_at": activity.get("last_search_at")
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/performance-stats")
async def performance_stats():
    """Get performance statistics (for debugging)"""
    try:
        info = redis_client.info()

        return {
            "redis": {
                "connected": True,
                "used_memory": info.get("used_memory_human", "unknown"),
                "hit_rate": info.get("keyspace_hits", 0),
                "total_commands": info.get("total_commands_processed", 0)
            }
        }
    except Exception as e:
        logger.error(f"Stats error: {e}")
        return {"error": str(e)}

@app.get("/")
async def root():
    """API documentation endpoint"""
    return {
        "name": "Medicine Search API - Optimized v2.0",
        "version": "2.0.0",
        "features": [
            "Redis search result caching (5 min)",
            "Performance timing for each request",
            "Reduced result limit (20 instead of 100)"
        ],
        "endpoints": {
            "health": "GET /health",
            "search": "GET /api/search?query=aspirin&limit=20",
            "presets": "GET /api/medicines/presets",
            "recent": "GET /api/recent-searches?user_id=user123",
            "track": "POST /api/track-search",
            "analytics": "GET /api/user-analytics?user_id=user123",
            "stats": "GET /api/performance-stats"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
