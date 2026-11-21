import json
import urllib.parse
import urllib.request
from pathlib import Path

BASE_DIR = Path(r"D:\DATN\PB1-H\backend\src\main\resources\imgDb")
HEADERS = {
    "User-Agent": "O2OFoodPickupAcademicBot/1.0 (https://haui.edu.vn; student-project@o2ofood.vn)"
}

# Curated Wikipedia article titles -> guaranteed accurate, clean lead food/store photos
WIKI_TARGETS = [
    # 10 Products in O2OFoodPickupDB + 7 extra in V2__seed_initial_data.sql
    ("products", "prd-chicken-breast.jpg", ["Chicken_as_food", "Poultry"]),
    ("products", "prd-pork-belly.jpg", ["Pork_belly", "Pork"]),
    ("products", "prd-beef-shank.jpg", ["Beef_shank", "Beef_tenderloin", "Beef"]),
    ("products", "prd-egg-chicken.jpg", ["Egg_as_food", "Eggs_as_food"]),
    ("products", "prd-white-tofu.jpg", ["Tofu"]),
    ("products", "prd-broccoli.jpg", ["Broccoli"]),
    ("products", "prd-cucumber.jpg", ["Cucumber"]),
    ("products", "prd-morning-glory.jpg", ["Ipomoea_aquatica"]),
    ("products", "prd-tomato.jpg", ["Tomato"]),
    ("products", "prd-rock-apple.jpg", ["Apple"]),
    ("products", "prd-salmon-fillet.jpg", ["Salmon_as_food", "Atlantic_salmon"]),
    ("products", "prd-shrimp-peeled.jpg", ["Shrimp_and_prawn_as_food", "Penaeus_monodon"]),
    ("products", "prd-choy-sum.jpg", ["Choy_sum", "Bok_choy"]),
    ("products", "prd-potato.jpg", ["Potato"]),
    ("products", "prd-carrot.jpg", ["Carrot"]),
    ("products", "prd-enoki-mushroom.jpg", ["Enokitake"]),
    ("products", "prd-st25-rice.jpg", ["Jasmine_rice", "White_rice"]),
    # 3 Pickup Point / Agency Storefront photos
    ("pickup-points", "pp-01-nhon.jpg", ["Convenience_store", "Grocery_store"]),
    ("pickup-points", "pp-02-caugiay.jpg", ["Supermarket", "Minimarket"]),
    ("pickup-points", "pp-03-mydinh.jpg", ["Greengrocer", "Farmers'_market"]),
]

# 12 Users matched to exact gender in O2OFoodPickupDB
USER_TARGETS = [
    ("user-01-admin.jpg", "male"),          # Nguyễn Quản Trị
    ("user-02-warehouse.jpg", "male"),      # Trần Văn Kho
    ("user-03-dispatcher.jpg", "female"),   # Lê Thị Điều Phối
    ("user-04-driver1.jpg", "male"),        # Phạm Quốc Tài Xế 1
    ("user-05-driver2.jpg", "male"),        # Hoàng Văn Tài Xế 2
    ("user-06-agent-nhon.jpg", "female"),   # Cô Lan - Đại Lý Nhổn
    ("user-07-agent-caugiay.jpg", "male"),  # Chú Minh - Đại Lý Cầu Giấy
    ("user-08-agent-mydinh.jpg", "female"), # Chị Hoa - Đại Lý Mỹ Đình
    ("user-09-customer1.jpg", "male"),      # Nông Thế Hưng
    ("user-10-customer2.jpg", "male"),      # Nguyễn Đức Mạnh
    ("user-11-customer3.jpg", "male"),      # Trần Trung Kiên
    ("user-12-customer4.jpg", "female"),    # Vũ Phương Linh
]


def fetch_json(url: str):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=25) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download_binary(url: str, dest: Path) -> int:
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=25) as resp:
        ctype = resp.headers.get("Content-Type", "")
        if "image" not in ctype.lower():
            raise ValueError(f"Not an image: {ctype}")
        data = resp.read()
        if len(data) < 3000:
            raise ValueError(f"Image too small: {len(data)} bytes")
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        return len(data)


def get_wiki_lead_image(title: str):
    params = urllib.parse.urlencode({
        "action": "query",
        "format": "json",
        "titles": title,
        "redirects": "1",
        "prop": "pageimages",
        "pithumbsize": "800",
    })
    url = f"https://en.wikipedia.org/w/api.php?{params}"
    data = fetch_json(url)
    pages = data.get("query", {}).get("pages", {})
    for page in pages.values():
        thumb = page.get("thumbnail", {})
        if thumb.get("source"):
            return thumb["source"], page.get("title", title)
    return None, title


def main():
    manifest = []
    ok_count = 0
    fail_count = 0

    # 1. Crawl Products & Pickup Points from curated Wikipedia pageimages
    for folder, filename, titles in WIKI_TARGETS:
        dest = BASE_DIR / folder / filename
        success = False
        for title in titles:
            try:
                img_url, resolved_title = get_wiki_lead_image(title)
                if not img_url:
                    continue
                size = download_binary(img_url, dest)
                manifest.append({
                    "folder": folder,
                    "filename": filename,
                    "relativePath": f"/imgDb/{folder}/{filename}",
                    "sizeBytes": size,
                    "source": f"Wikipedia:{resolved_title}",
                    "sourceUrl": img_url,
                })
                print(f"[OK] /imgDb/{folder}/{filename} ({size // 1024} KB) <- Wikipedia:{resolved_title}", flush=True)
                ok_count += 1
                success = True
                break
            except Exception as e:
                print(f"[WARN] {filename} ({title}): {e}", flush=True)
        if not success:
            fail_count += 1
            print(f"[FAIL] {folder}/{filename}", flush=True)

    # 2. Crawl clean, modern, gender-matched human portraits from randomuser.me
    males = fetch_json("https://randomuser.me/api/?results=10&gender=male&inc=picture&seed=o2ofood2026m")["results"]
    females = fetch_json("https://randomuser.me/api/?results=6&gender=female&inc=picture&seed=o2ofood2026f")["results"]
    m_idx = 0
    f_idx = 0

    for filename, gender in USER_TARGETS:
        dest = BASE_DIR / "users" / filename
        if gender == "male":
            img_url = males[m_idx]["picture"]["large"]
            m_idx += 1
        else:
            img_url = females[f_idx]["picture"]["large"]
            f_idx += 1
        try:
            size = download_binary(img_url, dest)
            manifest.append({
                "folder": "users",
                "filename": filename,
                "relativePath": f"/imgDb/users/{filename}",
                "sizeBytes": size,
                "source": f"randomuser.me ({gender})",
                "sourceUrl": img_url,
            })
            print(f"[OK] /imgDb/users/{filename} ({size // 1024} KB) <- {img_url}", flush=True)
            ok_count += 1
        except Exception as e:
            fail_count += 1
            print(f"[FAIL] users/{filename}: {e}", flush=True)

    manifest_path = BASE_DIR / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nDONE: {ok_count} downloaded, {fail_count} failed. Manifest: {manifest_path}", flush=True)


if __name__ == "__main__":
    main()
