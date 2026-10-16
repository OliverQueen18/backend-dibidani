from __future__ import annotations

import os
import threading
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from pipeline import process_image

ENGINE = os.environ.get("ENGINE", "local").strip() or "local"
TOKEN = os.environ.get("SERVICE_TOKEN", "")
UPLOADS_ROOT = Path(os.environ.get("UPLOADS_ROOT", "/app/uploads")).resolve()
WATERMARK_PATH = Path(os.environ.get("WATERMARK_PATH", "/app/assets/favicon-dibidani.png"))
MAX_CONCURRENCY = max(1, int(os.environ.get("MAX_CONCURRENCY", "1")))
VERSION = "1"

_slots = threading.Semaphore(MAX_CONCURRENCY)
app = FastAPI(title="Dibidani image studio", version=VERSION)


class ProcessBody(BaseModel):
    inputPath: str
    outputDir: str
    stem: str
    options: dict = Field(default_factory=dict)


def _under_root(raw: str) -> Path:
    path = Path(raw).resolve()
    try:
        path.relative_to(UPLOADS_ROOT)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Chemin hors stockage") from exc
    return path


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "engine": ENGINE, "version": VERSION}


@app.post("/process")
def process(body: ProcessBody, x_studio_token: str | None = Header(default=None)) -> dict:
    if TOKEN and (x_studio_token or "") != TOKEN:
        raise HTTPException(status_code=403, detail="Jeton studio invalide")
    if not body.stem or "/" in body.stem or "\\" in body.stem or ".." in body.stem:
        raise HTTPException(status_code=400, detail="Identifiant de fichier invalide")
    source = _under_root(body.inputPath)
    dest = _under_root(body.outputDir)
    if not source.is_file():
        raise HTTPException(status_code=404, detail="Image source introuvable")
    if not _slots.acquire(timeout=90):
        raise HTTPException(status_code=429, detail="Studio saturé")
    try:
        files = process_image(
            source,
            dest,
            body.stem,
            body.options or {},
            WATERMARK_PATH if WATERMARK_PATH.is_file() else None,
            ENGINE,
        )
        engine = files.pop("engine", ENGINE)
        return {"engine": engine, "version": VERSION, "files": files}
    except HTTPException:
        raise
    except OSError as exc:
        raise HTTPException(status_code=400, detail="Image illisible") from exc
    finally:
        _slots.release()
