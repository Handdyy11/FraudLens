/**
 * FraudLens — app.js
 * All fetch() calls to the Spring Boot API, Vis.js graph logic,
 * date filter, display mode switching, and account table sorting.
 * No frameworks. No npm. Pure vanilla JavaScript.
 *
 * Status labels used everywhere: CRITICAL, HIGH, MEDIUM, LOW, NORMAL
 */

'use strict';

const API = '/fraudlens/api';

// ─────────────────────────────────────────────
// DASHBOARD (index.html)
// ─────────────────────────────────────────────

function loadDashboard() {
  refreshStats();
  refreshAlerts();
  refreshFeed();
  // Poll every 3 seconds
  setInterval(() => { refreshStats(); refreshFeed(); }, 3000);
}

async function refreshStats() {
  try {
    const data = await fetchJSON(`${API}/stats`);
    setText('stat-total',    data.totalTransactions.toLocaleString());
    setText('stat-accounts', data.totalAccounts);
    setText('stat-cycles',   data.cyclesDetected);
    setText('stat-hubs',     data.hubAccounts);
    setText('stat-rapids',   data.rapidHopAlerts);
    setText('stat-alerts',   data.fraudAlerts);
  } catch (e) { console.error('Stats error', e); }
}

async function refreshAlerts() {
  const list = document.getElementById('alert-list');
  if (!list) return;
  try {
    const [cycles, hubs, rapids, thresholds] = await Promise.all([
      fetchJSON(`${API}/fraud/cycles`),
      fetchJSON(`${API}/fraud/hubs`),
      fetchJSON(`${API}/fraud/rapid-hops`),
      fetchJSON(`${API}/fraud/thresholds`)
    ]);

    const alerts = [];
    cycles.forEach(c => alerts.push({ type: 'CYCLE', desc: 'Circular pattern: ' + c.join(' → ') + ' → ' + c[0], accts: c }));
    if (hubs.length)       alerts.push({ type: 'HUB',       desc: 'High-degree hub accounts detected',          accts: hubs });
    if (rapids.length)     alerts.push({ type: 'RAPID_HOP',  desc: 'Rapid fund-layering within 5-minute window', accts: rapids });
    if (thresholds.length) alerts.push({ type: 'THRESHOLD',  desc: 'Structuring / smurfing below reporting threshold', accts: thresholds });

    setText('alert-count', alerts.length + ' alert' + (alerts.length !== 1 ? 's' : ''));

    if (alerts.length === 0) {
      list.innerHTML = '<div class="empty"><div class="empty-icon">✅</div><p>No fraud patterns detected.</p></div>';
      return;
    }

    list.innerHTML = alerts.map(a => `
      <div class="alert-item ${a.type}">
        <div class="alert-icon">${alertIcon(a.type)}</div>
        <div class="alert-body">
          <div class="alert-type">${a.type.replace('_', ' ')}</div>
          <div class="alert-desc">${a.desc}</div>
          <div class="alert-accts">${a.accts.slice(0, 8).join(', ')}${a.accts.length > 8 ? ' +' + (a.accts.length - 8) + ' more' : ''}</div>
        </div>
      </div>`).join('');
  } catch (e) { list.innerHTML = '<div class="empty"><p>Failed to load alerts.</p></div>'; }
}

async function refreshFeed() {
  const feed = document.getElementById('txn-feed');
  if (!feed) return;
  try {
    const txns = await fetchJSON(`${API}/transactions`);
    if (!txns.length) { feed.innerHTML = '<div class="empty"><p>No transactions yet.</p></div>'; return; }
    feed.innerHTML = txns.slice(0, 30).map(t => `
      <div class="txn-row">
        <div class="acct">${t.fromAccount}</div>
        <div class="arrow">→</div>
        <div class="acct">${t.toAccount}</div>
        <div class="amount">₹${Math.round(t.amount).toLocaleString()}</div>
        <div class="type-tag"><span class="badge badge-${(t.type || 'NORMAL').toLowerCase()}">${t.type || 'NORMAL'}</span></div>
        <div style="font-size:11px;color:var(--text-muted);">${fmtDate(t.timestamp)}</div>
      </div>`).join('');
  } catch (e) { feed.innerHTML = '<div class="empty"><p>Failed to load feed.</p></div>'; }
}

