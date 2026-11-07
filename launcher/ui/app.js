const TOKEN = new URLSearchParams(location.search).get('t');
const $ = (id) => document.getElementById(id);
const LOADERS = { vanilla: 'Vanilla', fabric: 'Fabric', quilt: 'Quilt', forge: 'Forge', neoforge: 'NeoForge', liteloader: 'LiteLoader' };
let state = null, versions = null, editing = null;

async function api(path, body) {
  const opt = { headers: { 'X-Token': TOKEN } };
  if (body !== undefined) { opt.method = 'POST'; opt.body = JSON.stringify(body); opt.headers['Content-Type'] = 'application/json'; }
  const r = await fetch(path, opt);
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || r.statusText);
  return j;
}
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function showErr(el, e) { el.textContent = e ? (e.message || e) : ''; el.style.display = e ? 'block' : 'none'; }

// ---------- pages ----------
document.querySelectorAll('nav button').forEach((b) => b.onclick = () => go(b.dataset.page));
function go(page) {
  document.querySelectorAll('nav button').forEach((b) => b.classList.toggle('on', b.dataset.page === page));
  document.querySelectorAll('main > section').forEach((s) => s.classList.toggle('hide', s.id !== 'page-' + page));
  if (page === 'mods') loadInstalled();
}

function profileLabel(p) { return `${LOADERS[p.loader || 'vanilla']} ${p.version}`; }

function render() {
  const s = state;
  $('foot').textContent = 'Version ' + s.version;
  // profile pickers
  for (const id of ['playProfile', 'modProfile']) {
    const sel = $(id), keep = sel.value || s.selected;
    sel.innerHTML = s.profiles.map((p) => `<option value="${esc(p.name)}">${esc(p.name)} - ${esc(profileLabel(p))}</option>`).join('');
    sel.value = s.profiles.some((p) => p.name === keep) ? keep : (s.profiles[0] || {}).name;
  }
  $('playAccount').innerHTML = s.accounts.length
    ? s.accounts.map((a) => `<option value="${esc(a.id)}">${esc(a.name)} (${a.type === 'microsoft' ? 'Microsoft' : 'offline'})</option>`).join('')
    : '<option value="">No account yet - add one in Accounts</option>';
  $('playAccount').value = s.account || '';
  updatePlayCard();
  // profiles list
  $('profileList').innerHTML = s.profiles.map((p) => `
    <div><div class="grow"><div class="t">${esc(p.name)}</div>
      <div class="s">${esc(profileLabel(p))} · ${Math.round((p.ram || 4096) / 1024 * 10) / 10} GB${p.boost ? ' · FPS Boost' : ''}${p.olkkuMod ? ' · Olkku menu' : ''}</div></div>
      ${s.running.includes(p.name) ? '<span class="pill inv">Running</span>' : ''}
      <button class="btn ghost" data-edit="${esc(p.name)}">Edit</button>
      <button class="btn ghost" data-folder="${esc(p.name)}">Folder</button>
      <button class="btn" data-play="${esc(p.name)}">Play</button></div>`).join('') || '<div class="muted">No profiles yet.</div>';
  document.querySelectorAll('[data-edit]').forEach((b) => b.onclick = () => openEditor(s.profiles.find((p) => p.name === b.dataset.edit)));
  document.querySelectorAll('[data-folder]').forEach((b) => b.onclick = () => api('/api/open', { profile: b.dataset.folder }));
  document.querySelectorAll('[data-play]').forEach((b) => b.onclick = () => { $('playProfile').value = b.dataset.play; go('play'); startPlay(); });
  // accounts
  $('accList').innerHTML = s.accounts.map((a) => `
    <div><img src="https://mc-heads.net/avatar/${esc(a.uuid)}/32" width="32" height="32" style="image-rendering:pixelated" onerror="this.style.visibility='hidden'">
      <div class="grow"><div class="t">${esc(a.name)}</div><div class="s">${a.type === 'microsoft' ? 'Microsoft account' : 'Offline name'}</div></div>
      ${a.id === s.account ? '<span class="pill inv">Using</span>' : `<button class="btn ghost" data-use="${esc(a.id)}">Use</button>`}
      <button class="btn ghost" data-rm="${esc(a.id)}">Remove</button></div>`).join('') || '<div class="muted">No accounts yet. Add an offline name or sign in with Microsoft below.</div>';
  document.querySelectorAll('[data-use]').forEach((b) => b.onclick = async () => { state = await api('/api/select', { account: b.dataset.use }); render(); });
  document.querySelectorAll('[data-rm]').forEach((b) => b.onclick = async () => { state = await api('/api/account/remove', { id: b.dataset.rm }); render(); });
  // settings
  const st = s.settings;
  $('stSnap').checked = st.snapshots; $('stClose').checked = st.closeOnLaunch;
  $('stJvm').value = st.jvmArgs || ''; $('stJava').value = st.javaPath || ''; $('stCid').value = st.clientId || '';
}

