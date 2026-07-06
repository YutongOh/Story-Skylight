(function (global) {
  function setupSkylightMeasureTool(deps) {
    const { els, framePointFromClient, FRAME_W, FRAME_H, onModeChange } = deps;
    if (!els?.measureBtn) return;

    let active = false;
    let onPhoneMove = null;

    const SKIP_TAGS = new Set(['HTML', 'BODY', 'HEAD', 'SCRIPT', 'STYLE', 'SVG']);
    const LAYOUT_IDS = new Set([
      'flowRoot',
      'layer-feed',
      'layer-inbox',
      'layer-desktop',
      'skylightRow',
      'storyRevealSlot',
      'phone',
      'feedVideo',
    ]);
    const LAYOUT_CLASS_HINTS = [
      'flow-root',
      'flow-layer',
      'feed-video',
      'feed-gradient',
      'skylight-row-inner',
      'skylight-row',
      'story-reveal-slot',
      'inbox-scroll',
      'inbox-list',
      'inbox-body',
      'desktop-wallpaper',
      'story-preview-interaction',
    ];
    const CONTAINER_SELECTORS = [
      '.skylight-item',
      '.feed-nav-cell',
      '.feed-nav-create',
      '.desktop-app',
      '.feed-action',
      '.feed-icon-btn',
      '.feed-tab',
      '.story-message-bubble',
      '.inbox-story-cell',
    ];
    const LEAF_SELECTOR = [
      'img',
      '.skylight-label',
      '.skylight-avatar-slot',
      '.skylight-plus-badge',
      '.skylight-create-body',
      '.skylight-create-ring',
      '.skylight-create-photo',
      '.skylight-create-base',
      '.skylight-ring',
      '.skylight-avatar',
      '.skylight-plus-icon',
      '.skylight-plus-stroke',
      '.feed-nav-label',
      '.feed-nav-icon',
      '.feed-action-label',
      '.feed-action-icon',
      '.feed-avatar',
      '.feed-avatar-ring',
      '.desktop-app-label',
      '.desktop-app-icon',
      '.story-preview-name',
      '.story-preview-time',
      '.story-progress-seg',
      '.system-home-indicator-handle',
    ].join(',');
    const MEASURE_TARGET_SELECTOR = [
      LEAF_SELECTOR,
      '.skylight-item',
      '.feed-nav-cell',
      '.feed-nav-create',
      '.feed-tab',
      '.feed-icon-btn',
      '.feed-action',
      '.feed-avatar-wrap',
      '.inbox-navbar',
      '.inbox-nav-title',
      '.inbox-story-cell',
      '.inbox-section-title',
      '.story-preview-header',
      '.story-message-bubble',
      '.story-preview-actions-row button',
      '.desktop-app',
      '.status-bar',
      '.system-home-indicator',
      '.effect-preset-btn',
    ].join(',');
    const SPACING_REFERENCE_SELECTOR = [
      LEAF_SELECTOR,
      '.skylight-item',
      '.feed-nav-cell',
      '.feed-nav-create',
      '.feed-tab',
      '.feed-icon-btn',
      '.feed-action',
      '.feed-avatar-wrap',
      '.inbox-navbar',
      '.inbox-story-cell',
      '.inbox-section-title',
      '.story-preview-header',
      '.story-message-bubble',
      '.desktop-app',
      '.status-bar',
      '.system-home-indicator',
    ].join(',');

    function isLayoutShell(el) {
      if (!el || el.nodeType !== 1) return true;
      if (el.id && LAYOUT_IDS.has(el.id)) return true;
      const cls = typeof el.className === 'string' ? el.className : '';
      return LAYOUT_CLASS_HINTS.some((hint) => cls.includes(hint));
    }

    function isMediaOverlayText(el, win) {
      if (!el || !el.textContent?.trim()) return false;
      const tag = el.tagName;
      if (tag !== 'SPAN' && tag !== 'P' && tag !== 'LABEL') return false;
      const cs = win.getComputedStyle(el);
      if (cs.position !== 'absolute' && cs.position !== 'fixed') return false;
      return Boolean(el.closest('.feed-action, .skylight-item, .desktop-app'));
    }

    function isContainer(el) {
      return CONTAINER_SELECTORS.some((sel) => el.matches?.(sel));
    }

    function isLeafTarget(el, win) {
      if (!isInspectable(el, win) || isLayoutShell(el)) return false;
      if (el.matches?.(LEAF_SELECTOR)) return true;
      if (el.tagName === 'SPAN' && el.classList.length && !isContainer(el)) return true;
      return false;
    }

    function measureTargetScore(el, win) {
      if (isLayoutShell(el)) return -1000;
      if (isLeafTarget(el, win)) return 200;
      if (isContainer(el)) return 40;
      if (win && isMediaOverlayText(el, win)) return 130;
      const tag = el.tagName;
      if (tag === 'BUTTON') return 30;
      if (tag === 'DIV' && !el.textContent.trim() && el.children.length > 0) return -200;
      return 20;
    }

    function isMeasureTarget(el, win) {
      return measureTargetScore(el, win) > 0;
    }

    function isInspectable(el, win) {
      if (!el || el.nodeType !== 1) return false;
      if (SKIP_TAGS.has(el.tagName)) return false;
      if (el.classList?.contains('phone')) return false;
      const cs = win.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) return false;
      if (el.offsetWidth < 1 || el.offsetHeight < 1) return false;
      return true;
    }

    function normalizeMeasureTarget(el, win) {
      if (!el || isLayoutShell(el)) return null;
      if (!isInspectable(el, win)) return null;
      return el;
    }

    function elementArea(el) {
      return el.offsetWidth * el.offsetHeight;
    }

    function pickFromStack(stack, win) {
      for (const el of stack) {
        if (isLeafTarget(el, win)) return el;
      }
      const nonContainers = stack.filter((el) => !isLayoutShell(el) && !isContainer(el));
      if (nonContainers.length) return nonContainers[0];
      const containers = stack.filter((el) => !isLayoutShell(el) && isContainer(el));
      if (containers.length) {
        return containers.sort((a, b) => elementArea(a) - elementArea(b))[0];
      }
      return null;
    }

    function pointerDistanceToRect(x, y, rect) {
      const cx = Math.max(rect.left, Math.min(x, rect.right));
      const cy = Math.max(rect.top, Math.min(y, rect.bottom));
      return Math.hypot(x - cx, y - cy);
    }

    function snapSlopForTarget(el, win) {
      const parent = el.parentElement;
      if (!parent) return 20;
      const pcs = win.getComputedStyle(parent);
      const pad = Math.max(
        parseFloat(pcs.paddingTop) || 0,
        parseFloat(pcs.paddingRight) || 0,
        parseFloat(pcs.paddingBottom) || 0,
        parseFloat(pcs.paddingLeft) || 0,
        parseFloat(pcs.rowGap || pcs.gap || '0') || 0,
        parseFloat(pcs.columnGap || pcs.gap || '0') || 0,
      );
      return Math.max(20, pad + 6);
    }

    function findNearestMeasureTarget(localX, localY, doc, win) {
      const nodes = doc.querySelectorAll(MEASURE_TARGET_SELECTOR);
      let best = null;
      let bestDist = Infinity;
      let bestArea = Infinity;
      nodes.forEach((el) => {
        if (!isInspectable(el, win) || isLayoutShell(el)) return;
        const rect = el.getBoundingClientRect();
        const slop = snapSlopForTarget(el, win);
        if (
          localX < rect.left - slop
          || localX > rect.right + slop
          || localY < rect.top - slop
          || localY > rect.bottom + slop
        ) return;
        const dist = pointerDistanceToRect(localX, localY, rect);
        const area = elementArea(el);
        if (dist < bestDist - 0.5 || (Math.abs(dist - bestDist) <= 0.5 && area < bestArea)) {
          bestDist = dist;
          bestArea = area;
          best = el;
        }
      });
      return best;
    }

    function pickElement(localX, localY, doc, win) {
      const rawStack = (doc.elementsFromPoint
        ? doc.elementsFromPoint(localX, localY)
        : [doc.elementFromPoint(localX, localY)]
      ).filter((el) => isInspectable(el, win));

      const picked = pickFromStack(rawStack, win);
      if (picked) return picked;

      const normalized = rawStack.map((el) => normalizeMeasureTarget(el, win)).filter(Boolean);
      const fallback = pickFromStack(normalized, win);
      if (fallback) return fallback;

      return findNearestMeasureTarget(localX, localY, doc, win);
    }

    function toScreenRectFromIframe(r) {
      const rect = els.frame.getBoundingClientRect();
      const sx = rect.width / Math.max(1, FRAME_W);
      const sy = rect.height / Math.max(1, FRAME_H);
      return {
        left: rect.left + r.left * sx,
        top: rect.top + r.top * sy,
        width: r.width * sx,
        height: r.height * sy,
      };
    }

    function dp(value) {
      const n = Number(value);
      return Number.isFinite(n) ? Math.round(n) : 0;
    }

    function parsePx(value) {
      return dp(parseFloat(value) || 0);
    }

    function readableColor(raw) {
      if (!raw || raw === 'transparent' || raw === 'rgba(0, 0, 0, 0)') return null;
      return raw;
    }

    function hasDirectText(el) {
      return [...el.childNodes].some(
        (node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim(),
      );
    }

    function isTextualElement(el, win) {
      if (!el || el.tagName === 'IMG') return false;
      if (el.tagName === 'BUTTON' && el.querySelector(':scope > img') && !el.textContent.trim()) return false;
      const textTags = new Set(['SPAN', 'P', 'LABEL', 'A', 'BUTTON']);
      if (textTags.has(el.tagName) && el.textContent.trim()) return true;
      if (hasDirectText(el)) return true;
      if (el.querySelector(':scope > span, :scope > p, :scope > label')) {
        const cs = win.getComputedStyle(el);
        if (cs.display !== 'none' && el.textContent.trim()) return true;
      }
      return false;
    }

    function textMeasureTarget(el, win) {
      if (!isTextualElement(el, win)) return null;
      return (
        el.querySelector(':scope > span, :scope > p, :scope > label')
        || (hasDirectText(el) ? el : null)
        || el
      );
    }

    function elementComponentName(el) {
      if (el.classList.contains('skylight-ring')) {
        const story = el.closest('[data-story-label]')?.dataset.storyLabel;
        return story ? `Story · ${story} 头像环` : 'Story · 头像环';
      }
      if (el.classList.contains('skylight-avatar')) {
        const story = el.closest('[data-story-label]')?.dataset.storyLabel;
        return story ? `Story · ${story} 头像` : 'Story · 头像';
      }
      if (el.classList.contains('skylight-label')) {
        return `Story · ${el.textContent.trim()} 标签`;
      }
      if (el.classList.contains('skylight-avatar-slot')) {
        if (el.closest('[data-skylight-action="create"]')) return 'Create · 头像区';
        const story = el.closest('[data-story-label]')?.dataset.storyLabel;
        return story ? `Story · ${story} 头像区` : 'Story · 头像区';
      }
      if (el.classList.contains('skylight-create-ring')) return 'Create · 头像环';
      if (el.classList.contains('skylight-create-photo')) return 'Create · 头像图';
      if (el.classList.contains('skylight-plus-badge')) return 'Create · 加号';
      if (el.dataset.name) return el.dataset.name;
      const named = el.closest('[data-name]');
      if (named?.dataset.name && named !== el) return named.dataset.name;
      if (el.dataset.storyLabel) return `Story · ${el.dataset.storyLabel}`;
      if (el.dataset.feedNav) return `Nav · ${el.dataset.feedNav}`;
      if (el.dataset.skylightAction) return `Skylight · ${el.dataset.skylightAction}`;
      if (el.dataset.desktopApp) return `App · ${el.dataset.desktopApp}`;
      if (el.dataset.figma) return `Figma ${el.dataset.figma}`;
      if (el.getAttribute('aria-label')) return el.getAttribute('aria-label');
      const classes = [...el.classList].filter((c) => !c.startsWith('is-') && c !== 'active' && c !== 'visible');
      if (classes.length) return classes.slice(0, 2).join(' · ');
      return el.tagName.toLowerCase();
    }

    function elementTypography(el, win) {
      const target = textMeasureTarget(el, win);
      if (!target) return null;
      const cs = win.getComputedStyle(target);
      if (global.TuxTypographyResolver) {
        const fromSelf = TuxTypographyResolver.describeElement(target, cs);
        if (fromSelf) return fromSelf;
      }
      const size = dp(parseFloat(cs.fontSize));
      const weight = cs.fontWeight;
      return { label: `${size} · ${weight}`, token: '' };
    }

    function elementTextColor(el, win) {
      const target = textMeasureTarget(el, win);
      if (!target) return null;
      return readableColor(win.getComputedStyle(target).color);
    }

    function elementHasCoverImage(el, win) {
      if (el.tagName === 'IMG') return true;
      const img = el.querySelector(':scope > img');
      if (!img || !isInspectable(img, win)) return false;
      const ir = img.getBoundingClientRect();
      const er = el.getBoundingClientRect();
      return ir.width >= er.width * 0.85 && ir.height >= er.height * 0.85;
    }

    function elementFill(el, win) {
      if (elementHasCoverImage(el, win)) return 'image';
      const cs = win.getComputedStyle(el);
      const bgImage = cs.backgroundImage;
      if (bgImage && bgImage !== 'none' && !/gradient/i.test(bgImage)) return 'image';
      return readableColor(cs.backgroundColor);
    }

    function formatMeasureColor(raw) {
      if (!raw) return null;
      if (global.TuxColorResolver) {
        const info = TuxColorResolver.describe(raw);
        if (info) return { swatch: raw, label: info.label, token: info.token };
      }
      return { swatch: raw, label: raw, token: '' };
    }

    function colorMeasureRow(label, raw) {
      const info = formatMeasureColor(raw);
      if (!info) return '';
      const title = info.token ? ` title="${info.token}"` : '';
      return `<div class="measure-row"><span>${label}</span><strong${title}><span class="measure-swatch" style="background:${info.swatch}"></span><span class="measure-color-label">${info.label}</span></strong></div>`;
    }

    function typographyMeasureRow(info) {
      if (!info?.label) return '';
      const title = info.token ? ` title="${info.token}"` : '';
      return `<div class="measure-row"><span>字号字重</span><strong${title}>${info.label}</strong></div>`;
    }

    function formatBoxSides(t, r, b, l) {
      return `<span class="measure-box-sides"><span>↑${dp(t)}</span><span>→${dp(r)}</span><span>↓${dp(b)}</span><span>←${dp(l)}</span></span>`;
    }

    function boxMeasureRow(label, t, r, b, l) {
      return `<div class="measure-row"><span>${label}</span><strong>${formatBoxSides(t, r, b, l)}</strong></div>`;
    }

    function hasBoxSides(box) {
      return box.t > 0 || box.r > 0 || box.b > 0 || box.l > 0;
    }

    function isSpacingReference(el, win, self) {
      if (!el || el === self) return false;
      if (self.contains(el) || el.contains(self)) return false;
      if (SKIP_TAGS.has(el.tagName)) return false;
      if (el.classList?.contains('phone')) return false;
      if (isLayoutShell(el)) return false;
      const cs = win.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) return false;
      const r = el.getBoundingClientRect();
      return r.width >= 1 && r.height >= 1;
    }

    function spacingReferences(doc, win, self) {
      const seen = new Set();
      const list = [];
      function add(el) {
        if (!el || seen.has(el)) return;
        if (!isSpacingReference(el, win, self)) return;
        seen.add(el);
        list.push(el);
      }
      doc.querySelectorAll(SPACING_REFERENCE_SELECTOR).forEach(add);
      let node = self.parentElement;
      while (node && !node.classList?.contains('phone')) {
        [...node.children].forEach((child) => add(child));
        node = node.parentElement;
      }
      return list;
    }

    function overlapSize(a1, a2, b1, b2) {
      return Math.min(a2, b2) - Math.max(a1, b1);
    }

    function overlapRatio(a1, a2, b1, b2) {
      const overlap = overlapSize(a1, a2, b1, b2);
      const span = Math.max(1, Math.min(a2 - a1, b2 - b1));
      return overlap / span;
    }

    function computeAroundSpacing(el, win, doc) {
      const rect = el.getBoundingClientRect();
      const sides = { t: Infinity, r: Infinity, b: Infinity, l: Infinity };
      const guides = { t: null, r: null, b: null, l: null };
      const eps = 0.5;
      const minOverlap = 0.2;

      function apply(side, distance, guide) {
        if (!Number.isFinite(distance) || distance < 0) return;
        if (distance < sides[side]) {
          sides[side] = distance;
          guides[side] = guide;
        }
      }

      spacingReferences(doc, win, el).forEach((other) => {
        const sr = other.getBoundingClientRect();
        const vOverlap = overlapRatio(rect.top, rect.bottom, sr.top, sr.bottom);
        const hOverlap = overlapRatio(rect.left, rect.right, sr.left, sr.right);

        if (vOverlap >= minOverlap && sr.right <= rect.left + eps) {
          const gapW = rect.left - sr.right;
          apply('l', gapW, {
            left: sr.right,
            top: Math.max(rect.top, sr.top),
            width: gapW,
            height: overlapSize(rect.top, rect.bottom, sr.top, sr.bottom),
          });
        }
        if (vOverlap >= minOverlap && sr.left >= rect.right - eps) {
          const gapW = sr.left - rect.right;
          apply('r', gapW, {
            left: rect.right,
            top: Math.max(rect.top, sr.top),
            width: gapW,
            height: overlapSize(rect.top, rect.bottom, sr.top, sr.bottom),
          });
        }
        if (hOverlap >= minOverlap && sr.bottom <= rect.top + eps) {
          const gapH = rect.top - sr.bottom;
          apply('t', gapH, {
            left: Math.max(rect.left, sr.left),
            top: sr.bottom,
            width: overlapSize(rect.left, rect.right, sr.left, sr.right),
            height: gapH,
          });
        }
        if (hOverlap >= minOverlap && sr.top >= rect.bottom - eps) {
          const gapH = sr.top - rect.bottom;
          apply('b', gapH, {
            left: Math.max(rect.left, sr.left),
            top: rect.bottom,
            width: overlapSize(rect.left, rect.right, sr.left, sr.right),
            height: gapH,
          });
        }
      });

      return {
        spacing: {
          t: sides.t === Infinity ? 0 : dp(sides.t),
          r: sides.r === Infinity ? 0 : dp(sides.r),
          b: sides.b === Infinity ? 0 : dp(sides.b),
          l: sides.l === Infinity ? 0 : dp(sides.l),
        },
        guides,
      };
    }

    function renderSpacingGuides(guides, spacing) {
      const root = els.measureSpacingGuides;
      if (!root) return;
      root.innerHTML = '';
      const sideLabels = { t: '↑', r: '→', b: '↓', l: '←' };
      ['t', 'r', 'b', 'l'].forEach((side) => {
        const value = spacing[side];
        const guide = guides[side];
        if (!guide || value <= 0) return;
        const sr = toScreenRectFromIframe(guide);
        const div = document.createElement('div');
        div.className = 'measure-spacing-guide';
        div.dataset.label = `${sideLabels[side]}${value}`;
        div.style.left = `${sr.left}px`;
        div.style.top = `${sr.top}px`;
        div.style.width = `${Math.max(sr.width, 2)}px`;
        div.style.height = `${Math.max(sr.height, 2)}px`;
        root.appendChild(div);
      });
    }

    function inspectElement(el, win) {
      const doc = win.document;
      const typography = elementTypography(el, win);
      const color = elementTextColor(el, win);
      const fill = elementFill(el, win);
      const { spacing, guides } = computeAroundSpacing(el, win, doc);
      return {
        el,
        name: elementComponentName(el),
        typography,
        color,
        fill,
        w: el.offsetWidth,
        h: el.offsetHeight,
        spacing,
        spacingGuides: guides,
      };
    }

    function hideMeasureUi() {
      if (els.measureHighlight) els.measureHighlight.style.display = 'none';
      if (els.measureGapHighlight) els.measureGapHighlight.style.display = 'none';
      if (els.measureSpacingGuides) els.measureSpacingGuides.innerHTML = '';
      if (els.measurePanel) els.measurePanel.hidden = true;
    }

    function positionPanel(targetRect) {
      const panel = els.measurePanel;
      const phoneRect = els.phoneWrap.getBoundingClientRect();
      panel.hidden = false;
      const pw = panel.offsetWidth || 160;
      const ph = panel.offsetHeight || 80;
      let left = phoneRect.right + 12;
      if (left + pw > window.innerWidth - 8) left = phoneRect.left - pw - 12;
      let top = targetRect.top + targetRect.height / 2 - ph / 2;
      top = Math.max(phoneRect.top + 8, Math.min(top, phoneRect.bottom - ph - 8));
      top = Math.max(8, Math.min(top, window.innerHeight - ph - 8));
      panel.style.left = `${Math.max(8, left)}px`;
      panel.style.top = `${top}px`;
    }

    function showElementMeasure(data) {
      const sr = toScreenRectFromIframe(data.el.getBoundingClientRect());
      if (els.measureGapHighlight) els.measureGapHighlight.style.display = 'none';
      if (els.measureHighlight) {
        els.measureHighlight.style.display = 'block';
        els.measureHighlight.style.left = `${sr.left}px`;
        els.measureHighlight.style.top = `${sr.top}px`;
        els.measureHighlight.style.width = `${sr.width}px`;
        els.measureHighlight.style.height = `${sr.height}px`;
      }

      const rows = [
        `<div class="measure-row"><span>尺寸</span><strong>${dp(data.w)} × ${dp(data.h)} dp</strong></div>`,
        `<div class="measure-row"><span>名称</span><strong>${data.name}</strong></div>`,
      ];
      if (data.typography) rows.push(typographyMeasureRow(data.typography));
      if (data.color) rows.push(colorMeasureRow('颜色', data.color));
      else if (data.fill === 'image') rows.push('<div class="measure-row"><span>颜色</span><strong>image</strong></div>');
      else if (data.fill) rows.push(colorMeasureRow('颜色', data.fill));
      if (hasBoxSides(data.spacing)) {
        rows.push(boxMeasureRow('相邻间距', data.spacing.t, data.spacing.r, data.spacing.b, data.spacing.l));
      }
      renderSpacingGuides(data.spacingGuides, data.spacing);
      els.measurePanel.innerHTML = rows.join('');
      els.measurePanel.hidden = false;
      positionPanel(sr);
    }

    function handlePointer(clientX, clientY) {
      const point = framePointFromClient(clientX, clientY);
      if (!point) {
        hideMeasureUi();
        return;
      }
      let doc;
      let win;
      try {
        doc = els.frame.contentDocument;
        win = els.frame.contentWindow;
      } catch (_) {
        hideMeasureUi();
        return;
      }
      if (!doc || !win) {
        hideMeasureUi();
        return;
      }
      const el = pickElement(point.x, point.y, doc, win);
      if (!el) {
        hideMeasureUi();
        return;
      }
      showElementMeasure(inspectElement(el, win));
    }

    function bindPhone() {
      if (onPhoneMove) return;
      onPhoneMove = (e) => {
        if (!active) return;
        handlePointer(e.clientX, e.clientY);
      };
      els.phoneWrap.addEventListener('pointermove', onPhoneMove, { passive: true });
      els.phoneWrap.addEventListener('pointerleave', hideMeasureUi, { passive: true });
    }

    function unbindPhone() {
      if (!onPhoneMove) return;
      els.phoneWrap.removeEventListener('pointermove', onPhoneMove);
      els.phoneWrap.removeEventListener('pointerleave', hideMeasureUi);
      onPhoneMove = null;
    }

    function setActive(on) {
      active = on;
      els.measureBtn.classList.toggle('is-active', on);
      els.measureBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
      document.body.classList.toggle('measure-mode', on);
      if (typeof onModeChange === 'function') onModeChange(on);
      if (!on) {
        unbindPhone();
        hideMeasureUi();
        return;
      }
      bindPhone();
    }

    els.measureBtn.addEventListener('click', () => setActive(!active));
    els.frame.addEventListener('load', () => {
      if (!active) hideMeasureUi();
    });

    return { isActive: () => active, setActive };
  }

  global.setupSkylightMeasureTool = setupSkylightMeasureTool;
})(window);
