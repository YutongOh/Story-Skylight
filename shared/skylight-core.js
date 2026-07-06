(function () {
  'use strict';

  const EASE = {
    fastOutSlowIn: 'cubic-bezier(0.4, 0.0, 0.2, 1.0)',
    easeOutStandard: 'cubic-bezier(0.33, 0.86, 0.2, 1)',
    easeInOut: 'cubic-bezier(0.25, 0, 0.25, 1)',
    linear: 'linear',
  };

  const cfg = window.__SKYLIGHT_VARIANT__ || {};
  const previews = window.__STORY_PREVIEWS__ || {};

  const MOTION = {
    maxHeight: cfg.maxHeight ?? 118,
    expandMs: cfg.expandMs ?? 450,
    collapseMs: cfg.collapseMs ?? 350,
    storySlideMs: cfg.storySlideMs ?? 300,
    settleBackMs: cfg.settleBackMs ?? 250,
    autoExpandDelayMs: cfg.autoExpandDelayMs ?? 400,
    tabCrossfadeMs: cfg.tabCrossfadeMs ?? 150,
    feedFadeMs: cfg.feedFadeMs ?? 90,
    storyDurationMs: cfg.storyDurationMs ?? 3500,
    previewEnterMs: cfg.previewEnterMs ?? 180,
    previewExitMs: cfg.previewExitMs ?? 150,
    photoCrossfadeMs: cfg.photoCrossfadeMs ?? 250,
    effectCoverLoadMs: cfg.effectCoverLoadMs ?? 1200,
    effectCoverEmergeMs: cfg.effectCoverEmergeMs ?? 150,
    albumEmergenceMs: cfg.albumEmergenceMs ?? 160,
  };

  const VARIANT = {
    startOnFeed: cfg.startOnFeed !== false,
    id: cfg.id || '',
  };
  const IS_V3 = VARIANT.id === 'v3';
  const ADD_UNREAD_STORIES_ON_REFRESH = cfg.addUnreadStoriesOnRefresh === true;
  const USE_INTEGRATED_SLIDE_REVEAL = cfg.integratedSlideReveal === true || VARIANT.id === 'v3';
  const AUTO_EXPAND_ONCE_PER_RUN = cfg.autoExpandOncePerRun === true;
  document.documentElement.dataset.skylightVariant = VARIANT.id;
  document.documentElement.classList.toggle('integrated-mask-disabled', cfg.integratedMaskEnabled === false);

  // A wheel "stream" is a burst of events spaced closer than this. It must be
  // larger than the gap between events of a SLOW manual scroll, otherwise the
  // settle timer fires between two slow steps and an auto-snap animation races
  // ahead of the finger (the "stutters then collapses" bug). Trackpad inertia
  // and slow manual scrolls both stay well under this.
  const WHEEL_IDLE_MS = 260;
  const REFRESH_WHEEL_IDLE_MS = 48;
  // Damp how much raw wheel distance moves the reveal, so a fast flick's inertia
  // tail doesn't blast it open/closed. The actual commit is by direction anyway.
  const SCROLL_SETTLE_IDLE_MS = 150;
  const TAB_REFRESH_EXPAND_MS = 200;
  const TAB_REFRESH_PULL_MS = 340;
  const TAB_REFRESH_SEQUENCE_MS = 460;
  const FLING_MIN_VELOCITY = 0.06;
  const FLING_FRICTION = 0.88;
  const GESTURE_AXIS_LOCK_PX = 8;
  const els = {
    phone: document.getElementById('phone'),
    layerDesktop: document.getElementById('layer-desktop'),
    layerFeed: document.getElementById('layer-feed'),
    layerInbox: document.getElementById('layer-inbox'),
    inboxRevealRoot: document.getElementById('inboxRevealRoot'),
    storyRevealSlot: document.getElementById('storyRevealSlot'),
    storyRefreshIndicator: document.getElementById('storyRefreshIndicator'),
    storyReleaseHint: document.getElementById('storyReleaseHint'),
    inboxListLayer: document.getElementById('inboxListLayer'),
    inboxScroll: document.getElementById('inboxScroll'),
    skylightRow: document.getElementById('skylightRow'),
    albumEmergence: document.getElementById('albumEmergence'),
    screenAlbum: document.getElementById('screen-album'),
    albumCloseBtn: document.getElementById('albumCloseBtn'),
    albumScroll: document.getElementById('albumScroll'),
    storyAddSheet: document.getElementById('storyAddSheet'),
    storyAddClose: document.getElementById('storyAddClose'),
    storyAddScroll: document.getElementById('storyAddScroll'),
    storyAddCameraNav: document.getElementById('storyAddCameraNav'),
    storyPreview: document.getElementById('storyPreview'),
    storyPreviewClose: document.getElementById('storyPreviewClose'),
    storyPhoto: document.getElementById('storyPhoto'),
    storyPhotoWrap: document.getElementById('storyPhotoWrap'),
    storyProgressRow: document.getElementById('storyProgressRow'),
    storyPreviewAvatar: document.getElementById('storyPreviewAvatar'),
    storyPreviewName: document.getElementById('storyPreviewName'),
    storyPreviewTime: document.getElementById('storyPreviewTime'),
    storyTapPrev: document.getElementById('storyTapPrev'),
    storyTapNext: document.getElementById('storyTapNext'),
  };

  let showFeed = VARIANT.startOnFeed;
  let showDesktop = false;
  let autoExpandEntryConsumed = false;
  let inboxTabUnreadDotConsumed = false;
  let showAlbum = false;
  let readLabels = new Set();
  const INITIAL_SKYLIGHT_META = [
    { label: 'Lindsey', avatar: 'inbox_story_lindsey.png' },
    { label: 'Maren', avatar: 'inbox_story_maren.png' },
    { label: 'Alena', avatar: 'inbox_story_alena.png' },
    { label: 'Rayna', avatar: 'inbox_story_rayna.png' },
  ];
  let skylightStoryMeta = INITIAL_SKYLIGHT_META.map((story) => ({ ...story }));
  let skylightOrder = INITIAL_SKYLIGHT_META.map(({ label }) => label);
  let allReadAutoCollapseEligible = false;
  let manualCollapseAfterAllRead = false;
  let allReadHintActive = false;
  let allReadHintTextSuppressed = false;
  let allReadHintGestureOffsetPx = 0;
  let allReadCollapsePendingAfterDesktopReturn = false;
  const ASSET = '../../shared/assets/inbox/';
  let effectCoverTimer = null;
  let effectCoverLoadStarted = false;
  let effectCoversRevealed = false;
  let pendingViewedLabel = null;
  let pendingViewedTimer = null;

  function setOverlayDarkMode(on) {
    els.phone?.classList.toggle('overlay-dark-mode', on);
  }

  function clamp01(value) {
    return Math.max(0, Math.min(1, value));
  }

  function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function easeInOutCubic(t) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  function allReadAutoCollapseEnabled() {
    return cfg.allReadAutoCollapseEnabled === true;
  }

  function allOtherStoriesRead() {
    return skylightStoryMeta.every(({ label }) => readLabels.has(label));
  }

  function resetAllReadAutoCollapseState() {
    allReadAutoCollapseEligible = false;
    manualCollapseAfterAllRead = false;
    allReadHintActive = false;
    allReadHintTextSuppressed = false;
    allReadHintGestureOffsetPx = 0;
    allReadCollapsePendingAfterDesktopReturn = false;
  }

  function noteAllReadReached() {
    if (!allReadAutoCollapseEnabled() || !allOtherStoriesRead()) return;
    allReadAutoCollapseEligible = true;
    manualCollapseAfterAllRead = false;
  }

  function noteManualAllReadCollapse() {
    if (!allReadAutoCollapseEnabled() || !allOtherStoriesRead()) return;
    if (allReadHintActive && allReadHintTextSuppressed) return;
    manualCollapseAfterAllRead = true;
    allReadAutoCollapseEligible = false;
    allReadHintActive = false;
    allReadHintTextSuppressed = false;
    allReadHintGestureOffsetPx = 0;
    allReadCollapsePendingAfterDesktopReturn = false;
  }

  function clearAllReadHint() {
    if (!allReadHintActive && !allReadHintTextSuppressed && allReadHintGestureOffsetPx <= 0) return;
    allReadHintActive = false;
    allReadHintTextSuppressed = false;
    allReadHintGestureOffsetPx = 0;
    if (!els.storyReleaseHint) return;
    els.storyReleaseHint.style.opacity = '0';
    els.storyReleaseHint.style.height = '0px';
    els.storyReleaseHint.style.background = '';
    els.storyReleaseHint.style.zIndex = '';
  }

  function allReadCollapsedHintHeight() {
    return cfg.allReadCollapsedHintHeight ?? 32;
  }

  function allReadCollapsedHintVisible() {
    return cfg.allReadCollapsedHintEnabled === true
      && allReadHintActive
      && !allReadHintTextSuppressed
      && !showFeed
      && !reveal.isSkylightSubstantiallyOpen()
      && !reveal.isAnimating
      && reveal.refreshOffset <= 0.5;
  }

  function allReadCollapsedHintOffset() {
    return allReadCollapsedHintVisible() ? allReadCollapsedHintHeight() : allReadHintGestureOffsetPx;
  }

  function consumeAllReadHintGestureOffset(controller) {
    if (allReadHintGestureOffsetPx <= 0 || !controller) return;
    const offset = allReadHintGestureOffsetPx;
    if (controller.usesIntegratedSlideReveal?.()) {
      controller.integratedRevealPx = Math.min(controller.maxPx, controller.integratedRevealPx + offset);
    } else if (controller.usesOverlayVisuals?.()) {
      controller.reveal = Math.min(controller.maxPx, controller.reveal + offset);
      controller.slideProgress = clamp01(controller.reveal / controller.maxPx);
      controller.storyVisible = controller.reveal > 0.5;
    }
    clearAllReadHint();
  }

  function beginAllReadHintGestureBaseline() {
    if (!allReadCollapsedHintVisible()) return;
    allReadHintTextSuppressed = true;
    allReadHintGestureOffsetPx = allReadCollapsedHintHeight();
    if (!els.storyReleaseHint) return;
    els.storyReleaseHint.style.opacity = '0';
    els.storyReleaseHint.style.height = '0px';
    els.storyReleaseHint.style.background = '';
    els.storyReleaseHint.style.zIndex = '';
  }

  function restoreAllReadHintIfCollapsed() {
    if (!allReadHintActive || !allReadHintTextSuppressed) return;
    if (showFeed || reveal.isSkylightSubstantiallyOpen() || reveal.isAnimating || reveal.refreshOffset > 0.5) return;
    allReadHintTextSuppressed = false;
    allReadHintGestureOffsetPx = 0;
    applyAllReadCollapsedHint();
  }

  function applyAllReadCollapsedHint() {
    const hint = els.storyReleaseHint;
    if (!hint) return;
    const visible = allReadCollapsedHintVisible();
    if (visible) {
      hint.textContent = cfg.allReadCollapsedHintText || 'Pull down to view story';
      hint.style.height = `${allReadCollapsedHintHeight()}px`;
      hint.style.opacity = '1';
      hint.style.background = 'var(--inbox-page)';
      hint.style.zIndex = '5';
      return;
    }
    if (!allReadHintActive || showFeed || reveal.isSkylightSubstantiallyOpen()) {
      hint.style.opacity = '0';
      hint.style.height = '0px';
      hint.style.background = '';
      hint.style.zIndex = '';
    }
  }

  function cubicBezier(x1, y1, x2, y2) {
    const cx = 3 * x1;
    const bx = 3 * (x2 - x1) - cx;
    const ax = 1 - cx - bx;
    const cy = 3 * y1;
    const by = 3 * (y2 - y1) - cy;
    const ay = 1 - cy - by;
    const sampleX = (t) => ((ax * t + bx) * t + cx) * t;
    const sampleY = (t) => ((ay * t + by) * t + cy) * t;
    const sampleDx = (t) => (3 * ax * t + 2 * bx) * t + cx;
    return (x) => {
      let t = x;
      for (let i = 0; i < 8; i += 1) {
        const dx = sampleX(t) - x;
        const d = sampleDx(t);
        if (Math.abs(dx) < 0.0001 || Math.abs(d) < 0.0001) break;
        t = Math.max(0, Math.min(1, t - dx / d));
      }
      return sampleY(t);
    };
  }

  const easeOutStandard = cubicBezier(0.33, 0.86, 0.2, 1);

  class DesignLabStoryRevealController {
    constructor() {
      this.maxPx = MOTION.maxHeight;
      this.pullThresholdPx = cfg.pullThreshold ?? 24;
      this.maxPullPx = cfg.maxPullDistance ?? this.maxPx;
      this.pushThresholdPx = cfg.pushThreshold ?? 30;
      this.refreshThresholdPx = cfg.refreshThreshold ?? 32;
      this.refreshIndicatorHeightPx = cfg.refreshIndicatorHeight ?? 56;
      this.refreshMaxPullPx = Math.min(
        cfg.refreshMaxPullDistance ?? this.refreshIndicatorHeightPx,
        this.refreshIndicatorHeightPx,
      );
      this.mode = cfg.lockStoryExpanded
        ? 'locked-integrated'
        : cfg.topDownStoryRevealEnabled
          ? 'integrated'
          : 'overlay';
      const autoStartsCollapsed = cfg.autoExpandOnEnter === true;
      this.reveal = cfg.startExpanded && !autoStartsCollapsed ? this.maxPx : 0;
      this.integratedRevealPx = cfg.startExpanded && !autoStartsCollapsed ? this.maxPx : 0;
      this.refreshOffset = 0;
      this.expanded = this.isIntegratedMode()
        ? this.integratedExpanded()
        : this.reveal >= this.maxPx - 0.5;
      this.isAnimating = false;
      this.isRefreshing = false;
      this.refreshLooping = false;
      this.autoExpandConsumed = false;
      this.storyVisible = this.mode !== 'overlay' || this.expanded;
      this.slideProgress = this.storyVisible ? clamp01(this.currentStoryHeight() / this.maxPx) : 0;
      this.pushAccum = 0;
      this.lastDragDown = false;
      this._autoExpandTimer = null;
      this._autoExpandFrame = null;
      this._animFrame = null;
      this._integratedOpacityFrame = null;
      this._integratedOpacityProgress = null;
      this._touch = null;
      this._activeTouchId = null;
      this._scrollSettleTimer = null;
      this._scrollListenerBound = false;
      this._wheelSettleTimer = null;
      this._refreshCompleteTimer = null;
      this._flingFrame = null;
      this._integratedAnimHiddenPx = null;
      this.wheelScrollDamping = cfg.wheelScrollDamping ?? 1;
      this._refreshVisualCache = { height: -1, progress: -1, opacity: -1, refreshing: false };
      this._revealRootModeKey = null;
    }

    isIntegratedMode() {
      return this.mode === 'integrated' || this.mode === 'locked-integrated';
    }

    usesIntegratedSlideReveal() {
      return this.mode === 'integrated' && USE_INTEGRATED_SLIDE_REVEAL;
    }

    usesOverlayVisuals() {
      return this.mode === 'overlay';
    }

    integratedSlideVisiblePx() {
      return Math.max(0, Math.min(this.maxPx, this.integratedRevealPx));
    }

    integratedHiddenPx() {
      if (this.usesIntegratedSlideReveal()) {
        return Math.max(0, Math.min(this.maxPx, this.maxPx - this.integratedSlideVisiblePx()));
      }
      if (this._integratedAnimHiddenPx != null) {
        return Math.max(0, Math.min(this.maxPx, this._integratedAnimHiddenPx));
      }
      const list = this.listEl();
      if (!list) return 0;
      return Math.max(0, Math.min(this.maxPx, list.scrollTop));
    }

    bakeIntegratedHiddenPx(hiddenPx) {
      const list = this.listEl();
      const clamped = Math.max(0, Math.min(this.maxPx, hiddenPx));
      if (this.usesIntegratedSlideReveal()) {
        this.integratedRevealPx = this.maxPx - clamped;
        if (list) {
          list.style.transform = '';
          list.scrollTop = 0;
        }
        return;
      }
      if (!list) return;
      list.style.transform = '';
      list.scrollTop = clamped;
    }

    animateIntegratedSlideRevealTo(targetRevealPx, duration, onComplete, options = {}) {
      this.cancelAnim();
      const clampedTarget = Math.max(0, Math.min(this.maxPx, targetRevealPx));
      const start = this.integratedRevealPx;
      const expanding = clampedTarget > start;
      const list = this.listEl();
      if (list) {
        list.style.transform = '';
        if (list.scrollTop <= this.maxPx + 0.5) list.scrollTop = 0;
      }
      if (Math.abs(start - clampedTarget) < 0.5) {
        this.integratedRevealPx = clampedTarget;
        this.expanded = clampedTarget >= this.maxPx - 0.5;
        this.storyVisible = clampedTarget > 0.5;
        this.applyVisuals();
        onComplete?.();
        return;
      }
      if (expanding) this.storyVisible = true;
      const layer = els.inboxListLayer;
      const safeDuration = Number.isFinite(duration) && duration > 0 ? duration : MOTION.expandMs;
      const opacityEasing = options.easing || easeOutStandard;
      this.setAnimating(true);
      if (layer) {
        layer.style.transition = 'none';
        this.integratedRevealPx = start;
        this._integratedOpacityProgress = clamp01(start / this.maxPx);
        this.applyVisuals();
        void layer.offsetHeight;
        layer.style.transition = `transform ${safeDuration}ms ${options.easingCss || EASE.easeOutStandard}`;
      }
      this._animFrame = requestAnimationFrame(() => {
        this.integratedRevealPx = clampedTarget;
        if (list && clampedTarget <= 0.5) list.scrollTop = 0;
        this.applyVisuals();
        const startedAt = performance.now();
        const delta = clampedTarget - start;
        const tickOpacity = (now) => {
          const t = clamp01((now - startedAt) / safeDuration);
          this._integratedOpacityProgress = clamp01((start + delta * opacityEasing(t)) / this.maxPx);
          this.applyVisuals();
          if (t < 1 && this.isAnimating) {
            this._integratedOpacityFrame = requestAnimationFrame(tickOpacity);
          }
        };
        this._integratedOpacityFrame = requestAnimationFrame(tickOpacity);
        this._animFrame = setTimeout(() => {
          this._animFrame = null;
          if (this._integratedOpacityFrame) cancelAnimationFrame(this._integratedOpacityFrame);
          this._integratedOpacityFrame = null;
          this._integratedOpacityProgress = null;
          if (layer) layer.style.transition = '';
          this.integratedRevealPx = clampedTarget;
          if (list && clampedTarget <= 0.5) list.scrollTop = 0;
          this.expanded = clampedTarget >= this.maxPx - 0.5;
          this.storyVisible = clampedTarget > 0.5;
          this.setAnimating(false);
          if (this.expanded) clearAllReadHint();
          this.applyVisuals();
          onComplete?.();
        }, safeDuration + 32);
      });
    }

    animateIntegratedScrollTo(targetHiddenPx, duration, onComplete, options = {}) {
      if (this.usesIntegratedSlideReveal()) {
        this.animateIntegratedSlideRevealTo(this.maxPx - targetHiddenPx, duration, onComplete, options);
        return;
      }
      this.cancelAnim();
      const clampedTarget = Math.max(0, Math.min(this.maxPx, targetHiddenPx));
      const collapsing = clampedTarget >= this.maxPx - 0.5;
      const start = this.integratedHiddenPx();
      if (Math.abs(start - clampedTarget) < 0.5) {
        this.expanded = !collapsing;
        this.storyVisible = !collapsing;
        this.applyVisuals();
        onComplete?.();
        return;
      }
      const list = this.listEl();
      if (list) list.style.transform = '';
      this._integratedAnimHiddenPx = start;
      if (!collapsing) this.storyVisible = true;
      // Collapse easing matches V4 overlay: easeOutStandard over collapseMs.
      const easing = options.easing ?? easeOutStandard;
      this.animateValue(
        () => start,
        (v) => {
          this._integratedAnimHiddenPx = v;
          if (list) list.scrollTop = Math.round(v);
        },
        clampedTarget,
        duration,
        () => {
          this._integratedAnimHiddenPx = null;
          this.bakeIntegratedHiddenPx(clampedTarget);
          this.expanded = !collapsing;
          this.storyVisible = !collapsing;
          if (this.expanded) clearAllReadHint();
          this.applyVisuals();
          onComplete?.();
        },
        {
          affectsReveal: true,
          easing,
          skipCancel: true,
        },
      );
    }

    animateIntegratedCollapse(onComplete) {
      noteManualAllReadCollapse();
      this.animateIntegratedScrollTo(this.maxPx, MOTION.collapseMs, onComplete, {
        easing: easeOutStandard,
      });
    }

    animateIntegratedExpand(onComplete) {
      this.animateIntegratedScrollTo(0, MOTION.expandMs, onComplete);
    }

    integratedProgress() {
      if (this.mode === 'locked-integrated') return 1;
      return clamp01(1 - this.integratedHiddenPx() / this.maxPx);
    }

    integratedExpanded() {
      return this.integratedHiddenPx() <= 0.5;
    }

    integratedListScrollTop() {
      return this.listEl()?.scrollTop ?? 0;
    }

    inboxTabRefreshPhase() {
      if (this.mode === 'locked-integrated') {
        return this.integratedListScrollTop() <= 0.5 ? 'expanded' : 'listScrolled';
      }
      if (this.isIntegratedMode()) {
        const scrollTop = this.integratedListScrollTop();
        if (scrollTop <= 0.5) return 'expanded';
        if (scrollTop <= this.maxPx + 4) return 'collapsed';
        return 'listScrolled';
      }
      const list = this.listEl();
      const listScrolled = !!(list && list.scrollTop > 4);
      const expanded = this.expanded && this.reveal >= this.maxPx - 0.5;
      if (expanded && !listScrolled) return 'expanded';
      if (!listScrolled) return 'collapsed';
      return 'listScrolled';
    }

    animateListScrollTo(target, duration, onComplete) {
      const list = this.listEl();
      if (!list) {
        onComplete?.();
        return;
      }
      const start = list.scrollTop;
      if (Math.abs(start - target) < 0.5) {
        onComplete?.();
        return;
      }
      this.animateValue(
        () => start,
        (v) => { list.scrollTop = Math.round(v); },
        target,
        duration,
        onComplete,
        { affectsReveal: false },
      );
    }

    animateIntegratedScrollTopTo(target, duration, onComplete) {
      const list = this.listEl();
      if (!list) {
        onComplete?.();
        return;
      }
      const start = list.scrollTop;
      if (Math.abs(start - target) < 0.5) {
        onComplete?.();
        return;
      }
      this.cancelAnim();
      list.style.transform = '';
      this._integratedAnimHiddenPx = null;
      if (target < this.maxPx - 0.5) this.storyVisible = true;
      this.animateValue(
        () => start,
        (v) => { list.scrollTop = Math.round(v); },
        target,
        duration,
        () => {
          this.bakeIntegratedHiddenPx(Math.max(0, Math.min(this.maxPx, target)));
          this.expanded = target <= 0.5;
          this.storyVisible = target <= 0.5 || this.mode === 'locked-integrated';
          this.applyVisuals();
          onComplete?.();
        },
        { affectsReveal: true, skipCancel: true },
      );
    }

    integratedPullCapScrollTop() {
      if (cfg.expandOnDrag === true || cfg.lockStoryExpanded) return 0;
      return Math.max(0, this.maxPx - this.maxPullPx);
    }

    clampIntegratedPullDuringDrag(down) {
      if (cfg.expandOnDrag === true || cfg.lockStoryExpanded || !this.isIntegratedMode() || !down) return;
      const list = this.listEl();
      if (!list || list.scrollTop <= 0.5) return;
      const minScrollTop = this.integratedPullCapScrollTop();
      if (list.scrollTop < minScrollTop) list.scrollTop = minScrollTop;
    }

    integratedCollapseOnDrag(deltaY) {
      if (!this.isIntegratedMode() || cfg.lockStoryExpanded) return false;
      if (this.isAnimating || this.isRefreshing || deltaY >= 0) return false;
      if (!this.expanded || this.integratedHiddenPx() > 0.5) return false;
      this.pushAccum += -deltaY;
      if (this.pushAccum < this.pushThresholdPx) return true;
      this.pushAccum = 0;
      this.animateIntegratedCollapse();
      if (this._touch) this._touch.canFling = false;
      return true;
    }

    initialIntegratedScrollTop() {
      if (this.usesIntegratedSlideReveal()) return 0;
      if (this.mode === 'locked-integrated') return 0;
      const autoStartsCollapsed = cfg.autoExpandOnEnter === true;
      return cfg.startExpanded && !autoStartsCollapsed ? 0 : this.maxPx;
    }

    setIntegratedScrollTop(value) {
      this.bakeIntegratedHiddenPx(value);
    }

    currentStoryHeight() {
      if (this.usesOverlayVisuals()) return this.reveal;
      return this.maxPx * this.integratedProgress();
    }

    listEl() {
      return els.inboxScroll;
    }

    skylightRowEl() {
      return els.skylightRow;
    }

    isPointInSkylight(x, y) {
      if (showFeed || !Number.isFinite(x) || !Number.isFinite(y)) return false;
      const slot = els.storyRevealSlot;
      if (!slot) return false;
      const rect = slot.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return false;
      const visibleHeight = this.usesOverlayVisuals()
        ? Math.min(rect.height, this.reveal + this.refreshLayoutPx())
        : Math.min(rect.height, Math.max(0, this.maxPx - this.integratedHiddenPx()));
      if (visibleHeight <= 0.5) return false;
      const bottom = rect.top + visibleHeight;
      return x >= rect.left && x <= rect.right && y >= rect.top && y <= bottom;
    }

    refreshLayoutPx() {
      return this.refreshOffset;
    }

    listAtTop() {
      const list = this.listEl();
      return !list || list.scrollTop <= 4;
    }

    scrollListBy(deltaPx) {
      const list = this.listEl();
      if (!list || deltaPx === 0) return;
      const maxScroll = Math.max(0, list.scrollHeight - list.clientHeight);
      list.scrollTop = Math.max(0, Math.min(maxScroll, list.scrollTop + deltaPx));
    }

    setAnimating(on) {
      this.isAnimating = on;
      els.inboxListLayer?.classList.toggle('is-reveal-animating', on);
      els.storyRevealSlot?.classList.toggle('is-reveal-animating', on);
    }

    cancelAnim() {
      if (this._animFrame) {
        cancelAnimationFrame(this._animFrame);
        clearTimeout(this._animFrame);
      }
      this._animFrame = null;
      clearTimeout(this._wheelSettleTimer);
      this._wheelSettleTimer = null;
      clearTimeout(this._refreshCompleteTimer);
      this._refreshCompleteTimer = null;
      clearTimeout(this._scrollSettleTimer);
      this._scrollSettleTimer = null;
      if (this._integratedOpacityFrame) cancelAnimationFrame(this._integratedOpacityFrame);
      this._integratedOpacityFrame = null;
      this._integratedOpacityProgress = null;
      this.stopFling(false);
      if (this.usesIntegratedSlideReveal() && els.inboxListLayer) {
        els.inboxListLayer.style.transition = '';
      }
      if (this._integratedAnimHiddenPx != null) {
        this.bakeIntegratedHiddenPx(this._integratedAnimHiddenPx);
        this._integratedAnimHiddenPx = null;
      }
      if (this.isAnimating) {
        this.setAnimating(false);
      }
    }

    isReleaseHintPullPhase() {
      return cfg.releaseHintEnabled === true
        && !this.expanded
        && !this.isAnimating
        && this.mode === 'overlay';
    }

    overlaySlideProgress(progress) {
      if (this.isReleaseHintPullPhase()) return 0;
      return this.isAnimating ? this.slideProgress : progress;
    }

    overlayStoryVisible() {
      if (this.isReleaseHintPullPhase()) return false;
      return this.storyVisible;
    }

    overlaySkylightOpacity() {
      if (this.isReleaseHintPullPhase()) return 0;
      if (cfg.storySlideEnabled) {
        return this.overlayStoryVisible() ? 1 : 0;
      }
      if (cfg.overlayOpacityEnabled === true) {
        if (!this.overlayStoryVisible()) return 0;
        const progress = clamp01(this.reveal / this.maxPx);
        const minOpacity = cfg.overlayMinOpacity ?? 0.18;
        const startProgress = cfg.overlayOpacityStartProgress ?? 0.42;
        const endProgress = cfg.overlayOpacityEndProgress ?? 0.95;
        const opacityProgress = clamp01((progress - startProgress) / Math.max(0.01, endProgress - startProgress));
        const eased = opacityProgress ** 3 * (opacityProgress * (opacityProgress * 6 - 15) + 10);
        return Math.max(0, Math.min(1, minOpacity + (1 - minOpacity) * eased));
      }
      // V2: keep slot opacity at 1; edge fade is mask-driven only (see overlaySkylightEdgeFadeMask).
      return this.overlayStoryVisible() ? 1 : 0;
    }

    integratedSkylightOpacity(progress) {
      if (cfg.integratedOpacityEnabled !== true) {
        return progress > 0.01 ? 1 : 0;
      }
      if (progress <= 0.001) return 0;
      const minOpacity = cfg.integratedOpacityMin ?? 0.18;
      const startProgress = cfg.integratedOpacityStartProgress ?? 0.06;
      const endProgress = cfg.integratedOpacityEndProgress ?? 0.85;
      const opacityProgress = clamp01((progress - startProgress) / Math.max(0.01, endProgress - startProgress));
      const eased = opacityProgress ** 3 * (opacityProgress * (opacityProgress * 6 - 15) + 10);
      return Math.max(0, Math.min(1, minOpacity + (1 - minOpacity) * eased));
    }

    overlaySkylightEdgeFadeMask() {
      if (cfg.overlayMaskEnabled === false) return null;
      if (cfg.storySlideEnabled) return null;
      const progress = clamp01(this.reveal / this.maxPx);
      if (progress <= 0.001) return null;
      if (progress >= 0.985) return null;
      const visiblePx = this.maxPx * progress;
      const fadePx = Math.min(28, Math.max(14, visiblePx * 0.34), visiblePx * 0.88);
      const solidEndPx = Math.max(0, visiblePx - fadePx);
      const softMidPx = solidEndPx + fadePx * 0.42;
      const softEndPx = solidEndPx + fadePx * 0.76;
      return [
        'linear-gradient(to bottom,',
        'rgba(0,0,0,1) 0px,',
        `rgba(0,0,0,1) ${solidEndPx}px,`,
        `rgba(0,0,0,0.82) ${softMidPx}px,`,
        `rgba(0,0,0,0.36) ${softEndPx}px,`,
        `rgba(0,0,0,0) ${visiblePx}px,`,
        'rgba(0,0,0,0) 100%)',
      ].join(' ');
    }

    integratedSkylightEdgeFadeMask() {
      if (cfg.integratedMaskEnabled === false) return null;
      if (cfg.storySlideEnabled) return null;
      const progress = clamp01(this.integratedProgress());
      if (progress <= 0.001) return null;
      if (progress >= 0.999) return null;
      // Integrated scroll hides from the top; anchor fade at the visible top edge.
      const hiddenPct = (1 - progress) * 100;
      const maxFadeBandPct = Math.min(34, Math.max(12, (40 / this.maxPx) * 100));
      const fadeBandPct = maxFadeBandPct * (1 - progress);
      if (fadeBandPct < 0.05) return null;
      const fadeEnd = Math.min(100, hiddenPct + fadeBandPct);
      // Lighter near nav/reveal edge, stronger toward list/bottom.
      return `linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0) ${hiddenPct}%, rgba(0,0,0,1) ${fadeEnd}%, rgba(0,0,0,1) 100%)`;
    }

    applySkylightMask(el) {
      if (!el) return;
      const mask = this.overlaySkylightEdgeFadeMask();
      if (mask) {
        el.style.webkitMaskImage = mask;
        el.style.maskImage = mask;
        el.style.webkitMaskMode = 'alpha';
        el.style.maskMode = 'alpha';
        el.style.webkitMaskSize = '100% 100%';
        el.style.maskSize = '100% 100%';
        el.style.webkitMaskRepeat = 'no-repeat';
        el.style.maskRepeat = 'no-repeat';
      } else {
        this.clearSkylightMask(el);
      }
    }

    applyIntegratedSkylightMask(el) {
      if (!el) return;
      const mask = this.integratedSkylightEdgeFadeMask();
      if (mask) {
        el.style.webkitMaskImage = mask;
        el.style.maskImage = mask;
        el.style.webkitMaskMode = 'alpha';
        el.style.maskMode = 'alpha';
        el.style.webkitMaskSize = '100% 100%';
        el.style.maskSize = '100% 100%';
        el.style.webkitMaskRepeat = 'no-repeat';
        el.style.maskRepeat = 'no-repeat';
      } else {
        this.clearSkylightMask(el);
      }
    }

    clearSkylightMask(el) {
      if (!el) return;
      el.style.maskImage = '';
      el.style.webkitMaskImage = '';
      el.style.maskMode = 'normal';
      el.style.webkitMaskMode = 'normal';
      el.style.maskSize = '';
      el.style.webkitMaskSize = '';
      el.style.maskRepeat = '';
      el.style.webkitMaskRepeat = '';
    }

    overlayReleaseReveal() {
      return Math.max(this.reveal, this._touch?.peakReveal ?? 0);
    }

    shouldSettleOverlayReveal() {
      return this.mode === 'overlay'
        && !this.expanded
        && this.overlayReleaseReveal() > 0.5;
    }

    pullDampingFactor() {
      if (cfg.releaseHintEnabled && !cfg.expandOnDrag) return 1;
      return cfg.pullDamping ?? 0.65;
    }

    syncOverlayGestureState() {
      if (this.mode !== 'overlay') return;
      this.expanded = this.reveal >= this.maxPx - 0.5;
      if (this.isReleaseHintPullPhase()) {
        this.storyVisible = false;
        this.slideProgress = 0;
      } else {
        this.storyVisible = this.reveal > 0.5;
        this.slideProgress = clamp01(this.reveal / this.maxPx);
      }
    }

    ensureGestureReady() {
      if (this.isAnimating && !this._animFrame) {
        this.setAnimating(false);
        this.syncOverlayGestureState();
      }
    }

    stopFling(runSettle) {
      if (this._flingFrame) {
        cancelAnimationFrame(this._flingFrame);
        this._flingFrame = null;
      }
      if (runSettle) this.afterFlingSettle();
    }

    afterFlingSettle() {
      if (this.mode === 'integrated' && !cfg.lockStoryExpanded && !this.isAnimating && !this.isRefreshing) {
        const hidden = this.integratedHiddenPx();
        if (hidden > 0.5 && hidden < this.maxPx - 0.5) {
          this.settleReveal();
        }
      }
    }

    recordGestureVelocity(dy) {
      if (!this._touch || dy === 0) return;
      const now = performance.now();
      const lastAt = this._touch.lastVelocityAt ?? now;
      const dt = now - lastAt;
      if (dt > 0 && dt < 80) {
        const sample = dy / dt;
        this._touch.velocityY = (this._touch.velocityY ?? 0) * 0.55 + sample * 0.45;
      } else if (dt >= 80) {
        this._touch.velocityY = dy / dt;
      }
      this._touch.lastVelocityAt = now;
    }

    flingCollapseVelocityPx() {
      return (cfg.pushFlingVelocity ?? 1500) / 1000;
    }

    flingStep(fingerDy) {
      if (this.isRefreshing || this.isAnimating || this.refreshOffset > 0.5) return false;

      if (this.isIntegratedMode()) {
        if (this.integratedHiddenPx() <= 0.5 && fingerDy > 0) return false;
        const list = this.listEl();
        if (!list) return false;
        const before = list.scrollTop;
        this.scrollListBy(-fingerDy);
        if (fingerDy > 0) this.clampIntegratedPullDuringDrag(true);
        if (list.scrollTop === before && Math.abs(fingerDy) > 0.5) return false;
        this.expanded = this.integratedExpanded();
        this.applyVisuals();
        return true;
      }

      if (this.expanded && this.listAtTop() && fingerDy > 0) return false;
      if (this.refreshOffset > 0) return false;
      if (!this.expanded && this.listAtTop() && fingerDy > 0 && this.reveal < this.maxPx - 0.5) return false;
      if (!this.expanded && this.reveal > 0.5 && fingerDy < 0) return false;
      if (this.expanded && fingerDy < 0) return false;

      const list = this.listEl();
      if (!list) return false;
      const before = list.scrollTop;
      this.scrollListBy(-fingerDy);
      return list.scrollTop !== before || Math.abs(fingerDy) <= 0.5;
    }

    startListFling(velocityY) {
      this.stopFling(false);
      let velocity = velocityY;
      let lastAt = performance.now();
      const step = (now) => {
        const dt = Math.min(48, Math.max(1, now - lastAt));
        lastAt = now;
        if (Math.abs(velocity) < FLING_MIN_VELOCITY || this.isRefreshing || this.isAnimating) {
          this.stopFling(true);
          return;
        }
        const dy = velocity * dt;
        if (!this.flingStep(dy)) {
          this.stopFling(true);
          return;
        }
        velocity *= Math.pow(FLING_FRICTION, dt / 16);
        this._flingFrame = requestAnimationFrame(step);
      };
      this._flingFrame = requestAnimationFrame(step);
    }

    stopAnimation() {
      this.cancelAnim();
      this.setAnimating(false);
    }

    setOffset() {
      const autoStartsCollapsed = cfg.autoExpandOnEnter === true;
      this.reveal = cfg.startExpanded && !autoStartsCollapsed ? this.maxPx : 0;
      this.integratedRevealPx = cfg.startExpanded && !autoStartsCollapsed ? this.maxPx : 0;
      this._integratedOpacityProgress = null;
      this.refreshOffset = 0;
      this.expanded = this.isIntegratedMode()
        ? this.integratedExpanded()
        : this.reveal >= this.maxPx - 0.5;
      this.storyVisible = this.mode !== 'overlay' || this.expanded;
      this.slideProgress = this.storyVisible ? clamp01(this.currentStoryHeight() / this.maxPx) : 0;
      this.isRefreshing = false;
      this.refreshLooping = false;
      this.autoExpandConsumed = false;
      this.pushAccum = 0;
      this.lastDragDown = false;
      this._refreshVisualCache = { height: -1, progress: -1, opacity: -1, refreshing: false };
      if (this.isIntegratedMode()) {
        const list = this.listEl();
        if (list) list.scrollTop = this.initialIntegratedScrollTop();
      }
      this.applyVisuals();
    }

    ensureIntegratedDom() {
      if (!els.inboxScroll || !els.storyRevealSlot) return;
      if (els.storyRevealSlot.parentElement !== els.inboxScroll) {
        els.inboxScroll.insertBefore(els.storyRevealSlot, els.inboxScroll.firstChild);
      }
    }

    ensureOverlayDom() {
      if (!els.inboxRevealRoot || !els.storyRevealSlot || !els.inboxListLayer) return;
      if (els.storyRevealSlot.parentElement !== els.inboxRevealRoot) {
        els.inboxRevealRoot.insertBefore(els.storyRevealSlot, els.inboxListLayer);
      }
      if (els.storyReleaseHint && els.storyReleaseHint.parentElement !== els.inboxRevealRoot) {
        els.inboxRevealRoot.insertBefore(els.storyReleaseHint, els.inboxListLayer);
      }
    }

    bindScroll() {
      const root = els.inboxRevealRoot;
      if (!root) return;
      if (!this._gestureGuardBound) {
        this._gestureGuardBound = true;
        const blockSelect = (event) => {
          if (!this._touch) return;
          event.preventDefault();
        };
        root.addEventListener('selectstart', blockSelect);
        root.addEventListener('dragstart', blockSelect);
        document.addEventListener('selectstart', blockSelect);
        document.addEventListener('dragstart', blockSelect);
      }
      root.addEventListener('touchstart', (e) => this.handleTouchStart(e), { passive: false });
      root.addEventListener('touchmove', (e) => this.handleTouchMove(e), { passive: false });
      root.addEventListener('touchend', (e) => this.handleTouchEnd(e), { passive: true });
      root.addEventListener('touchcancel', () => this.abortTouchStream(), { passive: true });
      root.addEventListener('pointerdown', (e) => this.handlePointerStart(e), { passive: false });
      root.addEventListener('pointermove', (e) => this.handlePointerMove(e), { passive: false });
      root.addEventListener('pointerup', () => this.handleEnd(), { passive: true });
      root.addEventListener('pointercancel', () => this.handleEnd(), { passive: true });
      root.addEventListener('wheel', (e) => this.handleWheel(e), { passive: false });
      const list = this.listEl();
      if (list && !this._scrollListenerBound) {
        this._scrollListenerBound = true;
        list.addEventListener('scroll', () => this.handleIntegratedScroll(), { passive: true });
      }
      if (this.isIntegratedMode()) {
        const scrollList = this.listEl();
        if (scrollList) scrollList.scrollTop = this.initialIntegratedScrollTop();
      }
      this.applyVisuals();
    }

    handleIntegratedScroll() {
      if (this.mode !== 'integrated' || cfg.lockStoryExpanded || this.isAnimating) return;
      this.expanded = this.integratedExpanded();
      this.applyVisuals();
      if (this._touch) return;
      clearTimeout(this._scrollSettleTimer);
      this._scrollSettleTimer = setTimeout(() => {
        this._scrollSettleTimer = null;
        if (this.mode !== 'integrated' || this.isAnimating || this.isRefreshing) return;
        const hidden = this.integratedHiddenPx();
        if (hidden > 0.5 && hidden < this.maxPx - 0.5) {
          this.settleReveal();
        }
      }, SCROLL_SETTLE_IDLE_MS);
    }

    applyRevealRootMode() {
      const root = els.inboxRevealRoot;
      if (!root) return;
      const useOverlayLayout = this.usesOverlayVisuals();
      const modeKey = useOverlayLayout
        ? 'overlay'
        : this.mode === 'locked-integrated'
          ? 'integrated-locked'
          : 'integrated';
      if (this._revealRootModeKey === modeKey) return;
      root.classList.remove('whole-page', 'gesture-reveal', 'mode-overlay', 'mode-integrated', 'mode-locked-integrated');
      if (useOverlayLayout) {
        root.classList.add('mode-overlay');
      } else {
        root.classList.add('mode-integrated');
        if (this.mode === 'locked-integrated') root.classList.add('mode-locked-integrated');
      }
      this._revealRootModeKey = modeKey;
    }

    applyVisuals() {
      const root = els.inboxRevealRoot;
      if (!root) return;
      this.applyRevealRootMode();
      if (this.usesOverlayVisuals()) this.ensureOverlayDom();
      else this.ensureIntegratedDom();

      const storyHeight = this.currentStoryHeight();
      const progress = clamp01(storyHeight / this.maxPx);
      if (this.usesOverlayVisuals()) {
        if (!this.isAnimating) {
          this.expanded = this.reveal >= this.maxPx - 0.5;
        }
      } else if (!this.isAnimating) {
        this.expanded = this.integratedExpanded();
      }

      if (this.usesOverlayVisuals()) {
        const skylightOpacity = this.overlaySkylightOpacity();
        const skylightVisible = skylightOpacity > 0.01;
        if (els.storyRevealSlot) {
          els.storyRevealSlot.style.height = `${this.maxPx}px`;
          els.storyRevealSlot.style.visibility = skylightVisible ? 'visible' : 'hidden';
          els.storyRevealSlot.style.opacity = `${skylightOpacity}`;
          els.storyRevealSlot.style.transform = `translateY(${this.refreshLayoutPx()}px)`;
          els.storyRevealSlot.style.pointerEvents = skylightVisible ? '' : 'none';
          this.applySkylightMask(els.storyRevealSlot);
        }
        if (els.inboxListLayer) {
          els.inboxListLayer.style.transform = `translateY(${this.reveal + this.refreshLayoutPx() + allReadCollapsedHintOffset()}px)`;
        }
        const slideInner = els.storyRevealSlot?.querySelector('.skylight-row-inner') || els.skylightRow;
        if (slideInner) {
          const slideProgress = this.overlaySlideProgress(progress);
          slideInner.style.transform = cfg.storySlideEnabled
            ? `translateY(${this.maxPx * (1 - slideProgress)}px)`
            : 'none';
          slideInner.style.opacity = skylightVisible ? '1' : '0';
          this.applySkylightMask(slideInner);
        }
        this.applyReleaseHint(progress);
      } else {
        const integratedProgress = this.integratedProgress();
        const integratedOpacityProgress = this._integratedOpacityProgress ?? integratedProgress;
        const storyVisible = (this.usesIntegratedSlideReveal() && this.isAnimating) || integratedProgress > 0.01;
        const integratedOpacity = this.integratedSkylightOpacity(integratedOpacityProgress);
        if (els.storyRevealSlot) {
          els.storyRevealSlot.style.height = `${this.maxPx}px`;
          els.storyRevealSlot.style.visibility = storyVisible ? 'visible' : 'hidden';
          els.storyRevealSlot.style.opacity = storyVisible ? `${integratedOpacity}` : '0';
          els.storyRevealSlot.style.transform = 'none';
          els.storyRevealSlot.style.pointerEvents = storyVisible ? '' : 'none';
          this.clearSkylightMask(els.storyRevealSlot);
        }
        if (els.inboxListLayer) {
          const hiddenPx = this.usesIntegratedSlideReveal() ? this.integratedHiddenPx() : 0;
          els.inboxListLayer.style.bottom = this.usesIntegratedSlideReveal() ? `-${this.maxPx}px` : '';
          els.inboxListLayer.style.transform = `translateY(${this.refreshLayoutPx() - hiddenPx + allReadCollapsedHintOffset()}px)`;
        }
        const slideInner = els.storyRevealSlot?.querySelector('.skylight-row-inner') || els.skylightRow;
        if (slideInner) {
          slideInner.style.transform = 'none';
          slideInner.style.opacity = '1';
          this.applyIntegratedSkylightMask(slideInner);
        }
        this.hideReleaseHint();
      }
      this.applyRefreshVisuals();
      applyAllReadCollapsedHint();
      restoreAllReadHintIfCollapsed();
    }

    applyReleaseHint(progress) {
      const hint = els.storyReleaseHint;
      if (!hint) return;
      const visible = cfg.releaseHintEnabled === true && !this.expanded && !this.isAnimating && this.reveal > 0.5 && this.refreshOffset <= 0.5;
      hint.textContent = cfg.releaseHintText || 'Pull down to view story';
      hint.style.height = `${this.reveal}px`;
      hint.style.opacity = visible ? `${this.pullThresholdPx > 0 ? clamp01(this.reveal / this.pullThresholdPx) : 1}` : '0';
    }

    hideReleaseHint() {
      if (!els.storyReleaseHint) return;
      els.storyReleaseHint.style.opacity = '0';
      els.storyReleaseHint.style.height = '0px';
    }

    isSkylightSubstantiallyOpen() {
      if (this.mode === 'locked-integrated') return true;
      if (this.isIntegratedMode()) {
        return this.integratedProgress() > 0.01 || this.expanded;
      }
      return this.reveal > 0.5 || this.expanded;
    }

    collapseSilentlyForAllRead() {
      this.stopFling(false);
      this.cancelAutoExpand();
      this.cancelAnim();
      clearTimeout(this._scrollSettleTimer);
      this._scrollSettleTimer = null;
      clearTimeout(this._wheelSettleTimer);
      this._wheelSettleTimer = null;
      this._touch = null;
      this.refreshOffset = 0;
      this.pushAccum = 0;
      this.lastDragDown = false;
      this.setAnimating(false);
      if (this.isIntegratedMode()) {
        this._integratedAnimHiddenPx = null;
        this._integratedOpacityProgress = null;
        this.bakeIntegratedHiddenPx(this.maxPx);
        this.integratedRevealPx = 0;
      } else {
        this.reveal = 0;
        this.slideProgress = 0;
      }
      this.expanded = false;
      this.storyVisible = false;
      this.applyVisuals();
    }

    applyRefreshVisuals() {
      const indicator = els.storyRefreshIndicator;
      if (!indicator) return;
      const progress = clamp01(this.refreshOffset / this.refreshThresholdPx);
      const refreshing = this.refreshLooping;
      const visualProgress = refreshing ? 1 : progress;
      const displayHeight = this.refreshLayoutPx();
      const opacity = this.refreshOffset > 0.5 || this.isRefreshing ? '1' : '0';
      const cache = this._refreshVisualCache;
      if (
        cache.height === displayHeight &&
        cache.progress === visualProgress &&
        cache.opacity === opacity &&
        cache.refreshing === refreshing
      ) {
        return;
      }
      cache.height = displayHeight;
      cache.progress = visualProgress;
      cache.opacity = opacity;
      cache.refreshing = refreshing;
      indicator.style.height = `${displayHeight}px`;
      indicator.style.opacity = opacity;
      indicator.classList.toggle('is-refreshing', refreshing);
      indicator.style.setProperty('--dualball-progress', `${visualProgress}`);
      indicator.style.setProperty('--dualball-alpha', `${visualProgress}`);
    }

    applyRefreshPull(deltaY) {
      if (deltaY === 0) return false;
      const prev = this.refreshOffset;
      this.refreshOffset = Math.max(0, Math.min(this.refreshMaxPullPx, this.refreshOffset + deltaY));
      if (Math.abs(this.refreshOffset - prev) < 0.01) return false;
      this.applyVisuals();
      return true;
    }

    cancelAutoExpand() {
      clearTimeout(this._autoExpandTimer);
      if (this._autoExpandFrame) cancelAnimationFrame(this._autoExpandFrame);
      this._autoExpandTimer = null;
      this._autoExpandFrame = null;
    }

    prepareAutoExpandOnEnter() {
      if (cfg.autoExpandOnEnter !== true || cfg.lockStoryExpanded) return;
      if (this.mode === 'integrated' && cfg.startExpanded !== true) return;

      this.cancelAutoExpand();
      this.stopAnimation();
      this.autoExpandConsumed = false;

      if (this.isIntegratedMode()) {
        this.setIntegratedScrollTop(this.maxPx);
        this.expanded = false;
        this.applyVisuals();
        return;
      }

      if (this.mode === 'overlay') {
        this.reveal = 0;
        this.expanded = false;
        this.storyVisible = false;
        this.slideProgress = 0;
        this.applyVisuals();
      }
    }

    scheduleAutoExpand() {
      if (cfg.autoExpandOnEnter !== true || this.autoExpandConsumed || cfg.lockStoryExpanded) return;
      if (this.mode === 'integrated' && cfg.startExpanded !== true) return;

      this.cancelAutoExpand();
      this._autoExpandFrame = requestAnimationFrame(() => {
        this._autoExpandFrame = null;
        this._autoExpandTimer = setTimeout(() => {
          this._autoExpandTimer = null;
          if (showFeed || this._touch) return;
          if (this.isIntegratedMode()) {
            if (this.integratedHiddenPx() <= 0.5) {
              this.expanded = true;
              this.autoExpandConsumed = true;
              this.applyVisuals();
              return;
            }
          } else if (this.mode === 'overlay' && this.expanded) {
            this.autoExpandConsumed = true;
            return;
          }
          this.autoExpandConsumed = true;
          this.animateExpand();
        }, MOTION.autoExpandDelayMs);
      });
    }

    animateValue(getter, setter, target, duration, onComplete, options = {}) {
      const start = getter();
      const delta = target - start;
      const affectsReveal = options.affectsReveal !== false;
      const easing = options.easing ?? easeOutStandard;
      const safeDuration = Number.isFinite(duration) && duration > 0 ? duration : MOTION.settleBackMs;
      if (!options.skipCancel) this.cancelAnim();
      if (Math.abs(delta) < 0.5) {
        setter(target);
        this.applyVisuals();
        onComplete?.();
        return;
      }
      if (affectsReveal) {
        clearTimeout(this._scrollSettleTimer);
        this._scrollSettleTimer = null;
        this.setAnimating(true);
      }
      const startedAt = performance.now();
      const tick = (now) => {
        const t = Math.min(1, (now - startedAt) / safeDuration);
        setter(start + delta * easing(t));
        this.applyVisuals();
        if (t < 1) {
          this._animFrame = requestAnimationFrame(tick);
          return;
        }
        this._animFrame = null;
        setter(target);
        if (affectsReveal) this.setAnimating(false);
        this.applyVisuals();
        onComplete?.();
      };
      this._animFrame = requestAnimationFrame(tick);
    }

    animateOverlayReveal(targetReveal, targetSlide, revealMs, slideMs, onComplete, options = {}) {
      const startReveal = this.reveal;
      const startSlide = this.slideProgress;
      const revealDelta = targetReveal - startReveal;
      const slideDelta = targetSlide - startSlide;
      const safeRevealMs = Number.isFinite(revealMs) && revealMs > 0 ? revealMs : MOTION.expandMs;
      const safeSlideMs = Number.isFinite(slideMs) && slideMs > 0 ? slideMs : MOTION.storySlideMs;
      const duration = Math.max(safeRevealMs, safeSlideMs);
      if (!options.skipCancel) this.cancelAnim();
      if (Math.abs(revealDelta) < 0.5 && Math.abs(slideDelta) < 0.01) {
        this.reveal = targetReveal;
        this.slideProgress = targetSlide;
        this.applyVisuals();
        onComplete?.();
        return;
      }
      this.setAnimating(true);
      const startedAt = performance.now();
      const tick = (now) => {
        const elapsed = now - startedAt;
        const revealT = Math.min(1, elapsed / safeRevealMs);
        const slideT = Math.min(1, elapsed / safeSlideMs);
        this.reveal = Math.max(0, Math.min(this.maxPx, startReveal + revealDelta * easeOutStandard(revealT)));
        this.slideProgress = clamp01(startSlide + slideDelta * easeOutStandard(slideT));
        this.applyVisuals();
        if (elapsed < duration) {
          this._animFrame = requestAnimationFrame(tick);
          return;
        }
        this._animFrame = null;
        this.reveal = targetReveal;
        this.slideProgress = targetSlide;
        if (targetReveal >= this.maxPx - 0.5) {
          this.expanded = true;
          this.storyVisible = true;
        }
        this.setAnimating(false);
        if (!options.skipFinalApplyVisuals) this.applyVisuals();
        onComplete?.();
      };
      this._animFrame = requestAnimationFrame(tick);
    }

    animateExpand(onComplete, expandMs = MOTION.expandMs) {
      if (this.mode === 'locked-integrated') {
        onComplete?.();
        return;
      }
      consumeAllReadHintGestureOffset(this);
      this.storyVisible = true;
      const slideMs = Math.min(expandMs, MOTION.storySlideMs);
      if (this.mode === 'overlay') {
        const fromReleaseHint = cfg.releaseHintEnabled
          && !cfg.expandOnDrag
          && this.reveal < this.maxPx - 0.5;
        this.slideProgress = fromReleaseHint ? 0 : clamp01(this.reveal / this.maxPx);
        this.hideReleaseHint();
        this.animateOverlayReveal(
          this.maxPx,
          1,
          expandMs,
          slideMs,
          () => {
            this.expanded = true;
            this.storyVisible = true;
            clearAllReadHint();
            this.applyVisuals();
            onComplete?.();
          },
        );
      } else {
        this.animateIntegratedScrollTo(0, expandMs, () => {
          this.expanded = true;
          onComplete?.();
        });
      }
    }

    animateOverlaySnap(targetReveal, onComplete) {
      this.slideProgress = clamp01(this.reveal / this.maxPx);
      this.animateOverlayReveal(
        targetReveal,
        targetReveal >= this.maxPx - 0.5 ? 1 : clamp01(targetReveal / this.maxPx),
        MOTION.settleBackMs,
        MOTION.settleBackMs,
        onComplete,
      );
    }

    animateCollapse() {
      if (this.mode === 'locked-integrated') return;
      noteManualAllReadCollapse();
      const finish = () => {
        this.expanded = false;
        if (this.mode === 'overlay') this.storyVisible = false;
        this.applyVisuals();
      };
      if (this.mode === 'overlay') {
        this.slideProgress = clamp01(this.reveal / this.maxPx);
        this.animateOverlayReveal(
          0,
          0,
          MOTION.collapseMs,
          MOTION.collapseMs,
          finish,
        );
      } else {
        this.animateIntegratedCollapse(() => {
          this.expanded = false;
        });
      }
    }

    settleReveal(releaseReveal = this.overlayReleaseReveal()) {
      if (this.mode === 'locked-integrated') return;
      if (this.mode === 'overlay') {
        if (cfg.releaseHintEnabled && !cfg.expandOnDrag && !this.expanded) {
          if (releaseReveal >= this.pullThresholdPx && this.listAtTop()) {
            this.animateExpand();
          } else if (releaseReveal > 0.5) {
            this.animateOverlaySnap(0, () => {
              this.storyVisible = false;
              this.expanded = false;
            });
          } else {
            this.animateCollapse();
          }
          return;
        }
        if (!this.lastDragDown && this.reveal < this.maxPx - 0.5) {
          if (this.maxPx - this.reveal >= this.pushThresholdPx) this.animateCollapse();
          else this.animateExpand();
        } else if (this.reveal >= this.pullThresholdPx && this.listAtTop()) {
          this.animateExpand();
        } else if (this.reveal > 0.5) {
          this.animateOverlaySnap(0, () => {
            this.storyVisible = false;
            this.expanded = false;
          });
        } else {
          this.animateCollapse();
        }
        return;
      }
      const hiddenPx = this.integratedHiddenPx();
      const visiblePx = this.maxPx - hiddenPx;
      if (this.lastDragDown) {
        if (visiblePx >= this.pullThresholdPx) this.animateExpand();
        else this.animateCollapse();
      } else if (hiddenPx >= this.pushThresholdPx) {
        this.animateCollapse();
      } else {
        this.animateExpand();
      }
    }

    beginTabRefreshSequence() {
      this.stopFling(false);
      this.cancelAutoExpand();
      clearTimeout(this._scrollSettleTimer);
      this._scrollSettleTimer = null;
      this._touch = null;
      this.setGestureActive(false);
      this.cancelAnim();
      this.refreshOffset = 0;
    }

    animateIntegratedTabRefreshSequence() {
      const list = this.listEl();
      if (!list) {
        this.runTabRefresh();
        return;
      }
      const start = list.scrollTop;
      if (start <= 0.5) {
        this.runTabRefresh();
        return;
      }

      this.beginTabRefreshSequence();
      list.style.transform = '';
      this._integratedAnimHiddenPx = null;
      if (this.mode !== 'locked-integrated') this.storyVisible = true;

      const duration = TAB_REFRESH_SEQUENCE_MS;
      const pullTarget = this.refreshIndicatorHeightPx;
      const startedAt = performance.now();

      this.setAnimating(true);
      const tick = (now) => {
        const u = Math.min(1, (now - startedAt) / duration);
        const e = easeOutStandard(u);
        list.scrollTop = Math.round(start * (1 - e));
        this.refreshOffset = pullTarget * e;
        this.bakeIntegratedHiddenPx(Math.min(list.scrollTop, this.maxPx));
        this.expanded = list.scrollTop <= 0.5;
        this.applyVisuals();
        if (u < 1) {
          this._animFrame = requestAnimationFrame(tick);
          return;
        }
        this._animFrame = null;
        this.setAnimating(false);
        this.commitTabRefresh();
      };
      this._animFrame = requestAnimationFrame(tick);
    }

    animateOverlayTabRefreshSequence() {
      const list = this.listEl();
      const listStart = list?.scrollTop ?? 0;
      const revealStart = this.reveal;
      const needScroll = listStart > 4;
      const needExpand = !(this.expanded && this.reveal >= this.maxPx - 0.5);

      if (!needScroll && !needExpand) {
        this.runTabRefresh();
        return;
      }

      this.beginTabRefreshSequence();
      const duration = TAB_REFRESH_SEQUENCE_MS;
      const pullTarget = this.refreshIndicatorHeightPx;
      const revealTarget = this.maxPx;
      const fromReleaseHint = cfg.releaseHintEnabled
        && !cfg.expandOnDrag
        && revealStart < this.maxPx - 0.5;
      const startedAt = performance.now();

      this.setAnimating(true);
      const tick = (now) => {
        const u = Math.min(1, (now - startedAt) / duration);
        const e = easeOutStandard(u);
        if (needScroll && list) list.scrollTop = Math.round(listStart * (1 - e));
        if (needExpand) {
          this.reveal = revealStart + (revealTarget - revealStart) * e;
          this.slideProgress = fromReleaseHint ? 0 : clamp01(this.reveal / this.maxPx);
          this.storyVisible = this.reveal > 0.5;
          if (u >= 1) {
            this.expanded = true;
            this.hideReleaseHint();
          }
        }
        this.refreshOffset = pullTarget * e;
        this.applyVisuals();
        if (u < 1) {
          this._animFrame = requestAnimationFrame(tick);
          return;
        }
        this._animFrame = null;
        this.setAnimating(false);
        this.commitTabRefresh();
      };
      this._animFrame = requestAnimationFrame(tick);
    }

    scheduleRefreshComplete() {
      clearTimeout(this._refreshCompleteTimer);
      this._refreshCompleteTimer = setTimeout(() => {
        this._refreshCompleteTimer = null;
        this.animateValue(
          () => this.refreshOffset,
          (v) => { this.refreshOffset = Math.max(0, Math.min(this.refreshIndicatorHeightPx, v)); },
          0,
          MOTION.settleBackMs,
          () => {
            this.isRefreshing = false;
            this.refreshLooping = false;
            if (ADD_UNREAD_STORIES_ON_REFRESH) addUnreadStoriesFromRefresh();
            this.applyVisuals();
          },
          { affectsReveal: false },
        );
      }, cfg.refreshDurationMs ?? 1200);
    }

    commitTabRefresh() {
      this._refreshVisualCache = { height: -1, progress: -1, opacity: -1, refreshing: false };
      this.isRefreshing = true;
      this.refreshLooping = true;
      this.refreshOffset = this.refreshIndicatorHeightPx;
      this.applyVisuals();
      this.scheduleRefreshComplete();
    }

    runTabRefresh() {
      if (this.isRefreshing) return;
      this._refreshVisualCache = { height: -1, progress: -1, opacity: -1, refreshing: false };
      const pullTarget = this.refreshIndicatorHeightPx;
      this.isRefreshing = true;
      this.refreshLooping = true;
      this.applyVisuals();
      if (this.refreshOffset >= pullTarget - 0.5) {
        this.refreshOffset = pullTarget;
        this.applyVisuals();
        this.scheduleRefreshComplete();
        return;
      }
      this.animateValue(
        () => this.refreshOffset,
        (v) => {
          this.refreshOffset = Math.max(0, Math.min(this.refreshMaxPullPx, v));
        },
        pullTarget,
        TAB_REFRESH_PULL_MS,
        () => {
          this.refreshOffset = pullTarget;
          this.applyVisuals();
          this.scheduleRefreshComplete();
        },
        { affectsReveal: false, easing: easeOutStandard },
      );
    }

    triggerInboxTabRefresh() {
      if (showFeed || this.isRefreshing || this.isAnimating) return;
      const phase = this.inboxTabRefreshPhase();
      if (phase === 'expanded') {
        this.beginTabRefreshSequence();
        this.runTabRefresh();
        return;
      }
      if (this.isIntegratedMode() || this.mode === 'locked-integrated') {
        this.animateIntegratedTabRefreshSequence();
        return;
      }
      this.animateOverlayTabRefreshSequence();
    }

    settleRefresh() {
      if (this.isRefreshing) return;
      this._refreshVisualCache = { height: -1, progress: -1, opacity: -1, refreshing: false };
      const shouldRefresh = this.refreshOffset >= this.refreshThresholdPx;
      if (!shouldRefresh) {
        this.animateValue(
          () => this.refreshOffset,
          (v) => { this.refreshOffset = Math.max(0, Math.min(this.refreshMaxPullPx, v)); },
          0,
          MOTION.settleBackMs,
          undefined,
          { affectsReveal: false },
        );
        return;
      }
      this.runTabRefresh();
    }

    beginGesture(clientX, clientY) {
      this.stopFling(false);
      clearTimeout(this._autoExpandTimer);
      this._autoExpandTimer = null;
      this.cancelAnim();
      this.syncOverlayGestureState();
      this.pushAccum = 0;
      this.clearTextSelection();
      this.setGestureActive(true);
      this._touch = {
        startX: clientX,
        startY: clientY,
        lastX: clientX,
        lastY: clientY,
        totalDx: 0,
        totalDy: 0,
        axis: null,
        moved: false,
        startedExpanded: this.expanded,
        startedHeight: this.currentStoryHeight(),
        startedRefresh: this.refreshOffset,
        startScroll: this.listEl()?.scrollTop ?? 0,
        peakReveal: this.reveal,
        velocityY: 0,
        lastVelocityAt: performance.now(),
        canFling: false,
      };
    }

    lockGestureAxis() {
      if (!this._touch || this._touch.axis) return;
      const { totalDx, totalDy, startX, startY } = this._touch;
      if (Math.abs(totalDx) < GESTURE_AXIS_LOCK_PX && Math.abs(totalDy) < GESTURE_AXIS_LOCK_PX) return;
      if (Math.abs(totalDx) > Math.abs(totalDy) && this.isPointInSkylight(startX, startY)) {
        this._touch.axis = 'x';
        this.clearTextSelection();
        return;
      }
      this._touch.axis = 'y';
    }

    scrollSkylightBy(deltaX) {
      const row = this.skylightRowEl();
      if (!row || deltaX === 0) return;
      const maxScroll = Math.max(0, row.scrollWidth - row.clientWidth);
      row.scrollLeft = Math.max(0, Math.min(maxScroll, row.scrollLeft - deltaX));
    }

    moveGesture(deltaX, deltaY) {
      this.ensureGestureReady();
      if (!this._touch || this.isRefreshing || this.isAnimating) return;
      this._touch.totalDx = (this._touch.totalDx ?? 0) + deltaX;
      this._touch.totalDy = (this._touch.totalDy ?? 0) + deltaY;
      if (Math.abs(deltaX) > 0.01 || Math.abs(deltaY) > 0.01) {
        this._touch.moved = true;
      }
      this.lockGestureAxis();
      if (this._touch.axis === 'x') {
        this.scrollSkylightBy(deltaX);
        return;
      }
      if (this._touch.axis === null) return;
      this.moveBy(deltaY);
    }

    moveBy(deltaY) {
      if (this.isRefreshing || this.isAnimating) return true;
      this.recordGestureVelocity(deltaY);
      const list = this.listEl();
      const down = deltaY > 0;
      if (down) beginAllReadHintGestureBaseline();
      this.lastDragDown = down;
      if (down) this.pushAccum = 0;

      if (this.isIntegratedMode()) {
        const hiddenPx = this.integratedHiddenPx();
        if (this.usesIntegratedSlideReveal()) {
          if (!this.expanded && this.listAtTop() && down) {
            const hintOffset = allReadHintGestureOffsetPx;
            const visualCurrent = this.integratedRevealPx + hintOffset;
            const visualNext = Math.min(this.maxPx, visualCurrent + deltaY);
            const next = hintOffset > 0
              ? Math.max(0, visualNext - hintOffset)
              : Math.min(this.maxPx, this.integratedRevealPx + deltaY);
            const used = hintOffset > 0
              ? visualNext - visualCurrent
              : next - this.integratedRevealPx;
            this.integratedRevealPx = next;
            this.storyVisible = next > 0.5;
            if (visualNext >= this.maxPx - 0.5 || next >= this.maxPx - 0.5) {
              this.integratedRevealPx = this.maxPx;
              this.expanded = true;
              clearAllReadHint();
              const remaining = hintOffset > 0 ? 0 : Math.max(0, deltaY - used);
              if (remaining > 0 && cfg.chainRefreshAfterExpand) {
                this.refreshOffset = Math.min(this.refreshMaxPullPx, this.refreshOffset + remaining);
              }
            }
            this.applyVisuals();
            return true;
          }
          if (!this.expanded && this.integratedRevealPx > 0.5 && !down) {
            this.integratedRevealPx = Math.max(0, this.integratedRevealPx + deltaY);
            this.storyVisible = this.integratedRevealPx > 0.5;
            this.applyVisuals();
            return true;
          }
        }
        if (hiddenPx <= 0.5 && down) {
          this.applyRefreshPull(deltaY);
          return true;
        }
        if (this.refreshOffset > 0 && !down) {
          this.applyRefreshPull(deltaY);
          return true;
        }
        if (this.integratedCollapseOnDrag(deltaY)) return true;
        if (list) {
          this.scrollListBy(-deltaY);
          this.clampIntegratedPullDuringDrag(down);
          this.expanded = this.integratedExpanded();
          this.applyVisuals();
          if (this._touch) this._touch.canFling = true;
        }
        return true;
      }

      if (this.expanded && this.listAtTop() && down) {
        this.applyRefreshPull(deltaY);
        return true;
      }
      if (this.refreshOffset > 0 && !down) {
        this.applyRefreshPull(deltaY);
        return true;
      }
      if (!this.expanded && this.reveal > 0.5 && !down) {
        this.reveal = Math.max(0, this.reveal + deltaY);
        if (this.isReleaseHintPullPhase()) {
          this.storyVisible = false;
          this.slideProgress = 0;
        } else {
          this.slideProgress = clamp01(this.reveal / this.maxPx);
          this.storyVisible = this.reveal > 0.5;
        }
        this.applyVisuals();
        return true;
      }
      if (!this.expanded && this.listAtTop() && down) {
        if (cfg.chainRefreshAfterExpand && cfg.expandOnDrag) {
          const current = this.reveal;
          const hintOffset = allReadHintGestureOffsetPx;
          const visualCurrent = current + hintOffset;
          const visualNext = Math.min(this.maxPx, visualCurrent + deltaY);
          const next = hintOffset > 0
            ? Math.max(0, visualNext - hintOffset)
            : Math.min(this.maxPx, current + deltaY);
          this.reveal = next;
          this.storyVisible = next > 0.5;
          this.slideProgress = clamp01(next / this.maxPx);
          const used = hintOffset > 0 ? visualNext - visualCurrent : next - current;
          const remaining = hintOffset > 0 ? 0 : Math.max(0, deltaY - used);
          if (visualNext >= this.maxPx - 0.5 || next >= this.maxPx - 0.5) {
            this.reveal = this.maxPx;
            this.storyVisible = true;
            this.expanded = true;
            clearAllReadHint();
            if (remaining > 0) {
              this.refreshOffset = Math.min(this.refreshMaxPullPx, this.refreshOffset + remaining);
            }
          }
        } else {
          const pullDamp = this.pullDampingFactor();
          this.reveal = Math.min(this.maxPullPx, this.reveal + deltaY * pullDamp);
          if (this._touch) {
            this._touch.peakReveal = Math.max(this._touch.peakReveal ?? this.reveal, this.reveal);
          }
          if (this.isReleaseHintPullPhase()) {
            this.storyVisible = false;
            this.slideProgress = 0;
          } else {
            this.slideProgress = clamp01(this.reveal / this.maxPx);
            this.storyVisible = this.reveal > 0.5;
          }
          if (cfg.expandOnDrag && this.reveal >= this.pullThresholdPx) {
            this.animateExpand();
          }
        }
        this.applyVisuals();
        return true;
      }
      if (this.expanded && !down) {
        this.pushAccum += -deltaY;
        if (this._touch && this.pushAccum >= this.pushThresholdPx) {
          this._touch.requestCollapse = true;
        }
        this.applyVisuals();
        return true;
      }
      if (list) {
        this.scrollListBy(-deltaY);
        if (this._touch) this._touch.canFling = true;
      }
      return true;
    }

    endGesture(releaseVelocityY) {
      this.ensureGestureReady();
      if (this._touch?.axis === 'x') {
        this._touch = null;
        this.setGestureActive(false);
        return;
      }
      const requestCollapse = this._touch?.requestCollapse === true;
      const releaseReveal = this.overlayReleaseReveal();
      const velocityY = Number.isFinite(releaseVelocityY)
        ? releaseVelocityY
        : (this._touch?.velocityY ?? 0);
      const canFling = this._touch?.canFling ?? false;
      const shouldFling = canFling &&
        Math.abs(velocityY) >= FLING_MIN_VELOCITY &&
        !this.isRefreshing &&
        !this.isAnimating;
      const settleOverlayRelease = this.mode === 'overlay'
        && !this.expanded
        && releaseReveal > 0.5;

      if (requestCollapse && !this.isRefreshing) {
        this.stopFling(false);
        clearTimeout(this._wheelSettleTimer);
        this._wheelSettleTimer = null;
        this._touch = null;
        this.setGestureActive(false);
        if (this.mode === 'integrated') {
          this.animateIntegratedCollapse();
        } else {
          this.animateCollapse();
        }
        return;
      }

      if (this.refreshOffset > 0.5) {
        this.settleRefresh();
      } else if (settleOverlayRelease) {
        this.settleReveal(releaseReveal);
      } else if (this.mode === 'overlay' && this.expanded && this.reveal < this.maxPx - 0.5) {
        this.settleReveal(releaseReveal);
      } else if (this.mode === 'integrated' && !cfg.lockStoryExpanded && !shouldFling) {
        const hidden = this.integratedHiddenPx();
        if (hidden > 0.5 && hidden < this.maxPx - 0.5) {
          this.settleReveal();
        }
      }

      if (
        (this.mode === 'overlay' || (this.mode === 'integrated' && !cfg.lockStoryExpanded))
        && this.expanded
        && velocityY <= -this.flingCollapseVelocityPx()
        && !this.isRefreshing
        && !this.isAnimating
      ) {
        this.stopFling(false);
        clearTimeout(this._wheelSettleTimer);
        this._wheelSettleTimer = null;
        this._touch = null;
        this.animateCollapse();
        this.setGestureActive(false);
        return;
      }

      this._touch = null;
      this.setGestureActive(false);

      const blockFling = settleOverlayRelease;
      if (shouldFling && !blockFling && !this.isAnimating) {
        this.startListFling(velocityY);
      }
    }

    clearTextSelection() {
      const sel = window.getSelection?.();
      if (sel?.rangeCount > 0) sel.removeAllRanges();
    }

    setGestureActive(on) {
      document.body.classList.toggle('is-gesture-active', on);
      if (!on) this.clearTextSelection();
    }

    handleTouchStart(event) {
      if (event.touches.length !== 1) return;
      const touch = event.touches[0];
      this._activeTouchId = touch.identifier;
      this.beginGesture(touch.clientX, touch.clientY);
    }

    handleTouchMove(event) {
      const touch = this.getActiveTouch(event.touches);
      if (!touch || !this._touch) return;
      const dx = touch.clientX - this._touch.lastX;
      const dy = touch.clientY - this._touch.lastY;
      event.preventDefault();
      this.moveGesture(dx, dy);
      this._touch.lastX = touch.clientX;
      this._touch.lastY = touch.clientY;
    }

    handleTouchEnd(event) {
      if (this._activeTouchId != null && this.getActiveTouch(event.touches)) return;
      this._activeTouchId = null;
      this.handleEnd();
    }

    getActiveTouch(touches) {
      if (!touches || touches.length === 0) return null;
      if (this._activeTouchId == null) return touches[0];
      for (let i = 0; i < touches.length; i += 1) {
        if (touches[i].identifier === this._activeTouchId) return touches[i];
      }
      return null;
    }

    abortTouchStream() {
      this._touch = null;
      this._activeTouchId = null;
      this.setGestureActive(false);
    }

    handlePointerStart(event) {
      if (event.pointerType === 'touch') return;
      if (this.isPointInSkylight(event.clientX, event.clientY)) {
        event.preventDefault();
      }
      this.beginGesture(event.clientX, event.clientY);
    }

    handlePointerMove(event) {
      if (!this._touch || event.pointerType === 'touch') return;
      const dx = event.clientX - this._touch.lastX;
      const dy = event.clientY - this._touch.lastY;
      const moved = Math.hypot(event.clientX - this._touch.startX, event.clientY - this._touch.startY);
      if (moved > 1) {
        event.preventDefault();
      }
      if (!this._touch.moved && moved <= 2) {
        return;
      }
      this.moveGesture(dx, dy);
      this._touch.lastX = event.clientX;
      this._touch.lastY = event.clientY;
    }

    handleEnd() {
      if (!this._touch) return;
      this.endGesture();
    }

    handlePreviewGestureStart(clientX, clientY) {
      this.beginGesture(clientX, clientY);
    }

    handlePreviewGestureMove(clientX, clientY, dy, dx) {
      if (!this._touch) return;
      if (Math.abs(dy) < 0.01 && Math.abs(dx) < 0.01) return;
      this.moveGesture(dx, dy);
      this._touch.lastX = clientX;
      this._touch.lastY = clientY;
    }

    handlePreviewGestureEnd(velocityY) {
      if (!this._touch) return;
      if (Number.isFinite(velocityY) && Math.abs(velocityY) > Math.abs(this._touch.velocityY ?? 0)) {
        this._touch.velocityY = velocityY;
      }
      this.endGesture(this._touch.velocityY ?? 0);
    }

    previewClickAt(x, y) {
      const target = document.elementFromPoint(x, y);
      if (!target) return false;
      const clickable = target.closest(
        'button, a, [role="button"], [data-story-label], [data-skylight-action], '
        + '[data-feed-nav], [data-feed-tab], .story-tap-prev, .story-tap-next, .feed-nav-create',
      );
      if (clickable) {
        clickable.click();
        return true;
      }
      return false;
    }

    handleWheel(event) {
      const delta = -event.deltaY * this.wheelScrollDamping;
      const atTopPull = delta > 0 && this.listAtTop();
      let revealActive = this.refreshOffset > 0;
      if (!revealActive && this.mode === 'overlay') {
        revealActive = atTopPull || this.reveal > 0 || this.expanded;
      }
      if (!revealActive && this.isIntegratedMode()) {
        revealActive = this.usesIntegratedSlideReveal()
          ? (atTopPull || (this.integratedRevealPx > 0.5 && this.integratedRevealPx < this.maxPx - 0.5))
          : atTopPull && this.integratedHiddenPx() <= 0.5;
      }
      if (!revealActive) {
        event.preventDefault();
        const wheelDelta = event.deltaY * this.wheelScrollDamping;
        this.scrollListBy(wheelDelta);
        if (wheelDelta < 0) this.clampIntegratedPullDuringDrag(true);
        if (this.mode === 'integrated') {
          this.expanded = this.integratedExpanded();
          this.applyVisuals();
        }
        return;
      }
      event.preventDefault();
      if (!this.isRefreshing && !this.isAnimating && this._animFrame && this.refreshOffset > 0.5 && delta < 0) {
        this.cancelAnim();
      }
      this.moveBy(delta);
      clearTimeout(this._wheelSettleTimer);
      if (!this.isRefreshing && delta > 0 && this.refreshOffset >= this.refreshThresholdPx) {
        this.endGesture();
        return;
      }
      const idleMs = this.refreshOffset > 0 ? REFRESH_WHEEL_IDLE_MS : WHEEL_IDLE_MS;
      this._wheelSettleTimer = setTimeout(() => this.endGesture(), idleMs);
    }
  }

  const reveal = new DesignLabStoryRevealController();

  function allReadHintBlocksAutoExpand() {
    return cfg.allReadCollapsedHintEnabled === true
      && allReadHintActive
      && !reveal.isSkylightSubstantiallyOpen();
  }

  function shouldBackgroundCollapseAllRead() {
    return allReadAutoCollapseEnabled()
      && !IS_V3
      && allReadAutoCollapseEligible
      && allOtherStoriesRead()
      && !manualCollapseAfterAllRead
      && reveal.isSkylightSubstantiallyOpen();
  }

  function shouldMarkDesktopReturnAllReadCollapse() {
    return IS_V3
      && desktopEnabled()
      && allReadAutoCollapseEnabled()
      && allReadAutoCollapseEligible
      && allOtherStoriesRead()
      && !manualCollapseAfterAllRead
      && reveal.isSkylightSubstantiallyOpen();
  }

  function shouldCollapseAllReadAfterDesktopReturn() {
    return IS_V3
      && allReadCollapsePendingAfterDesktopReturn
      && allReadAutoCollapseEnabled()
      && allOtherStoriesRead()
      && !manualCollapseAfterAllRead
      && reveal.isSkylightSubstantiallyOpen();
  }
  window.__storyRevealController = reveal;

  function setSystemBarMode(mode) {
    els.phone?.classList.toggle('feed-mode', mode === 'feed');
    els.phone?.classList.toggle('inbox-mode', mode === 'inbox');
    els.phone?.classList.toggle('desktop-mode', mode === 'desktop');
  }

  function setBottomNavActive(layer, tab) {
    if (!layer) return;
    layer.querySelectorAll('.feed-nav-cell[data-feed-nav]').forEach((btn) => {
      btn.classList.toggle('active', tab != null && btn.dataset.feedNav === tab);
    });
  }

  function syncInboxTabUnreadDot() {
    const showDot = cfg.inboxTabUnreadDotOnce === true && !inboxTabUnreadDotConsumed;
    document.querySelectorAll('.feed-nav-cell[data-feed-nav="inbox"]').forEach((btn) => {
      btn.classList.toggle('has-inbox-tab-dot', showDot);
    });
  }

  function syncFeedVideo() {
    const video = document.getElementById('feedVideo');
    if (!video) return;
    const shouldPlay = !showDesktop && showFeed && !els.layerFeed?.classList.contains('is-hidden');
    if (shouldPlay) {
      video.play().catch(() => {});
    } else {
      video.pause();
    }
  }

  function setupFeedVideo() {
    const video = document.getElementById('feedVideo');
    if (!video || video.dataset.feedReadyBound) return;
    video.dataset.feedReadyBound = '1';
    video.removeAttribute('poster');
    const markReady = () => video.classList.add('is-ready');
    if (video.readyState >= 2) markReady();
    else video.addEventListener('loadeddata', markReady, { once: true });
  }

  function applyDesktopLayer() {
    if (!els.layerDesktop) return;
    els.layerDesktop.classList.toggle('is-active', showDesktop);
    els.layerDesktop.setAttribute('aria-hidden', showDesktop ? 'false' : 'true');
    if (showDesktop) setSystemBarMode('desktop');
  }

  function showInboxLayer() {
    if (cfg.inboxTabUnreadDotOnce === true && !inboxTabUnreadDotConsumed) {
      inboxTabUnreadDotConsumed = true;
      syncInboxTabUnreadDot();
    }
    showDesktop = false;
    applyDesktopLayer();
    setSystemBarMode('inbox');
    showFeed = false;
    els.layerFeed?.classList.add('is-hidden');
    els.layerInbox?.classList.add('is-active');
    setBottomNavActive(els.layerFeed, 'inbox');
    setBottomNavActive(els.layerInbox, 'inbox');
    syncFeedVideo();
    if (allReadCollapsePendingAfterDesktopReturn) {
      if (shouldCollapseAllReadAfterDesktopReturn()) {
        allReadCollapsePendingAfterDesktopReturn = false;
        allReadAutoCollapseEligible = false;
        allReadHintActive = true;
        allReadHintTextSuppressed = false;
        allReadHintGestureOffsetPx = 0;
        reveal.cancelAutoExpand();
        reveal.collapseSilentlyForAllRead();
        return;
      }
      if (!allOtherStoriesRead() || manualCollapseAfterAllRead || !reveal.isSkylightSubstantiallyOpen()) {
        allReadCollapsePendingAfterDesktopReturn = false;
      }
    }
    const shouldAutoExpand = cfg.autoExpandOnEnter === true
      && !cfg.lockStoryExpanded
      && !allReadHintBlocksAutoExpand()
      && (!AUTO_EXPAND_ONCE_PER_RUN || !autoExpandEntryConsumed);
    if (shouldAutoExpand) {
      if (AUTO_EXPAND_ONCE_PER_RUN) autoExpandEntryConsumed = true;
      reveal.prepareAutoExpandOnEnter();
      if (reveal.usesIntegratedSlideReveal()) {
        reveal.cancelAutoExpand();
        reveal._autoExpandTimer = setTimeout(() => {
          reveal._autoExpandTimer = null;
          if (showFeed || reveal._touch) return;
          reveal.autoExpandConsumed = true;
          reveal.animateExpand();
        }, MOTION.autoExpandDelayMs);
        return;
      }
      reveal.scheduleAutoExpand();
      return;
    }
    reveal.cancelAutoExpand();
    reveal.applyVisuals();
  }

  function showFeedLayer(options = {}) {
    if (!options.keepDesktop) {
      showDesktop = false;
      applyDesktopLayer();
    }
    setSystemBarMode('feed');
    const shouldCollapseAllRead = shouldBackgroundCollapseAllRead();
    const feedLayer = els.layerFeed;
    const instantFeed = options.instant === true;
    if ((shouldCollapseAllRead || instantFeed) && feedLayer) {
      feedLayer.style.transition = 'none';
    }
    showFeed = true;
    feedLayer?.classList.remove('is-hidden');
    if ((shouldCollapseAllRead || instantFeed) && feedLayer) {
      void feedLayer.offsetHeight;
    }
    els.layerInbox?.classList.remove('is-active');
    setBottomNavActive(els.layerFeed, 'home');
    setBottomNavActive(els.layerInbox, null);
    syncFeedVideo();
    reveal.cancelAutoExpand();
    if (shouldCollapseAllRead) {
      allReadAutoCollapseEligible = false;
      allReadHintActive = true;
      allReadHintTextSuppressed = false;
      allReadHintGestureOffsetPx = 0;
      reveal.collapseSilentlyForAllRead();
      if (feedLayer) {
        requestAnimationFrame(() => {
          feedLayer.style.transition = '';
        });
      }
    } else if (instantFeed && feedLayer) {
      requestAnimationFrame(() => {
        feedLayer.style.transition = '';
      });
    } else {
      applyAllReadCollapsedHint();
    }
  }

  function renderSkylight() {
    if (!els.skylightRow) return;
    let html = [
      `<button type="button" class="skylight-item" data-skylight-action="create">`,
      `<span class="skylight-avatar-slot">`,
      `<img class="skylight-create-bg" src="${ASSET}inbox_story_create.png" alt="" />`,
      `<span class="skylight-plus-badge">`,
      `<img class="skylight-plus-icon" src="${ASSET}inbox_story_plus.png" alt="" />`,
      `<img class="skylight-plus-stroke" src="${ASSET}inbox_story_plus_stroke.png" alt="" />`,
      `</span></span><span class="skylight-label">Create</span></button>`,
    ].join('');
    skylightOrder.forEach((label) => {
      const meta = skylightStoryMeta.find((s) => s.label === label);
      if (!meta) return;
      const readCls = readLabels.has(label) ? ' read' : '';
      html += [
        `<button type="button" class="skylight-item${readCls}" data-story-label="${label}">`,
        `<span class="skylight-avatar-slot">`,
        `<img class="skylight-ring" src="${ASSET}inbox_story_ring.png" alt="" />`,
        `<img class="skylight-avatar" src="${ASSET}${meta.avatar}" alt="${label}" />`,
        `</span><span class="skylight-label">${label}</span></button>`,
      ].join('');
    });
    els.skylightRow.innerHTML = html;
    bindSkylightClicks();
  }

  function moveStoryToTail(label) {
    skylightOrder = skylightOrder.filter((l) => l !== label).concat(label);
    renderSkylight();
  }

  function markStoryViewed(label) {
    if (readLabels.has(label)) return;
    readLabels.add(label);
    moveStoryToTail(label);
    noteAllReadReached();
  }

  function resetSkylightStoriesToInitial() {
    skylightStoryMeta = INITIAL_SKYLIGHT_META.map((story) => ({ ...story }));
    skylightOrder = INITIAL_SKYLIGHT_META.map(({ label }) => label);
  }

  function normalizeRefreshStory(story) {
    if (!story || !story.label || !story.avatar) return null;
    return {
      label: String(story.label),
      avatar: String(story.avatar),
      timeText: story.timeText ? String(story.timeText) : 'now',
      photos: Array.isArray(story.photos) ? story.photos.map(String).filter(Boolean) : [],
    };
  }

  function addUnreadStoriesFromRefresh() {
    const incoming = Array.isArray(cfg.refreshNewStories)
      ? cfg.refreshNewStories.map(normalizeRefreshStory).filter(Boolean)
      : [];
    if (!incoming.length) return;
    const incomingLabels = [];
    incoming.forEach((story) => {
      incomingLabels.push(story.label);
      readLabels.delete(story.label);
      skylightStoryMeta = skylightStoryMeta.filter(({ label }) => label !== story.label).concat({
        label: story.label,
        avatar: story.avatar,
      });
      if (story.photos.length && !previews[story.label]) {
        previews[story.label] = {
          avatar: story.avatar,
          timeText: story.timeText,
          photos: story.photos,
        };
      }
    });
    skylightOrder = incomingLabels.concat(skylightOrder.filter((label) => !incomingLabels.includes(label)));
    pendingViewedLabel = null;
    clearTimeout(pendingViewedTimer);
    pendingViewedTimer = null;
    resetAllReadAutoCollapseState();
    renderSkylight();
  }

  function commitPendingStoryViewed() {
    clearTimeout(pendingViewedTimer);
    pendingViewedTimer = null;
    if (!pendingViewedLabel) return;
    markStoryViewed(pendingViewedLabel);
    pendingViewedLabel = null;
  }

  function bindSkylightClicks() {
    els.skylightRow?.querySelectorAll('[data-skylight-action="create"]').forEach((btn) => {
      btn.onclick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        openStoryAdd();
      };
    });
    els.skylightRow?.querySelectorAll('[data-story-label]').forEach((btn) => {
      btn.onclick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        openStoryPreview(btn.dataset.storyLabel);
      };
    });
  }

  let previewState = { label: null, index: 0, raf: null, start: 0, count: 0 };

  function buildProgress(count) {
    if (!els.storyProgressRow) return;
    els.storyProgressRow.innerHTML = '';
    for (let i = 0; i < count; i++) {
      const seg = document.createElement('div');
      seg.className = 'story-progress-seg';
      const fill = document.createElement('div');
      fill.className = 'story-progress-fill';
      seg.appendChild(fill);
      els.storyProgressRow.appendChild(seg);
    }
  }

  function updateProgress(progress, index, count) {
    els.storyProgressRow?.querySelectorAll('.story-progress-fill').forEach((fill, i) => {
      if (i < index) fill.style.width = '100%';
      else if (i === index) fill.style.width = `${progress * 100}%`;
      else fill.style.width = '0%';
    });
  }

  function stopPreviewProgress() {
    if (previewState.raf) cancelAnimationFrame(previewState.raf);
    previewState.raf = null;
  }

  function jumpPreviewIndex(nextIndex) {
    const count = previewState.count;
    if (count <= 0) return;
    const target = ((nextIndex % count) + count) % count;
    if (target === previewState.index) return;
    stopPreviewProgress();
    previewState.index = target;
    showPreviewPhoto(previewState.label, target);
    runPreviewProgress(count);
  }

  function runPreviewProgress(count) {
    stopPreviewProgress();
    previewState.count = count;
    previewState.start = performance.now();
    const tick = (now) => {
      const t = Math.min(1, (now - previewState.start) / MOTION.storyDurationMs);
      updateProgress(t, previewState.index, count);
      if (t >= 1) {
        jumpPreviewIndex(previewState.index + 1);
        return;
      }
      previewState.raf = requestAnimationFrame(tick);
    };
    previewState.raf = requestAnimationFrame(tick);
  }

  function showPreviewPhoto(label, index) {
    const data = previews[label];
    if (!data || !els.storyPhoto) return;
    const file = data.photos[index % data.photos.length];
    els.storyPhoto.style.opacity = '0';
    els.storyPhoto.onload = () => {
      els.storyPhoto.style.transition = `opacity ${MOTION.photoCrossfadeMs}ms linear`;
      els.storyPhoto.style.opacity = '1';
    };
    els.storyPhoto.src = `../../shared/assets/story/${file}`;
    updateProgress(0, index, data.photos.length);
    previewState.start = performance.now();
  }

  function openStoryPreview(label) {
    const data = previews[label];
    if (!data || !els.storyPreview) return;
    clearTimeout(pendingViewedTimer);
    pendingViewedLabel = readLabels.has(label) ? null : label;
    previewState.label = label;
    previewState.index = 0;
    if (els.storyPreviewAvatar) els.storyPreviewAvatar.src = `${ASSET}${data.avatar}`;
    if (els.storyPreviewName) els.storyPreviewName.textContent = label;
    if (els.storyPreviewTime) {
      els.storyPreviewTime.textContent = `· ${data.timeText || '3h ago'}`;
    }
    buildProgress(data.photos.length);
    showPreviewPhoto(label, 0);
    setOverlayDarkMode(true);
    els.storyPreview.hidden = false;
    requestAnimationFrame(() => els.storyPreview.classList.add('visible'));
    pendingViewedTimer = setTimeout(commitPendingStoryViewed, MOTION.previewEnterMs + 40);
    runPreviewProgress(data.photos.length);
  }

  function closeStoryPreview() {
    stopPreviewProgress();
    commitPendingStoryViewed();
    els.storyPreview?.classList.remove('visible');
    setOverlayDarkMode(false);
    setTimeout(() => {
      if (els.storyPreview) els.storyPreview.hidden = true;
    }, MOTION.previewExitMs);
  }

  function openStoryAdd() {
    if (!els.storyAddSheet) return;
    setOverlayDarkMode(true);
    els.storyAddSheet.hidden = false;
    requestAnimationFrame(() => els.storyAddSheet.classList.add('open'));
  }

  function closeStoryAdd() {
    if (!els.storyAddSheet) return;
    els.storyAddSheet.classList.remove('open');
    setOverlayDarkMode(false);
    setTimeout(() => {
      els.storyAddSheet.hidden = true;
    }, cfg.storyAddExitMs ?? 260);
  }

  function clearEffectCoverTimer() {
    if (effectCoverTimer) {
      clearTimeout(effectCoverTimer);
      effectCoverTimer = null;
    }
  }

  function resetEffectPresets() {
    document.querySelectorAll('.effect-preset-btn').forEach((btn) => {
      btn.setAttribute('aria-disabled', 'true');
      btn.classList.remove('revealed', 'emerge-active', 'loaded');
      btn.disabled = true;
    });
  }

  function revealEffectCovers() {
    if (effectCoversRevealed) return;
    effectCoversRevealed = true;
    document.querySelectorAll('.effect-preset-btn').forEach((btn) => {
      btn.removeAttribute('aria-disabled');
      btn.disabled = false;
      btn.classList.add('revealed', 'loaded');
      requestAnimationFrame(() => btn.classList.add('emerge-active'));
    });
  }

  function startEffectCoverLoad() {
    if (effectCoverLoadStarted || effectCoversRevealed) return;
    effectCoverLoadStarted = true;
    clearEffectCoverTimer();
    effectCoverTimer = setTimeout(() => {
      effectCoverTimer = null;
      revealEffectCovers();
    }, MOTION.effectCoverLoadMs);
  }

  function openAlbum() {
    if (showAlbum || !els.albumEmergence) return;
    showAlbum = true;
    els.screenAlbum?.classList.add('active');
    els.layerFeed.style.transition = `opacity ${MOTION.feedFadeMs}ms ${EASE.fastOutSlowIn}`;
    els.layerFeed.style.opacity = '0';
    els.layerFeed.style.pointerEvents = 'none';
    els.albumEmergence.classList.remove('closing');
    els.albumEmergence.classList.add('visible', 'open');
    setTimeout(startEffectCoverLoad, MOTION.albumEmergenceMs + 32);
  }

  function closeAlbum() {
    if (!showAlbum || !els.albumEmergence) return;
    showAlbum = false;
    clearEffectCoverTimer();
    effectCoverLoadStarted = false;
    effectCoversRevealed = false;
    resetEffectPresets();
    if (els.albumScroll) els.albumScroll.scrollTop = 0;
    els.layerFeed.style.opacity = '1';
    els.layerFeed.style.pointerEvents = 'auto';
    els.albumEmergence.classList.remove('open');
    els.albumEmergence.classList.add('closing');
    const onClose = (e) => {
      if (e.target !== els.albumEmergence || e.propertyName !== 'opacity') return;
      els.screenAlbum?.classList.remove('active');
      els.albumEmergence.classList.remove('visible', 'closing');
      els.albumEmergence.removeEventListener('transitionend', onClose);
    };
    els.albumEmergence.addEventListener('transitionend', onClose);
  }

  function setupFeedNav() {
    els.layerFeed?.querySelectorAll('.feed-nav-cell[data-feed-nav]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const nav = btn.dataset.feedNav;
        if (nav === 'create') return;
        if (showAlbum) return;
        if (nav === 'inbox') {
          showInboxLayer();
          return;
        }
        showFeedLayer();
        setBottomNavActive(els.layerFeed, nav);
      });
    });
    document.querySelectorAll('.feed-tab[data-feed-tab]').forEach((btn) => {
      btn.addEventListener('click', () => {
        if (showAlbum) return;
        document.querySelectorAll('.feed-tab').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  function setupInboxNav() {
    els.layerInbox?.querySelectorAll('.feed-nav-cell[data-feed-nav]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const nav = btn.dataset.feedNav;
        if (nav === 'home') {
          showFeedLayer();
          return;
        }
        if (nav === 'inbox') {
          if (!showFeed) {
            reveal.triggerInboxTabRefresh();
            return;
          }
          showInboxLayer();
        }
      });
    });
  }

  function desktopEnabled() {
    return cfg.desktopEnabled === true && !!els.layerDesktop;
  }

  function showDesktopLayer() {
    if (!desktopEnabled()) return;
    if (shouldMarkDesktopReturnAllReadCollapse()) {
      allReadCollapsePendingAfterDesktopReturn = true;
    }
    showDesktop = true;
    reveal.cancelAutoExpand();
    reveal.cancelAnim();
    reveal._touch = null;
    reveal._activeTouchId = null;
    clearEffectCoverTimer();
    closeAlbum();
    closeStoryAdd();
    closeStoryPreview();
    applyDesktopLayer();
    syncFeedVideo();
  }

  function hideDesktopAndOpenApp() {
    if (!desktopEnabled()) return;
    showFeedLayer({ keepDesktop: true, instant: true });
    showDesktop = false;
    applyDesktopLayer();
    syncFeedVideo();
  }

  function setupDesktopNav() {
    if (!desktopEnabled()) return;
    els.layerDesktop.querySelectorAll('[data-desktop-app]').forEach((btn) => {
      btn.addEventListener('click', () => {
        if (btn.dataset.desktopApp !== 'tiktok') return;
        hideDesktopAndOpenApp();
      });
    });
  }

  function wrapSkylightRow() {
    if (!els.skylightRow || els.skylightRow.parentElement?.classList.contains('skylight-row-inner')) return;
    const inner = document.createElement('div');
    inner.className = 'skylight-row-inner';
    inner.style.width = '100%';
    els.skylightRow.parentElement.insertBefore(inner, els.skylightRow);
    inner.appendChild(els.skylightRow);
  }

  function resetDemo() {
    showDesktop = false;
    showFeed = VARIANT.startOnFeed;
    autoExpandEntryConsumed = false;
    inboxTabUnreadDotConsumed = false;
    showAlbum = false;
    readLabels = new Set();
    resetSkylightStoriesToInitial();
    resetAllReadAutoCollapseState();
    if (reveal._animFrame) {
      cancelAnimationFrame(reveal._animFrame);
      clearTimeout(reveal._animFrame);
    }
    reveal.cancelAnim();
    reveal._touch = null;
    reveal._activeTouchId = null;
    reveal.setOffset();
    reveal.setAnimating(false);
    clearEffectCoverTimer();
    effectCoverLoadStarted = false;
    effectCoversRevealed = false;
    resetEffectPresets();
    closeAlbum();
    closeStoryAdd();
    closeStoryPreview();
    renderSkylight();
    syncInboxTabUnreadDot();
    reveal.applyVisuals();
    if (showFeed) showFeedLayer();
    else showInboxLayer();
  }

  function init() {
    wrapSkylightRow();
    renderSkylight();
    syncInboxTabUnreadDot();
    setupFeedVideo();
    reveal.bindScroll();
    setupFeedNav();
    setupInboxNav();
    setupDesktopNav();
    els.albumCloseBtn && (els.albumCloseBtn.onclick = closeAlbum);
    els.storyAddClose && (els.storyAddClose.onclick = closeStoryAdd);
    els.storyPreviewClose && (els.storyPreviewClose.onclick = closeStoryPreview);
    els.storyTapPrev && (els.storyTapPrev.onclick = () => jumpPreviewIndex(previewState.index - 1));
    els.storyTapNext && (els.storyTapNext.onclick = () => jumpPreviewIndex(previewState.index + 1));

    if (els.storyAddScroll && els.storyAddCameraNav) {
      els.storyAddScroll.addEventListener('scroll', () => {
        const obscured = els.storyAddScroll.scrollTop >= 104;
        els.storyAddCameraNav.hidden = !obscured;
      });
      els.storyAddCameraNav.onclick = () => els.storyAddScroll.scrollTo({ top: 0, behavior: 'smooth' });
    }

    reveal.applyVisuals();
    if (VARIANT.startOnFeed) showFeedLayer();
    else showInboxLayer();
    applyDesktopLayer();
    syncFeedVideo();

    window.addEventListener('message', (e) => {
      if (e.data?.type === 'skylight:reload') resetDemo();
      if (e.data?.type === 'skylight:exit-to-desktop') showDesktopLayer();
      if (e.data?.type === 'skylight:preview-click') {
        reveal.previewClickAt(Number(e.data.x) || 0, Number(e.data.y) || 0);
      }
      if (e.data?.type === 'skylight:preview-gesture-start') {
        if (showDesktop) return;
        reveal.handlePreviewGestureStart(
          Number(e.data.clientX ?? e.data.x) || 0,
          Number(e.data.clientY ?? e.data.y) || 0,
        );
      }
      if (e.data?.type === 'skylight:preview-gesture-move') {
        if (showDesktop) return;
        reveal.handlePreviewGestureMove(
          Number(e.data.clientX ?? e.data.x) || 0,
          Number(e.data.clientY ?? e.data.y) || 0,
          Number(e.data.dy) || 0,
          Number(e.data.dx) || 0,
        );
      }
      if (e.data?.type === 'skylight:preview-gesture-active') {
        if (showDesktop) return;
        reveal.setGestureActive(!!e.data.active);
      }
      if (e.data?.type === 'skylight:preview-gesture-end') {
        if (showDesktop) return;
        reveal.handlePreviewGestureEnd(Number(e.data.velocityY));
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
