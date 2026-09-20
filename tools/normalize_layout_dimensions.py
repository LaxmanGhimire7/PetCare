"""Move literal XML layout dimensions into reusable Android resources."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
SPACING = {2, 4, 8, 12, 16, 20, 24, 32, 48}
LITERAL = re.compile(r'(?<=")(-?\d+(?:\.\d+)?)(dp|sp)(?=")')


def resource_for(number: str, unit: str) -> str:
    if unit == "dp" and number.isdigit() and int(number) in SPACING:
        return f"space_{number}"
    if number == "0" and unit == "dp":
        return "layout_zero"
    part = number.replace("-", "negative_").replace(".", "_")
    return f"layout_{part}_{unit}"


def main() -> None:
    target = ROOT / "values/layout_dimensions.xml"
    custom: dict[str, str] = {}
    if target.exists():
        custom.update(re.findall(r'<dimen name="([^"]+)">([^<]+)</dimen>',
                                 target.read_text(encoding="utf-8")))
    count = 0
    for directory in ROOT.glob("layout*"):
        for path in directory.glob("*.xml"):
            source = path.read_bytes().decode("utf-8")

            def replace(match: re.Match[str]) -> str:
                nonlocal count
                number, unit = match.groups()
                resource = resource_for(number, unit)
                if not resource.startswith("space_"):
                    custom[resource] = f"{number}{unit}"
                count += 1
                return f"@dimen/{resource}"

            updated = LITERAL.sub(replace, source)
            if updated != source:
                path.write_bytes(updated.encode("utf-8"))

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Exact legacy form and auth sizes shared across XML layouts. -->",
        "<resources>",
    ]
    lines.extend(f'    <dimen name="{name}">{value}</dimen>'
                 for name, value in sorted(custom.items()))
    lines.append("</resources>")
    target.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    print(f"Replaced {count} literal dimensions with {len(custom)} extra resources")


if __name__ == "__main__":
    main()
