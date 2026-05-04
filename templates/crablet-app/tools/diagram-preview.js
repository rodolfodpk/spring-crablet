#!/usr/bin/env node
// Generates a standalone HTML diagram preview from event-model.yaml.
// Open diagram-preview.html in a browser to view the Event Modeling board.
// Usage: node tools/diagram-preview.js [MODEL=path/to/event-model.yaml]
'use strict';

const path = require('path');
const fs   = require('fs');

let yaml;
try {
  yaml = require('js-yaml');
} catch {
  console.error('Missing js-yaml. Install it for this template with:');
  console.error('  npm install --prefix tools --silent');
  process.exit(1);
}

const TOOLS_DIR   = __dirname;
const APP_ROOT    = path.resolve(TOOLS_DIR, '..');
const MODEL_PATH  = process.env.MODEL || path.join(APP_ROOT, 'event-model.yaml');
const OUT_PATH    = process.env.OUT   || path.join(APP_ROOT, 'diagram-preview.html');
const RENDERER_JS = fs.readFileSync(path.join(TOOLS_DIR, 'event-model-renderer.js'), 'utf-8');

const model = yaml.load(fs.readFileSync(MODEL_PATH, 'utf-8'));
const safeJson = obj => JSON.stringify(obj).replace(/</g, '\\u003c');

const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Event Model Preview — ${model.domain || 'diagram'}</title>
<style>body { margin: 0; padding: 1rem; font-family: sans-serif; }</style>
</head>
<body>
<div id="diagram"></div>
<script>${RENDERER_JS}</script>
<script>
  window.__MODEL__   = ${safeJson(model)};
  window.__SIDECAR__ = {};
  const merged = EventModelRenderer.mergeEventModelForDiagram(window.__MODEL__, {});
  EventModelRenderer.render(merged, document.getElementById('diagram'));
</script>
</body>
</html>`;

fs.writeFileSync(OUT_PATH, html, 'utf-8');
console.log('Diagram preview written to: ' + OUT_PATH);
console.log('Open diagram-preview.html in a browser to view the Event Modeling board.');
