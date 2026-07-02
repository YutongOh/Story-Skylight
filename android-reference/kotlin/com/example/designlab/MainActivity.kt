package com.example.designlab

import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bytedance.compose.preview.tiktokenv.TikTokEnvTheme
import com.bytedance.tux.compose.TuxColors
import com.bytedance.tux.compose.TuxTheme
import com.example.designlab.demo.hellotux_als_compose.HelloTuxAlsComposeScene
import com.example.designlab.demo.hellotux_als_xml.HelloTuxAlsXmlScene
import com.example.designlab.demo.hellotux_assem_compose.HelloTuxAssemComposeFragment
import com.example.designlab.demo.hellotux_assem_xml.HelloTuxAssemXmlFragment
import com.example.designlab.demo.hellotux_fragment_compose.HelloTuxFragmentComposeFragment
import com.example.designlab.demo.hellotux_fragment_xml.HelloTuxFragmentXmlFragment
import com.example.designlab.design_assets.I18nCatalogScreen
import com.example.designlab.design_assets.TUXColorCatalogScreen
import com.example.designlab.design_assets.TUXFontCatalogScreen
import com.example.designlab.design_assets.TUXIconCatalogScreen
import com.example.designlab.playgrounds.ai.aimusiccore.AIMusicCorePanelScreen
import com.example.designlab.playgrounds.aigame.AIGameScreen
import com.example.designlab.playgrounds.autopicksheet.AutoPickSheetScreen
import com.example.designlab.playgrounds.abulmv1.AbulmV1Screen
import com.example.designlab.playgrounds.abulmv2.AbulmV2Screen
import com.example.designlab.playgrounds.abulmv4.AbulmV4Screen
import com.example.designlab.playgrounds.creation.FigmaAlbumV4Screen
import com.example.designlab.playgrounds.creation.FigmaMultiPhotoScreen
import com.example.designlab.playgrounds.feed.FeedScreen
import com.example.designlab.playgrounds.figmainbox.*
import com.example.designlab.playgrounds.privacy.PrivacyControlsScreenAgent
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerPlaygrounds()

        val rootComposeView = ComposeView(this)
        rootComposeView.consumeWindowInsets = false
        setContentView(rootComposeView)
        rootComposeView.setContent {
            // ViewInspector 的 Dark Mode 工具通过 `DesignLabUiOverrides.forceDarkMode` 推一个
            // 全局信号过来；这里把它跟系统暗色 OR 起来，作为 effectiveDark 传给 TikTokEnvTheme。
            // 对应 iOS 的 `appWindow.overrideUserInterfaceStyle = .dark`（强制时为 .dark，
            // 否则跟随 .unspecified / 系统）。
            val effectiveDark = DesignLabUiOverrides.forceDarkMode || isSystemInDarkTheme()

            // edge-to-edge 之后，系统 status / nav bar 仍然由 host 决定 icon 颜色。
            // dark 主题 → 浅色 icon（isAppearanceLight* = false），反之亦然。三键导航场景
            // Android 会自动给 nav bar 加对应的 scrim，所以白底蓝按钮的违和感会消失。
            val view = LocalView.current
            LaunchedEffect(effectiveDark) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !effectiveDark
                controller.isAppearanceLightNavigationBars = !effectiveDark
            }

            // i18N 工具：当 forceLanguage != null 时，构造一个覆盖 locale 的 Configuration
            // + 通过 `createConfigurationContext` 派生新 Context，并把它们注入 Compose 树。
            // 这样：
            //  - `stringResource(R.string.x)` 自动按新 locale 解析（前提是 playground 提供了
            //    `values-<lang>/strings.xml`）
            //  - `LocalLayoutDirection` 自动从 ar 等 RTL 语言翻转为 Rtl
            // 对应 iOS `SandboxI18nManager.setLanguage(...)` + `applyLayoutDirection()`。
            val forceLanguage = DesignLabUiOverrides.i18nLanguage
            val origConfig = LocalConfiguration.current
            val origContext = LocalContext.current

            val effectiveConfig = remember(forceLanguage, origConfig) {
                if (forceLanguage == null) origConfig
                else Configuration(origConfig).apply {
                    setLocale(Locale.forLanguageTag(forceLanguage))
                }
            }
            val effectiveContext = remember(forceLanguage, origContext, effectiveConfig) {
                if (forceLanguage == null) origContext
                else origContext.createConfigurationContext(effectiveConfig)
            }
            // 没有 override 时 fall back 到外层布局方向，保持 CompositionLocalProvider
            // 的调用位置不变（见下方注释）。
            val origLayoutDir = LocalLayoutDirection.current
            val effectiveLayoutDir = remember(forceLanguage, origLayoutDir) {
                if (forceLanguage == null) {
                    origLayoutDir
                } else if (TextUtils.getLayoutDirectionFromLocale(
                        Locale.forLanguageTag(forceLanguage)
                    ) == View.LAYOUT_DIRECTION_RTL
                ) {
                    LayoutDirection.Rtl
                } else {
                    LayoutDirection.Ltr
                }
            }

            // ⚠️ 必须始终走同一个 CompositionLocalProvider 调用 —— 不要按 forceLanguage
            // 是否为 null 在 if/else 之间切换。Compose 把两个分支视为不同的 composition
            // group slot，分支翻转时整棵子树（含 AppNavHost 的 `selectedIdx` state）会
            // 被 dispose / 重建，效果就是切换 i18n 时当前 playground 被强制返回 list。
            //
            // 解决：当无 override 时，provide 原值（即 no-op），让 composition 结构保持稳定。
            CompositionLocalProvider(
                LocalConfiguration provides effectiveConfig,
                LocalContext provides effectiveContext,
                LocalLayoutDirection provides effectiveLayoutDir,
            ) {
                TikTokEnvTheme(darkTheme = effectiveDark) {
                    // Color Picker tool reads the active TUX color tokens for closest-token
                    // matching. Snapshot them once per theme change via reflection on TuxColors.
                    SnapshotTuxColorsForPicker()
                    AppNavHost()
                }
            }
        }
        ViewInspectorHost.attach(this)
    }

    private fun registerPlaygrounds() {
        // Registry is process-singleton; Activity.onCreate may run multiple times without process exit.
        PlaygroundRegistry.clear()
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "TUX Color Catalog", category = "Design Assets",
            content = { onBack -> TUXColorCatalogScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "TUX Font Catalog", category = "Design Assets",
            content = { onBack -> TUXFontCatalogScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "TUX Icon Catalog", category = "Design Assets",
            content = { onBack -> TUXIconCatalogScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "I18n Catalog", category = "Design Assets",
            content = { onBack -> I18nCatalogScreen(onBack) }
        ))

        PlaygroundRegistry.register(PlaygroundEntry(
            name = "AI Game", category = "Playgrounds",
            content = { onBack -> AIGameScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "AI Music Panel", category = "Playgrounds",
            content = { onBack -> AIMusicCorePanelScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "AutoPickSheet", category = "Playgrounds",
            content = { onBack -> AutoPickSheetScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Privacy Controls (Agent)", category = "Playgrounds",
            content = { onBack -> PrivacyControlsScreenAgent(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Abulm V1", category = "Playgrounds",
            content = { onBack -> AbulmV1Screen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Abulm V2", category = "Playgrounds",
            content = { onBack -> AbulmV2Screen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Abulm V3", category = "Playgrounds",
            content = { onBack -> FigmaAlbumV4Screen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Abulm V4", category = "Playgrounds",
            content = { onBack -> AbulmV4Screen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "Figma Multi-Photo", category = "Playgrounds",
            content = { onBack -> FigmaMultiPhotoScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "V1 Effect Loading 1.2s", category = "Playgrounds",
            content = { onBack ->
                FeedScreen(
                    onBack = onBack,
                    albumContent = { onAlbumBack, _ ->
                        AbulmV1Screen(onBack = onAlbumBack, manageSystemBars = false)
                    },
                )
            }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "V2 Effect Loading 1.2s", category = "Playgrounds",
            content = { onBack ->
                FeedScreen(
                    onBack = onBack,
                    albumContent = { onAlbumBack, _ ->
                        AbulmV2Screen(onBack = onAlbumBack, manageSystemBars = false)
                    },
                )
            }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "V3 Effect Loading 1.2s", category = "Playgrounds",
            content = { onBack -> FeedScreen(onBack) }
        ))
        PlaygroundRegistry.register(PlaygroundEntry(
            name = "V4 Effect Loading 1.2s", category = "Playgrounds",
            content = { onBack ->
                FeedScreen(
                    onBack = onBack,
                    albumContent = { onAlbumBack, _ ->
                        AbulmV4Screen(onBack = onAlbumBack, manageSystemBars = false)
                    },
                )
            }
        ))

        // HelloTUX 的 6 种交付形态（宿主 × UI 技术）。同一份 HelloTUX UI，差异只在架构接线。
        PlaygroundRegistry.register(PlaygroundEntry.fragment(
            name = "HelloTUX (Fragment + Compose)", category = "App Demo",
        ) { onBack -> HelloTuxFragmentComposeFragment().apply { onClose = onBack } })
        PlaygroundRegistry.register(PlaygroundEntry.fragment(
            name = "HelloTUX (Fragment + XML)", category = "App Demo",
        ) { onBack -> HelloTuxFragmentXmlFragment().apply { onClose = onBack } })
        PlaygroundRegistry.register(PlaygroundEntry.fragment(
            name = "HelloTUX (Assem + Compose)", category = "App Demo",
        ) { onBack -> HelloTuxAssemComposeFragment().apply { onClose = onBack } })
        PlaygroundRegistry.register(PlaygroundEntry.fragment(
            name = "HelloTUX (Assem + XML)", category = "App Demo",
        ) { onBack -> HelloTuxAssemXmlFragment().apply { onClose = onBack } })
        PlaygroundRegistry.register(PlaygroundEntry.scene(
            name = "HelloTUX (ALS + Compose)", category = "App Demo",
        ) { onBack -> HelloTuxAlsComposeScene().apply { onClose = onBack } })
        PlaygroundRegistry.register(PlaygroundEntry.scene(
            name = "HelloTUX (ALS + XML)", category = "App Demo",
        ) { onBack -> HelloTuxAlsXmlScene().apply { onClose = onBack } })

        PlaygroundRegistry.register(PlaygroundEntry.composable(
            name = "Story Skylight V1", category = "Playgrounds",
        ) { onBack ->
            StorySkylightFlowScreen(
                onBack = onBack,
                pullThreshold = 24.dp,
                maxPullDistance = 72.dp,
                startExpanded = true,
                startOnFeed = true,
                enableCreateNavigation = true,
                topDownStoryRevealEnabled = true,
                lockStoryExpanded = true,
            )
        })
        PlaygroundRegistry.register(PlaygroundEntry.composable(
            name = "Story Skylight V2", category = "Playgrounds",
        ) { onBack ->
            StorySkylightFlowScreen(
                onBack = onBack,
                pullThreshold = 0.dp,
                pushThreshold = 12.dp,
                storySlideEnabled = false,
                expandOnDrag = true,
                autoExpandOnEnter = true,
                startOnFeed = true,
                enableCreateNavigation = true,
                chainRefreshAfterExpand = true,
            )
        })
        PlaygroundRegistry.register(PlaygroundEntry.composable(
            name = "Story Skylight V3", category = "Playgrounds",
        ) { onBack ->
            StorySkylightFlowScreen(
                onBack = onBack,
                pullThreshold = 48.dp,
                pushThreshold = 12.dp,
                maxPullDistance = StoryRevealMotion.MaxHeight,
                storySlideEnabled = false,
                expandOnDrag = false,
                startExpanded = true,
                autoExpandOnEnter = true,
                startOnFeed = true,
                enableCreateNavigation = true,
                topDownStoryRevealEnabled = true,
            )
        })
        PlaygroundRegistry.register(PlaygroundEntry.composable(
            name = "Story Skylight V4", category = "Playgrounds",
        ) { onBack ->
            StorySkylightFlowScreen(
                onBack = onBack,
                pullThreshold = 24.dp,
                maxPullDistance = 72.dp,
                autoExpandOnEnter = true,
                startOnFeed = true,
                enableCreateNavigation = true,
                releaseHintEnabled = true,
            )
        })
        // @@PLAYGROUND_REGISTRATION@@
    }
}

@Composable
private fun AppNavHost() {
    val allEntries = remember { PlaygroundRegistry.grouped().flatMap { it.second } }
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        PlaygroundListScreen(
            onNavigate = { entry ->
                val idx = allEntries.indexOf(entry)
                if (idx >= 0) selectedIdx = idx
            }
        )

        Crossfade(
            targetState = selectedIdx,
            animationSpec = tween(durationMillis = 220),
            label = "playground-overlay",
        ) { idx ->
            if (idx != null) {
                val entry = allEntries.getOrNull(idx)
                if (entry != null) {
                    BackHandler {
                        selectedIdx = null
                    }
                    entry.content {
                        selectedIdx = null
                    }
                }
            }
        }
    }
}

/**
 * 反射 [TuxTheme.colors] 的所有 token，把当前主题下的 ARGB 值快照到
 * [DesignLabUiOverrides.tuxColorSnapshot]，供 ViewInspector 的 Color Picker 工具
 * 做"最接近 TUX token"匹配。
 *
 * TuxColors 用的是 `mutableStateOf` 委托存色值；通过 `name + "$delegate"` 拿到字段、
 * 反射读 `MutableState.value` 即可（与 [TUXColorCatalog] 的反射手法一致）。
 *
 * 主题变化（dark / light）时整个 TuxColors 实例换掉 → `LaunchedEffect(themeColors)`
 * 自动重跑 → snapshot 自动跟进。
 */
@Composable
private fun SnapshotTuxColorsForPicker() {
    val themeColors = TuxTheme.colors
    LaunchedEffect(themeColors) {
        DesignLabUiOverrides.tuxColorSnapshot = snapshotTuxColors(themeColors)
    }
}

private val NON_PICKER_COLOR_FIELDS = setOf("isLight", "isHighContrast")

private fun snapshotTuxColors(colors: TuxColors): List<TuxColorTokenSnapshot> {
    return colors::class.java.declaredFields
        .filter {
            it.name.endsWith("\$delegate") &&
                it.name.removeSuffix("\$delegate") !in NON_PICKER_COLOR_FIELDS
        }
        .mapNotNull { field ->
            field.isAccessible = true
            val name = field.name.removeSuffix("\$delegate")
            try {
                @Suppress("UNCHECKED_CAST")
                val state = field.get(colors) as androidx.compose.runtime.MutableState<Color>
                TuxColorTokenSnapshot(name = name, argb = state.value.toArgb())
            } catch (_: Throwable) {
                null
            }
        }
        .sortedBy { it.name }
}
