#!/usr/bin/env python3
"""
Setup pg_trgm extension and create indexes for fast medicine search
Run this ONCE to set up the database for optimal performance
"""
import psycopg2
import time

def setup_trgm():
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
        print("SETTING UP pg_trgm FOR FAST TEXT SEARCH")
        print("=" * 70)
        
        # Step 1: Create extension
        print("\n[1] Installing pg_trgm extension...")
        try:
            cur.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")
            conn.commit()
            print("    ✓ pg_trgm extension installed")
        except Exception as e:
            print(f"    ⚠️  Extension already exists or error: {e}")
            conn.rollback()
        
        # Step 2: Create gin index on name
        print("\n[2] Creating GIN index on medicines.name...")
        print("    (This may take a few minutes for 15k+ medicines...)")
        
        try:
            cur.execute("DROP INDEX IF EXISTS medicines_name_trgm;")
            conn.commit()
            
            start = time.time()
            cur.execute("""
                CREATE INDEX medicines_name_trgm 
                ON medicines 
                USING gin(name gin_trgm_ops);
            """)
            conn.commit()
            duration = time.time() - start
            print(f"    ✓ Index created in {duration:.2f}s")
        except Exception as e:
            print(f"    ❌ Error creating index: {e}")
            conn.rollback()
        
        # Step 3: Create gin index on description
        print("\n[3] Creating GIN index on medicines.description...")
        try:
            cur.execute("DROP INDEX IF EXISTS medicines_description_trgm;")
            conn.commit()
            
            start = time.time()
            cur.execute("""
                CREATE INDEX medicines_description_trgm 
                ON medicines 
                USING gin(description gin_trgm_ops);
            """)
            conn.commit()
            duration = time.time() - start
            print(f"    ✓ Index created in {duration:.2f}s")
        except Exception as e:
            print(f"    ❌ Error creating index: {e}")
            conn.rollback()
        
        # Step 4: Create regular indexes (if not exist)
        print("\n[4] Creating standard indexes...")
        indexes = [
            ("medicines_category_idx", "CREATE INDEX IF NOT EXISTS medicines_category_idx ON medicines(category);"),
            ("medicines_preset_idx", "CREATE INDEX IF NOT EXISTS medicines_preset_idx ON medicines(is_preset);"),
        ]
        
        for idx_name, idx_sql in indexes:
            try:
                cur.execute(idx_sql)
                conn.commit()
                print(f"    ✓ {idx_name}")
            except Exception as e:
                print(f"    ⚠️  {idx_name}: {e}")
                conn.rollback()
        
        # Step 5: Verify
        print("\n[5] Verifying indexes...")
        cur.execute("""
            SELECT indexname FROM pg_indexes 
            WHERE tablename = 'medicines'
            ORDER BY indexname;
        """)
        indexes = cur.fetchall()
        print(f"    Total indexes: {len(indexes)}")
        for idx, in indexes:
            print(f"      - {idx}")
        
        # Step 6: Quick performance test
        print("\n[6] Performance test (searching for 'para')...")
        
        start = time.time()
        cur.execute("""
            SELECT COUNT(*) FROM medicines 
            WHERE name ILIKE '%para%' LIMIT 20;
        """)
        count = cur.fetchone()[0]
        duration = time.time() - start
        print(f"    ✓ Found {count} results in {duration*1000:.2f}ms")
        
        cur.close()
        conn.close()
        
        print("\n" + "=" * 70)
        print("✅ SETUP COMPLETE!")
        print("=" * 70)
        print("\nYour search performance should be 10-50x faster now.")
        print("Next: Restart the FastAPI server to enable Redis caching.")
        print("\n")
        
    except psycopg2.Error as e:
        print(f"\n❌ Database error: {e}")
        print("\nMake sure PostgreSQL is running:")
        print("  docker-compose -f backend/docker-compose.yml up -d")

if __name__ == "__main__":
    setup_trgm()
