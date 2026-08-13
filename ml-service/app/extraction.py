import os
import re
import json
import logging
import time
from typing import Optional

logger = logging.getLogger(__name__)

EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")
PHONE_RE = re.compile(r"(\+?\d{1,3}[\s-]?)?(\d{10}|\(?\d{3,4}\)?[\s-]?\d{3}[\s-]?\d{3,4})")
WEBSITE_RE = re.compile(r"\b(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?\b")

DESIGNATION_HINTS = {
    "agent",
    "manager",
    "director",
    "developer",
    "engineer",
    "consultant",
    "founder",
    "ceo",
    "cto",
    "coo",
    "president",
    "lead",
    "specialist",
    "designer",
    "architect",
    "officer",
}

COMPANY_HINTS = {
    "inc",
    "llc",
    "ltd",
    "corp",
    "corporation",
    "company",
    "co.",
    "group",
    "solutions",
    "services",
    "studio",
    "realty",
    "real estate",
    "agency",
    "technologies",
    "systems",
}

ADDRESS_HINTS = {
    "street",
    "st",
    "road",
    "rd",
    "avenue",
    "ave",
    "lane",
    "ln",
    "drive",
    "dr",
    "suite",
    "ste",
    "floor",
    "fl",
    "city",
    "state",
    "zip",
    "postal",
    "box",
}

_nlp = None
_GEMINI_QUOTA_BACKOFF_UNTIL = 0.0


def _regex_fields(text: str) -> dict:
    normalized_variants = [
        text,
        re.sub(r"\s*@\s*", "@", text),
        re.sub(r"\s*\.\s*", ".", text),
    ]

    emails = []
    phones = []
    websites = []

    for variant in normalized_variants:
        emails.extend(EMAIL_RE.findall(variant))
        phones.extend(m.group(0).strip() for m in PHONE_RE.finditer(variant))
        websites.extend(w for w in WEBSITE_RE.findall(variant) if "@" not in w)

    emails = list(dict.fromkeys(emails))
    phones = list(dict.fromkeys(phones))
    websites = list(dict.fromkeys(websites))

    return {
        "emails": emails,
        "phones": phones,
        "website": websites[0] if websites else None,
    }


def _clean_lines(text: str) -> list[str]:
    lines = []
    for raw in text.splitlines():
        line = re.sub(r"\s+", " ", raw).strip(" ,;:|•-")
        if line:
            lines.append(line)
    return lines


def _compact_contact_line(line: str) -> str:
    """Remove common OCR spacing around contact punctuation without collapsing
    the whole line, which helps when OCR outputs things like 'name @ domain . com'."""
    line = re.sub(r"\s*@\s*", "@", line)
    line = re.sub(r"\s*\.\s*", ".", line)
    line = re.sub(r"\s*-/\s*", "/", line)
    line = re.sub(r"\s*-\s*", "-", line)
    return re.sub(r"\s+", " ", line).strip()


def _is_plausible_text_line(line: str) -> bool:
    """Return True for lines that look like real card text, not OCR noise."""
    if not line:
        return False
    if EMAIL_RE.search(line) or PHONE_RE.search(line) or WEBSITE_RE.search(line):
        return False
    if re.fullmatch(r"[\d\W_]+", line):
        return False
    letters = re.findall(r"[A-Za-z]", line)
    return len(letters) >= 2


def _looks_like_name(line: str) -> bool:
    if EMAIL_RE.search(line) or PHONE_RE.search(line) or WEBSITE_RE.search(line):
        return False
    if any(ch.isdigit() for ch in line):
        return False

    words = line.split()
    if not (2 <= len(words) <= 4):
        return False

    lowered = line.lower()
    if any(hint in lowered for hint in DESIGNATION_HINTS):
        return False
    if any(hint in lowered for hint in COMPANY_HINTS):
        return False
    if any(hint in lowered for hint in ADDRESS_HINTS):
        return False

    capitalized = sum(1 for word in words if word[:1].isupper())
    return capitalized >= max(1, len(words) - 1)


