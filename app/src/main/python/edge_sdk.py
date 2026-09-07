import json


def parse_update(raw_update):
    if isinstance(raw_update, str):
        return json.loads(raw_update)
    return raw_update


def parse_config(config_json):
    if not config_json:
        return {}
    if isinstance(config_json, str):
        return json.loads(config_json)
    return config_json


def respond(text, note="repository response"):
    return {"action": "pass", "output_text": str(text), "note": str(note)}


def passthrough(note="pass"):
    return {"action": "pass", "output_text": None, "note": str(note)}


def drop(note="dropped"):
    return {"action": "drop", "output_text": None, "note": str(note)}
