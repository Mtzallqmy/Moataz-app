import json


def process_update(raw_update: str, config_json: str = "{}") -> str:
    update = json.loads(raw_update)
    config = json.loads(config_json or "{}")
    message = update.get("message") or update.get("channel_post") or {}
    text = (message.get("text") or message.get("caption") or "")
    keyword = (config.get("keyword") or "").strip()
    if keyword and keyword.casefold() not in text.casefold():
        return json.dumps({"action": "drop", "note": f"تم إسقاط الرسالة: لا تحتوي {keyword}"}, ensure_ascii=False)
    return json.dumps({"action": "pass", "note": "اجتازت حارس الكلمات"}, ensure_ascii=False)
