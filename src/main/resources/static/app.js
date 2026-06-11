const state = {
    pageNo: 1,
    pageSize: 20,
    total: 0,
    cameras: [],
    selected: null,
    flvPlayer: null,
    recording: false,
    recordTaskID: null,
    playbackActive: false,
    playbackDragging: false,
    playbackTimer: null,
    playbackOriginalBeginMs: null,
    playbackCurrentBeginMs: null,
    playbackEndMs: null
};

const els = {
    statusText: document.querySelector("#statusText"),
    refreshBtn: document.querySelector("#refreshBtn"),
    searchForm: document.querySelector("#searchForm"),
    keywordInput: document.querySelector("#keywordInput"),
    pageSizeSelect: document.querySelector("#pageSizeSelect"),
    cameraList: document.querySelector("#cameraList"),
    prevBtn: document.querySelector("#prevBtn"),
    nextBtn: document.querySelector("#nextBtn"),
    pageText: document.querySelector("#pageText"),
    cameraTitle: document.querySelector("#cameraTitle"),
    cameraMeta: document.querySelector("#cameraMeta"),
    playBtn: document.querySelector("#playBtn"),
    stopBtn: document.querySelector("#stopBtn"),
    videoPlayer: document.querySelector("#videoPlayer"),
    playbackBegin: document.querySelector("#playbackBegin"),
    playbackEnd: document.querySelector("#playbackEnd"),
    playbackBtn: document.querySelector("#playbackBtn"),
    playbackSeek: document.querySelector("#playbackSeek"),
    playbackTimeText: document.querySelector("#playbackTimeText"),
    ptzSpeed: document.querySelector("#ptzSpeed"),
    ptzSpeedValue: document.querySelector("#ptzSpeedValue"),
    captureBtn: document.querySelector("#captureBtn"),
    captureDialog: document.querySelector("#captureDialog"),
    captureCloseBtn: document.querySelector("#captureCloseBtn"),
    captureImg: document.querySelector("#captureImg"),
    captureDownload: document.querySelector("#captureDownload"),
    recordStartBtn: document.querySelector("#recordStartBtn")
};

function setStatus(text) { els.statusText.textContent = text; }

async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || `请求失败：${response.status}`);
    return body;
}

// ===================== 监控点列表 =====================

async function loadCameras() {
    setStatus("正在加载...");
    const params = new URLSearchParams({ pageNo: String(state.pageNo), pageSize: String(state.pageSize) });
    if (els.keywordInput.value.trim()) params.set("keyword", els.keywordInput.value.trim());

    try {
        const page = await fetchJson(`/api/hikvision/cameras?${params}`);
        state.total = page.total || 0;
        state.cameras = page.list || [];
        renderCameras();
        renderPager();
        setStatus(`${state.cameras.length} / ${state.total} 个监控点`);
    } catch (error) {
        state.cameras = [];
        renderCameras();
        renderPager();
        setStatus(error.message);
    }
}

function renderCameras() {
    if (!state.cameras.length) {
        els.cameraList.innerHTML = `<div class="camera-empty">暂无监控点</div>`;
        return;
    }
    els.cameraList.innerHTML = state.cameras.map((c, i) => {
        const sel = state.selected?.cameraIndexCode === c.cameraIndexCode ? " selected" : "";
        const statusClass = c.statusName === "在线" ? "online" : c.statusName === "离线" ? "offline" : "unknown";
        return `
            <div class="camera-item${sel}" data-index="${i}">
                <div class="camera-item-icon"><i data-lucide="video"></i></div>
                <div class="camera-item-info">
                    <div class="camera-item-name" title="${escapeHtml(c.cameraName || "")}">${escapeHtml(c.cameraName || "-")}</div>
                    <div class="camera-item-sub" title="${escapeHtml(c.regionName || "")}">${escapeHtml(c.regionName || "-")}</div>
                </div>
                <div class="camera-item-status ${statusClass}" title="${escapeHtml(c.statusName || "")}"></div>
            </div>`;
    }).join("");

    // 重新初始化 lucide 图标
    if (window.lucide) lucide.createIcons({ nodes: els.cameraList.querySelectorAll("[data-lucide]") });

    els.cameraList.querySelectorAll(".camera-item").forEach(item => {
        item.addEventListener("click", () => selectCamera(state.cameras[Number(item.dataset.index)]));
    });
}

function renderPager() {
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    els.pageText.textContent = `${state.pageNo} / ${totalPages}`;
    els.prevBtn.disabled = state.pageNo <= 1;
    els.nextBtn.disabled = state.pageNo >= totalPages;
}

