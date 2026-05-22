from __future__ import annotations

VENDOR_HIKVISION = "hikvision"
VENDOR_DAHUA = "dahua"
VENDOR_HUAWEI = "huawei"
VENDOR_EZVIZ = "ezviz"
VENDOR_XIAOMI = "xiaomi"

VENDOR_LABELS: dict[str, str] = {
    VENDOR_HIKVISION: "海康",
    VENDOR_DAHUA: "大华",
    VENDOR_HUAWEI: "华为",
    VENDOR_EZVIZ: "萤石",
    VENDOR_XIAOMI: "小米",
}

ALL_VENDORS: tuple[str, ...] = tuple(VENDOR_LABELS.keys())


def vendor_label(vendor: str | None) -> str:
    if not vendor:
        return ""
    return VENDOR_LABELS.get(vendor, vendor)


def vendor_flag(vendor: str | None) -> str:
    """Short tag for CLI table output."""
    if not vendor:
        return "---"
    return {
        VENDOR_HIKVISION: "HIK",
        VENDOR_DAHUA: "DAH",
        VENDOR_HUAWEI: "HW ",
        VENDOR_EZVIZ: "YS ",
        VENDOR_XIAOMI: "MI ",
    }.get(vendor, vendor[:3].upper())