// ─────────────────────────────────────────────
// GRAPH PAGE (graph.html)
// ─────────────────────────────────────────────

let network = null;
let currentViewMode = 'month';
let cachedNodes = null;
let cachedEdges = null;

async function loadGraph() {
  // Hide date picker initially for month view
  const datePicker = document.getElementById('date-picker');
  const dateLabel = document.getElementById('date-label');
  if (datePicker) datePicker.style.display = 'none';
  if (dateLabel) dateLabel.style.display = 'none';

  const filterPanel = document.getElementById('month-filter-panel');
  if (filterPanel) filterPanel.style.display = 'flex';
  
  await updateGraph();
}

async function updateGraph() {
  const datePicker = document.getElementById('date-picker');
  const date = datePicker ? datePicker.value : '2024-01-18';
  
  try {
    const data = await fetchJSON(`${API}/graph?view=${currentViewMode}&date=${date}`);
    cachedNodes = data.nodes;
    cachedEdges = data.edges;
    renderNetwork(cachedNodes, cachedEdges);
  } catch (e) {
    document.getElementById('network-container').innerHTML =
      '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:rgba(255,255,255,0.5)">Failed to load graph.</div>';
  }
}

function onFilterChange() {
  if (currentViewMode === 'month' && cachedNodes && cachedEdges) {
    renderNetwork(cachedNodes, cachedEdges);
  }
}