function selectCamera(camera) {
    state.selected = camera;
    els.cameraTitle.textContent = camera.cameraName || camera.cameraIndexCode || "未命名";
    els.cameraMeta.textContent = `${camera.regionName || "-"} · ${camera.statusName || "-"}`;
    const ok = !!camera.cameraIndexCode;
    els.playBtn.disabled = !ok;
    els.stopBtn.disabled = !ok;
    els.playbackBtn.disabled = !ok;
    els.captureBtn.disabled = !ok;
    els.recordStartBtn.disabled = !ok;
    renderCameras();
}

// ===================== FLV 播放器 =====================

function destroyPlayer() {
    stopPlaybackTimer();
    if (state.flvPlayer) {
        state.flvPlayer.pause();
        state.flvPlayer.unload();
        state.flvPlayer.detachMediaElement();
        state.flvPlayer.destroy();
        state.flvPlayer = null;
    }
    els.videoPlayer.removeAttribute("src");
    els.videoPlayer.load();
}

function playFlvUrl(url) {
    destroyPlayer();
    if (!flvjs.isSupported()) { setStatus("浏览器不支持 FLV 播放"); return; }

    state.flvPlayer = flvjs.createPlayer(
        { type: "flv", isLive: true, url },
        { enableWorker: false, enableStashBuffer: false, stashInitialSize: 128, autoCleanupSourceBuffer: true }
    );
    state.flvPlayer.attachMediaElement(els.videoPlayer);
    state.flvPlayer.load();
    state.flvPlayer.play().catch(() => setStatus("请手动点击播放"));

    state.flvPlayer.on(flvjs.Events.ERROR, (t, d) => {
        console.error("FLV Error:", t, d);
        setStatus(`播放错误: ${d || t}`);
    });
}

// ===================== 预览 & 停止 =====================

function playSelected() {
    if (!state.selected?.cameraIndexCode) return;
    const id = encodeURIComponent(state.selected.cameraIndexCode);
    resetPlaybackTimeline();
    setStatus("正在连接视频流...");
    playFlvUrl(`/api/hikvision/cameras/${id}/live.flv`);
    setStatus("正在播放");
}

function stopSelected() {
    destroyPlayer();
    resetPlaybackTimeline();
    setStatus("已停止");
}

// ===================== 录像回放 =====================

function playPlayback() {
    if (!state.selected?.cameraIndexCode) return;
    const b = els.playbackBegin.value, e = els.playbackEnd.value;
    if (!b || !e) { setStatus("请选择开始和结束时间"); return; }

    const beginMs = new Date(b).getTime();
    const endMs = new Date(e).getTime();
    if (!Number.isFinite(beginMs) || !Number.isFinite(endMs) || endMs <= beginMs) {
        setStatus("回放结束时间必须晚于开始时间");
        return;
    }
    preparePlaybackTimeline(beginMs, endMs);
    startPlaybackAt(beginMs);
}

function startPlaybackAt(startMs) {
    if (!state.selected?.cameraIndexCode || !state.playbackEndMs) return;
    const maxStartMs = state.playbackEndMs - 1000;
    const safeStartMs = Math.min(Math.max(startMs, state.playbackOriginalBeginMs), maxStartMs);
    const id = encodeURIComponent(state.selected.cameraIndexCode);
    const begin = encodeURIComponent(dateToISO8601(new Date(safeStartMs)));
    const end = encodeURIComponent(dateToISO8601(new Date(state.playbackEndMs)));
    state.playbackCurrentBeginMs = safeStartMs;
    state.playbackActive = true;
    const offsetSeconds = Math.floor((safeStartMs - state.playbackOriginalBeginMs) / 1000);
    setPlaybackSeekValue(offsetSeconds);
    setStatus("正在获取回放...");
    playFlvUrl(`/api/hikvision/cameras/${id}/playback.flv?begin=${begin}&end=${end}`);
    startPlaybackTimer();
    setStatus("正在回放");
}

function toISO8601(localDatetime) {
    return dateToISO8601(new Date(localDatetime));
}

function dateToISO8601(d) {
    const off = -d.getTimezoneOffset();
    const sign = off >= 0 ? "+" : "-";
    const hh = String(Math.floor(Math.abs(off) / 60)).padStart(2, "0");
    const mm = String(Math.abs(off) % 60).padStart(2, "0");
    return d.getFullYear()
        + "-" + String(d.getMonth() + 1).padStart(2, "0")
        + "-" + String(d.getDate()).padStart(2, "0")
        + "T" + String(d.getHours()).padStart(2, "0")
        + ":" + String(d.getMinutes()).padStart(2, "0")
        + ":" + String(d.getSeconds()).padStart(2, "0")
        + "." + String(d.getMilliseconds()).padStart(3, "0")
        + sign + hh + ":" + mm;
}

