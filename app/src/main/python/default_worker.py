import json
from datetime import datetime, timezone


def process_update(raw_update: str, config_json: str = "{}") -> str:
    update = json.loads(raw_update)
    message = update.get("message") or update.get("channel_post") or {}
    config = json.loads(config_json or "{}")
    result = {
        "action": "pass",
        "note": f"فحص محلي • {config.get('route_name', 'route')} • {datetime.now(timezone.utc).isoformat()}",
        "has_text": bool(message.get("text") or message.get("caption")),
    }
    return json.dumps(result, ensure_ascii=False)