function renderNetwork(nodes, edges) {
  if (network) {
    network.destroy();
    network = null;
  }

  const container = document.getElementById('network-container');
  const isMonthView = currentViewMode === 'month';
  const filteredData = isMonthView ? filterMonthGraphData(nodes, edges) : { nodes, edges };

  const visNodes = new vis.DataSet(filteredData.nodes.map(n => {
    const meta = getNodeVisualMeta(n, isMonthView);
    return {
      id: n.id,
      label: n.id,
      title: `${n.id}\nStatus: ${n.status}\nRisk Score: ${n.riskScore}`,
      color: { background: meta.color, border: meta.border, highlight: { background: '#ffffff', border: '#0C447C' } },
      size: meta.size,
      font: { color: meta.fontColor, size: meta.fontSize, face: 'Segoe UI', bold: meta.isHighlighted },
      borderWidth: meta.borderWidth,
      shadow: meta.shadow,
      shape: 'dot',
      mass: meta.isHighlighted ? 2 : 1
    };
  }));

  const nodeMap = new Map(nodes.map(n => [n.id, n]));
  const visEdges = new vis.DataSet(filteredData.edges.map((e, i) => {
    const meta = getEdgeVisualMeta(e, isMonthView, nodeMap);
    return {
      id: `edge_${i}`,
      originalIndex: i,
      from: e.from,
      to: e.to,
      color: { color: meta.color, highlight: meta.highlightColor },
      width: meta.width,
      arrows: { to: { enabled: true, scaleFactor: 0.5 } },
      smooth: { type: 'straightCross', roundness: 0.0 },
      title: `${e.from} → ${e.to}\n₹${Math.round(e.totalAmount || 0).toLocaleString()} (${e.txnCount} txns)`
    };
  }));

  setText('node-count', filteredData.nodes.length);
  setText('edge-count', filteredData.edges.length);

  network = new vis.Network(container, { nodes: visNodes, edges: visEdges }, {
    autoResize: true,
    interaction: {
      hover: true,
      tooltipDelay: 200,
      hideEdgesOnDrag: true,
      dragView: true,
      zoomView: true,
      selectConnectedEdges: false
    },
    physics: isMonthView ? {
      enabled: true,
      stabilization: { iterations: 300, fit: true, updateInterval: 20 },
      solver: 'forceAtlas2Based',
      forceAtlas2Based: {
        gravitationalConstant: -90,
        centralGravity: 0.01,
        springLength: 180,
        springConstant: 0.025,
        damping: 0.08,
        avoidOverlap: 1.0
      }
    } : {
      enabled: true,
      stabilization: { iterations: 150, fit: true, updateInterval: 20 },
      barnesHut: { gravitationalConstant: -8000, springLength: 120 }
    },
    layout: { improvedLayout: true, randomSeed: 2 },
    edges: { smooth: false, arrows: { to: { enabled: true, scaleFactor: 0.45 } } },
    nodes: { shape: 'dot', font: { face: 'Segoe UI', size: isMonthView ? 12 : 10 } }
  });

  network.once('stabilizationIterationsDone', function () {
    if (!network) return;

    network.stopSimulation();
    network.setOptions({ physics: { enabled: false } });

    const frozenNodes = visNodes.getIds().map(id => ({
      id,
      fixed: { x: true, y: true },
      physics: false
    }));

    visNodes.update(frozenNodes);
    network.redraw();
  });

  network.on('click', params => {
    if (params.nodes.length > 0) {
      const nodeId = params.nodes[0];
      showDetailPanel(nodeId, filteredData.nodes, filteredData.edges);
    }
  });

  if (isMonthView) {
    network.on('hoverNode', function (params) {
      const hoveredId = params.node;
      const connectedNodes = network.getConnectedNodes(hoveredId);
      const connectedEdges = network.getConnectedEdges(hoveredId);

      const neighbors = new Set(connectedNodes);
      neighbors.add(hoveredId);

      const edgesToHighlight = new Set(connectedEdges);

      const nodeUpdates = visNodes.getIds().map(id => {
        const nodeObj = nodes.find(n => n.id === id);
        if (!nodeObj) return { id };
        const meta = getNodeVisualMeta(nodeObj, isMonthView);

        if (neighbors.has(id)) {
          return {
            id: id,
            color: { background: meta.color, border: meta.border },
            font: { color: meta.fontColor }
          };
        } else {
          return {
            id: id,
            color: {
              background: 'rgba(75, 85, 99, 0.15)',
              border: 'rgba(50, 50, 50, 0.1)'
            },
            font: { color: 'rgba(255, 255, 255, 0.15)' }
          };
        }
      });

      const edgeUpdates = visEdges.get().map(edge => {
        if (edgesToHighlight.has(edge.id)) {
          const edgeObj = filteredData.edges[edge.originalIndex];
          const meta = getEdgeVisualMeta(edgeObj, isMonthView, nodeMap);
          return {
            id: edge.id,
            color: { color: meta.color }
          };
        } else {
          return {
            id: edge.id,
            color: { color: 'rgba(255, 255, 255, 0.05)' }
          };
        }
      });

      visNodes.update(nodeUpdates);
      visEdges.update(edgeUpdates);
    });

    network.on('blurNode', function (params) {
      const nodeUpdates = visNodes.getIds().map(id => {
        const nodeObj = nodes.find(n => n.id === id);
        if (!nodeObj) return { id };
        const meta = getNodeVisualMeta(nodeObj, isMonthView);
        return {
          id: id,
          color: { background: meta.color, border: meta.border },
          font: { color: meta.fontColor }
        };
      });

      const edgeUpdates = visEdges.get().map(edge => {
        const edgeObj = filteredData.edges[edge.originalIndex];
        const meta = getEdgeVisualMeta(edgeObj, isMonthView, nodeMap);
        return {
          id: edge.id,
          color: { color: meta.color }
        };
      });

      visNodes.update(nodeUpdates);
      visEdges.update(edgeUpdates);
    });

    setTimeout(() => network.fit({ animation: true, duration: 450 }), 180);
  }
}

function isMerchantAccount(nodeId) {
  if (nodeId && nodeId.startsWith('ACC_')) {
    const num = parseInt(nodeId.substring(4), 10);
    return num >= 81 && num <= 100;
  }
  return false;
}

