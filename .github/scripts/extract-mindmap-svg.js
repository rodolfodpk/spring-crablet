#!/usr/bin/env node
// Renders a markmap HTML file with headless Chromium and saves a PNG screenshot.
// PNG renders correctly on GitHub (SVGs get sanitized and lose fonts/styles).
// Usage: node extract-mindmap-svg.js <input.html> <output.png>
'use strict';

const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const [,, htmlFile, outFile] = process.argv;
if (!htmlFile || !outFile) {
  console.error('Usage: node extract-mindmap-svg.js <input.html> <output.png>');
  process.exit(1);
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage();

  await page.setViewportSize({ width: 2000, height: 1200 });
  await page.goto(`file://${path.resolve(htmlFile)}`);

  // Wait for markmap D3 layout to complete
  await page.waitForTimeout(5000);

  // Fit the diagram so all nodes are visible within the viewport
  await page.evaluate(() => {
    const svgEl = document.querySelector('svg.markmap');
    if (!svgEl) return;
    const mm = svgEl.__markmap__;
    if (mm && typeof mm.fit === 'function') mm.fit();
  });

  await page.waitForTimeout(1000);

  fs.mkdirSync(path.dirname(path.resolve(outFile)), { recursive: true });
  await page.screenshot({ path: outFile, fullPage: false });

  await browser.close();
  console.log(`PNG written to ${outFile}`);
})();
