#!/usr/bin/env python3
"""
Diagnostic script to check database state and guide optimization
"""
import psycopg2
from psycopg2 import sql

def check_database():
    try:
        conn = psycopg2.connect(
            host="localhost",
            database="medicines_db",
            user="med_user",
            password="med_pass123",
            port="5432"
        )
        cur = conn.cursor()
        
        print("=" * 70)
        print("MEDICINE DATABASE DIAGNOSTIC")
        print("=" * 70)
        
        # Check total count
        cur.execute("SELECT COUNT(*) FROM medicines;")
        total = cur.fetchone()[0]
        print(f"\n✓ Total medicines in database: {total:,}")
        
        # Check preset vs non-preset
        cur.execute("""
            SELECT 
                COUNT(CASE WHEN is_preset = true THEN 1 END) as presets,
                COUNT(CASE WHEN is_preset = false THEN 1 END) as non_presets
            FROM medicines;
        """)
        presets, non_presets = cur.fetchone()
        print(f"  - Preset medicines: {presets}")
        print(f"  - Non-preset medicines: {non_presets:,}")
        
        # Check if pg_trgm extension exists
        cur.execute("""
            SELECT EXISTS(
                SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'
            );
        """)
        has_trgm = cur.fetchone()[0]
        print(f"\n✓ pg_trgm extension installed: {has_trgm}")
        
        # Check if trgm index exists
        cur.execute("""
            SELECT EXISTS(
                SELECT 1 FROM pg_indexes 
                WHERE indexname = 'medicines_name_trgm'
            );
        """)
        has_index = cur.fetchone()[0]
        print(f"✓ medicines_name_trgm index exists: {has_index}")
        
        # Check available indexes on medicines table
        cur.execute("""
            SELECT indexname, indexdef 
            FROM pg_indexes 
            WHERE tablename = 'medicines'
            ORDER BY indexname;
        """)
        indexes = cur.fetchall()
        print(f"\n✓ Current indexes on medicines table:")
        for idx_name, idx_def in indexes:
            print(f"  - {idx_name}")
        
        # Check Redis connection
        try:
            import redis
            r = redis.Redis.from_url("redis://localhost:6379", decode_responses=True)
            r.ping()
            print(f"\n✓ Redis is connected")
            
            # Check Redis memory usage
            info = r.info()
            print(f"  - Redis used memory: {info['used_memory_human']}")
        except Exception as e:
            print(f"\n✗ Redis error: {e}")
        
        # Performance test: ILIKE vs trgm
        print(f"\n" + "=" * 70)
        print("PERFORMANCE TEST: Search for 'para'")
        print("=" * 70)
        
        import time
        
        # Test 1: ILIKE (current)
        start = time.time()
        cur.execute("SELECT COUNT(*) FROM medicines WHERE name ILIKE '%para%';")
        ilike_count = cur.fetchone()[0]
        ilike_time = time.time() - start
        print(f"\nILIKE '%para%':")
        print(f"  Results: {ilike_count}")
        print(f"  Time: {ilike_time*1000:.2f}ms")
        
        if has_index:
            # Test 2: trgm similarity
            start = time.time()
            cur.execute("""
                SELECT COUNT(*) FROM medicines 
                WHERE name % 'para'
                ORDER BY similarity(name, 'para') DESC;
            """)
            trgm_count = cur.fetchone()[0]
            trgm_time = time.time() - start
            print(f"\ntrgm (name % 'para'):")
            print(f"  Results: {trgm_count}")
            print(f"  Time: {trgm_time*1000:.2f}ms")
            
            if ilike_time > 0:
                speedup = ilike_time / trgm_time
                print(f"\nSpeedup: {speedup:.1f}x faster with trgm")
        
        cur.close()
        conn.close()
        
        print("\n" + "=" * 70)
        print("RECOMMENDATIONS")
        print("=" * 70)
        
        if total > 5000:
            print("⚠️  Large dataset detected (5000+ medicines)")
            if not has_index:
                print("❌ pg_trgm index NOT found - INSTALL IT NOW")
                print("   This is critical for performance with 15k+ medicines")
            else:
                print("✓ pg_trgm index found - good!")
        
        print("\nNext steps:")
        print("1. Run: python3 setup_trgm.py  (creates the index)")
        print("2. Run: python3 -m backend.main  (starts server with caching)")
        print("3. Update Android: limit 100 → 20, persist userId")
        print("=" * 70)
        
    except Exception as e:
        print(f"Error connecting to database: {e}")
        print("Make sure PostgreSQL is running on localhost:5432")

if __name__ == "__main__":
    check_database()
