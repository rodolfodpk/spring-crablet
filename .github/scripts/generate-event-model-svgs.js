#!/usr/bin/env node
// Renders event-model diagrams with headless Chromium and saves SVG files.
// Reads docs/diagrams.manifest.json; for each diagram: parses YAML in Node,
// inlines renderer + JSON data into a self-contained page, extracts the SVG.
// Usage: node generate-event-model-svgs.js
'use strict';

const { chromium } = require('playwright');
const yaml = require('js-yaml');
const path = require('path');
const fs   = require('fs');

const REPO_ROOT   = path.resolve(__dirname, '..', '..');
const DOCS_DIR    = path.join(REPO_ROOT, 'docs');
const OUTPUT_DIR  = path.join(REPO_ROOT, 'shared-examples-domain', 'docs');
const RENDERER_JS = fs.readFileSync(path.join(DOCS_DIR, 'event-model-renderer.js'), 'utf-8');
const MANIFEST    = JSON.parse(fs.readFileSync(path.join(DOCS_DIR, 'diagrams.manifest.json'), 'utf-8'));

const safeJson = obj => JSON.stringify(obj).replace(/</g, '\\u003c');

(async () => {
  const browser = await chromium.launch();
  try {
    for (const diagram of MANIFEST.diagrams) {
      await generateDiagram(browser, diagram);
    }
    console.log('All event-model SVGs generated.');
  } finally {
    await browser.close();
  }
})().catch(err => {
  console.error(err);
  process.exit(1);
});

async function generateDiagram(browser, diagram) {
  const modelText   = fs.readFileSync(path.join(DOCS_DIR, diagram.modelPath), 'utf-8');
  const sidecarText = diagram.sidecarPath
    ? fs.readFileSync(path.join(DOCS_DIR, diagram.sidecarPath), 'utf-8')
    : null;

  const model   = yaml.load(modelText);
  const sidecar = sidecarText ? yaml.load(sidecarText) : {};

  const html = `<!DOCTYPE html><html><body>
<div id="diagram"></div>
<script>${RENDERER_JS}</script>
<script>
  window.__MODEL__   = ${safeJson(model)};
  window.__SIDECAR__ = ${safeJson(sidecar)};
  const merged = EventModelRenderer.mergeEventModelForDiagram(window.__MODEL__, window.__SIDECAR__ || {});
  EventModelRenderer.render(merged, document.getElementById('diagram'));
</script>
</body></html>`;

  const page = await browser.newPage();
  const pageErrors = [];
  page.on('pageerror', err => pageErrors.push(err));
  page.on('console', msg => {
    if (msg.type() === 'error') pageErrors.push(new Error(`console.error: ${msg.text()}`));
  });

  try {
    await page.setContent(html, { waitUntil: 'networkidle' });

    if (pageErrors.length > 0) {
      throw new Error(`Page errors for diagram "${diagram.id}":\n${pageErrors.map(e => e.message).join('\n')}`);
    }

    const svgs = await page.locator('#diagram svg').all();
    if (svgs.length !== 1) {
      throw new Error(`Expected 1 svg element, found ${svgs.length} for diagram "${diagram.id}"`);
    }

    let svg = await svgs[0].evaluate(el => el.outerHTML);
    if (!svg || !svg.startsWith('<svg')) {
      throw new Error(`Empty or malformed SVG for diagram "${diagram.id}"`);
    }

    if (pageErrors.length > 0) {
      throw new Error(`Page errors after render for diagram "${diagram.id}":\n${pageErrors.map(e => e.message).join('\n')}`);
    }

    if (!/^<svg\b[^>]*\sxmlns=/.test(svg)) {
      svg = svg.replace(/^<svg\b/, '<svg xmlns="http://www.w3.org/2000/svg"');
    }

    const outPath = path.join(OUTPUT_DIR, `${diagram.id}-event-model.svg`);
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, svg, 'utf-8');
    console.log(`  ${diagram.id}: ${outPath}`);
  } finally {
    await page.close();
  }
}
