#!/usr/bin/env node
/**
 * Configura TR-069 en VSOL V2804* (login + menú ACS).
 * Cierra sesión web previa si existe (solo una sesión simultánea).
 */
import { chromium } from "playwright";
import { writeFileSync } from "fs";

const VSOL_HOST = process.env.VSOL_HOST || "192.168.1.1";
const VSOL_USER = process.env.VSOL_USER || "admin";
const VSOL_PASS = process.env.VSOL_PASS || "admin";
const ACS_URL = process.env.ACS_URL || "http://192.168.1.2:7547";
const ACS_USER = process.env.ACS_CPE_USERNAME || "gigafiber-acs";
const ACS_PASS = process.env.ACS_CPE_PASSWORD || "";
const INFORM_INTERVAL = process.env.ACS_INFORM_INTERVAL || "300";
const HEADLESS = process.env.HEADLESS !== "0";
const SKIP_LOGOUT = process.env.SKIP_LOGOUT === "1";

if (!ACS_PASS) {
  console.error("Falta ACS_CPE_PASSWORD (export desde scripts/genieacs/.env)");
  process.exit(1);
}

const base = `http://${VSOL_HOST}`;

async function forceLogout(page) {
  if (SKIP_LOGOUT) return;
  await page.goto(`${base}/boaform/admin/formLogout`, {
    waitUntil: "domcontentloaded",
    timeout: 10000,
  }).catch(() => null);
  await page.waitForTimeout(800);
}

async function login(page) {
  await forceLogout(page);
  await page.goto(`${base}/login.html`, { waitUntil: "domcontentloaded", timeout: 15000 });

  if (!page.url().includes("login")) {
    console.log(`Sesión activa sin login: ${page.url()}`);
    return;
  }

  await page.waitForSelector("#login_username", { timeout: 10000 });

  const captchaVisible = await page.locator("#id_captcha").isVisible().catch(() => false);
  if (captchaVisible) {
    let captcha = await page.evaluate(() =>
      typeof show_num !== "undefined" ? show_num.join("") : ""
    );
    if (!captcha) {
      await page.click("#canvas");
      await page.waitForTimeout(400);
      captcha = await page.evaluate(() => show_num.join(""));
    }
    if (captcha) await page.fill("#text", captcha);
  }

  await page.fill("#login_username", VSOL_USER);
  await page.fill("#login_password", VSOL_PASS);

  const loginResp = await Promise.all([
    page.waitForResponse((r) => r.url().includes("formLogin"), { timeout: 15000 }).catch(() => null),
    page.click("#Login_Button"),
  ]).then(([r]) => r);

  await page.waitForTimeout(1200);
  const body = loginResp ? await loginResp.text().catch(() => "") : "";
  if (body.includes("YOU_HAVE_LOGINED") || /logined/i.test(await page.locator("body").innerText().catch(() => ""))) {
    throw new Error(
      "VSOL bloqueó login: otra sesión web activa. Cierra la pestaña del VSOL en el navegador y vuelve a ejecutar."
    );
  }

  if (page.url().includes("login") && !body.match(/success|index|home|main/i)) {
    throw new Error(`Login falló (user=${VSOL_USER}). Respuesta: ${body.slice(0, 120)}`);
  }
  console.log(`Login OK -> ${page.url()}`);
}

async function collectMenuTexts(page) {
  const texts = new Set();
  for (const frame of page.frames()) {
    const items = await frame.locator("a, span.menu, li, .nav-link, .sidebar .nav-item").allTextContents().catch(() => []);
    for (const t of items) {
      const s = t.replace(/\s+/g, " ").trim();
      if (s.length > 1 && s.length < 80) texts.add(s);
    }
  }
  return [...texts];
}

async function clickFirstMatching(page, patterns) {
  for (const frame of page.frames()) {
    for (const pat of patterns) {
      const re = typeof pat === "string" ? new RegExp(pat, "i") : pat;
      const loc = frame.getByText(re, { exact: false }).first();
      if (await loc.count()) {
        await loc.click({ timeout: 5000 }).catch(() => null);
        await page.waitForTimeout(900);
        return pat;
      }
    }
  }
  return null;
}

async function findTr069Frame(page) {
  for (const frame of page.frames()) {
    const html = await frame.content().catch(() => "");
    if (/tr069|cwmp|acs url|management server|inform interval|periodic inform/i.test(html)) {
      return frame;
    }
  }
  return null;
}

