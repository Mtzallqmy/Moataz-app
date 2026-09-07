import asyncio
import importlib.util
import inspect
import json
import os
import sys
import threading
import traceback

_lock = threading.RLock()
_contexts = {}
_known_repo_roots = set()


class _RepoContext:
    def __init__(self, repo_root):
        self.repo_root = os.path.realpath(repo_root)
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(
            target=self._run_loop,
            name="edge-repo-" + str(abs(hash(self.repo_root)))[:8],
            daemon=True,
        )
        self.thread.start()
        self.module = None
        self.module_key = None
        self.started = False

    def _run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def run_async(self, awaitable, timeout=300):
        future = asyncio.run_coroutine_threadsafe(_await_value(awaitable), self.loop)
        return future.result(timeout=timeout)


async def _await_value(value):
    return await value


def _context(repo_root):
    root = os.path.realpath(repo_root)
    ctx = _contexts.get(root)
    if ctx is None:
        ctx = _RepoContext(root)
        _contexts[root] = ctx
        _known_repo_roots.add(root)
    return ctx


def _safe_path(root, path):
    root = os.path.realpath(root)
    path = os.path.realpath(path)
    if path != root and not path.startswith(root + os.sep):
        raise RuntimeError("path escapes repository root")
    return path


def _apply_runtime_environment(repo_root, runtime_json):
    runtime = json.loads(runtime_json or "{}")
    data_dir = runtime.get("data_dir") or os.path.join(repo_root, ".edge-data")
    cache_dir = runtime.get("cache_dir") or os.path.join(data_dir, "cache")
    downloads_dir = runtime.get("downloads_dir") or os.path.join(data_dir, "downloads")
    for path in (data_dir, cache_dir, downloads_dir):
        os.makedirs(path, exist_ok=True)

    values = {
        "EDGE_HOST": "1",
        "EDGE_RUNTIME": str(runtime.get("runtime", "python-managed")),
        "EDGE_APP_ID": str(runtime.get("app_id", "")),
        "EDGE_DATA_DIR": data_dir,
        "EDGE_CACHE_DIR": cache_dir,
        "HOME": data_dir,
        "TMPDIR": cache_dir,
        "DOWNLOAD_DIR": downloads_dir,
        "DATABASE_URL": runtime.get("database_url") or (
            "sqlite+aiosqlite:///" + os.path.join(data_dir, "app.db")
        ),
    }
    token = runtime.get("bot_token")
    if token:
        values["BOT_TOKEN"] = token
    for key, value in values.items():
        os.environ[key] = str(value)
    return runtime


def _remove_stale_modules(repo_root):
    """Remove modules loaded from this repo when a new repository revision is activated.

    We intentionally do not remove third-party modules from the APK runtime pack.
    """
    root = os.path.realpath(repo_root)
    for name, module in list(sys.modules.items()):
        file_name = getattr(module, "__file__", None)
        if not file_name:
            continue
        try:
            real = os.path.realpath(file_name)
        except Exception:
            continue
        if real == root or real.startswith(root + os.sep):
            sys.modules.pop(name, None)


def _load_module(ctx, entry_path):
    repo_root = ctx.repo_root
    entry_path = _safe_path(repo_root, entry_path)
    if not os.path.isfile(entry_path):
        raise RuntimeError("entry point not found: %s" % os.path.relpath(entry_path, repo_root))

    vendor = os.path.join(repo_root, "vendor")
    for path in (vendor, repo_root):
        if os.path.isdir(path) and path not in sys.path:
            sys.path.insert(0, path)

    version_file = os.path.join(repo_root, ".edge-version")
    version = ""
    if os.path.isfile(version_file):
        try:
            with open(version_file, "r", encoding="utf-8") as handle:
                version = handle.read().strip()
        except Exception:
            version = ""
    key = (entry_path, os.path.getmtime(entry_path), version)
    if ctx.module is not None and ctx.module_key == key:
        return ctx.module

    if ctx.started and ctx.module is not None:
        shutdown = getattr(ctx.module, "shutdown", None)
        if callable(shutdown):
            try:
                value = shutdown()
                if inspect.isawaitable(value):
                    ctx.run_async(value, timeout=30)
            except Exception:
                pass
    ctx.started = False
    _remove_stale_modules(repo_root)

    module_name = "edge_repo_" + str(abs(hash((repo_root, entry_path, version))))
    spec = importlib.util.spec_from_file_location(module_name, entry_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("unable to load repository entry point")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    ctx.module = module
    ctx.module_key = key
    return module


def _run_callable(ctx, callback, *args, timeout=300):
    result = callback(*args)
    if inspect.isawaitable(result):
        return ctx.run_async(result, timeout=timeout)
    return result


def _ensure_started(ctx, module, runtime_config):
    if ctx.started:
        return
    startup = getattr(module, "startup", None)
    if callable(startup):
        _run_callable(ctx, startup, runtime_config, timeout=120)
    ctx.started = True


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


def process_update(repo_root, entry_path, handler_name, raw_update, config_json, runtime_json="{}"):
    with _lock:
        repo_root = os.path.realpath(repo_root)
        runtime_config = _apply_runtime_environment(repo_root, runtime_json)
        ctx = _context(repo_root)
        module = _load_module(ctx, entry_path)
        _ensure_started(ctx, module, runtime_config)
        handler = getattr(module, handler_name, None)
        if not callable(handler):
            raise RuntimeError("handler not found: %s" % handler_name)
        try:
            result = _run_callable(ctx, handler, raw_update, config_json, timeout=600)
            return json.dumps(_normalize(result), ensure_ascii=False)
        except Exception as exc:
            details = "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))[-6000:]
            raise RuntimeError("repository handler failed: %s\n%s" % (exc, details)) from exc


def health(repo_root, entry_path, runtime_json="{}"):
    with _lock:
        repo_root = os.path.realpath(repo_root)
        runtime_config = _apply_runtime_environment(repo_root, runtime_json)
        ctx = _context(repo_root)
        module = _load_module(ctx, entry_path)
        _ensure_started(ctx, module, runtime_config)
        callback = getattr(module, "health", None)
        if not callable(callback):
            return json.dumps({"ok": True, "detail": "entry point loaded"})
        result = _run_callable(ctx, callback, timeout=30)
        if isinstance(result, dict):
            return json.dumps(result, ensure_ascii=False)
        return json.dumps({"ok": bool(result), "detail": str(result)}, ensure_ascii=False)
