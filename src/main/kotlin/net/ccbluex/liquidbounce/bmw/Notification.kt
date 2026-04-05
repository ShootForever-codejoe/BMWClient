package net.ccbluex.liquidbounce.bmw

import net.ccbluex.liquidbounce.event.events.NotificationEvent.Severity
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import net.minecraft.text.Text

fun notifyAsMessage(module: ClientModule?, content: String) {
    if (module == null) {
        mc.player?.sendMessage(Text.of("§7[§eBMW§7] §f$content"), false)
    } else {
        mc.player?.sendMessage(Text.of("§7[§eBMW§7] [§b${module.displayName()}§7] §f$content"), false)
    }
}

fun notifyAsMessage(content: String) {
    notifyAsMessage(null, content)
}

fun notifyAsNotification(module: ClientModule?, content: String, severity: Severity = Severity.INFO) {
    notification(module?.displayName() ?: "BMWClient", Text.of(content), severity)
}

fun notifyAsNotification(content: String, severity: Severity = Severity.INFO) {
    notifyAsNotification(null, content, severity)
}

fun notifyAsMessageAndNotification(module: ClientModule?, content: String, severity: Severity = Severity.INFO) {
    notifyAsMessage(module, content)
    notifyAsNotification(module, content, severity)
}

fun notifyAsMessageAndNotification(content: String, severity: Severity = Severity.INFO) {
    notifyAsMessageAndNotification(null, content, severity)
}

fun ClientModule.displayName(): String {
    if (!ModuleHud.spaceSeperatedNames) return name

    if (name.isEmpty()) return name

    val result = StringBuilder()
    result.append(name[0])

    for (i in 1 until name.length) {
        val currentChar = name[i]
        val previousChar = name[i - 1]

        if (currentChar.isUpperCase() && previousChar.isLowerCase()) {
            result.append(' ')
        }
        result.append(currentChar)
    }

    return result.toString()
}
