import json
from datetime import datetime, timezone


def process_update(raw_update: str) -> str:
    """First local Python hook for Telegram updates.

    Keep this function deterministic and fast. Later versions will load user
    plugins and return routing/transform actions instead of only diagnostics.
    """
    update = json.loads(raw_update)
    message = update.get("message") or update.get("channel_post") or {}
    chat = message.get("chat") or {}

    result = {
        "update_id": update.get("update_id"),
        "chat_id": chat.get("id"),
        "message_id": message.get("message_id"),
        "has_text": bool(message.get("text") or message.get("caption")),
        "processed_at": datetime.now(timezone.utc).isoformat(),
    }
    return json.dumps(result, ensure_ascii=False)
