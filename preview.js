(function () {
  const FRAME_W = 360;
  const FRAME_H = 800;
  const ZOOM_STEPS = [0.5, 0.75, 1, 1.25, 1.5];
  const ZOOM_FIT = 'fit';
  const STAGE_PAD = 16;
  const TOOLBAR_PHONE_GAP = 24;
  const PHONE_BORDER_PX = 20;
  const TAP_SLOP = 6;
  const PREVIEW_BUILD = '148';

  const VARIANTS = [
    { id: 'v2', label: 'V2', path: 'variants/v2/index.html' },
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
    reloadBtn: document.getElementById('reloadBtn'),
    variantCaption: document.getElementById('variantCaption'),
    cursor: document.getElementById('touch-cursor'),
  };

  let currentVariant = 'v1';
  let zoomMode = ZOOM_FIT;
  let zoomIndex = 2;

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
    return 'v2';
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
    updateUrl(variantId);
    if (reload) {
      els.frame.style.opacity = '0';
      els.frame.src = variantFrameUrl(meta.path);
    }
  }

  function reloadDemo() {
    try {
      els.frame.contentWindow.postMessage({ type: 'skylight:reload' }, '*');
    } catch (_) {
      els.frame.src = variantFrameUrl(
        VARIANTS.find((v) => v.id === currentVariant)?.path || 'variants/v2/index.html',
      );
    }
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

  if (els.variantSelect && VARIANTS.length > 1) {
    VARIANTS.forEach((variant) => {
      const option = document.createElement('option');
      option.value = variant.id;
      option.textContent = variant.label;
      els.variantSelect.appendChild(option);
    });
    els.variantSelect.addEventListener('change', () => setVariant(els.variantSelect.value));
  } else if (els.variantSelect) {
    els.variantSelect.style.display = 'none';
  }
  els.zoomOutBtn?.addEventListener('click', zoomOut);
  els.zoomInBtn?.addEventListener('click', zoomIn);
  els.zoomFitBtn?.addEventListener('click', setZoomFit);
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
