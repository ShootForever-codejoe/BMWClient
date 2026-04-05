/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.shit

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.events.HeypixelSWKillEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.ModuleAutoL.CHARSET
import java.util.EnumSet

object ModuleAutoInvite : ClientModule("AutoInvite", Category.SHIT) {

    private enum class InviteWhen(override val choiceName: String) : NamedChoice {
        KILL("Kill"),
        DIE("Die")
    }

    private val inviteWhen by multiEnumChoice("InviteWhen", EnumSet.of(InviteWhen.KILL, InviteWhen.DIE))

    private val autoL = tree(object : ToggleableConfigurable(this, "AutoL", false) {
        val delay by int("Delay", 40, 1..200, "ticks")
        val messages by textList("Messages", mutableListOf("废物敢不敢再来一局"))
        val randomStringInEnd by boolean("RandomStringInEnd", true)
    })

    private val autoSing = tree(object : ToggleableConfigurable(this, "AutoSing", true) {
        val delay by int("Delay", 40, 1..200, "ticks")
    })

    private val autoZdW = tree(object : ToggleableConfigurable(this, "AutoZdW", true) {
        val byDelay by boolean("ByDelay", true)
        val delay by int("Delay", 200, 1..1000, "ticks")
        val whenWorldChange by boolean("WhenWorldChange", true)
    })

    private val hideYourMessage by boolean("HideYourMessage", false)

    private var autoLDelay = 0
    private var autoZdWDelay = 0
    private var singDelay = 0
    private var singIndex = 0
    private var waitForMessageTicks = 0
    private var oldMessage = ""

    private val song = arrayOf(
        "珍九鼎食万钱",
        "我要开服圈钱",
        "开个吉吉岛圈钱",
        "把我钱包充填",
        "我要秀智商下限",
        "我还要搞诈骗",
        "圈光你们这群小可爱",
        "我们修不好疾跑",
        "我们圈钱圈到爆",
        "我们要打击黑客",
        "不然圈钱没着落",
        "我们封禁机器码",
        "爱死黑客的麻麻",
        "我们缝合反作弊",
        "四个反作弊才接地气",
        "对着外挂释放洪荒之力",
        "我们追着风格打",
        "因为要圈钱开宝马",
        "我们相信网易摁死所有外挂",
        "Crush the cheats,let's make it right",
        "Earning cash to shine so bright",
        "With hard work, we'll seize the day",
        "Drive a Beemer, pave our way",
        "圈钱卖VIP只要1000元",
        "圈死你们这帮小可爱",
        "这就是吉吉岛，你能怎么搞",
        "敢写外挂就给你们全部抓起来",
        "Even can't fix the sprint, our game's got flaws",
        "Hacking never stop, breaking all the laws",
        "Gotta hit the hackers strike'em with a blow",
        "Without that money's stuck, nowhere to go",
        "We're banning HWID yeah, we play it rough",
        "Killing cheat vibe, think we've done enough",
        "Has 4 anti-cheats make it real tight",
        "Four layers deep, now it feels just right",
        "布吉岛电脑版开服时入账如同大风刮",
        "现在我已经轻而易举随便开上了宝马",
        "如今我轻松驾驭宝马驰骋天涯",
        "对于反作弊问题我们也是十足的专家",
        "反作弊领域我们堪称绝对专家",
        "随随便便打倒花雨庭布吉岛一家独大",
        "横扫花雨庭布吉岛独占鳌头称霸",
        "风格纳纹木糖醇来开挂也是随便拷打",
        "风格纳纹木糖醇开挂照样碾压",
        "摁着外挂打到布吉岛外挂认输叫爸爸",
        "打跪外挂逼得他们认父求饶喊爸",
        "写外挂的地址野爹证也是随便的颁发",
        "外挂作者野爹认证随意颁发",
        "你们这些外挂去别的服务器别来我家",
        "请你们滚去别服休想来我家撒野",
        "damn, no one matter who you are",
        "管你何方神圣统统不在话下",
        "Make heypixel cheats, cops come knock in, you see",
        "制作作弊插件警察立马登门稽查",
        "My death note linked your fate in my script",
        "死亡笔记早已注定你的结局",
        "Mess with my BMW dreams, your fate to be flipped",
        "敢阻我宝马梦让你命运翻车",
        "In the game of fate, never gonna yield",
        "命运博弈中永不低头认输",
        "什么疾跑只有低能儿需要",
        "疾跑功能只有低能才需依靠",
        "我们的 mod随随便便给你们调教",
        "我们的模组随意拿捏你们技巧",
        "我们会写举报举报你就封号",
        "编写举报信让你账号永久封停",
        "测试不了给你妖猫大跌笑尿",
        "测试失败令妖猫大跌耻笑",
        "你妖猫大跌的远控随便给你电脑仙人跳",
        "妖猫远程操控给你电脑设套",
        "后门被发现了我操",
        "后门暴露猝不及防",
        "赶紧发篇文章致歉给外挂完爆",
        "急忙发文道歉反被外挂打爆",
        "沈阳珠海滨州全都弱爆",
        "沈阳珠海滨州全都不堪一击",
        "我们野爹证给外挂 ban到仰天长啸",
        "野爹认证让外挂封禁哀嚎",
        "宝马职业开局送哥附魔金苹果给外挂锤爆",
        "开局附魔金苹果锤爆外挂",
        "击退什么的我们也是根本不会调",
        "击退参数我们从不需调整",
        "连击给人连飞我们检测 cps阻止外挂摇",
        "连击检测CPS杜绝外挂摇",
        "就算 ban绿色了我们也没有任何改变",
        "误封绿色玩家我们毫无愧意",
        "绿色 ban了自己申诉没有悬念",
        "绿色玩家申诉注定徒劳无功",
        "BMW, it’ s my dream ride",
        "宝马是我梦寐以求的座驾",
        "Gotta drive a BMW, gonna cruise with pride",
        "驾驶宝马傲游街道满怀自豪",
        "Making money, drive BMW, scheme so tight",
        "赚钱计划周密宝马随行",
        "所有服务器服主都给我记住",
        "布吉岛已经成为网易的中流砥柱",
        "花雨庭被我们打到不剩底裤",
        "能不能竞争过我们你们心里没点-数",
        "有外挂我们就招聘一群客服",
        "封禁外挂日日夜夜带着我的黑-",
        "去领工资吧客服们钱在我的迈巴赫里",
        "这般员工待遇其他服务器拿什么比",
        "heypixel is the best",
        "never take a player's quest",
        "heypixel is the best",
        "lnvest in us,we're truly blessed",
        "heypixel is the best",
        "join us as a welcome guest",
        "heypixel is the best",
        "fell our zest,we're ahead of the rest",
        "疾跑修不好走路像-",
        "再厉害的玩家也会在这里栽倒",
        "误封也是一点不会改变",
        "绿色玩家被 ban来申诉随时待见",
        "办比赛开挂不举报就不会封号",
        "这样反外挂显得卓有成效",
        "外挂玩家看见直接被恐惧笼罩",
        "不充钱你无权申诉原因无可奉告",
        "那些绿玩就是充钱玩家的游戏体验",
        "被封号了手写保证书少给我犯贱",
        "你们都在说我偷服务端圈钱",
        "无所谓，我的公关证明这都是谣言",
        "无所谓，游戏中我会屏蔽发言",
        "我们会强制闭嘴所有谏言",
        "我们缝合开源插件",
        "把内容充填",
        "随随便便变成我们自研",
        "宝马职业送附魔金苹果劲爆圈钱",
        "不这样怎么才能",
        "珍九鼎食万钱",
        "heypixel is the best",
        "never take a player's quest",
        "heypixel is the best",
        "lnvest in us,we're truly blessed",
        "heypixel is the best",
        "join us as a welcome guest",
        "heypixel is the best",
        "fell our zest,we're ahead of the rest",
        "不这样怎么才能",
        "珍九鼎食万钱",
        "heypixel is the best",
        "never take a player's quest",
        "heypixel is the best",
        "lnvest in us,we're truly blessed",
        "heypixel is the best",
        "join us as a welcome guest",
        "heypixel is the best",
        "fell our zest,we're ahead of the rest"
    )