function preparePlaybackTimeline(beginMs, endMs) {
    state.playbackOriginalBeginMs = beginMs;
    state.playbackCurrentBeginMs = beginMs;
    state.playbackEndMs = endMs;
    state.playbackActive = true;
    state.playbackDragging = false;
    const durationSeconds = Math.max(1, Math.floor((endMs - beginMs) / 1000));
    els.playbackSeek.max = String(durationSeconds);
    els.playbackSeek.value = "0";
    els.playbackSeek.disabled = false;
    updatePlaybackTimeText(0, durationSeconds);
}

function resetPlaybackTimeline() {
    stopPlaybackTimer();
    state.playbackActive = false;
    state.playbackDragging = false;
    state.playbackOriginalBeginMs = null;
    state.playbackCurrentBeginMs = null;
    state.playbackEndMs = null;
    els.playbackSeek.max = "0";
    els.playbackSeek.value = "0";
    els.playbackSeek.disabled = true;
    updatePlaybackTimeText(0, 0);
}

function startPlaybackTimer() {
    stopPlaybackTimer();
    state.playbackTimer = window.setInterval(syncPlaybackTimeline, 500);
}

function stopPlaybackTimer() {
    if (state.playbackTimer) {
        window.clearInterval(state.playbackTimer);
        state.playbackTimer = null;
    }
}

function syncPlaybackTimeline() {
    if (!state.playbackActive || state.playbackDragging || state.playbackOriginalBeginMs == null || state.playbackCurrentBeginMs == null) {
        return;
    }
    const videoSeconds = Number.isFinite(els.videoPlayer.currentTime) ? els.videoPlayer.currentTime : 0;
    const elapsedSeconds = Math.floor((state.playbackCurrentBeginMs - state.playbackOriginalBeginMs) / 1000 + videoSeconds);
    setPlaybackSeekValue(elapsedSeconds);
}

function setPlaybackSeekValue(seconds) {
    const max = Number(els.playbackSeek.max) || 0;
    const value = Math.min(Math.max(0, Math.floor(seconds)), max);
    els.playbackSeek.value = String(value);
    updatePlaybackTimeText(value, max);
}

function updatePlaybackTimeText(currentSeconds, totalSeconds) {
    els.playbackTimeText.textContent = `${formatDuration(currentSeconds)} / ${formatDuration(totalSeconds)}`;
}

