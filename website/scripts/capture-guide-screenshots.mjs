/**
 * Automated screenshot capture for the Build Your Social Media App guide.
 *
 * Prerequisites:
 * 1. Start Docker: docker run -it -p 9300:9300 --pull always ghcr.io/kakao/actionbase:standalone
 * 2. Start guide: guide start hands-on-social
 *
 * Usage:
 * node scripts/capture-guide-screenshots.mjs
 */

import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const BASE_URL = 'http://localhost:9300';
const OUTPUT_DIR = path.join(__dirname, '../public/images/guides/social-media');

const STEPS = [
  '01-welcome',
  '02-zipdoki-intro',
  '03-set-up',
  '04-load-sample-data',
  '05-select-database',
  '06-explore-the-data',
  '07-follows',
  '08-create-follows-table',
  '09-follow-a-user',
  '10-check-follow-status',
  '11-count-followers',
  '12-list-followers',
  '13-likes',
  '14-like-a-post',
  '15-check-like-status',
  '16-and-more',
  '17-feed',
  '18-all-done',
  '19-try-it-yourself',
];

// Sidebar is 320px wide, capture only the right content area
const SIDEBAR_WIDTH = 320;

async function capture(page, name, index) {
  const filePath = path.join(OUTPUT_DIR, `${name}.png`);
  const viewport = page.viewportSize();
  await page.screenshot({
    path: filePath,
    clip: {
      x: SIDEBAR_WIDTH,
      y: 0,
      width: viewport.width - SIDEBAR_WIDTH,
      height: viewport.height,
    },
  });
  console.log(`[${index + 1}/${STEPS.length}] Saved: ${name}.png`);
}

async function clickNext(page, stepIndex) {
  if (stepIndex === 0) {
    // First step has analytics consent - click "share & start"
    const shareBtn = page.locator('#analytics-share-btn');
    await shareBtn.waitFor({ state: 'visible', timeout: 5000 });
    await shareBtn.click();
  } else {
    // Regular driver.js next button
    const nextBtn = page.locator('.driver-popover-next-btn');
    await nextBtn.waitFor({ state: 'visible', timeout: 5000 });
    await nextBtn.click();
  }
}

async function main() {
  // Ensure output directory exists
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }

  console.log('Starting browser...');
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    viewport: { width: 1330, height: 950 },
  });
  const page = await context.newPage();

  console.log('Loading page...');
  await page.goto(BASE_URL);

  // Wait for driver.js to initialize
  await page.waitForSelector('.driver-popover', { timeout: 10000 });
  console.log('Guide loaded!\n');

  console.log('=== Automated Screenshot Capture ===\n');

  for (let i = 0; i < STEPS.length; i++) {
    // Wait a bit for animations
    await page.waitForTimeout(500);

    // Capture screenshot
    await capture(page, STEPS[i], i);

    // Click next (except for last step)
    if (i < STEPS.length - 1) {
      try {
        await clickNext(page, i);
        // Wait for transition
        await page.waitForTimeout(300);
      } catch (err) {
        console.log(`  Note: Could not click next at step ${i + 1}, trying Enter key...`);
        await page.keyboard.press('Enter');
        await page.waitForTimeout(300);
      }
    }
  }

  console.log('\n=== All screenshots captured! ===');
  console.log(`Output directory: ${OUTPUT_DIR}`);

  await browser.close();
}

main().catch(console.error);
