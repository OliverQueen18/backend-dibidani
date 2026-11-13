from typing import Literal
import os
from pydantic import BaseModel, ConfigDict, Field


class Options(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    engine: Literal["rembg", "local"] = "rembg"
    model: str = Field(default_factory=lambda: os.getenv("REMBG_MODEL", "isnet-general-use"))
    removeBackground: bool = True
    whiteBackground: bool = True
    background: Literal["white", "transparent", "gray", "custom"] = "white"
    backgroundColor: str = Field(default="#FFFFFF", pattern=r"^#[0-9a-fA-F]{6}$")
    format: Literal["webp", "png", "jpeg"] = "webp"
    width: int = Field(default=1200, ge=100, le=2400)
    height: int = Field(default=1200, ge=100, le=2400)
    quality: int = Field(default=82, ge=40, le=100)
    refineEdges: bool = False
    enhance: bool = False
    brightness: float = Field(default=1, ge=0.5, le=1.5)
    contrast: float = Field(default=1, ge=0.5, le=1.5)
    rotation: Literal[0, 90, 180, 270] = 0
    recenter: bool = True
    fillRatio: float = Field(default=0.82, ge=0.55, le=0.98)
    zoom: float = Field(default=1, ge=0.6, le=2.2)
    offsetX: float = Field(default=0, ge=-0.4, le=0.4)
    offsetY: float = Field(default=0, ge=-0.4, le=0.4)
    shadow: bool = False
    watermark: bool = False
    watermarkSize: float = Field(default=0.06, ge=0.03, le=0.18)
    watermarkOpacity: float = Field(default=0.65, ge=0.2, le=1)
    watermarkMargin: float = Field(default=0.025, ge=0.01, le=0.08)
    watermarkPosition: str = "BOTTOM_RIGHT"
    watermarkVariant: str = "favicon"
