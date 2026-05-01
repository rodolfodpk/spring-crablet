#!/usr/bin/env node
// Renders a markmap HTML file with headless Chromium and extracts the SVG.
// Usage: node extract-mindmap-svg.js <input.html> <output.svg>
'use strict';

const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const [,, htmlFile, outFile] = process.argv;
if (!htmlFile || !outFile) {
  console.error('Usage: node extract-mindmap-svg.js <input.html> <output.svg>');
  process.exit(1);
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage();

  await page.setViewport({ width: 2000, height: 1100 });
  await page.goto(`file://${path.resolve(htmlFile)}`);

  // Wait for markmap to finish layouting (uses requestAnimationFrame internally)
  await page.waitForTimeout(4000);

  // Expand all nodes, then re-fit so the full tree is visible
  await page.evaluate(() => {
    const svgEl = document.querySelector('svg.markmap');
    if (!svgEl) return;
    // Find the markmap instance attached to the SVG element
    const mm = svgEl.__markmap__;
    if (!mm) return;
    // Recursively mark every node as open
    function expandAll(node) {
      if (!node) return;
      node.payload = { ...node.payload, fold: 0 };
      (node.children || []).forEach(expandAll);
    }
    if (mm.state && mm.state.data) expandAll(mm.state.data);
    mm.renderData();
    mm.fit();
  });

  // Allow re-render animation to settle
  await page.waitForTimeout(2000);

  const svgContent = await page.evaluate(() => {
    const svgEl = document.querySelector('svg.markmap');
    if (!svgEl) return null;
    // Snapshot current viewBox so the exported SVG is self-contained
    const vb   = svgEl.getAttribute('viewBox') || '0 0 2000 1100';
    const clone = svgEl.cloneNode(true);
    clone.setAttribute('xmlns',      'http://www.w3.org/2000/svg');
    clone.setAttribute('xmlns:xlink','http://www.w3.org/1999/xlink');
    clone.setAttribute('width',  '2000');
    clone.setAttribute('height', '1100');
    clone.setAttribute('viewBox', vb);
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + clone.outerHTML;
  });

  await browser.close();

  if (!svgContent) {
    console.error('No SVG element found in rendered page.');
    process.exit(1);
  }

  fs.mkdirSync(path.dirname(path.resolve(outFile)), { recursive: true });
  fs.writeFileSync(outFile, svgContent, 'utf8');
  console.log(`SVG written to ${outFile}`);
})();
