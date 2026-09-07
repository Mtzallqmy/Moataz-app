import json
import re


def process_update(raw_update: str, config_json: str = "{}") -> str:
    update = json.loads(raw_update)
    message = update.get("message") or update.get("channel_post") or {}
    text = message.get("text") or message.get("caption")
    if not text:
        return json.dumps({"action": "pass", "note": "لا يوجد نص لتنظيفه"}, ensure_ascii=False)

    cleaned = re.sub(r"[ \t]+", " ", text)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip()
    return json.dumps({"action": "pass", "output_text": cleaned, "note": "تم تنظيف النص محليًا"}, ensure_ascii=False)
