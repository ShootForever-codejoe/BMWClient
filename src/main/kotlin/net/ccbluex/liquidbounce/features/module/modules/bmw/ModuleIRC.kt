package net.ccbluex.liquidbounce.features.module.modules.bmw

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.bmw.BMW_SERVER_IP
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.ChatSendEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.dropPort
import net.ccbluex.liquidbounce.utils.client.inGame
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ModuleIRC : ClientModule("IRC", Category.BMW) {

    private object ServerIP : Choice("IP") {
        override val parent: ChoiceConfigurable<*>
            get() = server
    }

    private object ServerCustom : Choice("Custom") {
        override val parent: ChoiceConfigurable<*>
            get() = server

        val serverName by text("ServerName", "")
    }

    private object ServerHeypixel : Choice("Heypixel") {
        override val parent: ChoiceConfigurable<*>
            get() = server
    }

    private object ServerOMG : Choice("OMG") {
        override val parent: ChoiceConfigurable<*>
            get() = server
    }

    private val server = choices(
        "ServerAddress", ServerHeypixel, arrayOf(
            ServerIP,
            ServerCustom,
            ServerHeypixel,
            ServerOMG
        )
    )

    val enabledCheck by boolean("EnabledCheck", true)

    var webSocket: WebSocket? = null
    val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder().url(BMW_SERVER_IP).build()
    val connecting = AtomicBoolean(false)
    val connected = AtomicBoolean(false)

    private var shouldCreateUser = true

    fun createUser() : Boolean {
        if (network.connection.address.toString().split(":").first() == "local") return true
        if (server.activeChoice is ServerHeypixel || server.activeChoice is ServerOMG) {
             if (network.connection.address.toString().dropPort().split("/").last() != "127.0.0.1") {
                 return true
             }
        }

        if (!inGame || webSocket == null) return false

        webSocket!!.send(JsonObject().apply {
            addProperty("func", "create_user")
            addProperty(
                "server",
                when (server.activeChoice) {
                    is ServerIP -> network.connection.address.toString().dropPort().split("/").last()
                    is ServerCustom -> (server.activeChoice as ServerCustom).serverName
                    is ServerHeypixel -> "Heypixel"
                    is ServerOMG -> "OMG"
                    else -> ""
                }
            )
            addProperty("name", player.name.literalString!!)
        }.toString())

        return true
    }

    fun connect() {
        if (connecting.get() || connected.get()) return

        notifyAsMessage("[IRC] 尝试连接服务器……")
        connecting.set(true)

        client.dispatcher.executorService.execute {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connecting.set(false)
                    connected.set(true)
                    notifyAsMessage("[IRC] 连接服务器成功")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val messageJson = JsonParser.parseString(text).asJsonObject
                    when (messageJson.get("func").asString) {
                        "send_msg" -> {
                            notifyAsMessage("[IRC] §a${messageJson.get("name").asString}§f: ${messageJson.get("msg").asString}")
                        }

                        "create_user" -> {
                            val name = messageJson.get("name").asString
                            FriendManager.friends.add(FriendManager.Friend(name, "§a[BMW] §f${name}"))
                        }

                        "remove_user" -> {
                            val name = messageJson.get("name").asString
                            FriendManager.friends.remove(FriendManager.Friend(name, "§a[BMW] §f${name}"))
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    connecting.set(false)
                    connected.set(false)
                    notifyAsMessage("[IRC] 连接已断开")
                    enabled = false
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connecting.set(false)
                    if (connected.get()) {
                        notifyAsMessage("[IRC] 意外与服务器断开连接，状态码：${response?.code ?: "null"}")
                        connected.set(false)
                    } else {
                        notifyAsMessage("[IRC] 连接服务器失败，状态码：${response?.code ?: "null"}")
                    }
                    enabled = false
                }
            })
        }
    }

    fun disconnect() {
        if (!connecting.get() && connected.get()) {
            webSocket?.close(1000, null)
        }
    }

    @Suppress("unused")
    private val chatSendEventHandler = handler<ChatSendEvent> { event ->
        if (event.message.trimStart()[0] != '#') {
            return@handler
        }
        event.cancelEvent()

        if (!connected.get()) {
            notifyAsMessage("[IRC] 发送消息失败，原因：暂未连接服务器，请重启IRC")
            connect()
            return@handler
        }

        val msg = event.message.trimStart().substring(1).trim()
        if (msg.isEmpty()) {
            notifyAsMessage("[IRC] 发送消息失败，原因：内容为空")
            return@handler
        }

        webSocket!!.send(JsonObject().apply {
            addProperty("func", "send_msg")
            addProperty("msg", msg)
        }.toString())
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (connecting.get()) return@tickHandler

        if (!connected.get()) {
            notifyAsMessage("[IRC] 暂未连接服务器，请重启IRC")
            return@tickHandler
        }

        if (inGame) {
            if (shouldCreateUser) shouldCreateUser = false
            else return@tickHandler
        }

        if (!createUser()) {
            shouldCreateUser = true
        }
    }

    @Suppress("unused")
    private val disconnectEventHandler = handler<DisconnectEvent> {
        if (connecting.get()) return@handler

        if (!connected.get()) {
            notifyAsMessage("[IRC] 暂未连接服务器，请重启IRC")
            return@handler
        }

        webSocket?.send(JsonObject().apply {
            addProperty("func", "remove_user")
        }.toString())

        shouldCreateUser = true
    }

    override fun enable() {
        shouldCreateUser = true
        connect()
    }

    override fun disable() {
        disconnect()
    }

}
