from fastapi import FastAPI, HTTPException, UploadFile, File, Query, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, Column, String, Integer, Boolean, DateTime, func, or_
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from pydantic import BaseModel
from datetime import datetime, timedelta
import redis
import json
import os
import time
import logging
from typing import List, Optional

# ==================== LOGGING SETUP ====================
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ==================== CONFIG ====================
DATABASE_URL = "postgresql://med_user:med_pass123@localhost:5432/medicines_db"
REDIS_URL = "redis://localhost:6379"

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# Redis connection
redis_client = redis.Redis.from_url(REDIS_URL, decode_responses=True)

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

# ==================== DATABASE MODELS ====================

class Medicine(Base):
    __tablename__ = "medicines"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), unique=True, index=True)
    category = Column(String(100), index=True)
    description = Column(String(500), nullable=True)
    is_preset = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

class RecentSearch(Base):
    __tablename__ = "recent_searches"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(255), index=True)
    query = Column(String(255))
    category = Column(String(100), nullable=True)
    voice_search = Column(Boolean, default=False)
    searched_at = Column(DateTime, default=datetime.utcnow)

class UserActivity(Base):
    __tablename__ = "user_activity"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(255), unique=True, index=True)
    search_count = Column(Integer, default=0)
    searches_today = Column(Integer, default=0)
    last_search_at = Column(DateTime, default=datetime.utcnow)
    created_at = Column(DateTime, default=datetime.utcnow)

# ==================== PYDANTIC MODELS ====================

class MedicineResponse(BaseModel):
    id: int
    name: str
    category: str
    description: Optional[str]
    is_preset: bool
    
    class Config:
        from_attributes = True

class RecentSearchResponse(BaseModel):
    id: int
    query: str
    category: Optional[str]
    voice_search: bool
    searched_at: datetime
    
    class Config:
        from_attributes = True

class SearchRequest(BaseModel):
    query: str
    category: Optional[str] = None
    limit: int = 20

class CategoryRequest(BaseModel):
    pass

class TrackSearchRequest(BaseModel):
    user_id: str
    query: str
    category: Optional[str] = None
    voice_search: bool = False

# ==================== DATABASE INITIALIZATION ====================

def init_db():
    Base.metadata.create_all(bind=engine)
    
    # Load sample medicines if database is empty
    db = SessionLocal()
    if db.query(Medicine).count() == 0:
        sample_medicines = [
            # Presets (15 common medicines)
            ("Aspirin", "Pills", "Pain reliever", True),
            ("Ibuprofen", "Pills", "Anti-inflammatory", True),
            ("Paracetamol", "Tablets", "Fever reducer", True),
            ("Amoxicillin", "Capsules", "Antibiotic", True),
            ("Cough Syrup", "Syrups", "Cough relief", True),
            ("Antacid", "Tablets", "Heartburn relief", True),
            ("Multivitamin", "Tablets", "Vitamin supplement", True),
            ("Insulin", "Injections", "Diabetes management", True),
            ("Penicillin", "Injections", "Antibiotic injection", True),
            ("Lotion", "Soaps", "Skin care", True),
            ("Shampoo", "Shampoo", "Hair care", True),
            ("Plaster", "Plasters", "Wound covering", True),
            ("Band-Aid", "Bands", "First aid", True),
            ("Protein Powder", "Food", "Nutrition supplement", True),
            ("Horlicks", "Food", "Malt beverage", True),
            
            # Additional medicines for search testing
            ("Cetirizine", "Pills", "Antihistamine"),
            ("Lisinopril", "Tablets", "Blood pressure medication"),
            ("Metformin", "Tablets", "Diabetes medication"),
            ("Omeprazole", "Capsules", "Acid reflux"),
            ("Atorvastatin", "Tablets", "Cholesterol medication"),
            ("Amlodipine", "Tablets", "Blood pressure medication"),
            ("Sertraline", "Tablets", "Antidepressant"),
            ("Levothyroxine", "Tablets", "Thyroid medication"),
            ("Albuterol", "Inhalers", "Asthma medication"),
            ("Fluticasone", "Nasal Spray", "Allergy relief"),
        ]
        
        for name, category, description, *is_preset in sample_medicines:
            medicine = Medicine(
                name=name,
                category=category,
                description=description,
                is_preset=is_preset[0] if is_preset else False
            )
            db.add(medicine)
        
        db.commit()
        logger.info("✓ Sample medicines loaded into database")
    
    db.close()

# Initialize database on startup
@app.on_event("startup")
async def startup():
    init_db()
    logger.info("✓ FastAPI server started (v2.0 with caching)")
    logger.info("✓ PostgreSQL connected")
    logger.info("✓ Redis connected")
    
    # Check database size
    db = SessionLocal()
    count = db.query(func.count(Medicine.id)).scalar()
    logger.info(f"✓ Database contains {count:,} medicines")
    db.close()

