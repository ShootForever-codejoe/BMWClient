package net.ccbluex.liquidbounce.features.command.commands.bmw

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandFactory
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.bmw.ModuleIRC

object CommandIRC : CommandFactory {

    override fun createCommand(): Command {
        return CommandBuilder
            .begin("irc")
            .parameter(
                ParameterBuilder
                    .begin<String>("message")
                    .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                    .required()
                    .build()
            )
            .handler { command, args ->
                if (!ModuleIRC.enabled) {
                    throw CommandException(command.result("IRCNotEnabled"))
                }

                ModuleIRC.sendMsg(args[0] as String)
            }
            .build()
    }

}