def _looks_like_company(line: str) -> bool:
    lowered = line.lower()
    if EMAIL_RE.search(line) or PHONE_RE.search(line) or WEBSITE_RE.search(line):
        return False
    if any(hint in lowered for hint in COMPANY_HINTS):
        return True
    return line.isupper() and 2 <= len(line.split()) <= 6


def _looks_like_designation(line: str) -> bool:
    lowered = line.lower()
    return any(hint in lowered for hint in DESIGNATION_HINTS)


def _looks_like_address(line: str) -> bool:
    lowered = line.lower()
    return any(hint in lowered for hint in ADDRESS_HINTS) or bool(re.search(r"\d{4,}", line))


def _score_name_candidate(line: str) -> int:
    if not _is_plausible_text_line(line):
        return -100

    score = 0
    words = line.split()
    if 2 <= len(words) <= 4:
        score += 4
    if all(word[:1].isupper() for word in words if word):
        score += 3
    if len(line) <= 28:
        score += 1
    if any(hint in line.lower() for hint in DESIGNATION_HINTS | COMPANY_HINTS | ADDRESS_HINTS):
        score -= 8
    if any(ch.isdigit() for ch in line):
        score -= 5
    return score


def _score_company_candidate(line: str) -> int:
    if not _is_plausible_text_line(line):
        return -100

    lowered = line.lower()
    score = 0
    if any(hint in lowered for hint in COMPANY_HINTS):
        score += 6
    if line.isupper():
        score += 2
    if 2 <= len(line.split()) <= 5:
        score += 1
    if any(hint in lowered for hint in DESIGNATION_HINTS):
        score -= 8
    if any(hint in lowered for hint in ADDRESS_HINTS):
        score -= 6
    return score


def _score_designation_candidate(line: str) -> int:
    if not _is_plausible_text_line(line):
        return -100

    lowered = line.lower()
    score = 0
    if any(hint in lowered for hint in DESIGNATION_HINTS):
        score += 6
    if 2 <= len(line.split()) <= 4:
        score += 1
    if any(hint in lowered for hint in COMPANY_HINTS):
        score -= 6
    if any(hint in lowered for hint in ADDRESS_HINTS):
        score -= 6
    return score


def _heuristic_fields(text: str) -> dict:
    lines = [_compact_contact_line(line) for line in _clean_lines(text)]
    if not lines:
        return {"name": None, "designation": None, "company": None, "address": None}

    contact_index = len(lines)
    for idx, line in enumerate(lines):
        if EMAIL_RE.search(line) or PHONE_RE.search(line) or WEBSITE_RE.search(line):
            contact_index = idx
            break

    search_space = lines[:contact_index] if contact_index > 0 else lines

    # Use all non-contact lines for scoring so a card still works even when OCR
    # order is messy or the contact line is merged with a title.
    non_contact_lines = [line for line in lines if _is_plausible_text_line(line)]
    name = max(non_contact_lines, key=_score_name_candidate, default=None)
    if name is not None and _score_name_candidate(name) < 0:
        name = None

    designation = None
    company = None
    address = None

    if name and name in lines:
        start = lines.index(name) + 1
        for line in lines[start:]:
            if designation is None and _score_designation_candidate(line) > 0:
                designation = line
                continue
            if company is None and _score_company_candidate(line) > 0:
                company = line
                continue
            if address is None and _looks_like_address(line):
                address = line

    if company is None:
        company_candidates = [line for line in non_contact_lines if line != name]
        if company_candidates:
            company = max(company_candidates, key=_score_company_candidate)
            if _score_company_candidate(company) < 0:
                company = None

    if designation is None:
        designation_candidates = [line for line in non_contact_lines if line != name and line != company]
        if designation_candidates:
            designation = max(designation_candidates, key=_score_designation_candidate)
            if _score_designation_candidate(designation) < 0:
                designation = None

    if address is None:
        address_lines = [line for line in lines if _looks_like_address(line)]
        if address_lines:
            address = ", ".join(dict.fromkeys(address_lines))

    # If the name ended up looking suspiciously like a company or designation,
    # try the top few OCR lines instead.
    if name and (not _looks_like_name(name) or _looks_like_company(name) or _looks_like_designation(name)):
        top_candidates = [line for line in lines[:3] if _looks_like_name(line)]
        if top_candidates:
            name = max(top_candidates, key=_score_name_candidate)

    return {
        "name": name,
        "designation": designation,
        "company": company,
        "address": address,
    }


