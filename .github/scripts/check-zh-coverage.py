from pathlib import Path
from xml.etree import ElementTree


root = Path(__file__).resolve().parents[2]
resource_dirs = (
    root / "composeApp/src/commonMain/composeResources",
    root / "composeApp/src/androidMain/res",
)


def names(resources, folder):
    return {
        item.attrib["name"]
        for item in ElementTree.parse(resources / folder / "strings.xml").getroot()
        if item.attrib.get("translatable") != "false"
    }


for resources in resource_dirs:
    missing = sorted(names(resources, "values") - names(resources, "values-zh-rCN"))
    if missing:
        raise SystemExit(f"Missing Simplified Chinese resources in {resources}: " + ", ".join(missing))
print("Simplified Chinese covers all translatable resources.")
