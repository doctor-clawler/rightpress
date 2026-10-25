#!/usr/bin/env python3
"""Validate or scaffold native Android translations. Python standard library only."""

import argparse
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])")
TAG = re.compile(r"[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-(?:[A-Z]{2}|\d{3}))?")


def default_locale(res):
    properties = (res / "resources.properties").read_text(encoding="utf-8")
    matches = re.findall(r"^unqualifiedResLocale=(.+)$", properties, re.MULTILINE)
    if len(matches) != 1 or not TAG.fullmatch(matches[0]):
        raise ValueError("resources.properties needs one unqualifiedResLocale=<BCP-47 tag>")
    return matches[0]


def tag_for_folder(folder, default):
    if folder == "values":
        return default
    qualifier = folder.removeprefix("values-")
    if qualifier.startswith("b+"):
        tag = qualifier[2:].replace("+", "-")
    else:
        tag = qualifier.replace("-r", "-")
    # Android's historical resource qualifier aliases.
    tag = {"in": "id", "iw": "he", "ji": "yi"}.get(tag, tag)
    if not TAG.fullmatch(tag):
        raise ValueError(f"{folder}: unsupported locale qualifier; use values-b+<BCP+47>")
    return tag


def folder_for_tag(tag):
    if not TAG.fullmatch(tag):
        raise ValueError("Use a BCP-47 tag such as fi, pt-BR or zh-Hant")
    if tag in {"id", "he", "yi"}:
        return "values-" + {"id": "in", "he": "iw", "yi": "ji"}[tag]
    if "-" in tag:
        return "values-b+" + tag.replace("-", "+")
    return "values-" + tag


def strings(path):
    root = ET.parse(path).getroot()
    result = {}
    for item in root.findall("string"):
        name = item.get("name")
        if not name or name in result:
            raise ValueError(f"{path.name}: missing or duplicate string name {name!r}")
        result[name] = ("".join(item.itertext()), item.get("translatable") != "false")
    return result


def formats(value):
    return Counter((position or "implicit", kind) for position, kind in FORMAT.findall(value) if kind != "%")


def catalogs(res):
    default = default_locale(res)
    found = {}
    for path in sorted(res.glob("values*/strings.xml")):
        tag = tag_for_folder(path.parent.name, default)
        if tag in found:
            raise ValueError(f"Duplicate locale {tag}: {path.parent.name}")
        found[tag] = path
    return default, found


def validate(res):
    default, found = catalogs(res)
    baseline = strings(res / "values/strings.xml")
    required = {name: value for name, (value, translated) in baseline.items() if translated}
    errors = []
    for tag, path in found.items():
        entries = strings(path)
        if tag != default:
            missing = set(required) - entries.keys()
            extra = entries.keys() - baseline.keys()
            if missing:
                errors.append(f"{tag}: missing keys: {', '.join(sorted(missing))}")
            if extra:
                errors.append(f"{tag}: unknown keys: {', '.join(sorted(extra))}")
        identical = 0
        for name, source in required.items():
            if name not in entries:
                continue
            value, translated = entries[name]
            if not translated:
                errors.append(f"{tag}/{name}: required text cannot be marked nontranslatable")
            if not value.strip() or "TODO" in value:
                errors.append(f"{tag}/{name}: untranslated or empty text")
            if formats(source) != formats(value):
                errors.append(f"{tag}/{name}: positional format arguments differ from the default")
            if value == source:
                identical += 1
        # Shared terms such as OK, Home and %1$d ms are fine. A copied template is not.
        if tag != default and identical > len(required) // 3:
            errors.append(f"{tag}: {identical}/{len(required)} entries still match the English template")
    # The SDK/build can compile fonts silently; keep this app's system-font promise explicit.
    for path in res.rglob("*"):
        if path.is_file() and (path.suffix.lower() in {".ttf", ".otf", ".ttc", ".woff", ".woff2"} or path.parent.name == "font"):
            errors.append(f"Bundled/downloadable font resource is not allowed: {path.relative_to(res)}")
    if errors:
        raise ValueError("\n".join(errors))
    languages = {tag.split("-")[0] for tag in found}
    print(f"OK: {len(languages)} languages / {len(found)} locales / {len(required)} translated strings each")
    print(" ".join(sorted(found)))


def add(res, tag):
    default, found = catalogs(res)
    if tag in found or tag == default:
        raise ValueError(f"Locale already exists: {tag}")
    target = res / folder_for_tag(tag) / "strings.xml"
    if target.exists():
        raise ValueError(f"File already exists: {target}")
    root = ET.Element("resources")
    for name, (value, translated) in strings(res / "values/strings.xml").items():
        if translated:
            ET.SubElement(root, "string", name=name).text = "TODO: " + value
    ET.indent(root, space="    ")
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(root).write(target, encoding="utf-8", xml_declaration=True)
    with target.open("a", encoding="utf-8") as output:
        output.write("\n")
    print(f"Created {target.relative_to(res.parent.parent.parent.parent)}")
    print("Translate every TODO entry, preserve format tokens, then run: python3 tools/localization.py check")
    print("AGP discovers the new locale automatically. No Java or language-list edits needed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["check", "list", "add"])
    parser.add_argument("locale", nargs="?")
    parser.add_argument("--res", type=Path, default=RES, help=argparse.SUPPRESS)
    args = parser.parse_args()
    try:
        if args.command == "check":
            validate(args.res)
        elif args.command == "list":
            default, found = catalogs(args.res)
            for tag, path in sorted(found.items()):
                print(f"{tag}\t{path.parent.name}\t{'fallback' if tag == default else 'translation'}")
        else:
            if not args.locale:
                parser.error("add requires a BCP-47 locale tag")
            add(args.res, args.locale)
    except (ValueError, ET.ParseError, OSError) as error:
        print(f"Localization check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
