(function () {
  'use strict';

  // ─── Color palette — matched to docs/user/assets/wallet-opened-automation-and-outbox-board.svg ───
  const C = {
    bg:          '#f5f1e8',
    laneSep:     '#c9bfae',
    arrow:       '#55606e',
    text:        '#111827',
    textMuted:   '#6b7280',
    textSub:     '#374151',
    trigger:     { fill: '#ffffff', accent: '#efe7d6', stroke: '#d4d4d8' },
    command:     { fill: '#cfe3ff', accent: '#b9d4ff', stroke: '#8cb6ff' },
    event:       { fill: '#f7ea72', accent: '#f1df48', stroke: '#d2b100' },
    view:        { fill: '#b8efb6', accent: '#9ee09a', stroke: '#74c36a' },
    automation:  { fill: '#fbd38d', accent: '#f6bf5f', stroke: '#d69e2e' },
    outbox:      { fill: '#f9c4d2', accent: '#f5a8bc', stroke: '#db7b97' },
    badge: {
      idempotent:       { fill: '#dbeafe', stroke: '#93c5fd', text: '#1d4ed8' },
      commutative:      { fill: '#dcfce7', stroke: '#86efac', text: '#166534' },
      'non-commutative':{ fill: '#ffedd5', stroke: '#fdba74', text: '#9a3412' },
    },
  };

  // ─── Layout constants ────────────────────────────────────────────────────────
  const L = {
    labelW:      160,   // left gutter for lane labels
    colW:        200,   // width of each event column
    cardMargin:  16,    // space between column edge and card edge
    cardStripH:  18,    // card header accent strip height
    titleBlockH: 90,    // domain title + subtitle block at SVG top
    rightPad:    60,    // right margin
    laneH: {
      trigger:   110,
      command:   120,
      event:     130,
      view:      120,
      automation:110,
      outbox:    110,
    },
  };

  const LANES = ['trigger', 'command', 'event', 'view', 'automation', 'outbox'];

  // ─── SVG DOM helper ──────────────────────────────────────────────────────────
  const NS = 'http://www.w3.org/2000/svg';
  function el(tag, attrs, children) {
    const e = document.createElementNS(NS, tag);
    if (attrs) Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
    if (children) children.forEach(c => c && e.appendChild(c));
    return e;
  }
  function txt(content, attrs) {
    const e = el('text', attrs);
    e.textContent = content;
    return e;
  }

  // ─── Coordinate helpers ──────────────────────────────────────────────────────
  function colX(col)  { return L.labelW + col * L.colW; }
  function colCX(col) { return colX(col) + L.colW / 2; }

  function laneTopY(model, laneKey) {
    let y = L.titleBlockH;
    for (const k of LANES) {
      if (k === laneKey) return y;
      if (isLaneVisible(model, k)) y += L.laneH[k];
    }
    return y;
  }

  function isLaneVisible(model, laneKey) {
    switch (laneKey) {
      case 'trigger':    return (model.triggers || []).length > 0;
      case 'command':    return true;
      case 'event':      return true;
      case 'view':       return (model.views || []).length > 0;
      case 'automation': return (model.automations || []).length > 0;
      case 'outbox':     return (model.outbox || []).length > 0;
    }
    return false;
  }

  function totalHeight(model) {
    let h = L.titleBlockH;
    LANES.forEach(k => { if (isLaneVisible(model, k)) h += L.laneH[k]; });
    return h + 20; // bottom padding
  }

  function totalWidth(numCols) {
    return L.labelW + numCols * L.colW + L.rightPad;
  }

  // ─── Text helpers ────────────────────────────────────────────────────────────
  function truncate(s, max) {
    if (!s) return '';
    return s.length > max ? s.slice(0, max - 1) + '…' : s;
  }
  function stripCommandSuffix(name) {
    return name.replace(/Command$/, '');
  }

  // ─── buildDefs ───────────────────────────────────────────────────────────────
  function buildDefs() {
    const shadow = el('filter', { id: 'shadow', x: '-20%', y: '-20%', width: '140%', height: '160%' }, [
      el('feDropShadow', { dx: '0', dy: '6', stdDeviation: '5', 'flood-color': '#000000', 'flood-opacity': '0.18' }),
    ]);
    const arrowHead = el('marker', {
      id: 'arrowhead', markerWidth: '12', markerHeight: '12',
      refX: '10', refY: '6', orient: 'auto',
    }, [
      el('path', { d: 'M0,0 L12,6 L0,12 z', fill: C.arrow }),
    ]);
    const arrowHeadSm = el('marker', {
      id: 'arrowhead-sm', markerWidth: '8', markerHeight: '8',
      refX: '7', refY: '4', orient: 'auto',
    }, [
      el('path', { d: 'M0,0 L8,4 L0,8 z', fill: C.laneSep }),
    ]);
    return el('defs', {}, [shadow, arrowHead, arrowHeadSm]);
  }

  // ─── Layout ──────────────────────────────────────────────────────────────────
  function buildLayout(model) {
    const events   = model.events   || [];
    const commands = model.commands || [];
    const views    = model.views    || [];
    const autos    = model.automations   || [];
    const outboxes = model.outbox        || [];
    const triggers = model.triggers      || [];
    const synthDefs= model.syntheticCommands || [];

    // Step 1: event column index
    const eventIndex = {};
    events.forEach((e, i) => { eventIndex[e.name] = i; });
    const numEventCols = events.length;

    // Step 2: synthetic command column allocation
    // Named synthetics and anonymous synthetics share the same pool
    const synthIndex = {};      // name → col
    const synthMeta  = {};      // name → { displayLabel, note }
    synthDefs.forEach(s => { synthMeta[s.name] = s; });

    let nextSynthCol = numEventCols;
    function getSynthCol(name) {
      if (!(name in synthIndex)) {
        synthIndex[name] = nextSynthCol++;
        if (!(name in synthMeta)) {
          console.warn(`[EventModelRenderer] emitsCommand '${name}' not in commands or syntheticCommands — rendering as anonymous synthetic`);
        }
      }
      return synthIndex[name];
    }

    // Step 3: command placements — one slot per (col, stackIndex)
    const cmdColSlots = {}; // col → count of commands placed
    const commandPlacements = commands.map(cmd => {
      const firstEvent = (cmd.produces || [])[0];
      const col = firstEvent !== undefined && eventIndex[firstEvent] !== undefined
        ? eventIndex[firstEvent] : 0;
      const stackIdx = cmdColSlots[col] || 0;
      cmdColSlots[col] = stackIdx + 1;
      return { cmd, col, stackIdx };
    });

    // Step 4: resolve automation emitsCommand columns (after command placements)
    const resolvedCommandCols = {};
    commandPlacements.forEach(({ cmd, col }) => { resolvedCommandCols[cmd.name] = col; });

    const automationPlacements = autos.map(auto => {
      const fromCol = eventIndex[auto.triggeredBy] !== undefined
        ? eventIndex[auto.triggeredBy] : 0;
      let toCol;
      if (resolvedCommandCols[auto.emitsCommand] !== undefined) {
        toCol = resolvedCommandCols[auto.emitsCommand];
      } else {
        toCol = getSynthCol(auto.emitsCommand);
      }
      return { auto, fromCol, toCol };
    });

    // Step 5: synthetic command placements (as command cards in command lane)
    const syntheticPlacements = Object.entries(synthIndex).map(([name, col]) => {
      const meta = synthMeta[name] || {};
      return { name, col, displayLabel: meta.displayLabel || stripCommandSuffix(name), note: meta.note || '' };
    });

    // Step 6: view placements
    const viewPlacements = views.map(view => {
      const cols = (view.reads || []).map(n => eventIndex[n]).filter(c => c !== undefined);
      const minCol = cols.length > 0 ? Math.min(...cols) : 0;
      const maxCol = cols.length > 0 ? Math.max(...cols) : 0;
      return { view, minCol, maxCol, readCols: cols };
    });

    // Step 7: outbox placements
    const outboxPlacements = outboxes.map(pub => {
      const cols = (pub.handles || []).map(n => eventIndex[n]).filter(c => c !== undefined);
      const minCol = cols.length > 0 ? Math.min(...cols) : 0;
      const maxCol = cols.length > 0 ? Math.max(...cols) : 0;
      return { pub, minCol, maxCol };
    });

    // Step 8: trigger placements
    const triggerPlacements = triggers.map(trigger => {
      const linkedCmd = commands.find(c => c.name === trigger.linkedCommand);
      if (!linkedCmd) {
        console.warn(`[EventModelRenderer] Trigger '${trigger.name}': linkedCommand '${trigger.linkedCommand}' not found — skipping`);
        return null;
      }
      const firstEvent = (linkedCmd.produces || [])[0];
      const col = firstEvent !== undefined && eventIndex[firstEvent] !== undefined
        ? eventIndex[firstEvent] : 0;
      return { trigger, col, linkedCommand: linkedCmd.name };
    }).filter(Boolean);

    const numCols = nextSynthCol; // total columns including synthetics
    return {
      eventIndex, numCols, numEventCols,
      commandPlacements, syntheticPlacements,
      viewPlacements, automationPlacements,
      outboxPlacements, triggerPlacements,
      cmdColSlots,
    };
  }

  // ─── Card renderer ───────────────────────────────────────────────────────────
  function renderCard(opts) {
    const { x, y, w, h, colors, label, subtitle, tags, badge, eventBadge, note } = opts;
    const g = el('g', { filter: 'url(#shadow)' });
    g.appendChild(el('rect', { x, y, width: w, height: h, rx: '6', fill: colors.fill, stroke: colors.stroke, 'stroke-width': '2' }));
    g.appendChild(el('rect', { x, y, width: w, height: L.cardStripH, rx: '6', fill: colors.accent }));
    // Fix rounded corners on strip bottom edge
    g.appendChild(el('rect', { x, y: y + L.cardStripH - 6, width: w, height: 6, fill: colors.accent }));

    let textY = y + L.cardStripH + 22;
    const cx = x + w / 2;

    // Name
    g.appendChild(txt(truncate(label, 22), {
      x: cx, y: textY,
      'text-anchor': 'middle',
      'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
      'font-size': '14', 'font-weight': '700', fill: C.text,
    }));
    textY += 18;

    // Subtitle
    if (subtitle) {
      g.appendChild(txt(truncate(subtitle, 30), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '12', 'font-weight': '400', fill: C.textSub,
      }));
      textY += 16;
    }

    // Tags (events)
    if (tags && tags.length > 0) {
      g.appendChild(txt(tags.slice(0, 3).join(', '), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '11', 'font-weight': '400', fill: C.textMuted,
      }));
      textY += 14;
    }

    // Note (for synthetic commands)
    if (note) {
      g.appendChild(txt(truncate(note, 26), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '10', 'font-weight': '400', fill: C.textMuted,
      }));
    }

    // Event badge (e.g. "multi-entity DCB")
    if (eventBadge) {
      const bw = Math.min(w - 12, eventBadge.length * 7 + 16);
      const bx = x + (w - bw) / 2;
      const by = y + h - 22;
      g.appendChild(el('rect', { x: bx, y: by, width: bw, height: 16, rx: '4', fill: '#e0e7ff', stroke: '#a5b4fc', 'stroke-width': '1' }));
      g.appendChild(txt(eventBadge, {
        x: bx + bw / 2, y: by + 11,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '9', 'font-weight': '700', fill: '#4338ca',
      }));
    }

    // Pattern badge (commands, top-right corner)
    if (badge) {
      const bColors = C.badge[badge] || C.badge.idempotent;
      const bw = badge === 'non-commutative' ? 92 : badge === 'commutative' ? 72 : 68;
      const bx = x + w - bw - 6;
      const by = y + 4;
      g.appendChild(el('rect', { x: bx, y: by, width: bw, height: 13, rx: '3', fill: bColors.fill, stroke: bColors.stroke, 'stroke-width': '1' }));
      g.appendChild(txt(badge, {
        x: bx + bw / 2, y: by + 9,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '8', 'font-weight': '700', fill: bColors.text,
      }));
    }

    return g;
  }

  // ─── Arrow renderers ─────────────────────────────────────────────────────────
  function renderArrow(x, y1, y2) {
    return el('path', {
      d: `M ${x} ${y1} L ${x} ${y2}`,
      stroke: C.arrow, 'stroke-width': '3', fill: 'none',
      'marker-end': 'url(#arrowhead)',
      'stroke-linecap': 'round',
    });
  }

  function renderTriggerArrow(x, y1, y2) {
    return el('path', {
      d: `M ${x} ${y1} L ${x} ${y2}`,
      stroke: C.laneSep, 'stroke-width': '2', fill: 'none',
      'marker-end': 'url(#arrowhead-sm)',
      'stroke-linecap': 'round',
    });
  }

  function renderArc(x1, y1, x2, y2, label) {
    const mx = (x1 + x2) / 2;
    const my = Math.max(y1, y2) + 30;
    const g = el('g');
    g.appendChild(el('path', {
      d: `M ${x1} ${y1} C ${x1} ${my} ${x2} ${my} ${x2} ${y2}`,
      stroke: C.arrow, 'stroke-width': '2', fill: 'none',
      'stroke-dasharray': '8 5', 'stroke-linecap': 'round',
      'marker-end': 'url(#arrowhead)',
    }));
    if (label) {
      g.appendChild(txt(label, {
        x: mx, y: my - 4,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '10', 'font-weight': '700', fill: C.textMuted,
      }));
    }
    return g;
  }

  // ─── Lane background + labels ────────────────────────────────────────────────
  function renderLanes(model, totalW) {
    const g = el('g');
    g.appendChild(el('rect', { x: 0, y: 0, width: totalW, height: totalHeight(model), fill: C.bg }));

    const laneLabels = {
      trigger:    ['TRIGGER',    'What starts the flow'],
      command:    ['COMMAND',    'Intent to change state'],
      event:      ['EVENT',      'Committed business fact'],
      view:       ['VIEW',       'Async read model'],
      automation: ['AUTOMATION', 'Policy after committed event'],
      outbox:     ['OUTBOX',     'External publication (illustrative)'],
    };

    LANES.forEach(k => {
      if (!isLaneVisible(model, k)) return;
      const topY = laneTopY(model, k);
      g.appendChild(el('line', {
        x1: L.labelW - 10, y1: topY, x2: totalW, y2: topY,
        stroke: C.laneSep, 'stroke-width': '3', 'stroke-dasharray': '10 10',
      }));
      const [main, sub] = laneLabels[k];
      g.appendChild(txt(main, {
        x: L.labelW - 14, y: topY + 22,
        'text-anchor': 'end',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '13', 'font-weight': '700', fill: C.textSub,
        'letter-spacing': '0.5',
      }));
      g.appendChild(txt(sub, {
        x: L.labelW - 14, y: topY + 36,
        'text-anchor': 'end',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '10', 'font-weight': '400', fill: C.textMuted,
      }));
    });

    // Final bottom line
    const bottomY = totalHeight(model) - 20;
    g.appendChild(el('line', {
      x1: L.labelW - 10, y1: bottomY, x2: totalW, y2: bottomY,
      stroke: C.laneSep, 'stroke-width': '3', 'stroke-dasharray': '10 10',
    }));
    return g;
  }

  // ─── Header + legend ─────────────────────────────────────────────────────────
  function renderHeader(model, totalW) {
    const g = el('g');
    g.appendChild(txt(model.domain || 'Event Model', {
      x: totalW / 2, y: 40,
      'text-anchor': 'middle',
      'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
      'font-size': '24', 'font-weight': '700', fill: C.text,
    }));

    const legendItems = [
      { label: 'Command', colors: C.command },
      { label: 'Event',   colors: C.event },
      { label: 'View',    colors: C.view },
      { label: 'Automation', colors: C.automation },
      { label: 'Outbox',  colors: C.outbox },
    ];
    let lx = L.labelW;
    legendItems.forEach(({ label, colors }) => {
      g.appendChild(el('rect', { x: lx, y: 56, width: 14, height: 14, rx: '2', fill: colors.fill, stroke: colors.stroke, 'stroke-width': '1.5' }));
      g.appendChild(txt(label, {
        x: lx + 18, y: 67,
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '11', 'font-weight': '400', fill: C.textSub,
      }));
      lx += label.length * 7 + 34;
    });
    return g;
  }

  // ─── Main render function ────────────────────────────────────────────────────
  function render(model, container) {
    const layout  = buildLayout(model);
    const eventBadges = model.eventBadges || {};
    const { numCols, commandPlacements, syntheticPlacements,
            viewPlacements, automationPlacements,
            outboxPlacements, triggerPlacements, cmdColSlots } = layout;

    const tw = totalWidth(numCols);
    const th = totalHeight(model);

    const svg = el('svg', {
      width: tw, height: th,
      viewBox: `0 0 ${tw} ${th}`,
      xmlns: NS,
      role: 'img',
    });

    svg.appendChild(buildDefs());
    svg.appendChild(renderLanes(model, tw));
    svg.appendChild(renderHeader(model, tw));

    const events = model.events || [];

    // ── EVENT cards ──
    if (isLaneVisible(model, 'event')) {
      const laneY = laneTopY(model, 'event');
      const cardH = L.laneH.event - 2 * L.cardMargin;
      events.forEach((event, col) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = colX(col) + L.cardMargin;
        const cardY = laneY + L.cardMargin;
        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.event,
          label: event.name,
          subtitle: null,
          tags: event.tags || [],
          eventBadge: eventBadges[event.name] || null,
        }));
      });
    }

    // ── COMMAND cards + cmd→event arrows ──
    if (isLaneVisible(model, 'command')) {
      const cmdLaneY = laneTopY(model, 'command');
      const evtLaneY = laneTopY(model, 'event');
      const baseCardH = L.laneH.command - 2 * L.cardMargin;

      commandPlacements.forEach(({ cmd, col, stackIdx }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = colX(col) + L.cardMargin;
        const stackedH = baseCardH - 4;
        const cardY = cmdLaneY + L.cardMargin + stackIdx * (stackedH + 8);
        const cx = colCX(col);

        const guardNote = (cmd.guardEvents || []).length > 0 ? `guard: ${cmd.guardEvents[0]}` : null;
        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: stackedH,
          colors: C.command,
          label: stripCommandSuffix(cmd.name),
          subtitle: guardNote,
          badge: cmd.pattern,
        }));
        // cmd → event arrow (from actual card bottom)
        const arrowY1 = evtLaneY + L.cardMargin;
        const arrowY2 = cardY + stackedH + 6;
        if (arrowY2 < arrowY1 - 4) {
          svg.appendChild(renderArrow(cx, arrowY2, arrowY1 - 4));
        }
      });

      // Synthetic command cards (cross-domain targets)
      syntheticPlacements.forEach(({ name, col, displayLabel, note }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = colX(col) + L.cardMargin;
        const cardY = cmdLaneY + L.cardMargin;
        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: baseCardH,
          colors: { fill: C.command.fill, accent: C.command.accent, stroke: C.command.stroke },
          label: displayLabel,
          subtitle: 'synthetic',
          note: note,
        }));
      });
    }

    // ── TRIGGER cards + trigger→command arrows ──
    if (isLaneVisible(model, 'trigger')) {
      const trigLaneY = laneTopY(model, 'trigger');
      const cmdLaneY  = laneTopY(model, 'command');
      const cardH = L.laneH.trigger - 2 * L.cardMargin;

      triggerPlacements.forEach(({ trigger, col }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = colX(col) + L.cardMargin;
        const cardY = trigLaneY + L.cardMargin;
        const cx = colCX(col);

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.trigger,
          label: truncate(trigger.name, 22),
          subtitle: null,
        }));
        // trigger → command arrow (light, small arrowhead)
        const arrowY1 = cardY + cardH + 2;
        const arrowY2 = cmdLaneY + L.cardMargin - 2;
        if (arrowY2 > arrowY1 + 4) {
          svg.appendChild(renderTriggerArrow(cx, arrowY1, arrowY2));
        }
      });
    }

    // ── VIEW cards + event→view arrows ──
    if (isLaneVisible(model, 'view')) {
      const viewLaneY = laneTopY(model, 'view');
      const evtLaneY  = laneTopY(model, 'event');
      const cardH = L.laneH.view - 2 * L.cardMargin;

      viewPlacements.forEach(({ view, minCol, maxCol, readCols }) => {
        const spanCols = maxCol - minCol + 1;
        const cardW = spanCols * L.colW - 2 * L.cardMargin;
        const cardX = colX(minCol) + L.cardMargin;
        const cardY = viewLaneY + L.cardMargin;

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.view,
          label: view.name,
          subtitle: `reads ${view.reads ? view.reads.length : 0} events`,
          tags: view.tag ? [view.tag] : [],
        }));

        // event → view arrows (one per read event, deduped by column)
        const drawnCols = new Set();
        readCols.forEach(col => {
          if (drawnCols.has(col)) return;
          drawnCols.add(col);
          const cx = colCX(col);
          const y1 = evtLaneY + L.laneH.event - L.cardMargin + 4;
          const y2 = viewLaneY + L.cardMargin - 4;
          svg.appendChild(renderArrow(cx, y1, y2));
        });
      });
    }

    // ── AUTOMATION cards + arcs ──
    if (isLaneVisible(model, 'automation')) {
      const autoLaneY = laneTopY(model, 'automation');
      const evtLaneY  = laneTopY(model, 'event');
      const cmdLaneY  = laneTopY(model, 'command');
      const cardH = L.laneH.automation - 2 * L.cardMargin;

      automationPlacements.forEach(({ auto, fromCol, toCol }) => {
        const minCol = Math.min(fromCol, toCol);
        const maxCol = Math.max(fromCol, toCol);
        const spanCols = maxCol - minCol + 1;
        const cardW = Math.max(spanCols * L.colW - 2 * L.cardMargin, L.colW - 2 * L.cardMargin);
        const cardX = colX(minCol) + L.cardMargin;
        const cardY = autoLaneY + L.cardMargin;

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.automation,
          label: auto.name,
          subtitle: `on ${auto.triggeredBy} → ${stripCommandSuffix(auto.emitsCommand)}`,
        }));

        // arc: event bottom → automation top
        const evtCX   = colCX(fromCol);
        const evtArcY = evtLaneY + L.laneH.event - L.cardMargin + 4;
        const autoTopY = cardY - 4;
        svg.appendChild(renderArc(evtCX, evtArcY, evtCX, autoTopY, 'async'));

        // arc: automation top → command bottom (loop back up)
        const cmdCX    = colCX(toCol);
        const cmdArcY  = cmdLaneY + L.laneH.command - L.cardMargin;
        svg.appendChild(renderArc(colCX(minCol + (maxCol - minCol) / 2), autoTopY - 10, cmdCX, cmdArcY, null));
      });
    }

    // ── OUTBOX cards ──
    if (isLaneVisible(model, 'outbox')) {
      const outboxLaneY = laneTopY(model, 'outbox');
      const cardH = L.laneH.outbox - 2 * L.cardMargin;

      outboxPlacements.forEach(({ pub, minCol, maxCol }) => {
        const spanCols = maxCol - minCol + 1;
        const cardW = spanCols * L.colW - 2 * L.cardMargin;
        const cardX = colX(minCol) + L.cardMargin;
        const cardY = outboxLaneY + L.cardMargin;

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.outbox,
          label: pub.name,
          subtitle: `topic: ${pub.topic}`,
          tags: pub.handles || [],
        }));
      });
    }

    container.innerHTML = '';
    container.appendChild(svg);
  }

  // ─── Public API ──────────────────────────────────────────────────────────────
  window.EventModelRenderer = { render };
})();
