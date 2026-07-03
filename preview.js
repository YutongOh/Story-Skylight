(function () {
  const FRAME_W = 360;
  const FRAME_H = 800;
  const ZOOM_STEPS = [0.5, 0.75, 1, 1.25, 1.5];
  const ZOOM_FIT = 'fit';
  const STAGE_PAD = 16;
  const TOOLBAR_PHONE_GAP = 24;
  const PHONE_BORDER_PX = 20;
  const TAP_SLOP = 6;
  const PREVIEW_BUILD = '153';

  const VARIANTS = [
    { id: 'v1', label: 'V1', path: 'variants/v1/index.html' },
    { id: 'v2', label: 'V2', path: 'variants/v2/index.html' },
    { id: 'v3', label: 'V3', path: 'variants/v3/index.html' },
    { id: 'v4', label: 'V4', path: 'variants/v4/index.html' },
  ];

  const els = {
    toolbar: document.getElementById('toolbar'),
    stage: document.getElementById('stage'),
    phoneWrap: document.getElementById('phone-wrap'),
    frame: document.getElementById('app-frame'),
    variantSelect: document.getElementById('variantSelect'),
    zoomOutBtn: document.getElementById('zoomOutBtn'),
    zoomInBtn: document.getElementById('zoomInBtn'),
    zoomFitBtn: document.getElementById('zoomFitBtn'),
    zoomLabel: document.getElementById('zoomLabel'),
    exitBtn: document.getElementById('exitBtn'),
    demoBtn: document.getElementById('demoBtn'),
    reloadBtn: document.getElementById('reloadBtn'),
    variantCaption: document.getElementById('variantCaption'),
    cursor: document.getElementById('touch-cursor'),
  };

  let currentVariant = 'v1';
  let zoomMode = ZOOM_FIT;
  let zoomIndex = 2;
  let demoRunning = false;
  let demoCursorClientX = null;
  let demoCursorClientY = null;
  let activeDemoToken = null;
  const demoWaitCancelers = new Set();
  const DEMO_CANCELLED = 'DEMO_CANCELLED';

  function parseVariantFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const v = params.get('v') || params.get('variant');
    if (v && VARIANTS.some((item) => item.id === v)) return v;
    const fromDoc = document.documentElement.dataset.defaultVariant;
    if (fromDoc && VARIANTS.some((item) => item.id === fromDoc)) return fromDoc;
    const pathMatch = window.location.pathname.match(/preview-(v[1-4])(?:\.html)?$/i);
    if (pathMatch && VARIANTS.some((item) => item.id === pathMatch[1].toLowerCase())) {
      return pathMatch[1].toLowerCase();
    }
    return 'v1';
  }

  function updateUrl(variantId) {
    const url = new URL(window.location.href);
    url.searchParams.set('v', variantId);
    window.history.replaceState({}, '', url);
  }

  function toolbarClearance() {
    const rect = els.toolbar?.getBoundingClientRect();
    return rect ? rect.bottom + TOOLBAR_PHONE_GAP : 74;
  }

  function phoneChromeHeight(scale) {
    return (FRAME_H + PHONE_BORDER_PX) * scale;
  }

  function computeFitScale() {
    const clearance = toolbarClearance();
    const availW = els.stage.clientWidth - STAGE_PAD * 2;
    const availH = els.stage.clientHeight - clearance - STAGE_PAD;
    return Math.min(availW / FRAME_W, availH / (FRAME_H + PHONE_BORDER_PX), 1);
  }

  function currentScale() {
    if (zoomMode === ZOOM_FIT) return computeFitScale();
    return ZOOM_STEPS[zoomIndex];
  }

  function syncZoomUi() {
    const scale = currentScale();
    if (els.zoomLabel) {
      els.zoomLabel.textContent = `${Math.round(scale * 100)}%`;
    }
    if (els.zoomFitBtn) {
      els.zoomFitBtn.classList.toggle('is-active', zoomMode === ZOOM_FIT);
    }
    if (els.zoomOutBtn) {
      els.zoomOutBtn.disabled = zoomMode !== ZOOM_FIT && zoomIndex <= 0;
    }
    if (els.zoomInBtn) {
      els.zoomInBtn.disabled = zoomMode !== ZOOM_FIT && zoomIndex >= ZOOM_STEPS.length - 1;
    }
  }

  function applyLayout() {
    const topInset = toolbarClearance();
    const scale = currentScale();
    const phoneH = phoneChromeHeight(scale);
    const availH = Math.max(1, els.stage.clientHeight - topInset - STAGE_PAD);
    const topEdge = topInset + Math.max(0, (availH - phoneH) / 2);
    els.phoneWrap.style.left = '50%';
    els.phoneWrap.style.top = `${topEdge}px`;
    els.phoneWrap.style.transform = `translateX(-50%) scale(${scale})`;
    syncZoomUi();
  }

  function nearestStepIndex(scale) {
    let best = 0;
    let bestDelta = Infinity;
    ZOOM_STEPS.forEach((step, index) => {
      const delta = Math.abs(step - scale);
      if (delta < bestDelta) {
        bestDelta = delta;
        best = index;
      }
    });
    return best;
  }

  function setZoomFit() {
    zoomMode = ZOOM_FIT;
    applyLayout();
  }

  function setZoomStep(index) {
    zoomMode = 'step';
    zoomIndex = Math.max(0, Math.min(ZOOM_STEPS.length - 1, index));
    applyLayout();
  }

  function zoomOut() {
    if (zoomMode === ZOOM_FIT) {
      setZoomStep(nearestStepIndex(computeFitScale()) - 1);
      return;
    }
    setZoomStep(zoomIndex - 1);
  }

  function zoomIn() {
    if (zoomMode === ZOOM_FIT) {
      setZoomStep(nearestStepIndex(computeFitScale()) + 1);
      return;
    }
    setZoomStep(zoomIndex + 1);
  }

  function variantFrameUrl(path) {
    const url = new URL(path, window.location.href);
    url.searchParams.set('build', PREVIEW_BUILD);
    return `${url.pathname}${url.search}`;
  }

  function setVariant(variantId, reload = true) {
    currentVariant = variantId;
    const meta = VARIANTS.find((v) => v.id === variantId);
    if (!meta) return;
    els.variantSelect.value = variantId;
    if (els.variantCaption) els.variantCaption.textContent = meta.label;
    if (els.exitBtn) {
      els.exitBtn.hidden = variantId !== 'v3';
      els.exitBtn.disabled = variantId !== 'v3';
    }
    updateUrl(variantId);
    if (reload) {
      els.frame.style.opacity = '0';
      els.frame.src = variantFrameUrl(meta.path);
    }
  }

  function cancelDemoSequence() {
    if (!demoRunning && activeDemoToken == null) return;
    activeDemoToken = null;
    demoRunning = false;
    demoWaitCancelers.forEach((cancel) => cancel());
    demoWaitCancelers.clear();
    postToFrame('skylight:preview-gesture-active', { active: false });
    document.body.classList.remove('is-demo-running');
    if (els.demoBtn) {
      els.demoBtn.disabled = false;
      els.demoBtn.classList.remove('is-active');
    }
    els.cursor?.classList.remove('visible', 'is-down');
  }

  function reloadDemo(options = {}) {
    if (!options.fromDemo) cancelDemoSequence();
    try {
      els.frame.contentWindow.postMessage({ type: 'skylight:reload' }, '*');
    } catch (_) {
      els.frame.src = variantFrameUrl(
        VARIANTS.find((v) => v.id === currentVariant)?.path || 'variants/v1/index.html',
      );
    }
  }

  function exitToDesktop() {
    if (currentVariant !== 'v3') return;
    postToFrame('skylight:exit-to-desktop');
  }

  function frameRect() {
    return els.frame.getBoundingClientRect();
  }

  function frameScale() {
    const rect = frameRect();
    return {
      scaleX: rect.width > 0 ? FRAME_W / rect.width : 1,
      scaleY: rect.height > 0 ? FRAME_H / rect.height : 1,
    };
  }

  function framePointFromClient(clientX, clientY) {
    const rect = frameRect();
    if (!rect.width || !rect.height) return null;
    const x = (clientX - rect.left) * (FRAME_W / rect.width);
    const y = (clientY - rect.top) * (FRAME_H / rect.height);
    if (x < 0 || y < 0 || x > FRAME_W || y > FRAME_H) return null;
    return { x, y };
  }

  function postToFrame(type, payload = {}) {
    if (!els.frame.contentWindow) return false;
    els.frame.contentWindow.postMessage({ type, ...payload }, '*');
    return true;
  }

  function frameModalOpen() {
    try {
      const doc = els.frame.contentDocument;
      if (!doc) return false;
      if (!doc.getElementById('storyPreview')?.hidden) return true;
      if (!doc.getElementById('storyAddSheet')?.hidden) return true;
      if (doc.getElementById('screen-album')?.classList.contains('visible')) return true;
    } catch (_) {}
    return false;
  }

  function setupTouchCursor() {
    if (!els.cursor) return;
    let isCoarse = false;
    try {
      isCoarse = window.matchMedia('(hover: none) and (pointer: coarse)').matches;
    } catch (_) {}
    if (isCoarse) return;

    document.body.classList.add('has-touch-cursor');
    const cursor = els.cursor;

    function inFrame(clientX, clientY) {
      const rect = frameRect();
      return clientX >= rect.left && clientX <= rect.right
        && clientY >= rect.top && clientY <= rect.bottom;
    }

    function hideCursor() {
      cursor.classList.remove('visible', 'is-down');
    }

    function showCursor(clientX, clientY) {
      if (!inFrame(clientX, clientY)) {
        hideCursor();
        return;
      }
      cursor.style.left = `${clientX}px`;
      cursor.style.top = `${clientY}px`;
      cursor.classList.add('visible');
    }

    function onDown() {
      if (cursor.classList.contains('visible')) cursor.classList.add('is-down');
    }

    function onUp() {
      cursor.classList.remove('is-down');
    }

    els.phoneWrap.addEventListener('pointermove', (e) => {
      showCursor(e.clientX, e.clientY);
    }, { passive: true });

    els.phoneWrap.addEventListener('pointerleave', hideCursor);
    els.phoneWrap.addEventListener('pointerdown', onDown, true);
    els.phoneWrap.addEventListener('pointerup', onUp, true);
    els.phoneWrap.addEventListener('pointercancel', onUp, true);

    document.addEventListener('pointermove', (e) => {
      if (!inFrame(e.clientX, e.clientY)) hideCursor();
    }, { passive: true });
  }

  function setupPreviewGestures() {
    const drag = {
      active: false,
      gestureStarted: false,
      lastClientX: 0,
      lastClientY: 0,
      startX: 0,
      startY: 0,
      moved: false,
      pointerId: null,
      velocityY: 0,
      lastSampleAt: 0,
      blockGestures: false,
    };

    function resetDrag() {
      drag.active = false;
      drag.gestureStarted = false;
      drag.moved = false;
      drag.pointerId = null;
      drag.velocityY = 0;
      drag.lastSampleAt = 0;
      drag.lastClientX = 0;
      drag.lastClientY = 0;
      drag.blockGestures = false;
      document.body.classList.remove('is-preview-dragging');
      postToFrame('skylight:preview-gesture-active', { active: false });
    }

    function startGestureStream() {
      if (drag.gestureStarted) return;
      drag.gestureStarted = true;
      document.body.classList.add('is-preview-dragging');
      postToFrame('skylight:preview-gesture-active', { active: true });
      postToFrame('skylight:preview-gesture-start', {
        x: drag.startX,
        y: drag.startY,
        clientX: drag.startX,
        clientY: drag.startY,
      });
    }

    function finishDrag(e) {
      if (!drag.active) return;
      const point = framePointFromClient(e.clientX, e.clientY);
      const pointerId = drag.pointerId;
      if (drag.gestureStarted) {
        postToFrame('skylight:preview-gesture-end', point
          ? { x: point.x, y: point.y, velocityY: drag.velocityY }
          : { velocityY: drag.velocityY });
      } else if (point) {
        postToFrame('skylight:preview-click', point);
      }
      resetDrag();
      try {
        if (pointerId != null) els.phoneWrap.releasePointerCapture(pointerId);
      } catch (_) {}
    }

    els.phoneWrap.addEventListener('pointerdown', (e) => {
      if (demoRunning) return;
      if (e.button !== 0) return;
      const point = framePointFromClient(e.clientX, e.clientY);
      if (!point) return;
      drag.active = true;
      drag.gestureStarted = false;
      drag.lastClientX = e.clientX;
      drag.lastClientY = e.clientY;
      drag.startX = point.x;
      drag.startY = point.y;
      drag.moved = false;
      drag.pointerId = e.pointerId;
      drag.blockGestures = frameModalOpen();
      drag.velocityY = 0;
      drag.lastSampleAt = performance.now();
      els.phoneWrap.setPointerCapture(e.pointerId);
    });

    els.phoneWrap.addEventListener('pointermove', (e) => {
      if (demoRunning) return;
      if (!drag.active || e.pointerId !== drag.pointerId) return;
      if (drag.blockGestures) return;
      const point = framePointFromClient(e.clientX, e.clientY);
      if (!point) return;
      const { scaleX, scaleY } = frameScale();
      const dx = (e.clientX - drag.lastClientX) * scaleX;
      const dy = (e.clientY - drag.lastClientY) * scaleY;
      drag.lastClientX = e.clientX;
      drag.lastClientY = e.clientY;
      const now = performance.now();
      const dt = now - (drag.lastSampleAt || now);
      if (dt > 0 && dy !== 0) {
        const sample = dy / dt;
        drag.velocityY = drag.velocityY * 0.55 + sample * 0.45;
      }
      drag.lastSampleAt = now;
      const totalMove = Math.hypot(point.x - drag.startX, point.y - drag.startY);
      if (totalMove > TAP_SLOP) drag.moved = true;
      if (totalMove > 1) e.preventDefault();
      if (!drag.moved) return;
      startGestureStream();
      postToFrame('skylight:preview-gesture-move', {
        x: point.x,
        y: point.y,
        clientX: point.x,
        clientY: point.y,
        dx,
        dy,
      });
    });

    els.phoneWrap.addEventListener('pointerup', finishDrag);
    els.phoneWrap.addEventListener('pointercancel', finishDrag);
  }

  function wait(ms) {
    const token = activeDemoToken;
    return new Promise((resolve, reject) => {
      let settled = false;
      let cancel = null;
      const finish = () => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        if (cancel) demoWaitCancelers.delete(cancel);
        if (token != null && token !== activeDemoToken) {
          reject(new Error(DEMO_CANCELLED));
          return;
        }
        resolve();
      };
      const timeout = setTimeout(finish, ms);
      if (token != null) {
        cancel = finish;
        demoWaitCancelers.add(cancel);
      }
    });
  }

  function frameToClient(x, y) {
    const rect = frameRect();
    return {
      x: rect.left + (x / FRAME_W) * rect.width,
      y: rect.top + (y / FRAME_H) * rect.height,
    };
  }

  function setDemoCursor(frameX, frameY, down = false) {
    if (!els.cursor) return;
    const point = frameToClient(frameX, frameY);
    setDemoCursorClient(point.x, point.y, down);
  }

  function setDemoCursorClient(clientX, clientY, down = false) {
    if (!els.cursor) return;
    demoCursorClientX = clientX;
    demoCursorClientY = clientY;
    els.cursor.style.left = `${clientX}px`;
    els.cursor.style.top = `${clientY}px`;
    els.cursor.classList.add('visible');
    els.cursor.classList.toggle('is-down', down);
  }

  function easeInOutDemo(t) {
    return t < 0.5 ? 2 * t * t : 1 - ((-2 * t + 2) ** 2) / 2;
  }

  async function moveDemoCursorClientTo(clientX, clientY, duration = 340) {
    if (demoCursorClientX == null || demoCursorClientY == null || duration <= 0) {
      setDemoCursorClient(clientX, clientY, false);
      await wait(duration);
      return;
    }
    const fromX = demoCursorClientX;
    const fromY = demoCursorClientY;
    const steps = Math.max(1, Math.round(duration / 16));
    for (let step = 1; step <= steps; step += 1) {
      const t = easeInOutDemo(step / steps);
      setDemoCursorClient(
        fromX + (clientX - fromX) * t,
        fromY + (clientY - fromY) * t,
        false
      );
      await wait(duration / steps);
    }
  }

  async function moveDemoCursorTo(frameX, frameY, duration = 340) {
    const point = frameToClient(frameX, frameY);
    await moveDemoCursorClientTo(point.x, point.y, duration);
  }

  async function demoTap(frameX, frameY, options = {}) {
    await moveDemoCursorTo(frameX, frameY, options.moveMs ?? 340);
    setDemoCursor(frameX, frameY, true);
    await wait(options.downMs ?? 110);
    postToFrame('skylight:preview-click', { x: frameX, y: frameY });
    setDemoCursor(frameX, frameY, false);
    await wait(options.afterMs ?? 380);
  }

  function framePointForSelector(selector) {
    const doc = els.frame.contentDocument;
    const target = doc?.querySelector(selector);
    if (!target) return null;
    const rect = target.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    return {
      x: Math.max(0, Math.min(FRAME_W, rect.left + rect.width / 2)),
      y: Math.max(0, Math.min(FRAME_H, rect.top + rect.height / 2)),
    };
  }

  async function demoTapSelector(selector, options = {}) {
    const point = framePointForSelector(selector);
    if (!point) {
      await wait(options.afterMs ?? 280);
      return;
    }
    await demoTap(point.x, point.y, options);
  }

  async function demoTapPageSelector(selector, options = {}) {
    const target = document.querySelector(selector);
    if (!target) {
      await wait(options.afterMs ?? 280);
      return;
    }
    const rect = target.getBoundingClientRect();
    const x = rect.left + rect.width / 2;
    const y = rect.top + rect.height / 2;
    await moveDemoCursorClientTo(x, y, options.moveMs ?? 340);
    setDemoCursorClient(x, y, true);
    await wait(options.downMs ?? 110);
    target.click();
    setDemoCursorClient(x, y, false);
    await wait(options.afterMs ?? 420);
  }

  async function demoDrag(points, options = {}) {
    if (!points.length) return;
    const start = points[0];
    await moveDemoCursorTo(start.x, start.y, options.moveMs ?? 340);
    setDemoCursor(start.x, start.y, true);
    postToFrame('skylight:preview-gesture-active', { active: true });
    postToFrame('skylight:preview-gesture-start', {
      x: start.x,
      y: start.y,
      clientX: start.x,
      clientY: start.y,
    });
    let prev = start;
    for (let i = 1; i < points.length; i += 1) {
      const point = points[i];
      const steps = Math.max(1, options.stepsPerSegment ?? 12);
      for (let step = 1; step <= steps; step += 1) {
        const t = step / steps;
        const x = prev.x + (point.x - prev.x) * t;
        const y = prev.y + (point.y - prev.y) * t;
        setDemoCursor(x, y, true);
        postToFrame('skylight:preview-gesture-move', {
          x,
          y,
          clientX: x,
          clientY: y,
          dx: (point.x - prev.x) / steps,
          dy: (point.y - prev.y) / steps,
        });
        await wait(options.stepMs ?? 22);
      }
      prev = point;
    }
    postToFrame('skylight:preview-gesture-end', {
      x: prev.x,
      y: prev.y,
      velocityY: options.velocityY ?? 0,
    });
    postToFrame('skylight:preview-gesture-active', { active: false });
    setDemoCursor(prev.x, prev.y, false);
    await wait(options.afterMs ?? 520);
  }

  async function waitForFrameReady() {
    await wait(80);
    try {
      if (els.frame.contentDocument?.readyState !== 'complete') {
        await new Promise((resolve) => {
          const done = () => {
            els.frame.removeEventListener('load', done);
            resolve();
          };
          els.frame.addEventListener('load', done);
          setTimeout(done, 900);
        });
      }
    } catch (_) {}
    await wait(520);
  }

  async function resetForDemo() {
    reloadDemo({ fromDemo: true });
    await waitForFrameReady();
  }

  async function closeStoryPreview() {
    await demoTapSelector('#storyPreviewClose', { moveMs: 260, afterMs: 620 });
  }

  async function runV2AllReadHintDemo() {
    await resetForDemo();
    await demoTapSelector('#feedInboxBtn', { afterMs: 950 });
    for (const label of ['Lindsey', 'Maren', 'Alena', 'Rayna']) {
      await demoTapSelector(`[data-story-label="${label}"] .skylight-avatar-slot`, { afterMs: 520 });
      await closeStoryPreview();
    }
    await demoTapSelector('#layer-inbox [data-feed-nav="home"]', { afterMs: 240 });
    await demoTapSelector('#feedInboxBtn', { afterMs: 540 });
    await demoDrag([
      { x: 180, y: 156 },
      { x: 180, y: 244 },
    ], { stepMs: 16, afterMs: 720 });
  }

  async function runV3DesktopAllReadHintDemo() {
    await resetForDemo();
    await demoTapSelector('#feedInboxBtn', { moveMs: 420, afterMs: 1150 });
    for (const label of ['Lindsey', 'Maren', 'Alena', 'Rayna']) {
      await demoTapSelector(`[data-story-label="${label}"] .skylight-avatar-slot`, { moveMs: 360, afterMs: 720 });
      await closeStoryPreview();
    }
    await demoTapPageSelector('#exitBtn', { moveMs: 460, afterMs: 780 });
    await demoTapSelector('#desktopTikTokBtn', { moveMs: 460, afterMs: 760 });
    await demoTapSelector('#feedInboxBtn', { moveMs: 420, afterMs: 760 });
    await demoDrag([
      { x: 180, y: 156 },
      { x: 180, y: 244 },
    ], { moveMs: 360, stepsPerSegment: 16, stepMs: 22, afterMs: 900 });
  }

  async function runBasicDemo() {
    await resetForDemo();
    await demoTapSelector('#feedInboxBtn', { afterMs: 850 });
    await demoDrag([
      { x: 180, y: 170 },
      { x: 180, y: 230 },
      { x: 180, y: 180 },
    ], { stepMs: 18, afterMs: 420 });
  }

  async function runDemoSequence() {
    if (demoRunning) return;
    demoRunning = true;
    activeDemoToken = {};
    document.body.classList.add('is-demo-running', 'has-touch-cursor');
    if (els.demoBtn) {
      els.demoBtn.disabled = true;
      els.demoBtn.classList.add('is-active');
    }
    try {
      if (currentVariant === 'v2') {
        await runV2AllReadHintDemo();
      } else if (currentVariant === 'v3') {
        await runV3DesktopAllReadHintDemo();
      } else {
        await runBasicDemo();
      }
    } catch (err) {
      if (err?.message !== DEMO_CANCELLED) throw err;
    } finally {
      if (activeDemoToken != null) {
        activeDemoToken = null;
        demoRunning = false;
        document.body.classList.remove('is-demo-running');
        if (els.demoBtn) {
          els.demoBtn.disabled = false;
          els.demoBtn.classList.remove('is-active');
        }
      }
      els.cursor?.classList.remove('is-down');
    }
  }

  VARIANTS.forEach((variant) => {
    const option = document.createElement('option');
    option.value = variant.id;
    option.textContent = variant.label;
    els.variantSelect.appendChild(option);
  });

  els.variantSelect.addEventListener('change', () => setVariant(els.variantSelect.value));
  els.zoomOutBtn?.addEventListener('click', zoomOut);
  els.zoomInBtn?.addEventListener('click', zoomIn);
  els.zoomFitBtn?.addEventListener('click', setZoomFit);
  els.exitBtn?.addEventListener('click', exitToDesktop);
  els.demoBtn?.addEventListener('click', runDemoSequence);
  els.reloadBtn.addEventListener('click', reloadDemo);
  window.addEventListener('resize', applyLayout);
  if (els.toolbar && typeof ResizeObserver !== 'undefined') {
    new ResizeObserver(applyLayout).observe(els.toolbar);
  }
  els.frame.addEventListener('load', () => {
    els.frame.style.opacity = '1';
    requestAnimationFrame(applyLayout);
  });

  setupTouchCursor();
  setupPreviewGestures();

  currentVariant = parseVariantFromUrl();
  setVariant(currentVariant, true);
  setZoomFit();
})();