async function openTr069Page(page) {
  const paths = [
    ["Management", "TR-069"],
    ["Management", "TR069"],
    ["Management", "Remote Management"],
    ["Management", "ACS"],
    ["Advanced Setup", "TR-069"],
    ["Advanced", "TR-069"],
    ["Network", "Remote Management"],
    ["TR-069"],
    ["TR069"],
    ["CWMP"],
  ];

  for (const path of paths) {
    for (const item of path) {
      const clicked = await clickFirstMatching(page, [item]);
      if (clicked) console.log(`Click menú: ${item}`);
    }
    const frame = await findTr069Frame(page);
    if (frame) {
      console.log(`Pantalla TR-069: ${path.join(" > ")}`);
      return frame;
    }
  }

  // URLs directas comunes VSOL
  const direct = [
    "tr069.html",
    "tr069.asp",
    "management/tr069.html",
    "admin/tr069.html",
    "page/tr069.html",
  ];
  for (const p of direct) {
    await page.goto(`${base}/${p}`, { waitUntil: "domcontentloaded", timeout: 8000 }).catch(() => null);
    const frame = await findTr069Frame(page);
    if (frame) {
      console.log(`Pantalla TR-069 por URL: /${p}`);
      return frame;
    }
  }

  const menus = await collectMenuTexts(page);
  writeFileSync("/tmp/vsol-menus.txt", menus.join("\n"));
  throw new Error(`No se encontró TR-069. Menús volcados en /tmp/vsol-menus.txt (${menus.length} items)`);
}

async function setField(frame, patterns, value, secret = false) {
  for (const pat of patterns) {
    const re = typeof pat === "string" ? new RegExp(pat, "i") : pat;
    // label + input en la misma fila
    const row = frame.locator("tr").filter({ hasText: re }).first();
    if (await row.count()) {
      const inp = row.locator("input, select, textarea").first();
      if (await inp.count()) {
        const tag = await inp.evaluate((el) => el.tagName.toLowerCase());
        if (tag === "select") await inp.selectOption({ label: String(value) }).catch(() => inp.selectOption(String(value)));
        else await inp.fill(String(value));
        console.log(`Set ${pat} -> ${secret ? "***" : value}`);
        return true;
      }
    }
    const label = frame.locator("label, td, th, span, div").filter({ hasText: re }).first();
    if (await label.count()) {
      const inp = label.locator("xpath=following::input[1]|following::select[1]").first();
      if (await inp.count()) {
        await inp.fill(String(value));
        console.log(`Set ${pat} -> ${secret ? "***" : value}`);
        return true;
      }
    }
  }
  return false;
}

async function applyTr069Settings(frame, page) {
  // Enable TR-069
  for (const sel of [
    'input[type="checkbox"][name*="tr069" i]',
    'input[type="checkbox"][name*="cwmp" i]',
    'input[type="checkbox"][id*="tr069" i]',
    'input[type="checkbox"][name*="acs" i]',
  ]) {
    const el = frame.locator(sel).first();
    if (await el.count()) {
      await el.check({ force: true }).catch(() => null);
      console.log(`Enable checkbox: ${sel}`);
      break;
    }
  }

  await setField(frame, ["acs url", "url", "server url", "management server"], ACS_URL);
  await setField(frame, ["username", "user name", "acs user"], ACS_USER);
  await setField(frame, ["password", "acs pass"], ACS_PASS, true);
  await setField(frame, ["inform interval", "periodic inform", "inform"], INFORM_INTERVAL);

  const nameMap = {
    acs_url: ACS_URL,
    tr069_acs_url: ACS_URL,
    acsURL: ACS_URL,
    acsUserName: ACS_USER,
    acsPassword: ACS_PASS,
    informInterval: INFORM_INTERVAL,
    tr069InformInterval: INFORM_INTERVAL,
    periodicInformInterval: INFORM_INTERVAL,
  };
  for (const [name, val] of Object.entries(nameMap)) {
    const inp = frame.locator(`input[name="${name}"], input[id="${name}"], select[name="${name}"]`).first();
    if (await inp.count()) {
      await inp.fill(String(val));
      console.log(`Set name=${name}`);
    }
  }

  writeFileSync("/tmp/vsol-tr069.html", await frame.content().catch(() => ""));

  const saveBtn = frame.locator("button, input[type='submit'], input[type='button'], a").filter({
    hasText: /save|apply|submit|confirm|ok|确定/i,
  }).first();
  if (await saveBtn.count()) {
    await saveBtn.click();
    await page.waitForTimeout(2500);
    console.log("Guardado TR-069");
  } else {
    throw new Error("No se encontró botón Save/Apply en pantalla TR-069");
  }
}

async function main() {
  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await login(page);
    await page.screenshot({ path: "/tmp/vsol-home.png", fullPage: true });
    const frame = await openTr069Page(page);
    await applyTr069Settings(frame, page);
    await page.screenshot({ path: "/tmp/vsol-tr069-done.png", fullPage: true });
    console.log("\nTR-069 configurado.");
    console.log(`ACS URL: ${ACS_URL}`);
    console.log(`ACS user: ${ACS_USER}`);
  } finally {
    if (HEADLESS) await browser.close();
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
