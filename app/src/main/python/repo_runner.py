import importlib.util
import json
import os
import sys
import threading

_lock = threading.RLock()
_cache = {}


def _load_module(repo_root, entry_path):
    repo_root = os.path.realpath(repo_root)
    entry_path = os.path.realpath(entry_path)
    if not entry_path.startswith(repo_root + os.sep):
        raise RuntimeError("entry point escapes repository root")
    if not os.path.isfile(entry_path):
        raise RuntimeError("entry point not found")

    vendor = os.path.join(repo_root, "vendor")
    for path in (repo_root, vendor):
        if os.path.isdir(path) and path not in sys.path:
            sys.path.insert(0, path)

    mtime = os.path.getmtime(entry_path)
    key = (entry_path, mtime)
    module = _cache.get(key)
    if module is not None:
        return module

    stale = [k for k in _cache if k[0] == entry_path and k != key]
    for item in stale:
        _cache.pop(item, None)

    module_name = "edge_repo_" + str(abs(hash(entry_path)))
    spec = importlib.util.spec_from_file_location(module_name, entry_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("unable to load repository entry point")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    _cache[key] = module
    return module


def _normalize(result):
    if result is None:
        return {"action": "pass", "output_text": None, "note": "handler returned no response"}
    if isinstance(result, str):
        return {"action": "pass", "output_text": result, "note": "repository response"}
    if isinstance(result, dict):
        return {
            "action": str(result.get("action", "pass")),
            "output_text": result.get("output_text", result.get("text")),
            "note": str(result.get("note", "repository response")),
        }
    raise RuntimeError("handler must return dict, string or None")


def process_update(repo_root, entry_path, handler_name, raw_update, config_json):
    with _lock:
        module = _load_module(repo_root, entry_path)
        handler = getattr(module, handler_name, None)
        if not callable(handler):
            raise RuntimeError("handler not found: %s" % handler_name)
        result = handler(raw_update, config_json)
        return json.dumps(_normalize(result), ensure_ascii=False)