function updatePlayCard() {
  const p = state.profiles.find((x) => x.name === $('playProfile').value);
  $('playName').textContent = p ? p.name : '-';
  $('playInfo').textContent = p ? profileLabel(p) + (p.boost ? ' · FPS Boost' : '') : 'Make a profile in Versions';
}
$('playProfile').onchange = async () => { updatePlayCard(); state = await api('/api/select', { profile: $('playProfile').value }); };
$('playAccount').onchange = async () => { if ($('playAccount').value) state = await api('/api/select', { account: $('playAccount').value }); };

// ---------- play ----------
async function startPlay() {
  showErr($('playErr'), null);
  try { await api('/api/play', { name: $('playProfile').value }); pollTask(); }
  catch (e) { showErr($('playErr'), e); }
}
$('playBtn').onclick = startPlay;

let polling = false;
async function pollTask() {
  if (polling) return; polling = true;
  try {
    for (;;) {
      const t = await api('/api/task');
      $('barFill').style.width = t.running ? (t.total ? Math.round(t.done / t.total * 100) : 15) + '%' : (t.error ? '0%' : '100%');
      $('step').textContent = t.running ? t.step + (t.total > 1 ? ` (${t.done}/${t.total})` : '') : '';
      $('playBtn').disabled = t.running;
      $('playBtn').textContent = t.running ? 'WAIT' : 'PLAY';
      const log = $('log'); log.textContent = (t.log || []).join('\n') + (t.crash ? '\n\n--- last lines of the game log ---\n' + t.crash : ''); log.scrollTop = log.scrollHeight;
      showErr($('playErr'), t.error || null);
      if (!t.running) break;
      await new Promise((r) => setTimeout(r, 400));
    }
  } finally { polling = false; }
  state = await api('/api/state'); render();
}

// ---------- profiles ----------
async function loadVersions() {
  if (!versions) versions = await api('/api/versions');
  const snaps = state.settings.snapshots;
  const list = versions.versions.filter((v) => snaps || v.type === 'release');
  $('edVersion').innerHTML = list.map((v) => `<option value="${esc(v.id)}">${esc(v.id)}${v.type !== 'release' ? ' (' + esc(v.type.replace('_', ' ')) + ')' : ''}</option>`).join('');
}

async function refreshLoaders(keep) {
  const mc = $('edVersion').value;
  $('edLoader').innerHTML = '<option value="vanilla">Vanilla</option>';
  $('edLoaderInfo').textContent = 'Checking which mod loaders exist for ' + mc + '...';
  try {
    const l = await api('/api/loaders?mc=' + encodeURIComponent(mc));
    if ($('edVersion').value !== mc) return;
    $('edLoader').innerHTML = Object.keys(LOADERS).filter((k) => l[k]).map((k) => `<option value="${k}">${LOADERS[k]}${k !== 'vanilla' ? ' ' + esc(l[k]) : ''}</option>`).join('');
    const missing = Object.keys(LOADERS).filter((k) => !l[k]).map((k) => LOADERS[k]);
    $('edLoaderInfo').textContent = missing.length ? 'Not made for ' + mc + ': ' + missing.join(', ') : '';
    if (keep && l[keep]) $('edLoader').value = keep;
  } catch (e) { $('edLoaderInfo').textContent = "Couldn't check loaders: " + e.message; }
}

