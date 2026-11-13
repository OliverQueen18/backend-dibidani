"""Explicit, bounded CPU model registry. No network access during inference."""
import os
import threading
from pathlib import Path

KNOWN = {"isnet-general-use", "u2net", "silueta", "birefnet-general", "birefnet-general-lite"}


class ModelManager:
    def __init__(self):
        self.default = os.getenv("REMBG_MODEL", "isnet-general-use")
        self.allowed = tuple(dict.fromkeys(x.strip() for x in os.getenv("REMBG_ALLOWED_MODELS", self.default).split(",") if x.strip()))
        if not set(self.allowed) <= KNOWN or self.default not in self.allowed:
            raise ValueError("Configuration des modèles invalide")
        if os.getenv("STUDIO_ENV") == "production":
            approved = set(os.getenv("MODEL_LICENSE_APPROVALS", "").split(","))
            if not set(self.allowed) <= approved:
                raise ValueError("Licence des poids à documenter avant activation commerciale : voir MODEL-LICENSES.md")
        if os.getenv("REMBG_DEVICE", "cpu") != "cpu":
            raise ValueError("Cette version utilise exclusivement le CPU")
        self.limit = max(1, int(os.getenv("MAX_LOADED_MODELS", "1")))
        if len(self.allowed) > self.limit:
            raise ValueError("MAX_LOADED_MODELS doit couvrir les modèles autorisés")
        self.sessions = {}
        self.lock = threading.Lock()

    def load(self, name, download=False):
        if name not in self.allowed:
            raise ValueError("Modèle non autorisé")
        with self.lock:
            if name in self.sessions:
                return self.sessions[name]
            cache = Path(os.getenv("U2NET_HOME", str(Path.home() / ".u2net")))
            if not download and not (cache / (name + ".onnx")).is_file():
                raise RuntimeError("Modèle absent du cache. Exécutez la préparation des modèles.")
            from rembg import new_session
            # Rembg verifies its pinned checksum before opening the cached ONNX file.
            self.sessions[name] = new_session(name, providers=["CPUExecutionProvider"])
            return self.sessions[name]

    def preload(self, download=False):
        for name in self.allowed:
            self.load(name, download)

    def segment(self, image, name=None, refine=False):
        name = name or self.default
        if name not in self.allowed:
            raise ValueError("Modèle non autorisé")
        session = self.sessions.get(name)
        if session is None:
            raise RuntimeError("Modèle non chargé. Redémarrez le service après préparation du cache.")
        from rembg import remove
        return remove(image, session=session, alpha_matting=refine).convert("RGBA")

    def describe(self):
        return {"default": self.default, "models": [{"id": n, "loaded": n in self.sessions} for n in self.allowed], "device": "cpu",
                "maxBytes": int(os.getenv("MAX_IMAGE_MB", "10")) * 1024 * 1024,
                "maxPixels": int(os.getenv("MAX_IMAGE_PIXELS", "20000000"))}


manager = ModelManager()

if __name__ == "__main__":
    manager.preload(download=True)