function filterMonthGraphData(nodes, edges) {
  const fraudChecked = document.getElementById('filter-fraud')?.checked ?? true;
  const suspiciousChecked = document.getElementById('filter-suspicious')?.checked ?? true;
  const highRiskChecked = document.getElementById('filter-high-risk')?.checked ?? true;
  const normalChecked = document.getElementById('filter-normal')?.checked ?? false;
  const merchantChecked = document.getElementById('filter-merchant')?.checked ?? false;

  function isNodeCategoryChecked(node) {
    const status = (node.status || 'NORMAL').toUpperCase();
    const isMerchant = isMerchantAccount(node.id);

    if (isMerchant) return merchantChecked;
    if (status === 'FRAUD') return fraudChecked;
    if (status === 'SUSPICIOUS') return suspiciousChecked;
    if (status === 'AT_RISK') return highRiskChecked;
    return normalChecked;
  }

  // Only include transactions >= 5000 to filter out daily lifestyle clutter
  const significantEdges = edges.filter(e => (e.totalAmount || 0) >= 5000);

  // 1. Find the primary / seed nodes
  const seedIds = new Set(nodes.filter(n => isNodeCategoryChecked(n)).map(n => n.id));

  // 2. Identify neighbors and visible nodes
  const visibleNodeIds = new Set(seedIds);
  significantEdges.forEach(edge => {
    if (seedIds.has(edge.from) || seedIds.has(edge.to)) {
      visibleNodeIds.add(edge.from);
      visibleNodeIds.add(edge.to);
    }
  });

  const filteredNodes = nodes.filter(n => visibleNodeIds.has(n.id));
  const filteredEdges = significantEdges.filter(e => visibleNodeIds.has(e.from) && visibleNodeIds.has(e.to));

  return {
    nodes: filteredNodes,
    edges: filteredEdges
  };
}

function getNodeVisualMeta(node, isMonthView) {
  const status = (node.status || 'NORMAL').toUpperCase();
  const isMerchant = isMerchantAccount(node.id);

  if (isMonthView) {
    if (isMerchant) {
      return {
        color: 'rgba(75, 85, 99, 0.8)',
        border: '#1f2937',
        fontColor: 'rgba(255, 255, 255, 0.6)',
        fontSize: 10,
        size: 8,
        borderWidth: 1.0,
        shadow: false,
        isHighlighted: false
      };
    }

    if (status === 'FRAUD') {
      return {
        color: '#FF3333',
        border: '#b82b2a',
        fontColor: '#ffffff',
        fontSize: 12,
        size: 30,
        borderWidth: 3.5,
        shadow: true,
        isHighlighted: true
      };
    }
    if (status === 'SUSPICIOUS') {
      return {
        color: '#EF9F27',
        border: '#c07b10',
        fontColor: '#ffffff',
        fontSize: 11,
        size: 22,
        borderWidth: 2.5,
        shadow: true,
        isHighlighted: true
      };
    }
    if (status === 'AT_RISK') {
      return {
        color: '#4e9af1',
        border: '#2e6fc7',
        fontColor: '#ffffff',
        fontSize: 11,
        size: 16,
        borderWidth: 2.2,
        shadow: true,
        isHighlighted: true
      };
    }

    return {
      color: 'rgba(201, 162, 122, 0.6)',
      border: 'rgba(143, 107, 69, 0.6)',
      fontColor: 'rgba(255, 255, 255, 0.7)',
      fontSize: 10,
      size: 10,
      borderWidth: 1.0,
      shadow: false,
      isHighlighted: false
    };
  }

  return {
    color: nodeColor(status),
    border: nodeBorder(status),
    fontColor: '#ffffff',
    fontSize: 10,
    size: 8 + Math.min((node.riskScore || 0) / 4, 20),
    borderWidth: (status === 'FRAUD' || status === 'SUSPICIOUS') ? 3 : 1.5,
    shadow: status !== 'NORMAL',
    isHighlighted: status !== 'NORMAL'
  };
}

