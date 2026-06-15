import json
import psycopg2

# Connect
try:
    conn = psycopg2.connect(
        host="localhost",
        database="medicines_db",
        user="med_user",
        password="med_pass123"
    )
    cur = conn.cursor()
    print("✓ Database connected")
except Exception as e:
    print(f"❌ Database error: {e}")
    exit(1)

# Load JSON
try:
    with open('medicines.json', 'r') as f:
        medicines = json.load(f)
    print(f"✓ JSON loaded: {len(medicines)} medicines")
except Exception as e:
    print(f"❌ JSON error: {e}")
    exit(1)

# Show first 3 medicines from JSON
print(f"\nFirst 3 medicines from JSON:")
for med in medicines[:3]:
    print(f"  - {med}")

# Categorize
def get_category(name):
    name_up = name.upper()
    if "TAB" in name_up: return "Tablets"
    elif "CAP" in name_up: return "Capsules"
    elif "SYP" in name_up: return "Syrups"
    elif "DROP" in name_up: return "Drops"
    elif "OINT" in name_up: return "Ointments"
    else: return "Other"

# Clear old data
print(f"\nClearing old medicines...")
cur.execute("DELETE FROM medicines WHERE is_preset = false")
conn.commit()
print("✓ Cleared")

# Insert new medicines
print(f"\nInserting medicines...")
inserted = 0
for i, med_name in enumerate(medicines):
    try:
        cat = get_category(med_name)
        cur.execute(
            "INSERT INTO medicines (name, category, is_preset) VALUES (%s, %s, %s)",
            (med_name, cat, False)
        )
        inserted += 1
        
        if (i + 1) % 2000 == 0:
            conn.commit()
            print(f"  {i + 1}/{len(medicines)} inserted...")
    except Exception as e:
        conn.rollback()
        print(f"❌ Error on '{med_name}': {str(e)[:50]}")

conn.commit()
print(f"✓ {inserted} medicines inserted")

# Verify
cur.execute("SELECT COUNT(*) FROM medicines WHERE is_preset = false")
count = cur.fetchone()[0]
print(f"\nFinal count: {count} non-preset medicines in database")

# Show what's in DB now
cur.execute("SELECT name FROM medicines WHERE is_preset = false LIMIT 5")
samples = cur.fetchall()
print(f"\nFirst 5 medicines in database:")
for sample in samples:
    print(f"  - {sample[0]}")

cur.close()
conn.close()
print("\n✅ Done!")