async function openEditor(p) {
  editing = p ? p.name : '';
  showErr($('edErr'), null);
  $('editor').classList.remove('hide');
  $('edTitle').textContent = p ? 'Edit ' + p.name : 'New profile';
  $('edDelete').classList.toggle('hide', !p);
  try { await loadVersions(); } catch (e) { showErr($('edErr'), e); }
  const v = p ? p.version : (versions && versions.latest.release) || '26.3';
  if (![...$('edVersion').options].some((o) => o.value === v)) $('edVersion').insertAdjacentHTML('afterbegin', `<option value="${esc(v)}">${esc(v)}</option>`);
  $('edVersion').value = v;
  $('edName').value = p ? p.name : 'My ' + v;
  $('edRam').value = p ? (p.ram || 4096) : 4096; ramLabel();
  $('edBoost').checked = p ? p.boost : true;
  $('edOlkku').checked = p ? p.olkkuMod : true;
  $('edMc').checked = p ? p.useMinecraft : false;
  refreshLoaders(p ? p.loader : 'fabric');
  $('editor').scrollIntoView({ behavior: 'smooth' });
}
function ramLabel() { $('edRamV').textContent = (Math.round($('edRam').value / 102.4) / 10) + ' GB'; }
$('edRam').oninput = ramLabel;
$('edVersion').onchange = () => { const old = $('edLoader').value; if (!editing) $('edName').value = 'My ' + $('edVersion').value; refreshLoaders(old); };
$('newProfile').onclick = () => openEditor(null);
$('edCancel').onclick = () => $('editor').classList.add('hide');
$('edSave').onclick = async () => {
  try {
    state = await api('/api/profile/save', { old: editing, profile: {
      name: $('edName').value, version: $('edVersion').value, loader: $('edLoader').value, ram: +$('edRam').value,
      boost: $('edBoost').checked, olkkuMod: $('edOlkku').checked, useMinecraft: $('edMc').checked } });
    $('editor').classList.add('hide'); render();
  } catch (e) { showErr($('edErr'), e); }
};
$('edDelete').onclick = async () => {
  if (!confirm('Delete the profile "' + editing + '"? Its worlds stay in the folder.')) return;
  state = await api('/api/profile/delete', { name: editing }); $('editor').classList.add('hide'); render();
};