function getEdgeVisualMeta(edge, isMonthView, nodeMap) {
  if (isMonthView && nodeMap) {
    const fromNode = nodeMap.get(edge.from);
    const toNode = nodeMap.get(edge.to);

    const fromStatus = (fromNode?.status || 'NORMAL').toUpperCase();
    const toStatus = (toNode?.status || 'NORMAL').toUpperCase();

    const isFraud = fromStatus === 'FRAUD' || toStatus === 'FRAUD';
    const isSuspicious = fromStatus === 'SUSPICIOUS' || toStatus === 'SUSPICIOUS';
    const isHighRisk = fromStatus === 'AT_RISK' || toStatus === 'AT_RISK';

    if (isFraud) {
      return {
        color: 'rgba(255, 51, 51, 0.9)',
        highlightColor: '#FF3333',
        width: 4.0
      };
    }
    if (isSuspicious) {
      return {
        color: 'rgba(239, 159, 39, 0.8)',
        highlightColor: '#EF9F27',
        width: 2.5
      };
    }
    if (isHighRisk) {
      return {
        color: 'rgba(78, 154, 241, 0.8)',
        highlightColor: '#4e9af1',
        width: 2.0
      };
    }

    return {
      color: 'rgba(201, 162, 122, 0.25)',
      highlightColor: 'rgba(201, 162, 122, 0.6)',
      width: 1.0
    };
  }

  return {
    color: edge.type === 'FRAUD' ? 'rgba(226,75,74,0.7)' : 'rgba(100,150,200,0.25)',
    highlightColor: '#EF9F27',
    width: edge.type === 'FRAUD' ? 2.5 : 1
  };
}

async function setViewMode(mode) {
  currentViewMode = mode;
  ['month', 'week', 'day'].forEach(m => {
    const btn = document.getElementById('btn-' + m);
    if (btn) btn.classList.toggle('active', m === mode);
  });

  const datePicker = document.getElementById('date-picker');
  const dateLabel = document.getElementById('date-label');
  const filterPanel = document.getElementById('month-filter-panel');

  if (mode === 'month') {
    if (datePicker) datePicker.style.display = 'none';
    if (dateLabel) dateLabel.style.display = 'none';
    if (filterPanel) filterPanel.style.display = 'flex';
  } else {
    if (datePicker) datePicker.style.display = 'inline-block';
    if (dateLabel) dateLabel.style.display = 'inline-block';
    if (filterPanel) filterPanel.style.display = 'none';
  }

  await updateGraph();
}

async function onDateChange() {
  if (currentViewMode !== 'month') {
    await updateGraph();
  }
}

function showDetailPanel(nodeId, nodes, edges) {
  const node = nodes.find(n => n.id === nodeId);
  if (!node) return;
  const outEdges = edges.filter(e => e.from === nodeId);
  const inEdges  = edges.filter(e => e.to   === nodeId);
  document.getElementById('dp-title').textContent = nodeId;
  document.getElementById('dp-content').innerHTML = `
    <div class="detail-row"><span class="detail-key">Status</span><span class="detail-value"><span class="badge badge-${statusBadgeClass(node.status)}">${node.status}</span></span></div>
    <div class="detail-row"><span class="detail-key">Risk Score</span><span class="detail-value">${node.riskScore}</span></div>
    <div class="detail-row"><span class="detail-key">Outgoing links</span><span class="detail-value">${outEdges.length}</span></div>
    <div class="detail-row"><span class="detail-key">Incoming links</span><span class="detail-value">${inEdges.length}</span></div>
    <div class="detail-row"><span class="detail-key">Total sent</span><span class="detail-value">₹${Math.round(outEdges.reduce((s,e)=>s+(e.totalAmount||0),0)).toLocaleString()}</span></div>
    <div class="detail-row"><span class="detail-key">Total received</span><span class="detail-value">₹${Math.round(inEdges.reduce((s,e)=>s+(e.totalAmount||0),0)).toLocaleString()}</span></div>`;
  document.getElementById('detail-panel').classList.add('open');
}

function closeDetailPanel() { document.getElementById('detail-panel').classList.remove('open'); }

// ─────────────────────────────────────────────
// ACCOUNTS PAGE (accounts.html)
// ─────────────────────────────────────────────

let allAccountData = [];
let sortKey = 'riskScore', sortDir = -1;

async function loadAccounts() {
  try {
    allAccountData = await fetchJSON(`${API}/accounts`);
    renderTable();
  } catch (e) {
    document.getElementById('table-container').innerHTML = '<div class="empty"><p>Failed to load accounts.</p></div>';
  }
}

