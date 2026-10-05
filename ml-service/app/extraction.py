import os
import re
import json
import logging
import time
from typing import Optional, Any

from openai import OpenAI

logger = logging.getLogger(__name__)

EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}", re.I)
MUNGED_EMAIL_RE = re.compile(
    r"[a-zA-Z0-9._%+-]+\s*@\s*[a-zA-Z0-9.-]+\s*\.\s*[a-zA-Z]{2,}",
    re.I,
)
PHONE_RE = re.compile(r"(?:\+|00)?\d[\d\s().-]{6,}\d")
WEBSITE_RE = re.compile(r"\b(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?\b", re.I)

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
    "pvt",
    "private",
    "limited",
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
_OPENAI_QUOTA_BACKOFF_UNTIL = 0.0


def _structured_line_text(line: Any) -> str:
    if line is None:
        return ""
    if hasattr(line, "text"):
        return str(getattr(line, "text") or "")
    if isinstance(line, dict):
        return str(line.get("text") or "")
    return str(line)


def _structured_line_bbox(line: Any) -> tuple[int, int, int, int]:
    if hasattr(line, "bbox"):
        bbox = getattr(line, "bbox")
    elif isinstance(line, dict):
        bbox = line.get("bbox")
    else:
        bbox = None

    if not bbox or len(bbox) != 4:
        return (0, 0, 0, 0)

    x1, y1, x2, y2 = bbox
    return (int(x1), int(y1), int(x2), int(y2))


def _structured_lines(ocr_lines: Optional[list[Any]]) -> list[str]:
    if not ocr_lines:
        return []

    ordered = sorted(
        ocr_lines,
        key=lambda line: (_structured_line_bbox(line)[1], _structured_line_bbox(line)[0]),
    )
    return [_compact_contact_line(_structured_line_text(line)) for line in ordered if _structured_line_text(line).strip()]


