"""Screenshots of the console for the README. Needs the service on BASE with demo data loaded.

    python3 scripts/screenshots.py [base-url] [chrome-path]
"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8091"
CHROME = sys.argv[2] if len(sys.argv) > 2 else "/usr/bin/google-chrome"
OUT = Path(__file__).resolve().parent.parent / "docs" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=CHROME)
    page = browser.new_page(viewport={"width": 1440, "height": 960}, device_scale_factor=1)

    page.goto(BASE + "/")
    page.wait_for_selector("app-player-table tbody tr")
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / "search.png"))
    first = page.locator("app-player-table a.name").first
    first.click()
    page.wait_for_selector("app-pitch-map svg .mark")
    page.wait_for_selector(".similar li")
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / "player.png"), full_page=True)

    page.goto(BASE + "/assistant")
    page.get_by_role("button", name="Left-footed centre-backs under 23").click()
    page.wait_for_selector("app-player-table tbody tr")
    page.wait_for_timeout(300)
    page.screenshot(path=str(OUT / "assistant.png"), full_page=True)
    page.get_by_role("button", name="A young winger from Serie A").click()
    page.wait_for_selector(".chip.is-stop")
    page.wait_for_timeout(300)
    page.screenshot(path=str(OUT / "assistant-not-covered.png"))

    page.goto(BASE + "/lab")
    page.wait_for_selector(".gates tbody tr")
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / "lab.png"), full_page=True)

    dark = browser.new_page(viewport={"width": 1440, "height": 960}, color_scheme="dark")
    dark.goto(BASE + "/")
    dark.wait_for_selector("app-player-table tbody tr")
    dark.locator("app-player-table a.name").nth(1).click()
    dark.wait_for_selector("app-pitch-map svg .mark")
    dark.wait_for_selector(".similar li")
    dark.wait_for_timeout(600)
    dark.screenshot(path=str(OUT / "player-dark.png"))
    browser.close()
print("wrote", ", ".join(sorted(x.name for x in OUT.glob("*.png"))))