    override fun onEnabled() {
        autoLDelay = 0
        autoZdWDelay = 0
        singDelay = 0
        singIndex = 0
        waitForMessageTicks = 0
        oldMessage = ""
    }

    private fun sendPartyCommand(content: String) {
        network.sendCommand(content)
        waitForMessageTicks = 20
        oldMessage = content
    }

    private fun randomString() = if (autoL.randomStringInEnd) {
        " <" + (1..10)
            .map { CHARSET.random() }
            .joinToString("") + ">"
    } else {
        ""
    }

    @Suppress("unused")
    private val killHandler = handler<HeypixelSWKillEvent> { event ->
        if (event.killer == player.name.string && InviteWhen.KILL in inviteWhen) {
            sendPartyCommand("zd i ${event.victim}")
        }

        if (event.victim == player.name.string && InviteWhen.DIE in inviteWhen) {
            sendPartyCommand("zd i ${event.killer}")
        }
    }

    @Suppress("unused")
    private val chatReceiveHandler = sequenceHandler<ChatReceiveEvent> { event ->
        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) return@sequenceHandler

        val message = event.message

        if (waitForMessageTicks > 0) {
            waitForMessageTicks--

            if (message.startsWith("队伍不存在") || message.startsWith("您还没有队伍")) {
                network.sendCommand("zd c")
                waitTicks(5)
                network.sendCommand(oldMessage)
            }

            if (hideYourMessage
                && oldMessage.startsWith("pc ")
                && message.startsWith("组队 >${player.name.string}: ${oldMessage.substring(3)}")
            ) {
                event.cancelEvent()
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (autoZdW.enabled && autoZdW.byDelay) {
            if (autoZdWDelay > 0) {
                autoZdWDelay--
            } else {
                sendPartyCommand("zd w")
                autoZdWDelay = autoZdW.delay
                return@tickHandler
            }
        }

        if (autoL.enabled) {
            if (autoLDelay > 0) {
                autoLDelay--
            } else {
                sendPartyCommand("pc ${autoL.messages.random()}${randomString()}")
                autoLDelay = autoL.delay
                return@tickHandler
            }
        }

        if (autoSing.enabled) {
            if (singDelay > 0) {
                singDelay--
            } else {
                sendPartyCommand("pc ${song[singIndex]}")
                singIndex = (singIndex + 1) % song.size
                singDelay = autoSing.delay
                return@tickHandler
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        if (autoZdW.enabled && autoZdW.whenWorldChange) {
            sendPartyCommand("zd w")
        }
    }

}
