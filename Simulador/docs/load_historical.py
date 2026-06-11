"""
Carga todos los archivos _envios_ICAO_.txt al endpoint POST /admin/historical/{icao}.
Lee los archivos desde un .zip o desde una carpeta.

Uso:
    python load_historical.py                          # usa defaults
    python load_historical.py --zip otra_ruta.zip
    python load_historical.py --dir carpeta/con/txts
    python load_historical.py --mode merge
    python load_historical.py --host http://prod:8080
"""

import argparse
import re
import sys
import zipfile
from pathlib import Path

import requests

# ── Defaults ──────────────────────────────────────────────────────────────────

DEFAULT_HOST    = "http://localhost:8080"
DEFAULT_ZIP     = Path(__file__).parent / "com.tasf.b2b/src/main/resources/_envios_preliminar_.zip"
DEFAULT_MODE    = "replace"
ICAO_RE         = re.compile(r"_envios_([A-Z]{4})_", re.IGNORECASE)

# ── Auth ──────────────────────────────────────────────────────────────────────

def login(host: str, username: str, password: str) -> str:
    url = f"{host}/api/v1/auth/login"
    resp = requests.post(url, json={"username": username, "password": password}, timeout=10)
    if resp.status_code != 200:
        print(f"[ERROR] Login fallido ({resp.status_code}): {resp.text}")
        sys.exit(1)
    token = resp.json()["accessToken"]
    print(f"[OK] Login exitoso")
    return token

# ── Upload ────────────────────────────────────────────────────────────────────

def upload_file(host: str, token: str, icao: str, filename: str,
                file_bytes: bytes, mode: str) -> dict:
    url = f"{host}/api/v1/admin/historical/{icao}?mode={mode}"
    headers = {"Authorization": f"Bearer {token}"}
    files = {"file": (filename, file_bytes, "text/plain")}
    resp = requests.post(url, headers=headers, files=files, timeout=300)
    return {"status": resp.status_code, "body": resp.json() if resp.content else {}}

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Carga históricos de shipments al simulador TASF")
    parser.add_argument("--host",     default=DEFAULT_HOST,      help="Base URL del servidor")
    parser.add_argument("--zip",      default=str(DEFAULT_ZIP),  help="Ruta al .zip con los archivos")
    parser.add_argument("--dir",      default=None,              help="Carpeta con los .txt (alternativa al zip)")
    parser.add_argument("--mode",     default=DEFAULT_MODE,      choices=["merge", "replace"])
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default=None)
    args = parser.parse_args()

    if args.password is None:
        import getpass
        args.password = getpass.getpass("Password: ")

    token = login(args.host, args.username, args.password)

    # Recopilar archivos a subir: (icao, filename, bytes)
    entries: list[tuple[str, str, bytes]] = []

    if args.dir:
        folder = Path(args.dir)
        for path in sorted(folder.glob("*.txt")):
            m = ICAO_RE.search(path.name.upper())
            if m:
                entries.append((m.group(1), path.name, path.read_bytes()))
            else:
                print(f"[SKIP] {path.name} — nombre no coincide con _envios_ICAO_")
    else:
        zip_path = Path(args.zip)
        if not zip_path.exists():
            print(f"[ERROR] No se encontró el zip: {zip_path}")
            sys.exit(1)
        with zipfile.ZipFile(zip_path) as zf:
            for name in sorted(zf.namelist()):
                m = ICAO_RE.search(name.upper())
                if m:
                    entries.append((m.group(1), Path(name).name, zf.read(name)))
                else:
                    print(f"[SKIP] {name} — nombre no coincide con _envios_ICAO_")

    if not entries:
        print("[ERROR] No se encontraron archivos _envios_ICAO_.txt")
        sys.exit(1)

    print(f"\nArchivos encontrados: {len(entries)}")
    print(f"Modo: {args.mode}\n")
    print("-" * 60)

    ok = 0
    failed = []

    for i, (icao, filename, data) in enumerate(entries, 1):
        size_kb = len(data) / 1024
        print(f"[{i}/{len(entries)}] {icao}  ({size_kb:.0f} KB) ... ", end="", flush=True)

        try:
            result = upload_file(args.host, token, icao, filename, data, args.mode)
            status = result["status"]
            body   = result["body"]

            if status == 201:
                count    = body.get("count", "?")
                n_errors = len(body.get("errors", []))
                print(f"OK — {count} shipments cargados" + (f", {n_errors} errores de línea" if n_errors else ""))
                ok += 1
                if n_errors:
                    for err in body["errors"][:3]:
                        print(f"       ↳ {err}")
                    if n_errors > 3:
                        print(f"       ↳ ... y {n_errors - 3} más")
            else:
                msg = body.get("message", body.get("detail", str(body)))
                print(f"FALLO ({status}) — {msg}")
                failed.append(icao)

        except requests.exceptions.Timeout:
            print("TIMEOUT — el servidor tardó más de 5 min")
            failed.append(icao)
        except Exception as e:
            print(f"ERROR — {e}")
            failed.append(icao)

    print("-" * 60)
    print(f"\nResumen: {ok}/{len(entries)} exitosos")
    if failed:
        print(f"Fallidos: {', '.join(failed)}")
        sys.exit(1)

if __name__ == "__main__":
    main()
