#!/usr/bin/env python3
"""Run the backend evaluation harness and return a failing exit code on gate failure."""

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()
    request = Request(
        f"{args.base_url.rstrip('/')}/api/evaluation/run",
        data=b"{}",
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=600) as response:
            result = json.load(response)
    except HTTPError as error:
        result = json.loads(error.read().decode("utf-8"))
        print(json.dumps(result, indent=2))
        return 1
    except (URLError, TimeoutError) as error:
        print(f"Evaluation service request failed: {error}", file=sys.stderr)
        return 2

    print(json.dumps(result, indent=2))
    if not result.get("buildPass", False):
        print("Evaluation build gate failed.", file=sys.stderr)
        return 1
    print("Evaluation build gate passed. CSV and Markdown reports were exported.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
