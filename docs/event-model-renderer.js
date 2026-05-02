(function () {
  'use strict';

  // ─── Color palette — matched to docs/user/assets/wallet-opened-automation-and-outbox-board.svg ───
  const C = {
    bg:          '#f5f1e8',
    rowSep:      '#c9bfae',
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
    labelW:      160,   // left gutter for row labels
    colW:        200,   // width of each event column
    cardMargin:  16,    // space between column edge and card edge
    cardStripH:  18,    // card header accent strip height
    titleBlockH:   90,   // domain title + subtitle block at SVG top
    rightPad:      60,   // right margin
    laneHeaderH:   28,   // height of the lane label strip at top of each band
    lanePadBottom: 12,   // padding below the last row in each band
    rowH: {
      trigger:   110,
      command:   120,
      event:     130,
      view:      120,
      automation:110,
      outbox:    110,
    },
  };

  // Uniform card height for canonical (actors) lane layout
  const CAN = {
    wireframeBaseH:      40,
    wireframePerStack:   22,
    procInnerH:          56,
    cardH:               112,
    stackGap:            8,
    betweenStacks:       12,
    viewMarginTop:       14,
  };

  const ROWS = ['trigger', 'command', 'event', 'view', 'automation', 'outbox'];

  // Uniform box for command / event / view cells in canonical (actors) layout
  const CAN_CARD_W = L.colW - 2 * L.cardMargin;

  // ─── Timeline column order (LTR) ─────────────────────────────────────────────
  // Docs diagrams derive from structural event-model.yaml (no hand-authored canvas graph).
  // Column index uses:
  //   · guardEvents → each produced event (guards before appended facts)
  //   · produces[i] → produces[i+1] for the same command
  //   · automation triggeredBy → first event produced by emitsCommand (structural command only)
  // Kahn topological sort; among ready nodes, smallest original events[] index wins (stable tie-break).
  // Cycles / broken graph → YAML events[] order (console warning).
  function computeEventTimelineOrder(model) {
    const events = model.events || [];
    const names = events.map(e => e.name);
    if (names.length === 0) return [];

    const nameSet = new Set(names);
    const indexOf = Object.fromEntries(names.map((n, i) => [n, i]));
    const succ = {};
    names.forEach(n => { succ[n] = new Set(); });

    function addEdge(u, v) {
      if (!u || !v || u === v) return;
      if (!nameSet.has(u) || !nameSet.has(v)) return;
      succ[u].add(v);
    }

    const commands = model.commands || [];
    commands.forEach(cmd => {
      const prod = cmd.produces || [];
      const guards = cmd.guardEvents || [];
      guards.forEach(g => {
        prod.forEach(p => addEdge(g, p));
      });
      for (let i = 0; i < prod.length - 1; i++) {
        addEdge(prod[i], prod[i + 1]);
      }
    });

    const cmdByName = Object.fromEntries(commands.map(c => [c.name, c]));
    (model.automations || []).forEach(auto => {
      const t = auto.triggeredBy;
      const target = cmdByName[auto.emitsCommand];
      if (target && t) {
        const firstP = (target.produces || [])[0];
        if (firstP) addEdge(t, firstP);
      }
    });

    const indeg = {};
    names.forEach(n => { indeg[n] = 0; });
    names.forEach(u => {
      succ[u].forEach(v => { indeg[v]++; });
    });

    const ready = new Set(names.filter(n => indeg[n] === 0));
    const out = [];

    while (ready.size > 0) {
      let best = null;
      let bestIdx = Infinity;
      ready.forEach(n => {
        const ix = indexOf[n];
        if (ix < bestIdx) {
          bestIdx = ix;
          best = n;
        }
      });
      ready.delete(best);
      out.push(best);
      succ[best].forEach(v => {
        indeg[v]--;
        if (indeg[v] === 0) ready.add(v);
      });
    }

    if (out.length !== names.length) {
      console.warn('[EventModelRenderer] event timeline has a cycle or unresolved edge — using events[] YAML order');
      return names.slice();
    }

    const pinned = model.eventOrder;
    if (!pinned || !Array.isArray(pinned) || pinned.length === 0) return out;

    const pinSet = new Set();
    const pinList = [];
    pinned.forEach(n => {
      if (nameSet.has(n) && !pinSet.has(n)) {
        pinSet.add(n);
        pinList.push(n);
      }
    });
    const rest = out.filter(n => !pinSet.has(n));
    return pinList.concat(rest);
  }

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
  function defaultColX(col)  { return L.labelW + col * L.colW; }
  function defaultColCX(col) { return defaultColX(col) + L.colW / 2; }

  function rowTopY(model, rowKey) {
    let y = L.titleBlockH;
    for (const k of ROWS) {
      if (k === rowKey) return y;
      if (isRowVisible(model, k)) y += L.rowH[k];
    }
    return y;
  }

  function isRowVisible(model, rowKey) {
    switch (rowKey) {
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
    ROWS.forEach(k => { if (isRowVisible(model, k)) h += L.rowH[k]; });
    return h + 20; // bottom padding
  }

  function totalWidth(layout) {
    if (layout.hasLaneSections) {
      return L.labelW + layout.totalCols * L.colW + L.rightPad;
    }
    return L.labelW + layout.numCols * L.colW + L.rightPad;
  }

  // ─── Text helpers ────────────────────────────────────────────────────────────
  function truncate(s, max) {
    if (!s) return '';
    return s.length > max ? s.slice(0, max - 1) + '…' : s;
  }

  /** ~px per character at given font-size for Trebuchet/Segoe UI sans truncation budgets */
  function charsThatFit(w, fontSizePx, padding) {
    const p = padding != null ? padding : 14;
    return Math.max(10, Math.floor((w - p) / (fontSizePx * 0.56)));
  }

  /** Split a long single token (e.g. CamelCase) into two lines before truncating. */
  function splitLabelLines(raw, maxPerLine, maxLines) {
    const s = String(raw || '');
    if (s.length <= maxPerLine) return [s];
    if (maxLines < 2) return [truncate(s, maxPerLine)];
    for (let i = Math.min(maxPerLine, s.length - 1); i > Math.floor(maxPerLine * 0.55); i--) {
      const ch = s[i];
      if (ch === ' ' || ch === '_' || ch === '.' || ch === '/') {
        const a = s.slice(0, i).trimEnd();
        const b = s.slice(i + 1).trim();
        if (b.length > 0 && a.length > 0 && a.length <= maxPerLine && b.length <= maxPerLine) return [a, b];
      }
    }
    for (let i = 2; i < s.length; i++) {
      const c = s[i];
      const p = s[i - 1];
      if (p >= 'a' && p <= 'z' && c >= 'A' && c <= 'Z') {
        const a = s.slice(0, i);
        const b = s.slice(i);
        if (a.length <= maxPerLine && b.length <= maxPerLine) return [a, b];
      }
    }
    const a = s.slice(0, maxPerLine);
    const b = s.slice(maxPerLine);
    if (b.length <= maxPerLine) return [a, b];
    return [a, truncate(b, maxPerLine)];
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
      id: 'arrowhead',
      markerUnits: 'userSpaceOnUse',
      markerWidth: '7',
      markerHeight: '7',
      refX: '6.2',
      refY: '3.5',
      orient: 'auto',
    }, [
      el('path', { d: 'M0,0 L7,3.5 L0,7 z', fill: C.arrow }),
    ]);
    const arrowHeadSm = el('marker', {
      id: 'arrowhead-sm',
      markerUnits: 'userSpaceOnUse',
      markerWidth: '5',
      markerHeight: '5',
      refX: '4.4',
      refY: '2.5',
      orient: 'auto',
    }, [
      el('path', { d: 'M0,0 L5,2.5 L0,5 z', fill: C.rowSep }),
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
    const laneDefs = model.lanes || [];
    const assignments = model.assignments || {};
    const laneIds = laneDefs.map(l => l.id).filter(Boolean);
    const hasLaneSections = laneIds.length > 1;
    const laneLabels = {};
    laneDefs.forEach(l => { if (l.id) laneLabels[l.id] = l.label || l.id; });

    const knownAssignmentKeys = new Set([
      ...events.map(e => e.name),
      ...commands.map(c => c.name),
      ...views.map(v => v.name),
      ...autos.map(a => a.name),
      ...outboxes.map(o => o.name),
      ...synthDefs.map(s => s.name),
    ]);
    const laneSet = new Set(laneIds);
    Object.entries(assignments).forEach(([name, laneId]) => {
      if (!knownAssignmentKeys.has(name)) {
        console.warn(`[EventModelRenderer] assignment '${name}' does not match a rendered element`);
      }
      if (hasLaneSections && !laneSet.has(laneId)) {
        console.warn(`[EventModelRenderer] assignment '${name}' uses unknown lane '${laneId}' — falling back to '${laneIds[0]}'`);
      }
    });

    function assignedLane(name, fallbackLane) {
      if (!hasLaneSections) return null;
      const assigned = assignments[name];
      if (assigned && laneSet.has(assigned)) return assigned;
      if (assigned && !laneSet.has(assigned)) return laneIds[0];
      if (fallbackLane) return fallbackLane;
      console.warn(`[EventModelRenderer] '${name}' has no lane assignment — using '${laneIds[0]}'`);
      return laneIds[0];
    }

    const laneColumnCounts = {};
    const laneStartCols = {};
    laneIds.forEach(id => { laneColumnCounts[id] = 0; });

    function reserveLaneCol(laneId) {
      const safeLane = laneSet.has(laneId) ? laneId : laneIds[0];
      const col = laneColumnCounts[safeLane] || 0;
      laneColumnCounts[safeLane] = col + 1;
      return col;
    }

    // Step 1: event column index (causal timeline; see computeEventTimelineOrder)
    const eventByName = Object.fromEntries(events.map(e => [e.name, e]));
    const timelineOrder = computeEventTimelineOrder(model);
    const eventIndex = {};
    const eventLanes = {};
    const eventLocalCols = {};
    timelineOrder.forEach((eventName, i) => {
      const e = eventByName[eventName];
      if (!e) return;
      if (hasLaneSections) {
        const laneId = assignedLane(e.name, null);
        eventLanes[e.name] = laneId;
        eventLocalCols[e.name] = reserveLaneCol(laneId);
      } else {
        eventIndex[e.name] = i;
      }
    });
    const numEventCols = events.length;

    // Step 2: synthetic command column allocation
    // Named synthetics and anonymous synthetics share the same pool
    const synthIndex = {};      // name → physical col
    const synthLane  = {};      // name → lane id
    const synthLocal = {};      // name → local col
    const synthMeta  = {};      // name → { displayLabel, note }
    synthDefs.forEach(s => { synthMeta[s.name] = s; });

    let nextSynthCol = numEventCols;
    function getSynthCol(name, fallbackLane) {
      if (!(name in synthIndex)) {
        if (hasLaneSections) {
          const laneId = assignedLane(name, fallbackLane);
          synthLane[name] = laneId;
          synthLocal[name] = reserveLaneCol(laneId);
          synthIndex[name] = null; // filled after lane starts are known
        } else {
          synthIndex[name] = nextSynthCol++;
        }
        if (!(name in synthMeta)) {
          console.warn(`[EventModelRenderer] emitsCommand '${name}' not in commands or syntheticCommands — rendering as anonymous synthetic`);
        }
      }
      return synthIndex[name];
    }

    // Step 3: command placements — one slot per (lane/local col, stackIndex)
    const cmdColSlots = {}; // physical col → count of commands placed
    const commandPlacements = commands.map(cmd => {
      const firstEvent = (cmd.produces || [])[0];
      if (hasLaneSections) {
        const anchorLane = firstEvent !== undefined ? eventLanes[firstEvent] : null;
        const laneId = assignedLane(cmd.name, anchorLane);
        if (anchorLane && laneId !== anchorLane) {
          console.warn(`[EventModelRenderer] command '${cmd.name}' assigned to '${laneId}' but produces event in '${anchorLane}' — using event lane`);
        }
        const effectiveLane = anchorLane || laneId;
        const localCol = firstEvent !== undefined && eventLocalCols[firstEvent] !== undefined
          ? eventLocalCols[firstEvent] : reserveLaneCol(effectiveLane);
        return { cmd, laneId: effectiveLane, localCol, col: null, stackIdx: 0 };
      }
      const col = firstEvent !== undefined && eventIndex[firstEvent] !== undefined
        ? eventIndex[firstEvent] : 0;
      return { cmd, laneId: null, localCol: col, col, stackIdx: 0 };
    });

    // Step 4: resolve automation emitsCommand columns (after command placements)
    const resolvedCommandCols = {};
    const resolvedCommandLanes = {};
    commandPlacements.forEach(({ cmd, col, laneId, localCol }) => {
      resolvedCommandCols[cmd.name] = col;
      resolvedCommandLanes[cmd.name] = { laneId, localCol };
    });

    const automationPlacements = autos.map(auto => {
      if (hasLaneSections) {
        const fromLane = eventLanes[auto.triggeredBy] || laneIds[0];
        const autoLane = assignedLane(auto.name, fromLane);
        const fromLocalCol = eventLocalCols[auto.triggeredBy] !== undefined
          ? eventLocalCols[auto.triggeredBy] : 0;
        let toLane = autoLane;
        let toLocalCol = 0;
        if (resolvedCommandLanes[auto.emitsCommand]) {
          toLane = resolvedCommandLanes[auto.emitsCommand].laneId || autoLane;
          toLocalCol = resolvedCommandLanes[auto.emitsCommand].localCol;
        } else {
          getSynthCol(auto.emitsCommand, autoLane);
          toLane = synthLane[auto.emitsCommand] || autoLane;
          toLocalCol = synthLocal[auto.emitsCommand] || 0;
        }
        return { auto, laneId: autoLane, localCol: fromLocalCol, fromLane, fromLocalCol, toLane, toLocalCol, fromCol: null, toCol: null };
      }
      const fromCol = eventIndex[auto.triggeredBy] !== undefined
        ? eventIndex[auto.triggeredBy] : 0;
      let toCol;
      if (resolvedCommandCols[auto.emitsCommand] !== undefined) {
        toCol = resolvedCommandCols[auto.emitsCommand];
      } else {
        toCol = getSynthCol(auto.emitsCommand, null);
      }
      return { auto, laneId: null, localCol: fromCol, fromCol, toCol };
    });

    if (hasLaneSections) {
      let start = 0;
      laneIds.forEach(id => {
        laneStartCols[id] = start;
        start += Math.max(1, laneColumnCounts[id] || 0);
      });
      Object.entries(eventLocalCols).forEach(([name, localCol]) => {
        eventIndex[name] = laneStartCols[eventLanes[name]] + localCol;
      });
      commandPlacements.forEach(p => {
        p.col = laneStartCols[p.laneId] + p.localCol;
        const stackIdx = cmdColSlots[p.col] || 0;
        cmdColSlots[p.col] = stackIdx + 1;
        p.stackIdx = stackIdx;
        resolvedCommandCols[p.cmd.name] = p.col;
      });
      Object.entries(synthLocal).forEach(([name, localCol]) => {
        synthIndex[name] = laneStartCols[synthLane[name]] + localCol;
      });
      automationPlacements.forEach(p => {
        p.fromCol = laneStartCols[p.fromLane] + p.fromLocalCol;
        p.toCol = laneStartCols[p.toLane] + p.toLocalCol;
        p.col = laneStartCols[p.laneId] + p.localCol;
      });
    }

    // Step 5: synthetic command placements (as command cards in command row)
    const syntheticPlacements = Object.entries(synthIndex).map(([name, col]) => {
      const meta = synthMeta[name] || {};
      return { name, col, laneId: synthLane[name] || null, displayLabel: meta.displayLabel || stripCommandSuffix(name), note: meta.note || '' };
    });

    // Step 6: view placements
    const viewPlacements = views.map(view => {
      const cols = (view.reads || []).map(n => eventIndex[n]).filter(c => c !== undefined);
      if (hasLaneSections) {
        const readLanes = [...new Set((view.reads || []).map(n => eventLanes[n]).filter(Boolean))];
        const laneId = assignedLane(view.name, readLanes[0] || laneIds[0]);
        if (readLanes.length > 1) {
          console.warn(`[EventModelRenderer] view '${view.name}' reads events across lane partitions — rendering in '${laneId}'`);
        }
        const laneCols = (view.reads || [])
          .filter(n => eventLanes[n] === laneId)
          .map(n => eventIndex[n])
          .filter(c => c !== undefined);
        const minCol = laneCols.length > 0 ? Math.min(...laneCols) : laneStartCols[laneId];
        const maxCol = laneCols.length > 0 ? Math.max(...laneCols) : minCol;
        return { view, laneId, minCol, maxCol, readCols: cols };
      }
      const minCol = cols.length > 0 ? Math.min(...cols) : 0;
      const maxCol = cols.length > 0 ? Math.max(...cols) : 0;
      return { view, minCol, maxCol, readCols: cols };
    });

    // Step 7: outbox placements
    const outboxPlacements = outboxes.map(pub => {
      const cols = (pub.handles || []).map(n => eventIndex[n]).filter(c => c !== undefined);
      if (hasLaneSections) {
        const handledLanes = [...new Set((pub.handles || []).map(n => eventLanes[n]).filter(Boolean))];
        const laneId = assignedLane(pub.name, handledLanes[0] || laneIds[0]);
        if (handledLanes.length > 1) {
          console.warn(`[EventModelRenderer] outbox '${pub.name}' handles events across lane partitions — rendering in '${laneId}'`);
        }
        const laneCols = (pub.handles || [])
          .filter(n => eventLanes[n] === laneId)
          .map(n => eventIndex[n])
          .filter(c => c !== undefined);
        const minCol = laneCols.length > 0 ? Math.min(...laneCols) : laneStartCols[laneId];
        const maxCol = laneCols.length > 0 ? Math.max(...laneCols) : minCol;
        return { pub, laneId, minCol, maxCol };
      }
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
      return { trigger, col, linkedCommand: linkedCmd.name, laneId: hasLaneSections ? eventLanes[firstEvent] : null };
    }).filter(Boolean);

    const totalCols = hasLaneSections
      ? laneIds.reduce((sum, id) => sum + Math.max(1, laneColumnCounts[id] || 0), 0)
      : nextSynthCol;
    const numCols = nextSynthCol; // total columns including synthetics in legacy mode
    return {
      eventIndex, numCols, numEventCols,
      hasLaneSections, lanes: laneDefs, laneIds, laneLabels, laneStartCols, laneColumnCounts, totalCols,
      commandPlacements, syntheticPlacements,
      viewPlacements, automationPlacements,
      outboxPlacements, triggerPlacements,
      cmdColSlots,
    };
  }

  // ─── Horizontal swim-lane layout ─────────────────────────────────────────────
  function buildLaneLayout(model) {
    const events   = model.events   || [];
    const commands = model.commands || [];
    const views    = model.views    || [];
    const autos    = model.automations    || [];
    const outboxes = model.outbox         || [];
    const triggers = model.triggers       || [];
    const synthDefs = model.syntheticCommands || [];
    const laneDefs  = model.lanes         || [];
    const assignments = model.assignments || {};

    const laneIds  = laneDefs.map(l => l.id).filter(Boolean);
    const laneLabels = {};
    laneDefs.forEach(l => { if (l.id) laneLabels[l.id] = l.label || l.id; });
    const laneSet = new Set(laneIds);

    const synthMeta = {};
    synthDefs.forEach(s => { synthMeta[s.name] = s; });

    function assignedLane(name, fallbackLane) {
      const a = assignments[name];
      if (a && laneSet.has(a)) return a;
      if (a && !laneSet.has(a)) {
        console.warn(`[EventModelRenderer] assignment '${name}' uses unknown lane '${a}' — falling back to '${laneIds[0]}'`);
        return laneIds[0];
      }
      if (fallbackLane && laneSet.has(fallbackLane)) return fallbackLane;
      console.warn(`[EventModelRenderer] '${name}' has no lane assignment — using '${laneIds[0]}'`);
      return laneIds[0];
    }

    // Per-lane column counts (columns within each band start at 0)
    const laneColCount = {};
    laneIds.forEach(id => { laneColCount[id] = 0; });

    // Step 1: events (column order follows causal timeline where computable)
    const eventLanes   = {};  // event name → lane id
    const eventLocalCol = {}; // event name → column index (local = global in horizontal layout)
    const eventIndex   = {};  // same as eventLocalCol, kept for naming parity

    const eventByNameLane = Object.fromEntries(events.map(e => [e.name, e]));
    computeEventTimelineOrder(model).forEach(eventName => {
      const e = eventByNameLane[eventName];
      if (!e) return;
      const laneId = assignedLane(e.name, null);
      eventLanes[e.name] = laneId;
      const col = laneColCount[laneId] || 0;
      laneColCount[laneId] = col + 1;
      eventLocalCol[e.name] = col;
      eventIndex[e.name]    = col;
    });

    // Step 2: synthetic commands
    const synthIndex = {};
    const synthLanes = {};

    function getSynthCol(name, fallbackLane) {
      if (name in synthIndex) return synthIndex[name];
      const emittingAuto = autos.find(a => a.emitsCommand === name);
      const autoFallback = emittingAuto ? (eventLanes[emittingAuto.triggeredBy] || laneIds[0]) : laneIds[0];
      const laneId = assignedLane(name, fallbackLane || autoFallback);
      synthLanes[name] = laneId;
      const col = laneColCount[laneId] || 0;
      laneColCount[laneId] = col + 1;
      synthIndex[name] = col;
      if (!(name in synthMeta)) {
        console.warn(`[EventModelRenderer] emitsCommand '${name}' not in commands or syntheticCommands — rendering as anonymous synthetic`);
      }
      return col;
    }

    // Pre-allocate synthetics referenced by automations (before commands resolve them)
    autos.forEach(auto => {
      const isRealCmd = commands.some(c => c.name === auto.emitsCommand);
      if (!isRealCmd) getSynthCol(auto.emitsCommand, eventLanes[auto.triggeredBy]);
    });

    const syntheticPlacements = Object.entries(synthIndex).map(([name, col]) => {
      const meta = synthMeta[name] || {};
      return { name, col, laneId: synthLanes[name], displayLabel: meta.displayLabel || stripCommandSuffix(name), note: meta.note || '' };
    });

    // Step 3: commands
    const cmdColSlots = {}; // "laneId:col" → stack count
    const resolvedCommandCols  = {};
    const resolvedCommandLanes = {};

    const commandPlacements = commands.map(cmd => {
      const firstEvent  = (cmd.produces || [])[0];
      const anchorLane  = firstEvent ? eventLanes[firstEvent] : null;
      const laneId      = assignedLane(cmd.name, anchorLane);
      if (anchorLane && laneId !== anchorLane) {
        console.warn(`[EventModelRenderer] command '${cmd.name}' assigned to '${laneId}' but produces event in '${anchorLane}' — using event lane`);
      }
      const effectiveLane = anchorLane || laneId;
      const col = (firstEvent !== undefined && eventLocalCol[firstEvent] !== undefined)
        ? eventLocalCol[firstEvent]
        : (laneColCount[effectiveLane] || 0);
      const slotKey = `${effectiveLane}:${col}`;
      const stackIdx = cmdColSlots[slotKey] || 0;
      cmdColSlots[slotKey] = stackIdx + 1;
      resolvedCommandCols[cmd.name]  = col;
      resolvedCommandLanes[cmd.name] = { laneId: effectiveLane, col };
      return { cmd, laneId: effectiveLane, col, stackIdx };
    });

    // Step 4: automations
    const automationPlacements = autos.map(auto => {
      const fromLane = eventLanes[auto.triggeredBy] || laneIds[0];
      const laneId   = assignedLane(auto.name, fromLane);
      const fromCol  = eventLocalCol[auto.triggeredBy] !== undefined ? eventLocalCol[auto.triggeredBy] : 0;
      let toCol, toLane;
      if (resolvedCommandCols.hasOwnProperty(auto.emitsCommand)) {
        toCol  = resolvedCommandCols[auto.emitsCommand];
        toLane = resolvedCommandLanes[auto.emitsCommand].laneId;
      } else {
        toCol  = synthIndex[auto.emitsCommand] !== undefined ? synthIndex[auto.emitsCommand] : fromCol;
        toLane = synthLanes[auto.emitsCommand] || laneId;
      }
      return { auto, laneId, fromCol, toCol, toLane, isCrossLane: laneId !== toLane };
    });

    // Step 5: views (in-lane cols only; foreign-lane reads logged + omitted from arrows)
    const viewPlacements = views.map(view => {
      const readLanes = [...new Set((view.reads || []).map(n => eventLanes[n]).filter(Boolean))];
      const laneId = assignedLane(view.name, readLanes[0] || laneIds[0]);
      if (readLanes.length > 1) {
        console.warn(`[EventModelRenderer] view '${view.name}' reads events across lane partitions — rendering in '${laneId}', omitting foreign-lane arrows`);
      }
      const laneCols = (view.reads || [])
        .filter(n => eventLanes[n] === laneId)
        .map(n => eventLocalCol[n])
        .filter(c => c !== undefined);
      const minCol = laneCols.length > 0 ? Math.min(...laneCols) : 0;
      const maxCol = laneCols.length > 0 ? Math.max(...laneCols) : 0;
      return { view, laneId, minCol, maxCol, readCols: laneCols };
    });

    // Step 6: outbox (same foreign-lane logic)
    const outboxPlacements = outboxes.map(pub => {
      const handledLanes = [...new Set((pub.handles || []).map(n => eventLanes[n]).filter(Boolean))];
      const laneId = assignedLane(pub.name, handledLanes[0] || laneIds[0]);
      if (handledLanes.length > 1) {
        console.warn(`[EventModelRenderer] outbox '${pub.name}' handles events across lane partitions — rendering in '${laneId}'`);
      }
      const laneCols = (pub.handles || [])
        .filter(n => eventLanes[n] === laneId)
        .map(n => eventLocalCol[n])
        .filter(c => c !== undefined);
      const minCol = laneCols.length > 0 ? Math.min(...laneCols) : 0;
      const maxCol = laneCols.length > 0 ? Math.max(...laneCols) : 0;
      return { pub, laneId, minCol, maxCol };
    });

    // Step 7: triggers (inherit lane of linked command)
    const triggerPlacements = triggers.map(trigger => {
      const linkedCmd = commands.find(c => c.name === trigger.linkedCommand);
      if (!linkedCmd) {
        console.warn(`[EventModelRenderer] Trigger '${trigger.name}': linkedCommand '${trigger.linkedCommand}' not found — skipping`);
        return null;
      }
      const laneId = resolvedCommandLanes[linkedCmd.name]?.laneId || laneIds[0];
      const firstEvent = (linkedCmd.produces || [])[0];
      const col = (firstEvent !== undefined && eventLocalCol[firstEvent] !== undefined)
        ? eventLocalCol[firstEvent] : 0;
      return { trigger, col, laneId };
    }).filter(Boolean);

    // Assemble per-lane data buckets
    const laneData = {};
    laneIds.forEach(id => {
      laneData[id] = {
        eventCount:          events.filter(e => eventLanes[e.name] === id).length,
        commandPlacements:   commandPlacements.filter(p => p.laneId === id),
        syntheticPlacements: syntheticPlacements.filter(p => p.laneId === id),
        viewPlacements:      viewPlacements.filter(p => p.laneId === id),
        automationPlacements:automationPlacements.filter(p => p.laneId === id),
        outboxPlacements:    outboxPlacements.filter(p => p.laneId === id),
        triggerPlacements:   triggerPlacements.filter(p => p.laneId === id),
      };
    });

    const totalCols = Math.max(1, ...laneIds.map(id => Math.max(1, laneColCount[id] || 0)));

    return {
      laneIds, laneLabels, laneData, laneColCount, totalCols, eventIndex, eventLanes,
      commandPlacements, syntheticPlacements, automationPlacements,
      viewPlacements, outboxPlacements, triggerPlacements,
    };
  }

  function isRowVisibleInLane(ld, rowKey) {
    switch (rowKey) {
      case 'trigger':    return ld.triggerPlacements.length > 0;
      case 'command':    return ld.commandPlacements.length > 0 || ld.syntheticPlacements.length > 0;
      case 'event':      return ld.eventCount > 0;
      case 'view':       return ld.viewPlacements.length > 0;
      case 'automation': return ld.automationPlacements.length > 0;
      case 'outbox':     return ld.outboxPlacements.length > 0;
    }
    return false;
  }

  function laneContentHeight(ld) {
    return ROWS.reduce((h, k) => isRowVisibleInLane(ld, k) ? h + L.rowH[k] : h, 0);
  }
  function laneTotalHeight(ld) {
    return L.laneHeaderH + laneContentHeight(ld) + L.lanePadBottom;
  }
  function laneTopY(layout, laneIdx) {
    let y = L.titleBlockH;
    for (let i = 0; i < laneIdx; i++) y += laneTotalHeight(layout.laneData[layout.laneIds[i]]);
    return y;
  }
  function rowTopYInLane(layout, laneIdx, rowKey) {
    const ld = layout.laneData[layout.laneIds[laneIdx]];
    let y = laneTopY(layout, laneIdx) + L.laneHeaderH;
    for (const k of ROWS) {
      if (k === rowKey) return y;
      if (isRowVisibleInLane(ld, k)) y += L.rowH[k];
    }
    return y;
  }
  function totalHeightLanes(layout) {
    return layout.laneIds.reduce((h, id) => h + laneTotalHeight(layout.laneData[id]), L.titleBlockH) + 20;
  }
  function totalWidthLanes(layout) {
    return L.labelW + layout.totalCols * L.colW + L.rightPad;
  }

  // ─── Card renderer ───────────────────────────────────────────────────────────
  function renderCard(opts) {
    const { x, y, w, h, colors, label, subtitle, tags, badge, eventBadge, guardBadge, note } = opts;
    const g = el('g', { filter: 'url(#shadow)' });
    g.appendChild(el('rect', { x, y, width: w, height: h, rx: '6', fill: colors.fill, stroke: colors.stroke, 'stroke-width': '2' }));
    g.appendChild(el('rect', { x, y, width: w, height: L.cardStripH, rx: '6', fill: colors.accent }));
    // Fix rounded corners on strip bottom edge
    g.appendChild(el('rect', { x, y: y + L.cardStripH - 6, width: w, height: 6, fill: colors.accent }));

    const labelMax = charsThatFit(w, 14, 12);
    const subMax = charsThatFit(w, 12, 12);
    const noteMax = charsThatFit(w, 10, 12);
    const labelLines = splitLabelLines(label, labelMax, 2);

    let textY = y + L.cardStripH + 10;
    const cx = x + w / 2;

    // Name (up to two lines in narrow uniform cards)
    labelLines.forEach((line, li) => {
      g.appendChild(txt(line, {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '14', 'font-weight': '700', fill: C.text,
      }));
      textY += li === 0 && labelLines.length > 1 ? 16 : 18;
    });

    // Subtitle
    if (subtitle) {
      g.appendChild(txt(truncate(subtitle, subMax), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '12', 'font-weight': '400', fill: C.textSub,
      }));
      textY += 15;
    }

    // Tags (events)
    if (tags && tags.length > 0) {
      const tagStr = tags.slice(0, 3).join(', ');
      g.appendChild(txt(truncate(tagStr, charsThatFit(w, 11, 12)), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '11', 'font-weight': '400', fill: C.textMuted,
      }));
      textY += 13;
    }

    // Note (for synthetic commands)
    if (note) {
      g.appendChild(txt(truncate(note, noteMax), {
        x: cx, y: textY,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '10', 'font-weight': '400', fill: C.textMuted,
      }));
      textY += 12;
    }

    // Event badge (e.g. "multi-entity DCB")
    if (eventBadge) {
      const bw = Math.min(w - 12, charsThatFit(w, 9, 20) * 5 + 16);
      const bx = x + (w - bw) / 2;
      const by = y + h - 22;
      g.appendChild(el('rect', { x: bx, y: by, width: bw, height: 16, rx: '4', fill: '#e0e7ff', stroke: '#a5b4fc', 'stroke-width': '1' }));
      g.appendChild(txt(truncate(eventBadge, charsThatFit(bw, 9, 8)), {
        x: bx + bw / 2, y: by + 11,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '9', 'font-weight': '700', fill: '#4338ca',
      }));
    }

    // Guard badge (commands)
    if (guardBadge) {
      const guardText = `guard: ${guardBadge}`;
      const bw = Math.min(w - 12, charsThatFit(w, 9, 20) * 5 + 16);
      const bx = x + (w - bw) / 2;
      const by = y + h - (eventBadge ? 40 : 22);
      g.appendChild(el('rect', { x: bx, y: by, width: bw, height: 16, rx: '4', fill: '#eef2ff', stroke: '#a5b4fc', 'stroke-width': '1' }));
      g.appendChild(txt(truncate(guardText, charsThatFit(bw, 9, 8)), {
        x: bx + bw / 2, y: by + 11,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '9', 'font-weight': '700', fill: '#3730a3',
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
      stroke: C.arrow, 'stroke-width': '1.35', fill: 'none',
      'marker-end': 'url(#arrowhead)',
      'stroke-linecap': 'round',
    });
  }

  /** Directed edge; arrowhead at (xTo, yTo). opts: { dashed } */
  function renderArrowDirected(xFrom, yFrom, xTo, yTo, opts) {
    opts = opts || {};
    return el('path', {
      d: `M ${xFrom} ${yFrom} L ${xTo} ${yTo}`,
      stroke: C.arrow,
      'stroke-width': opts.strokeWidth != null ? opts.strokeWidth : '1.35',
      'stroke-dasharray': opts.dashed ? '5 4' : undefined,
      fill: 'none',
      'marker-end': 'url(#arrowhead)',
      'stroke-linecap': 'round',
    });
  }

  /** Event → view when event column ≠ view card column: vertical down, horizontal, then vertical down into the view top. */
  function renderArrowEventToViewOrtho(evtCx, evtBottom, viewCx, viewTop, dashed) {
    const gap = viewTop - evtBottom;
    if (gap <= 14) {
      return renderArrowDirected(evtCx, evtBottom + 2, viewCx, viewTop - 2, { dashed });
    }
    const elbowY = evtBottom + Math.max(8, Math.min(gap * 0.45, gap - 10));
    const d = `M ${evtCx} ${evtBottom + 2} L ${evtCx} ${elbowY} L ${viewCx} ${elbowY} L ${viewCx} ${viewTop - 2}`;
    return el('path', {
      d,
      stroke: C.arrow,
      'stroke-width': '1.35',
      'stroke-dasharray': dashed ? '5 4' : undefined,
      fill: 'none',
      'marker-end': 'url(#arrowhead)',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
    });
  }

  function renderTriggerArrow(x, y1, y2) {
    return el('path', {
      d: `M ${x} ${y1} L ${x} ${y2}`,
      stroke: C.rowSep, 'stroke-width': '1.1', fill: 'none',
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
      stroke: C.arrow, 'stroke-width': '1.25', fill: 'none',
      'stroke-dasharray': '6 4', 'stroke-linecap': 'round',
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

  function renderDownArc(x1, y1, x2, y2) {
    const cp1y = y1 + (y2 - y1) * 0.33;
    const cp2y = y1 + (y2 - y1) * 0.66;
    return el('path', {
      d: `M ${x1} ${y1} C ${x1} ${cp1y} ${x2} ${cp2y} ${x2} ${y2}`,
      stroke: C.arrow, 'stroke-width': '1.25', fill: 'none',
      'stroke-dasharray': '6 4', 'stroke-linecap': 'round',
      'marker-end': 'url(#arrowhead)',
    });
  }

  // ─── Horizontal swim-lane row backgrounds ─────────────────────────────────────
  function renderRowsLanes(layout, totalW) {
    const g = el('g');
    const th = totalHeightLanes(layout);
    g.appendChild(el('rect', { x: 0, y: 0, width: totalW, height: th, fill: C.bg }));

    const rowLabels = {
      trigger:    ['TRIGGER',    'What starts the flow'],
      command:    ['COMMAND',    'Intent to change state'],
      event:      ['EVENT',      'Committed business fact'],
      view:       ['VIEW',       'Async read model'],
      automation: ['AUTOMATION', 'Policy after committed event'],
      outbox:     ['OUTBOX',     'External publication (illustrative)'],
    };

    layout.laneIds.forEach((laneId, i) => {
      const ld      = layout.laneData[laneId];
      const bandTop = laneTopY(layout, i);
      const bandH   = laneTotalHeight(ld);

      // Band background
      g.appendChild(el('rect', {
        x: 0, y: bandTop, width: totalW, height: bandH,
        fill: i % 2 === 0 ? '#fff8e8' : '#ece4d2',
      }));

      // Lane header strip
      g.appendChild(el('rect', {
        x: 0, y: bandTop, width: totalW, height: L.laneHeaderH,
        fill: i % 2 === 0 ? '#eadfc8' : '#ddd1b8',
        stroke: '#b9a783', 'stroke-width': '1',
      }));
      g.appendChild(txt(layout.laneLabels[laneId] || laneId, {
        x: totalW / 2, y: bandTop + 19,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '13', 'font-weight': '700', fill: C.text, 'letter-spacing': '1',
      }));

      // Row separator lines and labels within the band
      ROWS.forEach(k => {
        if (!isRowVisibleInLane(ld, k)) return;
        const topY = rowTopYInLane(layout, i, k);
        g.appendChild(el('line', {
          x1: L.labelW - 10, y1: topY, x2: totalW, y2: topY,
          stroke: C.rowSep, 'stroke-width': '2', 'stroke-dasharray': '10 10',
        }));
        const [main, sub] = rowLabels[k];
        g.appendChild(txt(main, {
          x: L.labelW - 14, y: topY + 22,
          'text-anchor': 'end',
          'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          'font-size': '11', 'font-weight': '700', fill: C.textSub, 'letter-spacing': '0.5',
        }));
        g.appendChild(txt(sub, {
          x: L.labelW - 14, y: topY + 34,
          'text-anchor': 'end',
          'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          'font-size': '9', 'font-weight': '400', fill: C.textMuted,
        }));
      });

      // Bottom border of band
      g.appendChild(el('line', {
        x1: 0, y1: bandTop + bandH, x2: totalW, y2: bandTop + bandH,
        stroke: '#b9a783', 'stroke-width': '2',
      }));
    });
    return g;
  }

  // ─── Row background + labels ─────────────────────────────────────────────────
  function renderRows(model, layout, totalW) {
    const g = el('g');
    g.appendChild(el('rect', { x: 0, y: 0, width: totalW, height: totalHeight(model), fill: C.bg }));

    if (layout.hasLaneSections) {
      const bandTop = L.titleBlockH - 32;
      const headerH = 26;
      const bandBottom = totalHeight(model) - 20;
      const bandH = bandBottom - bandTop;
      layout.laneIds.forEach((laneId, i) => {
        const laneCols = Math.max(1, layout.laneColumnCounts[laneId] || 0);
        const x = defaultColX(layout.laneStartCols[laneId]);
        const w = laneCols * L.colW;
        g.appendChild(el('rect', {
          x, y: bandTop, width: w, height: bandH,
          fill: i % 2 === 0 ? '#fff8e8' : '#ece4d2',
        }));
        g.appendChild(el('rect', {
          x, y: bandTop, width: w, height: headerH,
          fill: i % 2 === 0 ? '#eadfc8' : '#ddd1b8',
          stroke: '#b9a783', 'stroke-width': '1.5',
        }));
        g.appendChild(el('line', {
          x1: x, y1: bandTop, x2: x, y2: bandBottom,
          stroke: '#a9966d', 'stroke-width': '2.5',
        }));
        g.appendChild(txt(layout.laneLabels[laneId] || laneId, {
          x: x + w / 2, y: bandTop + 18,
          'text-anchor': 'middle',
          'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          'font-size': '12', 'font-weight': '700', fill: C.text,
        }));
      });
      const lastLane = layout.laneIds[layout.laneIds.length - 1];
      const lastX = defaultColX(layout.laneStartCols[lastLane] + Math.max(1, layout.laneColumnCounts[lastLane] || 0));
      g.appendChild(el('line', {
        x1: lastX, y1: bandTop, x2: lastX, y2: bandBottom,
        stroke: '#a9966d', 'stroke-width': '2.5',
      }));
    }

    const rowLabels = {
      trigger:    ['TRIGGER',    'What starts the flow'],
      command:    ['COMMAND',    'Intent to change state'],
      event:      ['EVENT',      'Committed business fact'],
      view:       ['VIEW',       'Async read model'],
      automation: ['AUTOMATION', 'Policy after committed event'],
      outbox:     ['OUTBOX',     'External publication (illustrative)'],
    };

    ROWS.forEach(k => {
      if (!isRowVisible(model, k)) return;
      const topY = rowTopY(model, k);
      g.appendChild(el('line', {
        x1: L.labelW - 10, y1: topY, x2: totalW, y2: topY,
        stroke: C.rowSep, 'stroke-width': '3', 'stroke-dasharray': '10 10',
      }));
      const [main, sub] = rowLabels[k];
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
      stroke: C.rowSep, 'stroke-width': '3', 'stroke-dasharray': '10 10',
    }));
    return g;
  }

  // ─── Header + legend ─────────────────────────────────────────────────────────
  function renderHeader(model, totalW) {
    const g = el('g');
    const d = model.display || {};
    const title = d.title || model.domain || 'Event Model';
    const isActorBoard = (model.actors || []).length > 0;

    g.appendChild(txt(title, {
      x: totalW / 2, y: 40,
      'text-anchor': 'middle',
      'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
      'font-size': '24', 'font-weight': '700', fill: C.text,
    }));
    if (d.subtitle) {
      g.appendChild(txt(d.subtitle, {
        x: totalW / 2, y: 58,
        'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '13', 'font-weight': '400', fill: C.textMuted,
      }));
    }

    if (d.legend === false) return g;

    const legendItems = isActorBoard
      ? [
          { label: 'Command', colors: C.command },
          { label: 'Event', colors: C.event },
          { label: 'View', colors: C.view },
        ]
      : [
          { label: 'Command', colors: C.command },
          { label: 'Event', colors: C.event },
          { label: 'View', colors: C.view },
          { label: 'Automation', colors: C.automation },
          { label: 'Outbox', colors: C.outbox },
        ];
    const legendY = d.subtitle ? 72 : 56;
    let lx = L.labelW;
    legendItems.forEach(({ label, colors }) => {
      g.appendChild(el('rect', { x: lx, y: legendY, width: 14, height: 14, rx: '2', fill: colors.fill, stroke: colors.stroke, 'stroke-width': '1.5' }));
      g.appendChild(txt(label, {
        x: lx + 18, y: legendY + 11,
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '11', 'font-weight': '400', fill: C.textSub,
      }));
      lx += label.length * 7 + 34;
    });
    return g;
  }

  // ─── Diagram merge helper ────────────────────────────────────────────────────
  // Merges nested model.diagram metadata with an optional sidecar YAML.
  // Merge precedence (lowest → highest): core model < model.diagram < sidecar.
  // Timeline columns follow merged structural facts (computeEventTimelineOrder); no parallel canvas payload.
  // Returns a flat rendering model ready for buildLayout / render.
  function mergeEventModelForDiagram(model, sidecar) {
    sidecar = sidecar || {};
    const diag = model.diagram || {};

    // lanes / triggers / syntheticCommands: later defined array wins
    const lanes             = sidecar.hasOwnProperty('lanes')             ? sidecar.lanes
                            : diag.lanes             !== undefined         ? diag.lanes
                            : model.lanes            || [];
    const triggers          = sidecar.hasOwnProperty('triggers')          ? sidecar.triggers
                            : diag.triggers          !== undefined         ? diag.triggers
                            : model.triggers         || [];
    const syntheticCommands = sidecar.hasOwnProperty('syntheticCommands') ? sidecar.syntheticCommands
                            : diag.syntheticCommands !== undefined         ? diag.syntheticCommands
                            : model.syntheticCommands || [];
    const actors              = sidecar.hasOwnProperty('actors')              ? sidecar.actors
                              : diag.actors               !== undefined         ? diag.actors
                              : model.actors             || [];

    // assignments / eventBadges: shallow object merge, later keys override
    const assignments = Object.assign({},
      model.assignments || {},
      diag.assignments  || {},
      sidecar.hasOwnProperty('assignments') ? sidecar.assignments : {}
    );
    const eventBadges = Object.assign({},
      model.eventBadges || {},
      diag.eventBadges  || {},
      sidecar.hasOwnProperty('eventBadges') ? sidecar.eventBadges : {}
    );

    // automations: merge by name; model < diagram < sidecar
    const autoByName = {};
    (model.automations      || []).forEach(a => { autoByName[a.name] = a; });
    (diag.automations       || []).forEach(a => { autoByName[a.name] = a; });
    if (sidecar.hasOwnProperty('automations')) {
      (sidecar.automations  || []).forEach(a => { autoByName[a.name] = a; });
    }
    const automations = Object.values(autoByName);

    const notes = Object.assign({},
      model.notes || {},
      diag.notes || {},
      sidecar.hasOwnProperty('notes') ? sidecar.notes : {}
    );
    const display = Object.assign({},
      model.display || {},
      diag.display || {},
      sidecar.display || {}
    );
    const eventOrder = sidecar.hasOwnProperty('eventOrder') ? sidecar.eventOrder
      : diag.eventOrder !== undefined ? diag.eventOrder
      : model.eventOrder !== undefined ? model.eventOrder
      : null;

    const merged = Object.assign({}, model, {
      lanes, assignments, triggers, syntheticCommands, eventBadges, automations, actors,
      notes, display, eventOrder,
    });
    delete merged.diagram; // prevent double-apply if result is passed through render()
    return merged;
  }

  function headerReserveHeight(model) {
    const d = model.display || {};
    if (d.legend === false) return d.subtitle ? 76 : 60;
    return d.subtitle ? 102 : 90;
  }

  const EXTERNAL_ACTOR_KEY = '_external';

  /**
   * Canonical Event Modeling layout (refined): wireframe actor/processor bands above;
   * commands + events + views as uniform cards inside context lanes; outbox as edge notation only.
   */
  function buildCanonicalLayout(model) {
    const events = model.events || [];
    const commands = model.commands || [];
    const views = model.views || [];
    const outboxes = model.outbox || [];
    const triggers = model.triggers || [];
    const autos = model.automations || [];
    const synthDefs = model.syntheticCommands || [];
    const assignments = model.assignments || {};
    const declaredActors = model.actors || [];
    const notesMap = model.notes || {};

    const cmdByName = {};
    commands.forEach(c => { cmdByName[c.name] = c; });

    const eventByNameCanon = Object.fromEntries(events.map(e => [e.name, e]));
    const eventIndex = {};
    computeEventTimelineOrder(model).forEach((eventName, i) => {
      if (eventByNameCanon[eventName]) eventIndex[eventName] = i;
    });

    function humanWireframePlacements(triggerList) {
      const byCol = {};
      triggerList.forEach(t => {
        const cmd = cmdByName[t.linkedCommand];
        if (!cmd) return;
        const fe = (cmd.produces || [])[0];
        const col = fe !== undefined && eventIndex[fe] !== undefined ? eventIndex[fe] : 0;
        if (!byCol[col]) byCol[col] = [];
        byCol[col].push(t);
      });
      const wireframes = [];
      Object.keys(byCol).map(Number).sort((a, b) => a - b).forEach(col => {
        byCol[col].forEach((trigger, stackIdx) => {
          wireframes.push({ trigger, col: Number(col), stackIdx });
        });
      });
      return wireframes;
    }

    const humanBandSpecs = [];
    const externalTriggers = triggers.filter(tr => !tr.actor);
    if (externalTriggers.length > 0) {
      humanBandSpecs.push({
        kind: 'human',
        actorKey: EXTERNAL_ACTOR_KEY,
        label: 'External',
        wireframes: humanWireframePlacements(externalTriggers),
      });
    }
    declaredActors.forEach(a => {
      if (!a.id) return;
      const forActor = triggers.filter(tr => tr.actor === a.id);
      humanBandSpecs.push({
        kind: 'human',
        actorKey: a.id,
        label: a.label || a.id,
        wireframes: humanWireframePlacements(forActor),
      });
    });

    const sortedAutos = [...autos].sort((x, y) => {
      const cx = eventIndex[x.triggeredBy] !== undefined ? eventIndex[x.triggeredBy] : 9999;
      const cy = eventIndex[y.triggeredBy] !== undefined ? eventIndex[y.triggeredBy] : 9999;
      if (cx !== cy) return cx - cy;
      return String(x.name).localeCompare(String(y.name));
    });

    function nestedAutomationsForActorKey(actorKey) {
      return sortedAutos.filter(auto => {
        const ev = auto.triggeredBy;
        const producers = commands.filter(c => (c.produces || []).some(p => p === ev));
        if (producers.length === 0) return false;
        const keys = new Set();
        producers.forEach(cmd => {
          triggers.filter(t => t.linkedCommand === cmd.name).forEach(t => {
            keys.add(t.actor ? t.actor : EXTERNAL_ACTOR_KEY);
          });
        });
        if (keys.size !== 1) return false;
        return [...keys][0] === actorKey;
      }).sort((x, y) => {
        const cx = eventIndex[x.triggeredBy] !== undefined ? eventIndex[x.triggeredBy] : 9999;
        const cy = eventIndex[y.triggeredBy] !== undefined ? eventIndex[y.triggeredBy] : 9999;
        if (cx !== cy) return cx - cy;
        return String(x.name).localeCompare(String(y.name));
      });
    }

    const nestedAutoNames = new Set();
    humanBandSpecs.forEach(spec => {
      spec.nestedAutomations = nestedAutomationsForActorKey(spec.actorKey);
      (spec.nestedAutomations || []).forEach(a => nestedAutoNames.add(a.name));
    });

    let laneDefs = [...(model.lanes || [])];
    const defaultLaneId = '_default';
    const numCols = Math.max(1, events.length);
    const needsLanes = views.length > 0 || outboxes.length > 0 || synthDefs.length > 0 || commands.length > 0;
    if (laneDefs.length === 0 && needsLanes) {
      laneDefs = [{ id: defaultLaneId, label: model.domain || 'Subsystem' }];
    }
    const laneIds = laneDefs.map(l => l.id).filter(Boolean);
    const laneLabels = {};
    laneDefs.forEach(l => { laneLabels[l.id] = l.label || l.id; });
    const laneSet = new Set(laneIds);
    const primaryLaneId = laneIds[0] || defaultLaneId;

    function canonicalAssign(name, fallback) {
      const a = assignments[name];
      if (a && laneSet.has(a)) return a;
      if (a && !laneSet.has(a)) {
        console.warn(`[EventModelRenderer] assignment '${name}' uses unknown lane '${a}' — using '${fallback}'`);
        return fallback;
      }
      return fallback;
    }

    const laneColBuckets = {};
    laneIds.forEach(lid => {
      laneColBuckets[lid] = {};
      for (let c = 0; c < numCols; c++) laneColBuckets[lid][c] = [];
    });

    commands.forEach(cmd => {
      const prod = cmd.produces || [];
      const fe = prod[0];
      const col = fe !== undefined && eventIndex[fe] !== undefined ? eventIndex[fe] : 0;
      const laneId = canonicalAssign(cmd.name, primaryLaneId);
      if (!laneColBuckets[laneId]) return;
      laneColBuckets[laneId][col].push(cmd);
    });

    const synthAutoByEmit = {};
    sortedAutos.forEach(a => { synthAutoByEmit[a.emitsCommand] = a; });

    const synthMeta = {};
    synthDefs.forEach(s => { synthMeta[s.name] = s; });
    const syntheticPlacements = synthDefs.map(s => {
      const src = synthAutoByEmit[s.name];
      const col = src && eventIndex[src.triggeredBy] !== undefined ? eventIndex[src.triggeredBy] : 0;
      return {
        name: s.name,
        col,
        laneId: canonicalAssign(s.name, primaryLaneId),
        displayLabel: synthMeta[s.name]?.displayLabel || stripCommandSuffix(s.name),
        note: synthMeta[s.name]?.note || '',
      };
    });

    syntheticPlacements.forEach(sp => {
      if (!laneColBuckets[sp.laneId]) return;
      laneColBuckets[sp.laneId][sp.col].push({
        _synthetic: true,
        name: sp.name,
        pattern: null,
        produces: [],
        guardEvents: [],
        displayLabel: sp.displayLabel,
        note: sp.note,
      });
    });

    laneIds.forEach(lid => {
      for (let c = 0; c < numCols; c++) {
        laneColBuckets[lid][c].sort((a, b) => {
          const na = a._synthetic ? a.displayLabel || a.name : a.name;
          const nb = b._synthetic ? b.displayLabel || b.name : b.name;
          return na.localeCompare(nb);
        });
      }
    });

    function stackBlockHeight(laneId) {
      let maxH = 0;
      for (let c = 0; c < numCols; c++) {
        const bucket = laneColBuckets[laneId][c] || [];
        if (bucket.length === 0) continue;
        let colH = 0;
        bucket.forEach((cmd, bi) => {
          const firstP = (cmd.produces || [])[0];
          const hasEvt = !cmd._synthetic && firstP && eventByNameCanon[firstP];
          colH += CAN.cardH + (hasEvt ? CAN.stackGap + CAN.cardH : 0);
          if (bi < bucket.length - 1) colH += CAN.betweenStacks;
        });
        maxH = Math.max(maxH, colH);
      }
      return maxH;
    }

    const viewPlacements = views.map(view => {
      const readCols = [...new Set((view.reads || []).map(en => eventIndex[en]).filter(cx => cx !== undefined))].sort((a, b) => a - b);
      const laneId = canonicalAssign(view.name, primaryLaneId);
      let minCol = 0;
      let maxCol = Math.max(0, events.length - 1);
      if (readCols.length > 0) {
        minCol = readCols[0];
        maxCol = readCols[readCols.length - 1];
      }
      return { view, laneId, minCol, maxCol, readCols };
    });

    const headerY = headerReserveHeight(model);
    const topBands = [];
    let yCursor = headerY;

    humanBandSpecs.forEach(spec => {
      const wf = spec.wireframes || [];
      const byCol = {};
      wf.forEach(w => {
        byCol[w.col] = Math.max(byCol[w.col] || 0, w.stackIdx + 1);
      });
      let maxStacks = 1;
      Object.values(byCol).forEach(n => { maxStacks = Math.max(maxStacks, n); });
      const wfBlockH = CAN.wireframeBaseH + Math.max(0, maxStacks - 1) * CAN.wireframePerStack;
      const nestCount = (spec.nestedAutomations || []).length;
      const nestH = nestCount > 0 ? CAN.procInnerH + 8 : 0;
      const h = 8 + wfBlockH + nestH + L.lanePadBottom;
      topBands.push(Object.assign({}, spec, { y: yCursor, h, wfBlockH, nestH }));
      yCursor += h;
    });

    sortedAutos.filter(a => !nestedAutoNames.has(a.name)).forEach(auto => {
      const h = 8 + CAN.procInnerH + L.lanePadBottom;
      const fromCol = eventIndex[auto.triggeredBy] !== undefined ? eventIndex[auto.triggeredBy] : 0;
      topBands.push({
        kind: 'processor',
        label: auto.name,
        auto,
        fromCol,
        y: yCursor,
        h,
        wfBlockH: 0,
        nestH: 0,
      });
      yCursor += h;
    });

    const laneBands = [];

    laneIds.forEach(laneId => {
      const stackH = stackBlockHeight(laneId);
      const vps = viewPlacements.filter(vp => vp.laneId === laneId);
      const viewsH = vps.length === 0
        ? 0
        : vps.length * CAN.cardH + Math.max(0, vps.length - 1) * CAN.viewMarginTop + 8;
      const stackArea = stackH > 0 ? stackH + 16 : 0;
      if (stackArea === 0 && viewsH === 0) return;
      const h = L.laneHeaderH + stackArea + viewsH + L.lanePadBottom;
      laneBands.push({
        laneId,
        label: laneLabels[laneId] || laneId,
        y: yCursor,
        h,
        stackH,
        innerStacksY: yCursor + L.laneHeaderH + 8,
        viewPlacements: vps,
        viewsH,
      });
      yCursor += h;
    });

    const totalW = L.labelW + numCols * L.colW + L.rightPad;
    const totalH = yCursor + 20;

    return {
      topBands,
      laneBands,
      laneColBuckets,
      laneIds,
      laneLabels,
      primaryLaneId,
      viewPlacements,
      outboxes,
      syntheticPlacements,
      sortedAutos,
      humanBandSpecs,
      eventIndex,
      eventByNameCanon,
      eventsList: events,
      numCols,
      totalW,
      totalH,
      eventBadges: model.eventBadges || {},
      notesMap,
      commands,
      cmdByName,
      model,
      headerY,
    };
  }

  function renderCanonical(model, container) {
    const layout = buildCanonicalLayout(model);
    const notesMap = layout.notesMap || {};
    const eventBadges = layout.eventBadges;
    const tw = layout.totalW;
    const th = layout.totalH;
    const headerY = layout.headerY;

    const svg = el('svg', {
      width: tw, height: th,
      viewBox: `0 0 ${tw} ${th}`, xmlns: NS, role: 'img',
    });
    svg.appendChild(buildDefs());
    svg.appendChild(el('rect', { x: 0, y: 0, width: tw, height: th, fill: C.bg }));
    svg.appendChild(renderHeader(model, tw));

    const timeLabelY = Math.max(12, headerY - 8);
    svg.appendChild(txt('Time ───────────────────────────────────────────►', {
      x: L.labelW,
      y: timeLabelY,
      'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
      'font-size': '10',
      'font-weight': '600',
      fill: C.textMuted,
    }));

    const wfAnchors = [];
    const procAnchors = [];
    const positionsByEvent = {};
    const cmdTops = {};
    const viewCardsMeta = [];

    function laneIdForCommand(cmdName) {
      for (const lid of layout.laneIds) {
        for (let c = 0; c < layout.numCols; c++) {
          if ((layout.laneColBuckets[lid][c] || []).some(x => x.name === cmdName && !x._synthetic)) {
            return lid;
          }
        }
      }
      return layout.primaryLaneId;
    }

    function laneIdForPrimaryEvent(evName) {
      const cmd = layout.commands.find(co => (co.produces || [])[0] === evName);
      if (cmd) return laneIdForCommand(cmd.name);
      return layout.primaryLaneId;
    }

    /** Horizontal anchor for nested automations: forward (right) of the triggered event column center. */
    function nestedAutoAnchorX(evtCol) {
      const cx = defaultColCX(evtCol);
      const colRight = defaultColX(evtCol) + L.colW;
      return Math.min(cx + L.colW * 0.42, colRight - 20);
    }

    layout.topBands.forEach((bd, idx) => {
      const stripe = idx % 2 === 0 ? '#fff8e8' : '#ece4d2';
      svg.appendChild(el('rect', { x: 0, y: bd.y, width: tw, height: bd.h, fill: stripe }));
      svg.appendChild(el('line', {
        x1: 0, y1: bd.y + bd.h, x2: tw, y2: bd.y + bd.h,
        stroke: '#c9bfae', 'stroke-width': '1',
      }));

      const actorLabelAttrs = {
        x: L.labelW - 10,
        y: bd.y + bd.h / 2,
        'text-anchor': 'end',
        'dominant-baseline': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '12',
        'font-weight': '700',
        fill: C.textSub,
      };

      if (bd.kind === 'human') {
        svg.appendChild(txt(truncate(bd.label, 28), actorLabelAttrs));

        const innerTop = bd.y + 8;
        const wfBlockH = bd.wfBlockH;
        (bd.wireframes || []).forEach(wf => {
          const cx = defaultColCX(wf.col);
          const yT = innerTop + wf.stackIdx * CAN.wireframePerStack;
          const yB = yT + CAN.wireframePerStack - 2;
          svg.appendChild(txt('≡', {
            x: cx - 52, y: yT + 14,
            'font-size': '14', fill: C.textSub, 'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          }));
          svg.appendChild(txt(truncate(wf.trigger.name, 24), {
            x: cx, y: yT + 14,
            'text-anchor': 'middle',
            'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
            'font-size': '12', 'font-weight': '600', fill: C.text,
          }));
          svg.appendChild(el('line', {
            x1: cx - 28, y1: yT + 22, x2: cx + 28, y2: yT + 22,
            stroke: C.textMuted, 'stroke-width': '1', 'stroke-dasharray': '4 3',
          }));
          wfAnchors.push({
            col: wf.col,
            cx,
            yTop: yT,
            yBottom: yB,
            linkedCommand: wf.trigger.linkedCommand,
            actorKey: bd.actorKey,
          });
        });

        const nestY = innerTop + wfBlockH + 4;
        (bd.nestedAutomations || []).forEach((auto, ni) => {
          const rowY = nestY + ni * (CAN.procInnerH + 2);
          const fromCol = layout.eventIndex[auto.triggeredBy] !== undefined ? layout.eventIndex[auto.triggeredBy] : 0;
          const anchorX = nestedAutoAnchorX(fromCol);
          const ax = Math.min(anchorX, tw - 200);
          svg.appendChild(txt('⚙', {
            x: ax, y: rowY + 6,
            'font-size': '23', fill: C.textSub, 'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          }));
          svg.appendChild(txt(truncate(auto.name, 40), {
            x: ax + 26, y: rowY + 14,
            'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
            'font-size': '11', 'font-weight': '700', fill: C.text,
          }));
          svg.appendChild(txt(`on ${auto.triggeredBy} → ${stripCommandSuffix(auto.emitsCommand)}`, {
            x: ax + 26, y: rowY + 30,
            'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
            'font-size': '9', fill: C.textMuted,
          }));
          const nestBottom = rowY + CAN.procInnerH - 2;
          procAnchors.push({
            auto,
            yBottom: nestBottom,
            fromCol,
            arcFromX: anchorX,
          });
        });
      } else if (bd.kind === 'processor') {
        svg.appendChild(txt(truncate(bd.auto.name, 28), actorLabelAttrs));
        const innerY = bd.y + 8;
        svg.appendChild(txt('⚙', {
          x: L.labelW + 4, y: innerY + 4,
          'font-size': '24', fill: C.textSub, 'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        }));
        svg.appendChild(txt(`on ${bd.auto.triggeredBy} → ${stripCommandSuffix(bd.auto.emitsCommand)}`, {
          x: L.labelW + 32, y: innerY + 14,
          'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
          'font-size': '10', fill: C.textMuted,
        }));
        procAnchors.push({
          auto: bd.auto,
          yBottom: innerY + CAN.procInnerH - 2,
          fromCol: bd.fromCol,
        });
      }
    });

    layout.laneBands.forEach((lb, bi) => {
      const stripe = bi % 2 === 0 ? '#e8efe8' : '#dce8dc';
      const headStripe = bi % 2 === 0 ? '#c8dfc8' : '#b8d4b8';
      svg.appendChild(el('rect', { x: 0, y: lb.y, width: tw, height: lb.h, fill: stripe }));
      svg.appendChild(el('rect', {
        x: 0, y: lb.y, width: tw, height: L.laneHeaderH,
        fill: headStripe, stroke: '#6b846b', 'stroke-width': '1',
      }));
      svg.appendChild(txt(truncate(lb.label, 56), {
        x: tw / 2, y: lb.y + 19, 'text-anchor': 'middle',
        'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
        'font-size': '12', 'font-weight': '700', fill: C.textSub,
      }));

      const innerStacksY = lb.innerStacksY;
      const lid = lb.laneId;

      for (let c = 0; c < layout.numCols; c++) {
        const bucket = layout.laneColBuckets[lid][c] || [];
        let y = innerStacksY;
        bucket.forEach((cmd, bi) => {
          const x = defaultColX(c) + L.cardMargin;
          const w = CAN_CARD_W;
          const cx = defaultColCX(c);
          if (cmd._synthetic) {
            svg.appendChild(renderCard({
              x, y, w, h: CAN.cardH,
              colors: C.command,
              label: cmd.displayLabel || stripCommandSuffix(cmd.name),
              note: cmd.note || null,
            }));
            y += CAN.cardH;
            if (bi < bucket.length - 1) y += CAN.betweenStacks;
          } else {
            cmdTops[lid + '\n' + cmd.name] = y;
            const guardNote = (cmd.guardEvents || []).length > 0 ? `guard: ${(cmd.guardEvents || []).join(', ')}` : null;
            const guardBadge = (cmd.guardEvents || []).length > 0 ? (cmd.guardEvents || []).join(', ') : null;
            svg.appendChild(renderCard({
              x, y, w, h: CAN.cardH,
              colors: C.command,
              label: stripCommandSuffix(cmd.name),
              subtitle: guardNote,
              badge: cmd.pattern,
              guardBadge,
              note: notesMap[cmd.name] || null,
              eventBadge: eventBadges[cmd.name] || null,
            }));
            const cmdBottom = y + CAN.cardH;
            y += CAN.cardH;
            const firstP = (cmd.produces || [])[0];
            const ev = firstP ? layout.eventByNameCanon[firstP] : null;
            if (ev) {
              y += CAN.stackGap;
              svg.appendChild(renderCard({
                x, y, w, h: CAN.cardH,
                colors: C.event,
                label: ev.name,
                tags: ev.tags || [],
                eventBadge: eventBadges[ev.name] || null,
                note: notesMap[ev.name] || null,
              }));
              const evtBottom = y + CAN.cardH;
              svg.appendChild(renderArrowDirected(cx, cmdBottom + 2, cx, y - 2));
              if (!positionsByEvent[lid]) positionsByEvent[lid] = {};
              positionsByEvent[lid][ev.name] = { evtBottom, evtTop: y, col: c };
              y += CAN.cardH;
            }
            if (bi < bucket.length - 1) y += CAN.betweenStacks;
          }
        });
      }

      let vy = innerStacksY + (lb.stackH > 0 ? lb.stackH + 16 : 8);
      lb.viewPlacements.forEach(vp => {
        const cardY = vy;
        const viewCx = defaultColX(vp.minCol) + L.cardMargin + CAN_CARD_W / 2;
        svg.appendChild(renderCard({
          x: defaultColX(vp.minCol) + L.cardMargin,
          y: cardY,
          w: CAN_CARD_W,
          h: CAN.cardH,
          colors: C.view,
          label: vp.view.name,
          subtitle: `reads ${(vp.view.reads || []).length} events`,
          tags: vp.view.tag ? [vp.view.tag] : [],
          note: notesMap[vp.view.name] || null,
        }));
        viewCardsMeta.push({ vp, laneId: lid, topY: cardY, cx: viewCx });
        (vp.view.reads || []).forEach(evName => {
          const col = layout.eventIndex[evName];
          if (col === undefined) return;
          const srcLane = laneIdForPrimaryEvent(evName);
          const pack = positionsByEvent[srcLane] && positionsByEvent[srcLane][evName];
          if (!pack) return;
          const evtCx = defaultColCX(col);
          const crossLane = srcLane !== lid;
          if (cardY > pack.evtBottom + 4) {
            if (col === vp.minCol) {
              svg.appendChild(renderArrowDirected(evtCx, pack.evtBottom + 2, viewCx, cardY - 2, { dashed: crossLane }));
            } else {
              svg.appendChild(renderArrowEventToViewOrtho(evtCx, pack.evtBottom, viewCx, cardY, crossLane));
            }
          }
        });
        vy += CAN.cardH + CAN.viewMarginTop;
      });
    });

    wfAnchors.forEach(a => {
      const lid = laneIdForCommand(a.linkedCommand);
      const yCmd = cmdTops[lid + '\n' + a.linkedCommand];
      if (yCmd != null && a.yBottom + 6 < yCmd - 2) {
        svg.appendChild(renderArrowDirected(a.cx, a.yBottom + 4, a.cx, yCmd - 2));
      }
    });

    if ((model.display || {}).publicationEdges === true) {
      layout.outboxes.forEach(pub => {
        (pub.handles || []).forEach(evName => {
          const col = layout.eventIndex[evName];
          if (col === undefined) return;
          const sl = laneIdForPrimaryEvent(evName);
          const pack = positionsByEvent[sl] && positionsByEvent[sl][evName];
          if (!pack) return;
          const x0 = defaultColX(col) + L.colW - L.cardMargin - 4;
          const y0 = (pack.evtTop + pack.evtBottom) / 2;
          const x1 = Math.min(tw - L.rightPad - 4, x0 + 110);
          svg.appendChild(el('path', {
            d: `M ${x0} ${y0} L ${x1} ${y0}`,
            stroke: C.arrow,
            'stroke-width': '1.25',
            'stroke-dasharray': '4 3',
            fill: 'none',
            'marker-end': 'url(#arrowhead)',
          }));
          svg.appendChild(txt(`publication → ${truncate(pub.topic, 16)}`, {
            x: x1 + 4,
            y: y0 + 4,
            'font-family': "'Trebuchet MS','Segoe UI',sans-serif",
            'font-size': '9',
            fill: C.textMuted,
          }));
        });
      });
    }

    procAnchors.forEach(pa => {
      const synth = layout.syntheticPlacements.find(sp => sp.name === pa.auto.emitsCommand);
      if (!synth) return;
      const lid = synth.laneId;
      const c = synth.col;
      const lb = layout.laneBands.find(b => b.laneId === lid);
      if (!lb) return;
      const bucket = layout.laneColBuckets[lid][c] || [];
      const idx = bucket.findIndex(x => x._synthetic && x.name === synth.name);
      if (idx < 0) return;
      let y = lb.innerStacksY;
      for (let i = 0; i < idx; i++) {
        const cmd = bucket[i];
        if (cmd._synthetic) {
          y += CAN.cardH;
        } else {
          const firstP = (cmd.produces || [])[0];
          const hasEvt = firstP && layout.eventByNameCanon[firstP];
          y += CAN.cardH + (hasEvt ? CAN.stackGap + CAN.cardH : 0);
        }
        y += CAN.betweenStacks;
      }
      const synthTop = y;
      if (pa.yBottom + 8 < synthTop - 4) {
        const fromX = pa.arcFromX != null ? pa.arcFromX : defaultColCX(pa.fromCol);
        svg.appendChild(renderDownArc(fromX, pa.yBottom, defaultColCX(c), synthTop - 2));
      }
    });

    const wfFeedbackSeen = new Set();
    viewCardsMeta.forEach(vm => {
      wfAnchors.forEach(wf => {
        if (wf.actorKey == null) return;
        const cmd = layout.cmdByName[wf.linkedCommand];
        if (!cmd) return;
        const fe = (cmd.produces || [])[0];
        if (!fe || !(vm.vp.view.reads || []).includes(fe)) return;
        const k = `${vm.vp.view.name}\0${wf.linkedCommand}\0${wf.actorKey}`;
        if (wfFeedbackSeen.has(k)) return;
        wfFeedbackSeen.add(k);
        svg.appendChild(renderArrowDirected(vm.cx, vm.topY - 2, wf.cx, wf.yBottom + 4));
      });
    });

    const vAutoSeen = new Set();
    viewCardsMeta.forEach(vm => {
      procAnchors.forEach(pa => {
        if (!(vm.vp.view.reads || []).includes(pa.auto.triggeredBy)) return;
        const k = `${vm.vp.view.name}\0${pa.auto.name}`;
        if (vAutoSeen.has(k)) return;
        vAutoSeen.add(k);
        const ax = pa.arcFromX != null ? pa.arcFromX : defaultColCX(pa.fromCol);
        svg.appendChild(renderArrowDirected(vm.cx, vm.topY - 2, ax, pa.yBottom + 4, { dashed: true }));
      });
    });

    container.innerHTML = '';
    container.appendChild(svg);
  }
  // ─── Main render function ────────────────────────────────────────────────────
  function render(model, container) {
    model = mergeEventModelForDiagram(model, {});
    if ((model.actors || []).length > 0) {
      renderCanonical(model, container);
      return;
    }
    if ((model.lanes || []).length > 0) { renderWithLanes(model, container); return; }
    const layout  = buildLayout(model);
    const eventBadges = model.eventBadges || {};
    const { numCols, commandPlacements, syntheticPlacements,
            viewPlacements, automationPlacements,
            outboxPlacements, triggerPlacements, cmdColSlots } = layout;

    const tw = totalWidth(layout);
    const th = totalHeight(model);

    const svg = el('svg', {
      width: tw, height: th,
      viewBox: `0 0 ${tw} ${th}`,
      xmlns: NS,
      role: 'img',
    });

    svg.appendChild(buildDefs());
    svg.appendChild(renderRows(model, layout, tw));
    svg.appendChild(renderHeader(model, tw));

    const events = model.events || [];

    // ── EVENT cards ──
    if (isRowVisible(model, 'event')) {
      const rowY = rowTopY(model, 'event');
      const cardH = L.rowH.event - 2 * L.cardMargin;
      events.forEach((event, col) => {
        const physicalCol = layout.eventIndex[event.name] !== undefined ? layout.eventIndex[event.name] : col;
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(physicalCol) + L.cardMargin;
        const cardY = rowY + L.cardMargin;
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
    if (isRowVisible(model, 'command')) {
      const cmdRowY = rowTopY(model, 'command');
      const evtRowY = rowTopY(model, 'event');
      const baseCardH = L.rowH.command - 2 * L.cardMargin;

      commandPlacements.forEach(({ cmd, col, stackIdx }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(col) + L.cardMargin;
        const stackedH = baseCardH - 4;
        const cardY = cmdRowY + L.cardMargin + stackIdx * (stackedH + 8);
        const cx = defaultColCX(col);

        const guardNote = (cmd.guardEvents || []).length > 0 ? `guard: ${(cmd.guardEvents || []).join(', ')}` : null;
        const guardBadge = (cmd.guardEvents || []).length > 0 ? (cmd.guardEvents || []).join(', ') : null;
        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: stackedH,
          colors: C.command,
          label: stripCommandSuffix(cmd.name),
          subtitle: guardNote,
          badge: cmd.pattern,
          guardBadge,
        }));
        // cmd → event arrow (from actual card bottom)
        const arrowY1 = evtRowY + L.cardMargin;
        const arrowY2 = cardY + stackedH + 6;
        if (arrowY2 < arrowY1 - 4) {
          svg.appendChild(renderArrow(cx, arrowY2, arrowY1 - 4));
        }
      });

      // Synthetic command cards (cross-domain targets)
      syntheticPlacements.forEach(({ name, col, displayLabel, note }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(col) + L.cardMargin;
        const cardY = cmdRowY + L.cardMargin;
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
    if (isRowVisible(model, 'trigger')) {
      const trigRowY = rowTopY(model, 'trigger');
      const cmdRowY  = rowTopY(model, 'command');
      const cardH = L.rowH.trigger - 2 * L.cardMargin;

      triggerPlacements.forEach(({ trigger, col }) => {
        const cardW = L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(col) + L.cardMargin;
        const cardY = trigRowY + L.cardMargin;
        const cx = defaultColCX(col);

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.trigger,
          label: truncate(trigger.name, 22),
          subtitle: null,
        }));
        // trigger → command arrow (light, small arrowhead)
        const arrowY1 = cardY + cardH + 2;
        const arrowY2 = cmdRowY + L.cardMargin - 2;
        if (arrowY2 > arrowY1 + 4) {
          svg.appendChild(renderTriggerArrow(cx, arrowY1, arrowY2));
        }
      });
    }

    // ── VIEW cards + event→view arrows ──
    if (isRowVisible(model, 'view')) {
      const viewRowY = rowTopY(model, 'view');
      const evtRowY  = rowTopY(model, 'event');
      const cardH = L.rowH.view - 2 * L.cardMargin;

      viewPlacements.forEach(({ view, minCol, maxCol, readCols }) => {
        const spanCols = maxCol - minCol + 1;
        const cardW = spanCols * L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(minCol) + L.cardMargin;
        const cardY = viewRowY + L.cardMargin;

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
          const cx = defaultColCX(col);
          const y1 = evtRowY + L.rowH.event - L.cardMargin + 4;
          const y2 = viewRowY + L.cardMargin - 4;
          svg.appendChild(renderArrow(cx, y1, y2));
        });
      });
    }

    // ── AUTOMATION cards + arcs ──
    if (isRowVisible(model, 'automation')) {
      const autoRowY = rowTopY(model, 'automation');
      const evtRowY  = rowTopY(model, 'event');
      const cmdRowY  = rowTopY(model, 'command');
      const cardH = L.rowH.automation - 2 * L.cardMargin;

      automationPlacements.forEach(({ auto, fromCol, toCol, col }) => {
        const minCol = layout.hasLaneSections ? col : Math.min(fromCol, toCol);
        const maxCol = layout.hasLaneSections ? col : Math.max(fromCol, toCol);
        const spanCols = maxCol - minCol + 1;
        const cardW = Math.max(spanCols * L.colW - 2 * L.cardMargin, L.colW - 2 * L.cardMargin);
        const cardX = defaultColX(minCol) + L.cardMargin;
        const cardY = autoRowY + L.cardMargin;

        svg.appendChild(renderCard({
          x: cardX, y: cardY, w: cardW, h: cardH,
          colors: C.automation,
          label: auto.name,
          subtitle: `on ${auto.triggeredBy} → ${stripCommandSuffix(auto.emitsCommand)}`,
        }));

        // arc: event bottom → automation top
        const evtCX   = defaultColCX(fromCol);
        const evtArcY = evtRowY + L.rowH.event - L.cardMargin + 4;
        const autoTopY = cardY - 4;
        svg.appendChild(renderArc(evtCX, evtArcY, evtCX, autoTopY, 'async'));

        // arc: automation top → command bottom (loop back up)
        const cmdCX    = defaultColCX(toCol);
        const cmdArcY  = cmdRowY + L.rowH.command - L.cardMargin;
        svg.appendChild(renderArc(defaultColCX(minCol + (maxCol - minCol) / 2), autoTopY - 10, cmdCX, cmdArcY, null));
      });
    }

    // ── OUTBOX cards ──
    if (isRowVisible(model, 'outbox')) {
      const outboxRowY = rowTopY(model, 'outbox');
      const cardH = L.rowH.outbox - 2 * L.cardMargin;

      outboxPlacements.forEach(({ pub, minCol, maxCol }) => {
        const spanCols = maxCol - minCol + 1;
        const cardW = spanCols * L.colW - 2 * L.cardMargin;
        const cardX = defaultColX(minCol) + L.cardMargin;
        const cardY = outboxRowY + L.cardMargin;

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

  // ─── Horizontal swim-lane render path ────────────────────────────────────────
  function renderWithLanes(model, container) {
    const layout     = buildLaneLayout(model);
    const eventBadges = model.eventBadges || {};
    const tw = totalWidthLanes(layout);
    const th = totalHeightLanes(layout);

    const svg = el('svg', {
      width: tw, height: th, viewBox: `0 0 ${tw} ${th}`, xmlns: NS, role: 'img',
    });
    svg.appendChild(buildDefs());
    svg.appendChild(renderRowsLanes(layout, tw));
    svg.appendChild(renderHeader(model, tw));

    layout.laneIds.forEach((laneId, laneIdx) => {
      const ld = layout.laneData[laneId];

      // EVENT cards
      if (isRowVisibleInLane(ld, 'event')) {
        const rowY = rowTopYInLane(layout, laneIdx, 'event');
        const cardH = L.rowH.event - 2 * L.cardMargin;
        (model.events || []).filter(e => layout.eventLanes[e.name] === laneId).forEach(event => {
          const col = layout.eventIndex[event.name];
          svg.appendChild(renderCard({
            x: defaultColX(col) + L.cardMargin, y: rowY + L.cardMargin,
            w: L.colW - 2 * L.cardMargin, h: cardH,
            colors: C.event, label: event.name,
            tags: event.tags || [],
            eventBadge: eventBadges[event.name] || null,
          }));
        });
      }

      // COMMAND cards + cmd→event arrows
      if (isRowVisibleInLane(ld, 'command')) {
        const cmdRowY  = rowTopYInLane(layout, laneIdx, 'command');
        const evtRowY  = isRowVisibleInLane(ld, 'event') ? rowTopYInLane(layout, laneIdx, 'event') : null;
        const baseCardH = L.rowH.command - 2 * L.cardMargin;

        ld.commandPlacements.forEach(({ cmd, col, stackIdx }) => {
          const stackedH = baseCardH - 4;
          const cardY    = cmdRowY + L.cardMargin + stackIdx * (stackedH + 8);
          const cx       = defaultColCX(col);
          const guardBadge = (cmd.guardEvents || []).length > 0 ? (cmd.guardEvents || []).join(', ') : null;
          svg.appendChild(renderCard({
            x: defaultColX(col) + L.cardMargin, y: cardY,
            w: L.colW - 2 * L.cardMargin, h: stackedH,
            colors: C.command, label: stripCommandSuffix(cmd.name),
            subtitle: (cmd.guardEvents || []).length > 0 ? `guard: ${(cmd.guardEvents || []).join(', ')}` : null,
            badge: cmd.pattern,
            guardBadge,
          }));
          if (evtRowY !== null) {
            const arrowY1 = evtRowY + L.cardMargin;
            const arrowY2 = cardY + stackedH + 6;
            if (arrowY2 < arrowY1 - 4) svg.appendChild(renderArrow(cx, arrowY2, arrowY1 - 4));
          }
        });

        ld.syntheticPlacements.forEach(({ col, displayLabel, note }) => {
          svg.appendChild(renderCard({
            x: defaultColX(col) + L.cardMargin, y: cmdRowY + L.cardMargin,
            w: L.colW - 2 * L.cardMargin, h: baseCardH,
            colors: C.command, label: displayLabel, subtitle: 'synthetic', note,
          }));
        });
      }

      // TRIGGER cards + trigger→command arrows
      if (isRowVisibleInLane(ld, 'trigger')) {
        const trigRowY = rowTopYInLane(layout, laneIdx, 'trigger');
        const cmdRowY  = rowTopYInLane(layout, laneIdx, 'command');
        const cardH    = L.rowH.trigger - 2 * L.cardMargin;
        ld.triggerPlacements.forEach(({ trigger, col }) => {
          const cardY = trigRowY + L.cardMargin;
          svg.appendChild(renderCard({
            x: defaultColX(col) + L.cardMargin, y: cardY,
            w: L.colW - 2 * L.cardMargin, h: cardH,
            colors: C.trigger, label: truncate(trigger.name, 22),
          }));
          const arrowY1 = cardY + cardH + 2;
          const arrowY2 = cmdRowY + L.cardMargin - 2;
          if (arrowY2 > arrowY1 + 4) svg.appendChild(renderTriggerArrow(defaultColCX(col), arrowY1, arrowY2));
        });
      }

      // VIEW cards + event→view arrows (in-lane events only)
      if (isRowVisibleInLane(ld, 'view')) {
        const viewRowY = rowTopYInLane(layout, laneIdx, 'view');
        const evtRowY  = isRowVisibleInLane(ld, 'event') ? rowTopYInLane(layout, laneIdx, 'event') : null;
        const cardH    = L.rowH.view - 2 * L.cardMargin;
        ld.viewPlacements.forEach(({ view, minCol, maxCol, readCols }) => {
          const spanCols = maxCol - minCol + 1;
          svg.appendChild(renderCard({
            x: defaultColX(minCol) + L.cardMargin, y: viewRowY + L.cardMargin,
            w: spanCols * L.colW - 2 * L.cardMargin, h: cardH,
            colors: C.view, label: view.name,
            subtitle: `reads ${(view.reads || []).length} events`,
            tags: view.tag ? [view.tag] : [],
          }));
          if (evtRowY !== null) {
            const drawnCols = new Set();
            readCols.forEach(col => {
              if (drawnCols.has(col)) return;
              drawnCols.add(col);
              svg.appendChild(renderArrow(defaultColCX(col),
                evtRowY + L.rowH.event - L.cardMargin + 4,
                viewRowY + L.cardMargin - 4));
            });
          }
        });
      }

      // AUTOMATION cards + within-lane arcs
      if (isRowVisibleInLane(ld, 'automation')) {
        const autoRowY = rowTopYInLane(layout, laneIdx, 'automation');
        const evtRowY  = isRowVisibleInLane(ld, 'event')   ? rowTopYInLane(layout, laneIdx, 'event')   : null;
        const cmdRowY  = isRowVisibleInLane(ld, 'command')  ? rowTopYInLane(layout, laneIdx, 'command')  : null;
        const cardH    = L.rowH.automation - 2 * L.cardMargin;
        ld.automationPlacements.forEach(({ auto, fromCol, toCol, isCrossLane }) => {
          const cardY = autoRowY + L.cardMargin;
          svg.appendChild(renderCard({
            x: defaultColX(fromCol) + L.cardMargin, y: cardY,
            w: L.colW - 2 * L.cardMargin, h: cardH,
            colors: C.automation, label: auto.name,
            subtitle: `on ${auto.triggeredBy} → ${stripCommandSuffix(auto.emitsCommand)}`,
          }));
          // event → automation arc (always within source lane)
          if (evtRowY !== null) {
            const evtCX   = defaultColCX(fromCol);
            const evtArcY = evtRowY + L.rowH.event - L.cardMargin + 4;
            svg.appendChild(renderArc(evtCX, evtArcY, evtCX, cardY - 4, 'async'));
          }
          // automation → command arc (within-lane only)
          if (!isCrossLane && cmdRowY !== null) {
            const cmdCX   = defaultColCX(toCol);
            const cmdArcY = cmdRowY + L.rowH.command - L.cardMargin;
            svg.appendChild(renderArc(defaultColCX(fromCol), cardY - 10, cmdCX, cmdArcY, null));
          }
        });
      }

      // OUTBOX cards
      if (isRowVisibleInLane(ld, 'outbox')) {
        const outboxRowY = rowTopYInLane(layout, laneIdx, 'outbox');
        const cardH      = L.rowH.outbox - 2 * L.cardMargin;
        ld.outboxPlacements.forEach(({ pub, minCol, maxCol }) => {
          const spanCols = maxCol - minCol + 1;
          svg.appendChild(renderCard({
            x: defaultColX(minCol) + L.cardMargin, y: outboxRowY + L.cardMargin,
            w: spanCols * L.colW - 2 * L.cardMargin, h: cardH,
            colors: C.outbox, label: pub.name,
            subtitle: `topic: ${pub.topic}`, tags: pub.handles || [],
          }));
        });
      }
    });

    // Cross-lane automation arcs (after all per-lane cards are drawn)
    layout.laneIds.forEach((laneId, laneIdx) => {
      const ld = layout.laneData[laneId];
      if (!isRowVisibleInLane(ld, 'automation')) return;
      const autoRowY = rowTopYInLane(layout, laneIdx, 'automation');
      const cardH    = L.rowH.automation - 2 * L.cardMargin;
      ld.automationPlacements.filter(p => p.isCrossLane).forEach(({ fromCol, toCol, toLane }) => {
        const srcCX = defaultColCX(fromCol);
        const srcY  = autoRowY + L.cardMargin + cardH;
        const tgtLaneIdx = layout.laneIds.indexOf(toLane);
        if (tgtLaneIdx < 0) return;
        const tgtLd = layout.laneData[toLane];
        if (!isRowVisibleInLane(tgtLd, 'command')) return;
        const tgtCX = defaultColCX(toCol);
        const tgtY  = rowTopYInLane(layout, tgtLaneIdx, 'command') + L.cardMargin;
        svg.appendChild(renderDownArc(srcCX, srcY, tgtCX, tgtY));
      });
    });

    container.innerHTML = '';
    container.appendChild(svg);
  }

  // ─── Public API ──────────────────────────────────────────────────────────────
  window.EventModelRenderer = { render, mergeEventModelForDiagram };
})();
