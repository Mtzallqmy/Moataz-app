import json
import platform


def diagnostics() -> str:
    libraries = {}
    for module_name in ("requests", "bs4", "dateutil"):
        try:
            module = __import__(module_name)
            libraries[module_name] = getattr(module, "__version__", "installed")
        except Exception as exc:
            libraries[module_name] = f"ERROR: {exc}"
    libraries["xml"] = "stdlib"
    return json.dumps({"python": platform.python_version(), "libraries": libraries}, ensure_ascii=False)