def _spacy_names(text: str) -> dict:
    global _nlp
    if _nlp is None:
        import spacy
        _nlp = spacy.load("en_core_web_sm")

    doc = _nlp(text)
    person = next((ent.text for ent in doc.ents if ent.label_ == "PERSON"), None)
    org = next((ent.text for ent in doc.ents if ent.label_ == "ORG"), None)

    return {"name": person, "company": org, "designation": None, "address": None}


def _gemini_fields(text: str) -> Optional[dict]:
    global _GEMINI_QUOTA_BACKOFF_UNTIL

    now = time.time()
    if now < _GEMINI_QUOTA_BACKOFF_UNTIL:
        logger.info("Skipping Gemini until %.0f due to recent quota failure; using spaCy + regex fallback", _GEMINI_QUOTA_BACKOFF_UNTIL)
        return None

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        logger.info("GEMINI_API_KEY not set; using spaCy + regex fallback")
        return None

    try:
        import google.generativeai as genai

        logger.info("GEMINI_API_KEY detected; using Gemini for field extraction")
        genai.configure(api_key=api_key)
        model_candidates = [
            os.getenv("GEMINI_MODEL", "gemini-2.0-flash"),
            "gemini-1.5-flash",
        ]

        prompt = (
            "Extract structured contact fields from this OCR text taken from a "
            "business card. Return ONLY compact JSON with keys: name, designation, "
            "company, website, address (each a string or null). Do not include "
            "emails or phone numbers, those are handled separately.\n\n"
            f"OCR text:\n{text}"
        )
        last_exc = None
        for model_name in model_candidates:
            try:
                logger.info("Trying Gemini model %s", model_name)
                model = genai.GenerativeModel(model_name)
                response = model.generate_content(prompt)
                cleaned = (
                    response.text.strip()
                    .removeprefix("```json")
                    .removesuffix("```")
                    .strip()
                )
                try:
                    parsed = json.loads(cleaned)
                except json.JSONDecodeError:
                    match = re.search(r"\{.*\}", cleaned, re.DOTALL)
                    if not match:
                        raise
                    parsed = json.loads(match.group(0))

                logger.info("Gemini extraction succeeded with model %s", model_name)
                return parsed
            except Exception as exc:
                last_exc = exc
                logger.warning("Gemini model %s failed: %s", model_name, exc)
                if "quota" in str(exc).lower() or "429" in str(exc):
                    _GEMINI_QUOTA_BACKOFF_UNTIL = time.time() + 300
                    logger.info("Gemini quota hit; backing off Gemini attempts for 300 seconds")
                    break

        if last_exc is not None:
            raise last_exc
    except Exception as exc:  # rate limit, network issue, bad JSON, etc.
        logger.warning("Gemini extraction failed, falling back to spaCy: %s", exc)
        return None


def extract_fields(text: str):
    base = _regex_fields(text)
    base.update({k: v for k, v in _heuristic_fields(text).items() if v and not base.get(k)})

    gemini_result = _gemini_fields(text)
    if gemini_result:
        gemini_fields = {k: v for k, v in gemini_result.items() if v}
        if gemini_fields:
            logger.info("Extraction source: Gemini")
            base.update(gemini_fields)
            source = "gemini"
        else:
            logger.info("Gemini returned no structured fields; falling back to spaCy + regex")
            spacy_result = _spacy_names(text)
            base.update({k: v for k, v in spacy_result.items() if v and not base.get(k)})
            source = "spacy_regex"
    else:
        logger.info("Extraction source: spaCy + regex")
        spacy_result = _spacy_names(text)
        base.update({k: v for k, v in spacy_result.items() if v and not base.get(k)})
        source = "spacy_regex"

    logger.info(
        "Final extracted fields source=%s name=%r company=%r designation=%r website=%r emails=%r phones=%r address=%r",
        source,
        base.get("name"),
        base.get("company"),
        base.get("designation"),
        base.get("website"),
        base.get("emails"),
        base.get("phones"),
        base.get("address"),
    )

    return base, source