// ---------- mods ----------
async function loadInstalled() {
  const name = $('modProfile').value; if (!name) return;
  try {
    const mods = await api('/api/mods/list?profile=' + encodeURIComponent(name));
    drawInstalled(mods);
  } catch (e) { $('modInstalled').innerHTML = `<div class="muted">${esc(e.message)}</div>`; }
}
function drawInstalled(mods) {
  $('modInstalled').innerHTML = mods.map((m) => `
    <div><div class="grow"><div class="t">${esc(m.name)}</div><div class="s">${m.enabled ? 'On' : 'Off'}</div></div>
      <button class="btn ghost" data-tog="${esc(m.file)}">${m.enabled ? 'Turn off' : 'Turn on'}</button>
      <button class="btn ghost" data-del="${esc(m.file)}">Remove</button></div>`).join('') || '<div class="muted">No mods yet. Search above, or press FPS Boost.</div>';
  document.querySelectorAll('[data-tog]').forEach((b) => b.onclick = () => change(b.dataset.tog, 'toggle'));
  document.querySelectorAll('[data-del]').forEach((b) => b.onclick = () => change(b.dataset.del, 'remove'));
}
async function change(file, action) {
  try { drawInstalled(await api('/api/mods/change', { profile: $('modProfile').value, file, action })); } catch (e) { showErr($('modErr'), e); }
}
$('modProfile').onchange = () => { $('modResults').innerHTML = ''; loadInstalled(); };
async function search() {
  showErr($('modErr'), null);
  $('modResults').innerHTML = '<div class="muted">Searching...</div>';
  try {
    const hits = await api('/api/mods/search', { profile: $('modProfile').value, query: $('modQuery').value });
    $('modResults').innerHTML = hits.map((h) => `
      <div>${h.icon_url ? `<img src="${esc(h.icon_url)}" alt="">` : '<img alt="">'}
        <div class="grow"><div class="t">${esc(h.title)}</div><div class="s">${esc(h.description)}</div></div>
        <span class="s">${(h.downloads / 1e6 >= 1 ? (h.downloads / 1e6).toFixed(1) + 'M' : Math.round(h.downloads / 1e3) + 'K')}</span>
        <button class="btn" data-inst="${esc(h.project_id)}">Install</button></div>`).join('') || '<div class="muted">Nothing found for this version and loader.</div>';
    document.querySelectorAll('[data-inst]').forEach((b) => b.onclick = async () => {
      b.textContent = '...'; b.disabled = true;
      try { await api('/api/mods/install', { profile: $('modProfile').value, project: b.dataset.inst }); b.textContent = 'Installed'; loadInstalled(); }
      catch (e) { b.textContent = 'Install'; b.disabled = false; showErr($('modErr'), e); }
    });
  } catch (e) { $('modResults').innerHTML = ''; showErr($('modErr'), e); }
}
$('modSearch').onclick = search;
$('modQuery').onkeydown = (e) => { if (e.key === 'Enter') search(); };
$('openMods').onclick = () => api('/api/open', { profile: $('modProfile').value, what: 'mods' });
$('boostNow').onclick = async () => {
  const p = state.profiles.find((x) => x.name === $('modProfile').value); if (!p) return;
  showErr($('modErr'), null);
  for (const slug of ['sodium', 'lithium', 'ferrite-core', 'entityculling', 'immediatelyfast', 'modernfix', 'dynamic-fps']) {
    $('boostNow').textContent = slug + '...';
    try { await api('/api/mods/install', { profile: p.name, project: slug }); } catch (e) { /* not made for this version */ }
  }
  $('boostNow').textContent = 'FPS Boost'; loadInstalled();
};

// ---------- accounts ----------
$('offAdd').onclick = async () => {
  try { state = await api('/api/account/offline', { name: $('offName').value.trim() }); $('offName').value = ''; showErr($('offErr'), null); render(); }
  catch (e) { showErr($('offErr'), e); }
};
$('msBtn').onclick = async () => {
  showErr($('msErr'), null);
  try {
    const l = await api('/api/account/ms/start', {});
    $('msBox').classList.remove('hide'); $('msCode').textContent = l.code; $('msMsg').textContent = l.message;
    for (;;) {
      await new Promise((r) => setTimeout(r, 2000));
      const s = await api('/api/account/ms/poll');
      $('msMsg').textContent = s.message || '';
      if (s.status === 'done') { $('msBox').classList.add('hide'); state = await api('/api/state'); render(); break; }
      if (s.status === 'error') { $('msBox').classList.add('hide'); showErr($('msErr'), s.message); break; }
    }
  } catch (e) { showErr($('msErr'), e); }
};

// ---------- settings ----------
$('stSave').onclick = async () => {
  state = await api('/api/settings', { snapshots: $('stSnap').checked, closeOnLaunch: $('stClose').checked,
    jvmArgs: $('stJvm').value, javaPath: $('stJava').value, clientId: $('stCid').value });
  versions && loadVersions(); render(); $('stMsg').textContent = 'Saved.'; setTimeout(() => $('stMsg').textContent = '', 1500);
};
$('stFolder').onclick = () => api('/api/open', {});

(async () => {
  state = await api('/api/state');
  render();
  pollTask();
  if (!state.accounts.length) go('accounts');
})();
