package com.mab.aura.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The `AppWidgetProvider` the system talks to for Aura's Home Screen widget. Glance's base receiver forwards
 * the framework's broadcasts (add, update, resize, delete) to [AuraGlanceWidget]; all it has to declare is
 * which widget it hosts. Registered in the manifest against `@xml/aura_widget_info`.
 */
class AuraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuraGlanceWidget()
}