# ==================== API ENDPOINTS ====================

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/health")
async def health_check():
    """Health check endpoint with performance metrics"""
    db = SessionLocal()
    try:
        # Check database
        db_start = time.time()
        db.execute("SELECT 1")
        db_time = (time.time() - db_start) * 1000
        
        # Check Redis
        redis_start = time.time()
        redis_client.ping()
        redis_time = (time.time() - redis_start) * 1000
        
        return {
            "status": "healthy",
            "database": "connected",
            "redis": "connected",
            "performance": {
                "database_ms": f"{db_time:.2f}",
                "redis_ms": f"{redis_time:.2f}"
            },
            "timestamp": datetime.utcnow()
        }
    finally:
        db.close()

@app.get("/api/search") ## was a post req.
async def search_medicines(
    query: str = Query(..., min_length=1, description="Medicne name ot search"),
    category: Optional[str] = Query(None, description="Optional category filter"),
    limit: int = Query(20, ge=1, le=100, description="Max results(1-100)"),
    db: Session = Depends(get_db)
):
    """
    Search medicines by name/description with Redis caching
    
    Query Parameters:
    - query: search term (required)
    - category: filter by category (optional)
    - limit: max results (default: 20, max: 100)
    """
    start_time = time.time()
    
    try:
        # Enforce max limit
        limit = min(int(limit), 100)
        
        # Normalize query for cache
        normalized_query = query.lower().strip()
        category_key = category.lower().strip() if category else "all"

        # Generate cache key
        cache_key = f"search:{query.lower().strip()}:{category or 'all'}"
        
        # ===== STEP 1: Check Redis cache =====
        redis_start = time.time()
        try:
            cached_result = redis_client.get(cache_key)
            redis_time = (time.time() - redis_start) * 1000
            
            if cached_result:
                result = json.loads(cached_result)
                result["source"] = "redis_cache"
                result["timing"] = {
                    "redis_ms": f"{redis_time:.2f}",
                    "total_ms": f"{(time.time() - start_time) * 1000:.2f}"
                }
                logger.info(f"CACHE HIT: '{query}' in {redis_time:.2f}ms")
                return result
        except Exception as e:
            logger.warning(f"Redis error: {e}")
        
        # ===== STEP 2: Query PostgreSQL =====
        db_start = time.time()
        
        search_filter = or_(
            Medicine.name.ilike(f"%{query}%"),
            Medicine.description.ilike(f"%{query}%")
        )
        
        medicines = db.query(Medicine).filter(search_filter)
        
        # Apply category filter if provided
        if category:
            medicines = medicines.filter(Medicine.category.ilike(f"%{category}%"))
        
        results = medicines.limit(limit).all()
        db_time = (time.time() - db_start) * 1000
        
        # ===== STEP 3: Build response =====
        medicines_data = [
            {
                 "id": m.id,
                 "name": m.name,
                 "category": m.category,
                 "description": m.description,
                 "is_preset": m.is_preset
            }
            for m in results
        ]

        response = {
            "query": query,
            "category": category,
            "count": len(results),
            "medicines": medicines_data,
            "source": "database",
            "timing": {
                "database_ms": f"{db_time:.2f}",
                "total_ms": f"{(time.time() - start_time) * 1000:.2f}"
            }
        }
        
        # ===== STEP 4: Cache the result =====
        try:
            # Cache for 5 minutes
            redis_client.setex(
                cache_key,
                300,  # 5 minutes
                json.dumps({
                    "query": query,
                    "category": category,
                    "count": len(results),
                    "medicines": medicines_data
                }, default=str)
            )
            logger.info(f"Cached result for '{query} ({len(results)} items)")

        except Exception as e:
            logger.warning(f"Failed to cache result: {e}")
        
        logger.info(f"SEARCH: '{query}' found {len(results)} results in {db_time:.2f}ms (DB)")
        
        return response
        
    except Exception as e:
        logger.error(f"Search error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/search")  # ✅ OPTIONAL: Support POST as well
async def search_medicines_post(
    query: str = Query(..., min_length=1),
    category: Optional[str] = Query(None),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db)
):
    """Support POST requests as well (delegates to GET implementation)"""
    return await search_medicines(query, category, limit, db)

