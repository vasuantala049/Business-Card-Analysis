from pydantic import BaseModel, Field
from typing import Optional, List


class CardFields(BaseModel):
    name: Optional[str] = None
    designation: Optional[str] = None
    company: Optional[str] = None
    phones: List[str] = Field(default_factory=list)
    emails: List[str] = Field(default_factory=list)
    website: Optional[str] = None
    address: Optional[str] = None


class OcrVariantDebug(BaseModel):
    variant: str
    text: str
    score: float
    avg_confidence: float


class ExtractionResponse(BaseModel):
    raw_text: str
    fields: CardFields
    logo_image: Optional[str] = None  # base64 PNG, null if no logo region found
    extraction_source: str  # "openai" or "spacy_regex"
    confidence: float
    ocr_variants_debug: List[OcrVariantDebug] = Field(default_factory=list)
