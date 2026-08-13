from pydantic import BaseModel
from typing import Optional, List


class CardFields(BaseModel):
    name: Optional[str] = None
    designation: Optional[str] = None
    company: Optional[str] = None
    phones: List[str] = []
    emails: List[str] = []
    website: Optional[str] = None
    address: Optional[str] = None


class ExtractionResponse(BaseModel):
    raw_text: str
    fields: CardFields
    logo_image: Optional[str] = None  # base64 PNG, null if no logo region found
    extraction_source: str  # "gemini" or "spacy_regex"
    confidence: float