function renderTable() {
  const search = (document.getElementById('search-input')?.value || '').toLowerCase();
  const statusF = document.getElementById('status-filter')?.value || '';

  let rows = allAccountData.filter(a =>
    (a.accountId.toLowerCase().includes(search) || a.name.toLowerCase().includes(search)) &&
    (!statusF || a.status === statusF)
  );

  rows.sort((a, b) => {
    const av = a[sortKey], bv = b[sortKey];
    return typeof av === 'number' ? (av - bv) * sortDir : String(av).localeCompare(String(bv)) * sortDir;
  });

  setText('row-count', `Showing ${rows.length} of ${allAccountData.length} accounts`);

  if (!rows.length) {
    document.getElementById('table-container').innerHTML = '<div class="empty"><div class="empty-icon">🔍</div><p>No accounts match your filter.</p></div>';
    return;
  }

  document.getElementById('table-container').innerHTML = `
    <table>
      <thead><tr>
        ${thCell('accountId', 'Account ID')}
        ${thCell('name', 'Name')}
        ${thCell('totalTransactions', 'Transactions')}
        ${thCell('riskScore', 'Risk Score')}
        ${thCell('status', 'Status')}
      </tr></thead>
      <tbody>
        ${rows.map(a => `
          <tr>
            <td class="mono">${a.accountId}</td>
            <td>${a.name}</td>
            <td style="text-align:right">${Number(a.totalTransactions).toLocaleString()}</td>
            <td style="text-align:right">
              <div style="display:flex;align-items:center;gap:8px;justify-content:flex-end;">
                <div style="width:60px;height:6px;background:#e8ecf0;border-radius:4px;overflow:hidden;">
                  <div style="width:${Math.min(a.riskScore, 100)}%;height:100%;background:${riskColor(a.riskScore)};border-radius:4px;"></div>
                </div>
                <span style="font-weight:700;min-width:30px;text-align:right">${a.riskScore}</span>
              </div>
            </td>
            <td><span class="badge badge-${statusBadgeClass(a.status)}">${a.status}</span></td>
          </tr>`).join('')}
      </tbody>
    </table>`;
}

function thCell(key, label) {
  const active = key === sortKey ? ' sorted' : '';
  const icon = key === sortKey ? (sortDir === -1 ? '↓' : '↑') : '↕';
  return `<th class="${active}" onclick="sortBy('${key}')">${label} <span class="sort-icon">${icon}</span></th>`;
}

function sortBy(key) {
  if (sortKey === key) sortDir *= -1; else { sortKey = key; sortDir = -1; }
  renderTable();
}

function filterAccounts() { renderTable(); }


// ─────────────────────────────────────────────
// SHARED HELPERS
// ─────────────────────────────────────────────

async function fetchJSON(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  return res.json();
}

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
}

function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: '2-digit' });
}

/** Maps status labels to node background colors. */
function nodeColor(status) {
  return {
    FRAUD:      '#E24B4A',
    SUSPICIOUS: '#EF9F27',
    AT_RISK:    '#a1855c',
    NORMAL:     '#4e9af1'
  }[status] || '#4e9af1';
}
function nodeBorder(status) {
  return {
    FRAUD:      '#b82b2a',
    SUSPICIOUS: '#c07b10',
    AT_RISK:    '#7a5c35',
    NORMAL:     '#2e6fc7'
  }[status] || '#2e6fc7';
}

/** Maps numeric risk score to a color. */
function riskColor(score) {
  if (score >= 80) return '#E24B4A';
  if (score >= 40) return '#EF9F27';
  if (score >= 15) return '#a1855c';
  return '#639922';
}

/** Maps status string to CSS badge class suffix. */
function statusBadgeClass(status) {
  return (status || 'normal').toLowerCase().replace('_', '-');
}

function alertIcon(type) {
  return { CYCLE: '🔄', HUB: '🎯', RAPID_HOP: '⚡', THRESHOLD: '⚖️' }[type] || '⚠';
}
