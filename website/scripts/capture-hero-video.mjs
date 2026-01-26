/**
 * Automated video capture for the hero video.
 *
 * Logic:
 * 1. Wait (show current step for specified time)
 * 2. Press Enter (advance to next step)
 *
 * Prerequisites:
 * 1. Start Docker: docker run -it -p 9300:9300 --pull always ghcr.io/kakao/actionbase:standalone
 * 2. Start guide: guide start hands-on-social
 *
 * Usage:
 * node scripts/capture-hero-video.mjs
 *
 * Output:
 * - website/public/images/guides/social-media/hero.webm
 */

import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const BASE_URL = 'http://localhost:9300';
const OUTPUT_DIR = path.join(__dirname, '../public/images/guides/social-media');

// All steps in the guide (for navigation)
const ALL_STEPS = [
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

// Display times per step (ms) - total ~60 seconds
const HERO_SCENES = {
  '01-welcome': 2500,
  '02-zipdoki-intro': 2000,
  '03-set-up': 3000,
  '04-load-sample-data': 4000,
  '05-select-database': 2500,
  '06-explore-the-data': 4000,
  '07-follows': 3500,
  '08-create-follows-table': 3000,
  '09-follow-a-user': 4000,
  '10-check-follow-status': 3000,
  '11-count-followers': 3000,
  '12-list-followers': 3000,
  '13-likes': 3500,
  '14-like-a-post': 4000,
  '15-check-like-status': 3000,
  '16-and-more': 2500,
  '17-feed': 5000,
  '18-all-done': 3000,
};

// Last step to record (stop here)
const LAST_STEP = '18-all-done';

// Steps to skip quickly
const SKIP_TIME = 200;

async function main() {
  // Ensure output directory exists
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }

  // Use 2x resolution for better quality (Retina-like)
  const SCALE = 2;
  const WIDTH = 1330;
  const HEIGHT = 950;

  console.log('=== Hero Video Recording ===\n');
  console.log('Hero scenes:');
  for (const [step, time] of Object.entries(HERO_SCENES)) {
    console.log(`  - ${step}: ${time / 1000}s`);
  }
  console.log();

  console.log(`Starting browser (${WIDTH * SCALE}x${HEIGHT * SCALE})...`);
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    viewport: { width: WIDTH, height: HEIGHT },
    deviceScaleFactor: SCALE,
    recordVideo: {
      dir: OUTPUT_DIR,
      size: { width: WIDTH * SCALE, height: HEIGHT * SCALE },
    },
  });
  const page = await context.newPage();

  try {
    console.log(`Loading: ${BASE_URL}`);
    await page.goto(BASE_URL, { waitUntil: 'networkidle', timeout: 30000 });

    // Wait for driver.js to initialize
    await page.waitForSelector('.driver-popover', { timeout: 15000 });
    console.log('Guide loaded!\n');

    const lastStepIndex = ALL_STEPS.indexOf(LAST_STEP);

    // Navigate through steps, pausing on key scenes
    for (let i = 0; i <= lastStepIndex; i++) {
      const stepName = ALL_STEPS[i];
      const isHeroScene = stepName in HERO_SCENES;
      const waitTime = isHeroScene ? HERO_SCENES[stepName] : SKIP_TIME;

      if (isHeroScene) {
        console.log(`[HERO] ${stepName} (${waitTime / 1000}s)`);
      } else {
        console.log(`[skip] ${stepName}`);
      }

      // Wait to show current step
      await page.waitForTimeout(waitTime);

      // Advance to next step
      if (i < lastStepIndex) {
        if (stepName === '01-welcome') {
          // First step: click analytics button
          const shareBtn = page.locator('#analytics-share-btn');
          const startBtn = page.locator('#analytics-start-btn');
          try {
            await shareBtn.click({ timeout: 500 });
          } catch {
            try {
              await startBtn.click({ timeout: 500 });
            } catch {
              await page.keyboard.press('Enter');
            }
          }
        } else if (stepName === '17-feed') {
          // Feed step: click Next button directly (Enter might not work)
          const nextBtn = page.locator('.driver-popover-next-btn');
          try {
            await nextBtn.click({ timeout: 1000 });
          } catch {
            await page.keyboard.press('Enter');
          }
        } else {
          // All other steps: just press Enter
          await page.keyboard.press('Enter');
        }
        await page.waitForTimeout(150);
      }
    }

    // Final pause before ending
    await page.waitForTimeout(1000);
    console.log('\n=== Recording complete! ===\n');
  } catch (err) {
    console.error('\n=== ERROR ===');
    console.error(err.message);
    console.error('\nCheck:');
    console.error('1. Docker running at localhost:9300?');
    console.error('2. Guide started with: guide start hands-on-social\n');
  } finally {
    console.log('Saving video...');
    await page.close();
    await context.close();
    await browser.close();

    // Find and rename the video file
    const files = fs.readdirSync(OUTPUT_DIR);
    const videoFile = files.find((f) => f.endsWith('.webm') && f !== 'hero.webm');
    if (videoFile) {
      const oldPath = path.join(OUTPUT_DIR, videoFile);
      const newPath = path.join(OUTPUT_DIR, 'hero.webm');
      if (fs.existsSync(newPath)) {
        fs.unlinkSync(newPath);
      }
      fs.renameSync(oldPath, newPath);
      console.log(`Saved: ${newPath}`);
    } else {
      console.log('No video file found.');
    }
  }
}

main().catch(console.error);
