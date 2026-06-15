import json
import psycopg2
from datetime import datetime

print("=" * 60)
print("MEDICINE LOADER - DEBUG VERSION")
print("=" * 60)

# Step 1: Load JSON
print("\n[1] Loading medicines.json...")
try:
    with open('medicines.json', 'r') as f:
        medicines = json.load(f)
    print(f"    ✓ Loaded {len(medicines)} medicines")
    print(f"    First medicine: '{medicines[0]}'")
    print(f"    Last medicine: '{medicines[-1]}'")
except FileNotFoundError:
    print(f"    ❌ medicines.json not found!")
    exit(1)

# Step 2: Connect to DB
print("\n[2] Connecting to database...")
try:
    conn = psycopg2.connect(
        host="localhost",
        database="medicines_db",
        user="med_user",
        password="med_pass123",
        port="5432"
    )
    cur = conn.cursor()
    print(f"    ✓ Connected to medicines_db")
except Exception as e:
    print(f"    ❌ Connection failed: {e}")
    exit(1)

# Step 3: Check current count
print("\n[3] Checking current database...")
cur.execute("SELECT COUNT(*) as total, COUNT(CASE WHEN is_preset=true THEN 1 END) as presets FROM medicines;")
total, presets = cur.fetchone()
print(f"    Total medicines: {total}")
print(f"    Preset medicines: {presets}")
print(f"    Non-preset medicines: {total - presets}")

# Step 4: Show sample of current data
cur.execute("SELECT name FROM medicines WHERE is_preset = false LIMIT 1;")
sample = cur.fetchone()
if sample:
    print(f"    Sample non-preset: '{sample[0]}'")

# Step 5: Clear non-preset medicines
print("\n[4] Clearing old non-preset medicines...")
cur.execute("DELETE FROM medicines WHERE is_preset = false;")
conn.commit()
cur.execute("SELECT COUNT(*) FROM medicines WHERE is_preset = false;")
count_after_delete = cur.fetchone()[0]
print(f"    ✓ Deleted. Remaining non-preset: {count_after_delete}")

# Step 6: Helper function
def get_category(name):
    name_up = name.upper()
    if "TAB" in name_up: return "Tablets"
    elif "CAP" in name_up: return "Capsules"
    elif "SYP" in name_up: return "Syrups"
    elif "DROP" in name_up: return "Drops"
    elif "OINT" in name_up: return "Ointments"
    elif "GEL" in name_up: return "Gels"
    elif "CREAM" in name_up: return "Creams"
    elif "SPRAY" in name_up: return "Spray"
    elif "POWDER" in name_up: return "Powder"
    elif "SOAP" in name_up: return "Soaps"
    elif "SHAMPOO" in name_up: return "Shampoo"
    else: return "Other"

# Step 7: Insert medicines
print(f"\n[5] Inserting {len(medicines)} medicines...")

inserted = 0
skipped = 0
errors = 0

for i, med_name in enumerate(medicines):
    try:
        category = get_category(med_name)
        cur.execute(
            "INSERT INTO medicines (name, category, is_preset, created_at) VALUES (%s, %s, %s, %s)",
            (med_name, category, False, datetime.now())
        )
        inserted += 1
    except psycopg2.IntegrityError:
        conn.rollback()
        skipped += 1
    except Exception as e:
        conn.rollback()
        errors += 1
        if errors <= 3:
            print(f"    ⚠️  Error on '{med_name}': {str(e)[:60]}")
    
    # Progress every 3000
    if (i + 1) % 3000 == 0:
        conn.commit()
        pct = ((i + 1) / len(medicines)) * 100
        print(f"    Progress: {i+1}/{len(medicines)} ({pct:.1f}%)")

conn.commit()
print(f"    ✓ Inserted: {inserted}")
print(f"    Skipped (duplicates): {skipped}")
print(f"    Errors: {errors}")

# Step 8: Final verification
print("\n[6] Final verification...")
cur.execute("SELECT COUNT(*) FROM medicines WHERE is_preset = false;")
final_count = cur.fetchone()[0]
print(f"    Total non-preset medicines: {final_count}")

cur.execute("SELECT name FROM medicines WHERE is_preset = false LIMIT 5;")
samples = cur.fetchall()
print(f"    First 5 medicines in DB:")
for sample in samples:
    print(f"      - {sample[0]}")

cur.close()
conn.close()

print("\n" + "=" * 60)
print("✅ COMPLETE!")
print("=" * 60)
print(f"\nTest with: curl -X POST 'http://localhost:8000/api/search?query=HER&limit=5'")
