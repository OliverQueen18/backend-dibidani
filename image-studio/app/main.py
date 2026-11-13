from contextlib import asynccontextmanager
import hmac
import json
import logging
import os
import threading
from pathlib import Path
from fastapi import Depends, FastAPI, File, Header, HTTPException, Form, UploadFile
from fastapi.responses import JSONResponse, Response
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel, Field, ValidationError
from app.models import manager
from app.options import Options
from app.pipeline import MAX_BYTES, VERSION, process_file
from app.rendering import render, encode

log = logging.getLogger("dibidani.studio")
logging.basicConfig(level=logging.INFO)
ROOT = Path(os.getenv("UPLOADS_ROOT", "/app/uploads")).resolve()
TOKEN = os.getenv("SERVICE_TOKEN", "")
WATERMARK = os.getenv("WATERMARK_PATH", "/app/assets/favicon-dibidani.png")
slots = threading.BoundedSemaphore(max(1, int(os.getenv("MAX_CONCURRENCY", "1"))))
ready = False


@asynccontextmanager
async def lifespan(app):
    global ready
    if not TOKEN:
        raise RuntimeError("SERVICE_TOKEN est obligatoire")
    try:
        manager.preload()
        ready = True
    except Exception:
        log.error("Modèles indisponibles : préparez le cache avant le démarrage")
    yield
    ready = False


app = FastAPI(title="Dibidani Image Studio", version=VERSION, lifespan=lifespan)


class RequestLimit:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope.get("method") != "POST":
            return await self.app(scope, receive, send)
        limit = MAX_BYTES + 1024 * 1024  # bounded multipart overhead
        headers = dict(scope.get("headers", []))
        try:
            length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            length = limit + 1
        if length > limit:
            return await JSONResponse(status_code=413, content={"code":"STUDIO_413","message":"Requête trop volumineuse"})(scope, receive, send)
        total = 0
        async def bounded_receive():
            nonlocal total
            event = await receive()
            total += len(event.get("body", b""))
            if total > limit:
                raise HTTPException(413, "Requête trop volumineuse")
            return event
        await self.app(scope, bounded_receive, send)


app.add_middleware(RequestLimit)


def auth(x_studio_token: str = Header(default="", alias="X-Studio-Token")):
    if not TOKEN or not hmac.compare_digest(x_studio_token, TOKEN):
        raise HTTPException(401, "Jeton de traitement invalide")


@app.exception_handler(HTTPException)
async def http_error(request, error):
    return JSONResponse(status_code=error.status_code, content={"code": f"STUDIO_{error.status_code}", "message": str(error.detail)})


@app.exception_handler(RequestValidationError)
async def validation_error(request, error):
    return JSONResponse(status_code=422, content={"code":"STUDIO_422","message":"Paramètres de traitement invalides"})


@app.get("/health")
def health():
    return JSONResponse(status_code=200 if ready else 503, content={"status": "UP" if ready else "NOT_READY", "version": VERSION})


@app.get("/models", dependencies=[Depends(auth)])
def models():
    return manager.describe()


class ProcessRequest(BaseModel):
    inputPath: str
    outputDir: str
    stem: str = Field(pattern=r"^[a-zA-Z0-9_-]{1,80}$")
    options: Options = Field(default_factory=Options)


def under(raw, base):
    path = Path(raw).resolve()
    if not path.is_relative_to(base.resolve()):
        raise HTTPException(400, "Chemin de stockage refusé")
    return path


def execute(action):
    if not slots.acquire(blocking=False):
        raise HTTPException(429, "Le studio est occupé. Réessayez dans un instant.")
    try:
        return action()
    except (ValueError, ValidationError) as error:
        raise HTTPException(400, "Image ou paramètres invalides") from error
    except FileNotFoundError as error:
        raise HTTPException(404, "Image source introuvable") from error
    except HTTPException:
        raise
    except Exception as error:
        log.error("Traitement échoué (%s)", type(error).__name__)
        raise HTTPException(503, "Le moteur IA est indisponible. L'original est conservé.") from error
    finally:
        slots.release()


@app.post("/process", dependencies=[Depends(auth)])
def process_image(body: ProcessRequest):
    source = under(body.inputPath, ROOT / "private" / "originals")
    destination = under(body.outputDir, ROOT / "studio")
    def action():
        if source.stat().st_size > MAX_BYTES:
            raise HTTPException(413, "Image trop volumineuse")
        return process_file(source, destination, body.stem, body.options.model_dump(), WATERMARK, ROOT)
    return execute(action)


def upload_result(file, raw, remove_only=False):
    try:
        options = Options.model_validate(json.loads(raw)).model_dump()
    except (ValueError, ValidationError) as error:
        raise HTTPException(400, "Paramètres invalides") from error
    data = file.file.read(MAX_BYTES + 1)
    file.file.close()
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "Image trop volumineuse")
    if remove_only:
        options.update(removeBackground=True, background="transparent", format="png", watermark=False)
    def action():
        image, opts = render(data, options, WATERMARK)
        return Response(encode(image, opts.format, opts.quality), media_type="image/" + opts.format)
    return execute(action)


@app.post("/remove-background", dependencies=[Depends(auth)])
def remove_background(file: UploadFile = File(...), options: str = Form("{}")):
    return upload_result(file, options, True)


@app.post("/process-image", dependencies=[Depends(auth)])
def process_upload(file: UploadFile = File(...), options: str = Form("{}")):
    return upload_result(file, options)
