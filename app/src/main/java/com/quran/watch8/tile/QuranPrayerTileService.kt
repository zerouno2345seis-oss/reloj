package com.quran.watch8.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.quran.watch8.MainActivity

/**
 * Dedicated Wear OS Preset Modes Switcher Tile (Widget)
 * Renders quick interactive tiles for the 5 curated watch faces.
 */
class QuranPrayerTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = buildPresetSwitcherTile()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }

    private fun buildPresetSwitcherTile(): TileBuilders.Tile {
        val root = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF0F172A.toInt()))
                            .build()
                    )
                    .build()
            )

        // Header Title
        val header = Text.Builder()
            .setText("🎨 أوضاع وقوالب الساعة")
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(
                        androidx.wear.protolayout.DimensionBuilders.sp(11f)
                    )
                    .setColor(argb(0xFFF59E0B.toInt()))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(dp(6f))
                            .setBottom(dp(4f))
                            .build()
                    )
                    .build()
            )
            .build()
        root.addContent(header)

        // Top Row: 3 items (المواقيت, المصحف, الساعة)
        val row1 = Row.Builder()
            .setWidth(expand())
            .setHeight(dp(62f))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(buildPresetButton("preset_prayer_strip", "🕌", "المواقيت", 0xFF047857.toInt()))
            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(buildPresetButton("preset_quran_focus", "📖", "المصحف", 0xFF0E7490.toInt()))
            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(buildPresetButton("preset_big_clock", "⏰", "الساعة", 0xFF7C3AED.toInt()))
            .build()
        root.addContent(row1)

        root.addContent(Spacer.Builder().setHeight(dp(4f)).build())

        // Bottom Row: 2 items (الأدوات, اللوني)
        val row2 = Row.Builder()
            .setWidth(expand())
            .setHeight(dp(62f))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(buildPresetButton("preset_smart_tools", "📁", "الأدوات", 0xFFEA580C.toInt()))
            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(buildPresetButton("preset_color_accent", "🎨", "الجمالي", 0xFFEC4899.toInt()))
            .build()
        root.addContent(row2)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                Layout.Builder()
                                    .setRoot(root.build())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildPresetButton(
        presetId: String,
        icon: String,
        label: String,
        bgColor: Int
    ): LayoutElement {
        val clickAction = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                "apply_preset",
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(presetId)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val btnCol = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickAction)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bgColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(10f))
                                    .build()
                            )
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(3f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(icon)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(16f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(10f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(btnCol)
            .build()
    }
}
