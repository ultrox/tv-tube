#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate the SmartTube-compatible update manifest for a built APK."
    )
    parser.add_argument(
        "--metadata",
        required=True,
        help="Path to Android Gradle output-metadata.json.",
    )
    parser.add_argument(
        "--download-base-url",
        required=True,
        help="Release asset base URL, without the APK filename.",
    )
    parser.add_argument(
        "--output",
        default="tv-tube.json",
        help="Manifest output path.",
    )
    parser.add_argument(
        "--changelog-file",
        help="Text file with one changelog entry per line.",
    )
    parser.add_argument(
        "--default-changelog",
        default="TVTube update",
        help="Fallback changelog entry when no changelog file is provided.",
    )
    return parser.parse_args()


def read_changelog(path, fallback):
    if path:
        text = Path(path).read_text(encoding="utf-8")
        lines = [line.strip() for line in text.splitlines() if line.strip()]
    else:
        lines = []

    return lines or [fallback]


def find_universal_apk(elements):
    for element in elements:
        if element.get("type") == "UNIVERSAL":
            return element

    for element in elements:
        if not element.get("filters"):
            return element

    raise ValueError("No universal APK entry found in output metadata")


def main():
    args = parse_args()
    metadata_path = Path(args.metadata)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    elements = metadata.get("elements") or []

    if not elements:
        raise ValueError(f"No APK elements found in {metadata_path}")

    version_pairs = {
        (element.get("versionCode"), element.get("versionName"))
        for element in elements
    }

    if len(version_pairs) != 1:
        raise ValueError(f"APK outputs disagree on version metadata: {version_pairs}")

    version_code, version_name = version_pairs.pop()
    universal_apk = find_universal_apk(elements)
    output_file = universal_apk.get("outputFile")

    if not version_code or not version_name or not output_file:
        raise ValueError("Missing versionCode, versionName, or outputFile in metadata")

    base_url = args.download_base_url.rstrip("/")
    manifest = {
        "package": {
            "downloadUrl": f"{base_url}/{output_file}",
        },
        version_name: {
            "versionCode": version_code,
            "changelog": read_changelog(args.changelog_file, args.default_changelog),
        },
    }

    output_path = Path(args.output)
    output_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(output_path)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"generate-update-manifest: {error}", file=sys.stderr)
        sys.exit(1)