def _normalize_contact_text(value: str) -> str:
    value = re.sub(r"\s*=\s*", "@", value)
    value = re.sub(r"\s*@\s*", "@", value)
    value = re.sub(r"\s*\.\s*", ".", value)
    value = re.sub(r"\s*\(\s*", "(", value)
    value = re.sub(r"\s*\)\s*", ")", value)
    value = re.sub(r"\s*\+\s*", "+", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def _extract_emails(text: str) -> list[str]:
    emails = []
    for variant in [text, _normalize_contact_text(text), text.lower()]:
        for match in list(EMAIL_RE.findall(variant)) + list(MUNGED_EMAIL_RE.findall(variant)):
            normalized = _normalize_contact_text(match).replace(" ", "").lower()
            if EMAIL_RE.fullmatch(normalized):
                emails.append(normalized)

    return list(dict.fromkeys(emails))


def _extract_emails_from_lines(lines: list[str]) -> list[str]:
    emails = []

    joined_variants = [
        "\n".join(lines),
        " ".join(lines),
        _normalize_contact_text("\n".join(lines)),
        _normalize_contact_text(" ".join(lines)),
    ]

    # Try the full block first, then adjacent line pairs, because OCR often
    # splits email local-parts and domains across two lines.
    for variant in joined_variants:
        emails.extend(_extract_emails(variant))

    for idx in range(len(lines) - 1):
        left = _normalize_contact_text(lines[idx])
        right = _normalize_contact_text(lines[idx + 1])

        left_local = left.replace("@", "").rstrip(".:;,-_")
        right_domain = right.lstrip("@ ").strip(".:;,-_")

        if left_local and right_domain and "." in right_domain:
            candidate = f"{left_local}@{right_domain}".replace(" ", "")
            if EMAIL_RE.fullmatch(candidate.lower()):
                emails.append(candidate.lower())

        # Also handle the common OCR mistake where @ becomes = on one line.
        if "=" in left and "." in right:
            candidate = f"{left.replace('=', '').replace(' ', '')}@{right.replace(' ', '')}".lower()
            if EMAIL_RE.fullmatch(candidate):
                emails.append(candidate)

    return list(dict.fromkeys(emails))


def _extract_phones(text: str) -> list[str]:
    phones = []
    seen = set()
    for variant in [text, _normalize_contact_text(text)]:
        for match in PHONE_RE.findall(variant):
            candidate = _normalize_contact_text(match)
            digits = re.sub(r"\D", "", candidate)
            if len(digits) < 7 or len(digits) > 15:
                continue

            key = digits.lstrip("0") or digits
            if key in seen:
                continue
            seen.add(key)

            cleaned = re.sub(r"\s+", " ", candidate).strip(" ,;:|")
            phones.append(cleaned)

    return phones


def _extract_websites(text: str) -> list[str]:
    websites = []
    for variant in [text, _normalize_contact_text(text)]:
        for match in WEBSITE_RE.findall(variant):
            cleaned = _normalize_contact_text(match).strip(" ,;:|")
            if "@" not in cleaned:
                websites.append(cleaned)

    return list(dict.fromkeys(websites))


def _line_tokens(line: str) -> list[str]:
    return re.findall(r"[a-zA-Z0-9&]+", line.lower())


def _hint_count(line: str, hints: set[str]) -> int:
    lowered = line.lower()
    tokens = set(_line_tokens(line))
    count = 0
    for hint in hints:
        if " " in hint:
            if hint in lowered:
                count += 1
        elif hint in tokens:
            count += 1
    return count


def _regex_fields(
    text: str,
    lines: Optional[list[str]] = None,
    extra_texts: Optional[list[str]] = None,
) -> dict:
    search_text = "\n".join(lines) if lines else text
    normalized_variants = [search_text, _normalize_contact_text(search_text)]
    if extra_texts:
        for extra in extra_texts:
            if extra and extra.strip():
                normalized_variants.extend([extra, _normalize_contact_text(extra)])

    emails = []
    phones = []
    websites = []

    for variant in normalized_variants:
        emails.extend(_extract_emails(variant))
        phones.extend(_extract_phones(variant))
        websites.extend(_extract_websites(variant))

    if lines:
        emails.extend(_extract_emails_from_lines(lines))

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


def _contains_contact_details(line: str) -> bool:
    normalized = _normalize_contact_text(line)
    return bool(
        EMAIL_RE.search(normalized)
        or MUNGED_EMAIL_RE.search(normalized)
        or PHONE_RE.search(normalized)
        or WEBSITE_RE.search(normalized)
    )


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
    if _hint_count(line, COMPANY_HINTS) > 0:
        return True
    return line.isupper() and 2 <= len(line.split()) <= 6


def _looks_like_designation(line: str) -> bool:
    return _hint_count(line, DESIGNATION_HINTS) > 0


def _looks_like_address(line: str) -> bool:
    has_address_hint = _hint_count(line, ADDRESS_HINTS) > 0
    has_long_number = bool(re.search(r"\d{4,}", line))
    has_address_punctuation = "," in line
    return has_address_hint or has_long_number or has_address_punctuation


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
    score -= 4 * _hint_count(line, DESIGNATION_HINTS)
    score -= 4 * _hint_count(line, COMPANY_HINTS)
    score -= 3 * _hint_count(line, ADDRESS_HINTS)
    if any(ch.isdigit() for ch in line):
        score -= 5
    return score


def _score_company_candidate(line: str) -> int:
    if not _is_plausible_text_line(line):
        return -100

    score = 0
    score += 4 * _hint_count(line, COMPANY_HINTS)
    if any(token in line.lower() for token in ("private limited", "pvt ltd", "pvt. ltd", "limited")):
        score += 3
    if line.isupper():
        score += 2
    word_count = len(line.split())
    if 2 <= word_count <= 5:
        score += 1
    if word_count >= 4:
        score += 1
    if word_count >= 5:
        score += 1
    if len(line) >= 24:
        score += 1
    if len(line) <= 22:
        score -= 1
    score -= 4 * _hint_count(line, DESIGNATION_HINTS)
    score -= 3 * _hint_count(line, ADDRESS_HINTS)
    if re.search(r"\d", line):
        score -= 2
    return score


def _score_designation_candidate(line: str) -> int:
    if not _is_plausible_text_line(line):
        return -100

    score = 0
    score += 4 * _hint_count(line, DESIGNATION_HINTS)
    if 2 <= len(line.split()) <= 4:
        score += 1
    score -= 4 * _hint_count(line, COMPANY_HINTS)
    score -= 4 * _hint_count(line, ADDRESS_HINTS)
    if re.search(r"\d", line):
        score -= 3
    if len(line) > 40:
        score -= 2
    return score


def _score_address_candidate(line: str) -> int:
    if EMAIL_RE.search(line) or PHONE_RE.search(line) or WEBSITE_RE.search(line):
        return -100

    score = 0
    score += 3 * _hint_count(line, ADDRESS_HINTS)
    if re.search(r"\d", line):
        score += 2
    if "," in line:
        score += 2
    if len(line.split()) >= 4:
        score += 1
    score -= 3 * _hint_count(line, DESIGNATION_HINTS)
    score -= 2 * _hint_count(line, COMPANY_HINTS)
    return score


def _resolve_field_conflicts(fields: dict, lines: list[str]) -> dict:
    """Correct obvious cross-field swaps (company/designation/address/name)."""
    result = dict(fields)
    non_contact_lines = [line for line in lines if _is_plausible_text_line(line)]

    best_name = max(non_contact_lines, key=_score_name_candidate, default=None)
    best_company = max(non_contact_lines, key=_score_company_candidate, default=None)
    best_designation = max(non_contact_lines, key=_score_designation_candidate, default=None)

    if result.get("name") and _score_name_candidate(result["name"]) < 1 and best_name and _score_name_candidate(best_name) > _score_name_candidate(result["name"]):
        result["name"] = best_name

    if result.get("company") and _score_company_candidate(result["company"]) < 1 and best_company and _score_company_candidate(best_company) > _score_company_candidate(result["company"]):
        result["company"] = best_company

    if result.get("designation") and _score_designation_candidate(result["designation"]) < 1 and best_designation and _score_designation_candidate(best_designation) > _score_designation_candidate(result["designation"]):
        result["designation"] = best_designation

    company = result.get("company")
    designation = result.get("designation")
    if company and designation:
        company_as_company = _score_company_candidate(company)
        company_as_designation = _score_designation_candidate(company)
        designation_as_designation = _score_designation_candidate(designation)
        designation_as_company = _score_company_candidate(designation)
        if company_as_designation > company_as_company and designation_as_company > designation_as_designation:
            result["company"], result["designation"] = designation, company

    address_candidates = [line for line in lines if _score_address_candidate(line) > 1]
    if address_candidates:
        unique_candidates = list(dict.fromkeys(address_candidates))
        combined = ", ".join(unique_candidates)
        existing = result.get("address")
        if not existing or _score_address_candidate(existing) < 2:
            result["address"] = combined

    # Avoid assigning the exact same line to multiple semantic fields.
    used = {}
    for field in ("name", "designation", "company"):
        value = result.get(field)
        if value:
            used.setdefault(value, []).append(field)

    for value, field_names in used.items():
        if len(field_names) <= 1:
            continue
        ranking = sorted(
            field_names,
            key=lambda f: {
                "name": _score_name_candidate(value),
                "designation": _score_designation_candidate(value),
                "company": _score_company_candidate(value),
            }[f],
            reverse=True,
        )
        keeper = ranking[0]
        for f in field_names:
            if f != keeper:
                result[f] = None

    return result


def _heuristic_fields(text: str, lines: Optional[list[str]] = None) -> dict:
    lines = lines or [_compact_contact_line(line) for line in _clean_lines(text)]
    if not lines:
        return {"name": None, "designation": None, "company": None, "address": None}

    contact_index = len(lines)
    for idx, line in enumerate(lines):
        if _contains_contact_details(line):
            contact_index = idx
            break

    top_block = lines[:contact_index] if contact_index > 0 else lines[:3]
    bottom_block = lines[contact_index + 1 :] if contact_index < len(lines) else []

    candidate_lines = [line for line in top_block if _is_plausible_text_line(line)] or [
        line for line in lines if _is_plausible_text_line(line)
    ]

    name = max(candidate_lines, key=_score_name_candidate, default=None)
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
        company_candidates = [line for line in top_block if line != name] or [line for line in lines if line != name]
        if company_candidates:
            ranked_candidates = sorted(company_candidates, key=_score_company_candidate, reverse=True)
            company = ranked_candidates[0]
            if len(ranked_candidates) > 1:
                top_score = _score_company_candidate(ranked_candidates[0])
                second_score = _score_company_candidate(ranked_candidates[1])
                if second_score >= top_score - 1 and len(ranked_candidates[1].split()) > len(company.split()):
                    company = ranked_candidates[1]
            if _score_company_candidate(company) < 0:
                company = None

    if designation is None:
        designation_candidates = [line for line in top_block if line != name and line != company] or [
            line for line in lines if line != name and line != company
        ]
        if designation_candidates:
            designation = max(designation_candidates, key=_score_designation_candidate)
            if _score_designation_candidate(designation) < 0:
                designation = None

    if address is None:
        address_lines = [line for line in bottom_block if _score_address_candidate(line) > 1]
        if not address_lines:
            address_lines = [line for line in lines if _score_address_candidate(line) > 1]
        if address_lines:
            address = ", ".join(dict.fromkeys(address_lines))

    # If the name ended up looking suspiciously like a company or designation,
    # try the top few OCR lines instead.
    if name and (not _looks_like_name(name) or _looks_like_company(name) or _looks_like_designation(name)):
        top_candidates = [line for line in lines[:3] if _looks_like_name(line)]
        if top_candidates:
            name = max(top_candidates, key=_score_name_candidate)

    result = {
        "name": name,
        "designation": designation,
        "company": company,
        "address": address,
    }
    return _resolve_field_conflicts(result, lines)


def _spacy_names(text: str) -> dict:
    global _nlp
    if _nlp is None:
        import spacy
        _nlp = spacy.load("en_core_web_sm")

    doc = _nlp(text)
    person = next((ent.text for ent in doc.ents if ent.label_ == "PERSON"), None)
    org = next((ent.text for ent in doc.ents if ent.label_ == "ORG"), None)

    return {"name": person, "company": org, "designation": None, "address": None}


def _openai_fields(text: str) -> Optional[dict]:
    global _OPENAI_QUOTA_BACKOFF_UNTIL

    now = time.time()
    if now < _OPENAI_QUOTA_BACKOFF_UNTIL:
        logger.info(
            "Skipping OpenAI until %.0f due to recent quota failure; using spaCy + regex fallback",
            _OPENAI_QUOTA_BACKOFF_UNTIL,
        )
        return None

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        logger.info("OPENAI_API_KEY not set; using spaCy + regex fallback")
        return None

    try:
        client = OpenAI(api_key=api_key)
        logger.info("OPENAI_API_KEY detected; using OpenAI for field extraction")
        model_candidates = [
            os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            "gpt-4.1-mini",
        ]

        prompt = (
            "Extract structured contact fields from this OCR text taken from a "
            "business card. Return ONLY compact JSON with keys: name, designation, "
            "company, website, address (each a string or null). Do not include "
            "emails or phone numbers, those are handled separately. "
            "Important mapping rules: name must be a person name; designation must "
            "be a role/title; company must be an organization name; address must "
            "contain location-like text and never a pure company or designation. "
            "If uncertain, use null for that field.\n\n"
            f"OCR text:\n{text}"
        )
        last_exc = None
        for model_name in model_candidates:
            try:
                logger.info("Trying OpenAI model %s", model_name)
                response = client.chat.completions.create(
                    model=model_name,
                    temperature=0,
                    messages=[
                        {
                            "role": "system",
                            "content": "You extract business card contact details and must return compact JSON only.",
                        },
                        {"role": "user", "content": prompt},
                    ],
                )
                content = (response.choices[0].message.content or "").strip()
                cleaned = (
                    content
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

                logger.info("OpenAI extraction succeeded with model %s", model_name)
                return parsed
            except Exception as exc:
                last_exc = exc
                logger.warning("OpenAI model %s failed: %s", model_name, exc)
                if "quota" in str(exc).lower() or "429" in str(exc):
                    _OPENAI_QUOTA_BACKOFF_UNTIL = time.time() + 300
                    logger.info("OpenAI quota hit; backing off OpenAI attempts for 300 seconds")
                    break

        if last_exc is not None:
            raise last_exc
    except Exception as exc:  # rate limit, network issue, bad JSON, etc.
        logger.warning("OpenAI extraction failed, falling back to spaCy: %s", exc)
        return None


def extract_fields(
    text: str,
    ocr_lines: Optional[list[Any]] = None,
    extra_texts: Optional[list[str]] = None,
):
    lines = _structured_lines(ocr_lines) or [_compact_contact_line(line) for line in _clean_lines(text)]
    base = _regex_fields(text, lines, extra_texts=extra_texts)
    base.update({k: v for k, v in _heuristic_fields(text, lines).items() if v and not base.get(k)})

    openai_result = _openai_fields(text)
    if openai_result:
        openai_fields = {k: v for k, v in openai_result.items() if v}
        if openai_fields:
            logger.info("Extraction source: OpenAI")
            base.update(openai_fields)
            heuristic_fields = _heuristic_fields(text, lines)

            # Prefer the stronger heuristic company/designation/name when the
            # LLM returns a shorter or noisier answer from the OCR text.
            for field, scorer in (
                ("name", _score_name_candidate),
                ("designation", _score_designation_candidate),
                ("company", _score_company_candidate),
                ("address", _score_address_candidate),
            ):
                heuristic_value = heuristic_fields.get(field)
                current_value = base.get(field)
                if heuristic_value and (
                    not current_value
                    or scorer(heuristic_value) > scorer(current_value) + 1
                ):
                    base[field] = heuristic_value

            base = _resolve_field_conflicts(base, lines)
            source = "openai"
        else:
            logger.info("OpenAI returned no structured fields; falling back to spaCy + regex")
            spacy_result = _spacy_names(text)
            base.update({k: v for k, v in spacy_result.items() if v and not base.get(k)})
            base = _resolve_field_conflicts(base, lines)
            source = "spacy_regex"
    else:
        logger.info("Extraction source: spaCy + regex")
        spacy_result = _spacy_names(text)
        base.update({k: v for k, v in spacy_result.items() if v and not base.get(k)})
        base = _resolve_field_conflicts(base, lines)
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