@app.get("/api/categories")
async def get_categories(db: Session = Depends(get_db)):
    """Get all available medicine categories (cached)"""
    try:
        cache_key = "categories:all"
        
        # Check cache
        try:
            cached = redis_client.get(cache_key)
            if cached:
                result = json.loads(cached)
                result["source"] = "cache"
                return result
        except Exception:
            pass
        
        # Query database
        categories = db.query(Medicine.category).distinct().all()
        result = {
            "categories": [cat[0] for cat in categories if cat[0]],
            "count": len(categories)
        }
        
        # Cache for 1 hour
        try:
            redis_client.setex(cache_key, 3600, json.dumps(result))
        except Exception:
            pass
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/medicines/presets")
async def get_preset_medicines(db: Session = Depends(get_db)):
    """Get 15 preset common medicines (cached)"""
    try:
        cache_key = "presets:all"
        
        # Check cache
        try:
            cached = redis_client.get(cache_key)
            if cached:
                result = json.loads(cached)
                result["source"] = "cache"
                return result
        except Exception:
            pass
        
        # Query database
        presets = db.query(Medicine).filter(Medicine.is_preset == True).all()
        result = {
            "count": len(presets),
            "medicines": [
                {
                    "id": m.id,
                    "name": m.name,
                    "category": m.category,
                    "description": m.description
                }
                for m in presets
            ]
        }
        
        # Cache for 24 hours (presets don't change often)
        try:
            redis_client.setex(cache_key, 86400, json.dumps(result, default=str))
        except Exception:
            pass
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/voice-search")
async def voice_search(file: UploadFile = File(...), db: Session = Depends(get_db)):
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
async def get_recent_searches(user_id: str = Query(...), limit: int = 10, db: Session = Depends(get_db)):
    """Get recent searches for a user"""
    try:
        searches = db.query(RecentSearch).filter(
            RecentSearch.user_id == user_id
        ).order_by(
            RecentSearch.searched_at.desc()
        ).limit(limit).all()
        
        return {
            "user_id": user_id,
            "count": len(searches),
            "searches": [
                {
                    "id": s.id,
                    "query": s.query,
                    "category": s.category,
                    "voice_search": s.voice_search,
                    "searched_at": s.searched_at
                }
                for s in searches
            ]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/track-search")
async def track_search(request: TrackSearchRequest, db: Session = Depends(get_db)):
    """Track user search for analytics"""
    try:
        # Store in database
        search = RecentSearch(
            user_id=request.user_id,
            query=request.query,
            category=request.category,
            voice_search=request.voice_search
        )
        db.add(search)
        
        # Update or create user activity
        activity = db.query(UserActivity).filter(
            UserActivity.user_id == request.user_id
        ).first()
        
        if activity:
            activity.search_count += 1
            activity.searches_today += 1
            activity.last_search_at = datetime.utcnow()
        else:
            activity = UserActivity(
                user_id=request.user_id,
                search_count=1,
                searches_today=1
            )
            db.add(activity)
        
        # Cache recent searches in Redis
        cache_key = f"recent_searches:{request.user_id}"
        redis_client.lpush(cache_key, json.dumps({
            "query": request.query,
            "category": request.category,
            "timestamp": datetime.utcnow().isoformat(),
            "voice_search": request.voice_search
        }))
        redis_client.ltrim(cache_key, 0, 99)  # Keep last 100
        redis_client.expire(cache_key, 7 * 24 * 60 * 60)  # 7 days TTL
        
        db.commit()
        
        return {
            "status": "tracked",
            "user_id": request.user_id,
            "query": request.query,
            "timestamp": datetime.utcnow()
        }
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/user-analytics")
async def get_user_analytics(user_id: str = Query(...), db: Session = Depends(get_db)):
    """Get user search analytics"""
    try:
        activity = db.query(UserActivity).filter(
            UserActivity.user_id == user_id
        ).first()
        
        if not activity:
            return {
                "user_id": user_id,
                "search_count": 0,
                "searches_today": 0,
                "last_search_at": None
            }
        
        return {
            "user_id": user_id,
            "search_count": activity.search_count,
            "searches_today": activity.searches_today,
            "last_search_at": activity.last_search_at
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/performance-stats")
async def performance_stats():
    """Get performance statistics (for debugging)"""
    try:
        db = SessionLocal()
        
        # Get cache info
        info = redis_client.info()
        
        return {
            "redis": {
                "connected": True,
                "used_memory": info.get("used_memory_human", "unknown"),
                "hit_rate": info.get("keyspace_hits", 0),
                "total_commands": info.get("total_commands_processed", 0)
            },
            "database": {
                "total_medicines": db.query(func.count(Medicine.id)).scalar(),
                "cached_categories": redis_client.exists("categories:all"),
                "cached_presets": redis_client.exists("presets:all")
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
            "pg_trgm indexing for fast text search",
            "Performance timing for each request",
            "Reduced result limit (20 instead of 100)"
        ],
        "endpoints": {
            "health": "GET /health",
            "search": "POST /api/search?query=aspirin&limit=20",
            "categories": "GET /api/categories",
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
