/**
 * Automated video capture for the hero GIF.
 * Based on capture-guide-screenshots.mjs with video recording.
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
 *
 * Convert to GIF (requires ffmpeg):
 * ffmpeg -i hero.webm -vf "fps=15,scale=800:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" hero.gif
 */

import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const BASE_URL = 'http://localhost:9300';
const OUTPUT_DIR = path.join(__dirname, '../public/images/guides/social-media');

// Same steps as capture-guide-screenshots.mjs
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

// Major sections (titleNumber in stepsConfig) - pause longer
const MAJOR_SECTIONS = [
  '01-welcome',           // 1. Welcome
  '03-set-up',            // 2. Set Up
  '06-explore-the-data',  // 3. Explore the Data
  '07-follows',           // 4. Follows
  '13-likes',             // 5. Likes
  '17-feed',              // 6. Feed
  '18-all-done',          // 7. All Done
];

// Key action scenes - pause even longer
const KEY_SCENES = [
  '09-follow-a-user',
  '14-like-a-post',
];

// Timing configuration (in ms)
const TIMING = {
  NORMAL: 400,
  MAJOR: 1500,      // Major section headers
  KEY_SCENE: 2500,  // Important action moments
};

async function clickNext(page, stepIndex) {
  if (stepIndex === 0) {
    // First step has analytics consent - click "share & start" or "start"
    console.log('  Looking for analytics buttons...');
    const shareBtn = page.locator('#analytics-share-btn');
    const startBtn = page.locator('#analytics-start-btn');

    try {
      await shareBtn.waitFor({ state: 'visible', timeout: 3000 });
      console.log('  Found share button, clicking...');
      await shareBtn.click();
    } catch {
      console.log('  Share button not found, trying start button...');
      try {
        await startBtn.waitFor({ state: 'visible', timeout: 3000 });
        console.log('  Found start button, clicking...');
        await startBtn.click();
      } catch {
        console.log('  Start button not found, trying Enter key...');
        await page.keyboard.press('Enter');
      }
    }
  } else {
    // Regular driver.js next button
    const nextBtn = page.locator('.driver-popover-next-btn');
    try {
      await nextBtn.waitFor({ state: 'visible', timeout: 3000 });
      await nextBtn.click();
    } catch {
      console.log('  Next button not found, trying Enter key...');
      await page.keyboard.press('Enter');
    }
  }
}

async function main() {
  // Ensure output directory exists
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }

  // Use 2x resolution for better quality (Retina-like)
  const SCALE = 2;
  const WIDTH = 1330;
  const HEIGHT = 950;

  console.log(`Starting browser with ${SCALE}x resolution (${WIDTH * SCALE}x${HEIGHT * SCALE})...`);
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
    console.log(`Loading page: ${BASE_URL}`);
    await page.goto(BASE_URL, { waitUntil: 'networkidle', timeout: 30000 });
    console.log('Page loaded!');

    // Wait for driver.js to initialize
    console.log('Waiting for driver.js popover...');
    await page.waitForSelector('.driver-popover', { timeout: 15000 });
    console.log('Guide loaded!\n');

    console.log('=== Recording Hero Video ===\n');

    for (let i = 0; i < STEPS.length; i++) {
      const stepName = STEPS[i];
      const isKeyScene = KEY_SCENES.includes(stepName);
      const isMajorSection = MAJOR_SECTIONS.includes(stepName);

      // Determine wait time based on scene type
      let waitTime = TIMING.NORMAL;
      let label = '';
      if (isKeyScene) {
        waitTime = TIMING.KEY_SCENE;
        label = ' (KEY)';
      } else if (isMajorSection) {
        waitTime = TIMING.MAJOR;
        label = ' (MAJOR)';
      }

      await page.waitForTimeout(waitTime);

      console.log(`[${i + 1}/${STEPS.length}] ${stepName}${label}`);

      // Click next (except for last step)
      if (i < STEPS.length - 1) {
        try {
          await clickNext(page, i);
          // Wait for transition
          await page.waitForTimeout(300);
        } catch (err) {
          console.log(`  Error at step ${i + 1}: ${err.message}`);
          console.log('  Trying Enter key as fallback...');
          await page.keyboard.press('Enter');
          await page.waitForTimeout(300);
        }
      }
    }

    // Final pause on last screen
    await page.waitForTimeout(TIMING.KEY_SCENE);

    console.log('\n=== Recording finished! ===\n');

  } catch (err) {
    console.error('\n=== ERROR ===');
    console.error(err.message);
    console.error('\nPossible causes:');
    console.error('1. Guide server not running at localhost:9300');
    console.error('2. Docker container not started');
    console.error('3. Page structure changed\n');
  } finally {
    // Always close to save the video
    console.log('Closing browser and saving video...');
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
      console.log(`\nVideo saved: ${newPath}`);
      console.log('\nTo convert to GIF, run:');
      console.log(
        `  ffmpeg -i ${newPath} -vf "fps=15,scale=800:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" hero.gif`
      );
    } else {
      console.log('\nNo video file found to rename.');
    }
  }
}

main().catch(console.error);