function formatDuration(totalSeconds) {
    const safeSeconds = Math.max(0, Math.floor(totalSeconds || 0));
    const hours = Math.floor(safeSeconds / 3600);
    const minutes = Math.floor((safeSeconds % 3600) / 60);
    const seconds = safeSeconds % 60;
    if (hours > 0) {
        return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
    }
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function seekPlayback() {
    if (!state.playbackActive || state.playbackOriginalBeginMs == null) {
        return;
    }
    state.playbackDragging = false;
    const targetSeconds = Number(els.playbackSeek.value) || 0;
    const targetMs = state.playbackOriginalBeginMs + targetSeconds * 1000;
    startPlaybackAt(targetMs);
}

// ===================== 云台控制 =====================

function ptzCommand(cmd, action) {
    if (!state.selected?.cameraIndexCode) return;
    fetchJson(`/api/hikvision/cameras/${encodeURIComponent(state.selected.cameraIndexCode)}/ptz`, {
        method: "POST",
        body: JSON.stringify({ action, command: cmd, speed: Number(els.ptzSpeed.value) || 4 })
    }).catch(err => setStatus(err.message));
}

function initPtzControls() {
    document.querySelectorAll(".ptz-btn, .ptz-side-btn").forEach(btn => {
        const cmd = btn.dataset.cmd;
        if (!cmd) return;
        btn.addEventListener("mousedown", () => ptzCommand(cmd, 0));
        btn.addEventListener("mouseup", () => ptzCommand(cmd, 1));
        btn.addEventListener("mouseleave", () => ptzCommand(cmd, 1));
        btn.addEventListener("touchstart", e => { e.preventDefault(); ptzCommand(cmd, 0); });
        btn.addEventListener("touchend", () => ptzCommand(cmd, 1));
    });
    updatePtzSpeedValue();
    els.ptzSpeed.addEventListener("input", updatePtzSpeedValue);
}

function updatePtzSpeedValue() {
    els.ptzSpeedValue.textContent = els.ptzSpeed.value;
}

// ===================== 抓图 =====================

async function doCapture() {
    if (!state.selected?.cameraIndexCode) return;
    setStatus("正在抓图...");
    try {
        const r = await fetchJson(`/api/hikvision/cameras/${encodeURIComponent(state.selected.cameraIndexCode)}/capture`, { method: "POST" });
        state.capturePicUrl = r.picUrl;
        els.captureImg.src = proxyImageUrl(r.picUrl);
        els.captureDialog.showModal();
        setStatus("抓图成功");
    } catch (e) { setStatus(e.message); }
}

function proxyImageUrl(url) {
    return `/api/hikvision/image/proxy?url=${encodeURIComponent(url)}`;
}

async function downloadCapture() {
    if (!state.capturePicUrl) return;
    try {
        setStatus("正在下载图片...");
        const proxyUrl = proxyImageUrl(state.capturePicUrl);
        const resp = await fetch(proxyUrl);
        if (!resp.ok) throw new Error(`下载失败: ${resp.status}`);
        const blob = await resp.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = blobUrl;
        a.download = `capture_${new Date().toISOString().replace(/[:.]/g, "-")}.jpg`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(blobUrl);
        setStatus("图片已下载");
    } catch (e) {
        setStatus("下载失败: " + e.message);
    }
}

// ===================== 录像 =====================

async function toggleRecord() {
    if (!state.selected?.cameraIndexCode) return;
    const id = encodeURIComponent(state.selected.cameraIndexCode);
    if (state.recording) {
        try {
            await fetchJson(`/api/hikvision/cameras/${id}/record/stop`, { method: "POST", body: JSON.stringify({ taskID: state.recordTaskID }) });
            state.recording = false; state.recordTaskID = null;
            updateRecordBtn(); setStatus("录像已停止");
        } catch (e) { setStatus(e.message); }
    } else {
        try {
            const r = await fetchJson(`/api/hikvision/cameras/${id}/record/start`, { method: "POST", body: JSON.stringify({ type: 0 }) });
            state.recording = true; state.recordTaskID = r.taskID;
            updateRecordBtn(); setStatus("录像已开始");
        } catch (e) { setStatus(e.message); }
    }
}

function updateRecordBtn() {
    els.recordStartBtn.classList.toggle("recording", state.recording);
    els.recordStartBtn.querySelector("span").textContent = state.recording ? "停止" : "录像";
}

// ===================== 工具 =====================

function escapeHtml(v) {
    return String(v).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function initPlaybackDefaults() {
    const now = new Date();
    const ago = new Date(now.getTime() - 3600000);
    els.playbackEnd.value = toLocal(now);
    els.playbackBegin.value = toLocal(ago);
}

function toLocal(d) {
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0")
        + "T" + String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
}

// ===================== 事件绑定 =====================

els.searchForm.addEventListener("submit", e => { e.preventDefault(); state.pageNo = 1; loadCameras(); });
els.refreshBtn.addEventListener("click", loadCameras);
els.playBtn.addEventListener("click", playSelected);
els.stopBtn.addEventListener("click", stopSelected);
els.prevBtn.addEventListener("click", () => { if (state.pageNo > 1) { state.pageNo--; loadCameras(); } });
els.nextBtn.addEventListener("click", () => { const tp = Math.max(1, Math.ceil(state.total / state.pageSize)); if (state.pageNo < tp) { state.pageNo++; loadCameras(); } });
els.pageSizeSelect.addEventListener("change", () => { state.pageSize = Number(els.pageSizeSelect.value); state.pageNo = 1; loadCameras(); });
els.playbackBtn.addEventListener("click", playPlayback);
els.playbackSeek.addEventListener("pointerdown", () => { state.playbackDragging = true; });
els.playbackSeek.addEventListener("input", () => {
    state.playbackDragging = true;
    updatePlaybackTimeText(Number(els.playbackSeek.value) || 0, Number(els.playbackSeek.max) || 0);
});
els.playbackSeek.addEventListener("change", seekPlayback);
els.captureBtn.addEventListener("click", doCapture);
els.captureCloseBtn.addEventListener("click", () => els.captureDialog.close());
els.captureDownload.addEventListener("click", downloadCapture);
els.captureDialog.addEventListener("click", e => { if (e.target === els.captureDialog) els.captureDialog.close(); });
els.recordStartBtn.addEventListener("click", toggleRecord);
initPtzControls();

if (window.lucide) lucide.createIcons();
initPlaybackDefaults();
resetPlaybackTimeline();
loadCameras();
