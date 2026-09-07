# Moataz Edge GitHub Repository Runtime

Moataz Edge 0.3 can download a trusted Python repository from GitHub and run it as a managed processor inside the Android foreground service. The phone remains the host: Telegram polling, route matching, retries, secrets, diagnostics and delivery are handled by Moataz Edge.

## Supported production mode

The repository must expose one Python entry point and one handler function. Defaults used by the Android UI:

- Entry point: `edge_entry.py`
- Handler: `process_update`
- Runtime: Python 3.13 embedded by Chaquopy

Example:

```python
from edge_sdk import parse_update, respond


def process_update(raw_update, config_json):
    update = parse_update(raw_update)
    message = update.get("message") or update.get("channel_post") or {}
    text = message.get("text") or ""
    if not text:
        return None
    return respond(f"وصلت رسالتك: {text}", "github repo handler")
```

The handler receives:

1. `raw_update`: the complete Telegram update JSON string.
2. `config_json`: route metadata JSON containing route id/name and keyword.

Accepted return values:

- `str`: sent as `output_text`.
- `dict`: `{"action":"pass|drop", "output_text":"...", "note":"..."}`.
- `None`: pass with no generated text.

To make the repository behave like a normal bot, create a Route with:

- Source: blank (all chats) or a specific Telegram chat.
- Worker: the GitHub repository app.
- Destination: `@source` (same incoming chat).

## Existing projects

Do not run an existing infinite polling loop such as `bot.infinity_polling()` or `application.run_polling()` inside the repository. Moataz Edge already owns Telegram polling and lifecycle management. Add a small adapter file (`edge_entry.py`) which calls the business logic from your existing project.

Example adapter:

```python
from my_project.logic import answer
from edge_sdk import parse_update, respond


def process_update(raw_update, config_json):
    update = parse_update(raw_update)
    message = update.get("message") or {}
    text = message.get("text") or ""
    return respond(answer(text))
```

This design gives the Android host control over start/stop, retries, battery-safe foreground execution, diagnostics and rollback.

## Dependencies

Packages bundled in the APK are available without installing anything on the phone:

- `requests`
- `beautifulsoup4` / `bs4`
- `python-dateutil`
- their bundled dependencies
- Python 3.13 standard library
- `edge_sdk`

If `requirements.txt` contains packages outside this allowlist, synchronization is rejected before activation. A pure-Python dependency may instead be committed under `vendor/` in the repository. Native wheels and arbitrary runtime `pip install` are intentionally not supported because they are not reliable or safe on Android/ARM64.

## GitHub repositories

Public repositories work without a GitHub token. Private repositories require a fine-grained GitHub token with read access to the repository. The token is stored encrypted with Android Keystore and is only attached to GitHub API requests.

Repository sync is transactional:

1. Download a GitHub archive.
2. Enforce archive/file/unpacked-size limits.
3. Reject ZIP path traversal.
4. Validate the configured Python entry point.
5. Validate requirements.
6. Preserve the currently active version as `previous`.
7. Activate the new version as `current`.
8. Keep a one-click rollback target.

Enabled repository apps can auto-sync when the node starts if the installed copy is older than the sync window.

## Connector plugins

Routes can currently deliver to:

- Telegram chat/channel identifiers.
- `@source` for a bot-style reply to the incoming chat.
- `plugin:<id>` for installed connector plugins.

Built-in connector plugin types in 0.3:

- HTTPS JSON: sends a POST event payload, optionally with an encrypted Bearer token.
- Local Log: writes the output to the app diagnostic log.

HTTP plugins require HTTPS. Failures enter the existing retry/diagnostic path instead of being silently ignored.

## Security model

Hosted repository code is trusted code. It executes in the embedded Python runtime of the app. The downloader prevents archive/path attacks and refuses arbitrary package installation, but this is not a hardened hostile-code sandbox. Do not add repositories you do not trust.
