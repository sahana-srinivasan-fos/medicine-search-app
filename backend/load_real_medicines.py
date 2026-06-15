import json
import psycopg2
from datetime import datetime

# Database connection
conn = psycopg2.connect(
    host="localhost",
    database="medicines_db",
    user="med_user",
    password="med_pass123",
    port="5432"
)
cur = conn.cursor()

print("🗑️  Cleaning old medicines (keeping presets)...")
cur.execute("DELETE FROM medicines WHERE is_preset = false;")
conn.commit()
print("✓ Old medicines deleted")

# Load medicines from JSON
print("\n📂 Loading medicines from JSON file...")
with open('medicines.json', 'r') as f:
    medicines_list = json.load(f)

print(f"📊 Total medicines in JSON: {len(medicines_list)}")

# Function to categorize medicine based on name
def categorize_medicine(name):
    """Extract category from medicine name"""
    name_upper = name.upper()
    
    if "TAB" in name_upper:
        return "Tablets"
    elif "CAP" in name_upper:
        return "Capsules"
    elif "SYP" in name_upper or "SYRUP" in name_upper:
        return "Syrups"
    elif "INJ" in name_upper or "INJECTION" in name_upper:
        return "Injections"
    elif "DROPS" in name_upper or "DROP" in name_upper:
        return "Drops"
    elif "OINT" in name_upper or "OINTMENT" in name_upper:
        return "Ointments"
    elif "GEL" in name_upper:
        return "Gels"
    elif "CREAM" in name_upper:
        return "Creams"
    elif "SPRAY" in name_upper:
        return "Spray"
    elif "POWDER" in name_upper:
        return "Powder"
    elif "SOAP" in name_upper:
        return "Soaps"
    elif "SHAMPOO" in name_upper:
        return "Shampoo"
    elif "LOTION" in name_upper:
        return "Lotion"
    elif "PATCH" in name_upper:
        return "Patches"
    elif "INHALER" in name_upper or "INHAL" in name_upper:
        return "Inhalers"
    else:
        return "Other"

print(f"\n🔄 Loading {len(medicines_list)} medicines into database...")

batch_size = 1000
inserted = 0
skipped = 0

for i, medicine_name in enumerate(medicines_list):
    try:
        category = categorize_medicine(medicine_name)
        
        cur.execute(
            "INSERT INTO medicines (name, category, is_preset, created_at) VALUES (%s, %s, %s, %s)",
            (medicine_name, category, False, datetime.now())
        )
        inserted += 1
        
    except psycopg2.IntegrityError:
        conn.rollback()
        skipped += 1
    except Exception as e:
        conn.rollback()
        print(f"Error with '{medicine_name}': {str(e)[:50]}")
    
    # Commit in batches
    if (i + 1) % batch_size == 0:
        conn.commit()
        percentage = ((i + 1) / len(medicines_list)) * 100
        print(f"  ✓ Inserted: {inserted} | Progress: {percentage:.1f}%")

# Final commit
conn.commit()

# Get final stats
cur.execute("SELECT COUNT(*) FROM medicines;")
total = cur.fetchone()[0]

cur.execute("SELECT category, COUNT(*) FROM medicines WHERE is_preset = false GROUP BY category ORDER BY COUNT(*) DESC;")
categories_stats = cur.fetchall()

print(f"\n✅ LOADING COMPLETE!")
print(f"\nDatabase Statistics:")
print(f"  Total medicines: {total}")
print(f"  Newly inserted: {inserted}")
print(f"  Skipped (duplicates): {skipped}")

print(f"\nMedicines by Category (Real Data):")
for category, count in categories_stats:
    print(f"  {category}: {count}")

# Verify with a sample search
cur.execute("SELECT name FROM medicines WHERE is_preset = false LIMIT 5;")
samples = cur.fetchall()
print(f"\nSample medicines in database:")
for sample in samples:
    print(f"  - {sample[0]}")

cur.close()
conn.close()

print("\n🎯 Ready to test! Try: curl -X POST 'http://localhost:8000/api/search?query=HER&limit=5'")
