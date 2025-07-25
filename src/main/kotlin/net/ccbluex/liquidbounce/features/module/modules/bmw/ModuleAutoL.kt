package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.entity.Entity

object ModuleAutoL : ClientModule("AutoL", Category.BMW) {

    enum class WordPatternChoices(override val choiceName: String) : NamedChoice {
        POEM("Poem"),
        CUSTOM("Custom"),
    }
    private val wordPattern by enumChoice("WordPattern", WordPatternChoices.POEM)
    private val customMessages by textArray("CustomMessages", mutableListOf())
    private val nameInFront by boolean("NameInFront", true)
    private val advertisementInEnd by boolean("AdvertisementInEnd", true)

    private val enemies = mutableListOf<Entity>()

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        enemies.clear()
    }

    @Suppress("unused")
    private val attackEventHandler = handler<AttackEntityEvent> { event ->
        if (event.entity.isPlayer && !enemies.contains(event.entity)) {
            enemies.add(event.entity)
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        enemies.filter { !it.isAlive }.forEach {
            sayL(it)
            enemies.remove(it)
        }
    }

    private val poems = listOf(
        "海内存知己，天涯若比邻",
        "莫愁前路无知己，天下谁人不识君",
        "劝君更尽一杯酒，西出阳关无故人",
        "长风破浪会有时，直挂云帆济沧海",
        "会当凌绝顶，一览众山小",
        "欲穷千里目，更上一层楼",
        "天生我材必有用，千金散尽还复来",
        "路漫漫其修远兮，吾将上下而求索",
        "山重水复疑无路，柳暗花明又一村",
        "不畏浮云遮望眼，自缘身在最高层",
        "洛阳亲友如相问，一片冰心在玉壶",
        "青山一道同云雨，明月何曾是两乡",
        "相见时难别亦难，东风无力百花残",
        "桃花潭水深千尺，不及汪伦送我情",
        "孤帆远影碧空尽，唯见长江天际流",
        "又送王孙去，萋萋满别情",
        "春草明年绿，王孙归不归",
        "少壮不努力，老大徒伤悲",
        "宝剑锋从磨砺出，梅花香自苦寒来",
        "千磨万击还坚劲，任尔东西南北风",
        "纸上得来终觉浅，绝知此事要躬行",
        "黑发不知勤学早，白首方悔读书迟",
        "不经一番寒彻骨，怎得梅花扑鼻香",
        "读书破万卷，下笔如有神",
        "大鹏一日同风起，扶摇直上九万里",
        "沉舟侧畔千帆过，病树前头万木春",
        "千淘万漉虽辛苦，吹尽狂沙始到金",
        "时人不识凌云木，直待凌云始道高",
        "宁可枝头抱香死，何曾吹落北风中",
        "黄沙百战穿金甲，不破楼兰终不还",
        "江东子弟多才俊，卷土重来未可知",
        "仰天大笑出门去，我辈岂是蓬蒿人",
        "人生自古谁无死，留取丹心照汗青",
        "不要人夸颜色好，只留清气满乾坤",
        "问渠那得清如许，为有源头活水来",
        "江山代有才人出，各领风骚数百年",
        "粉身碎骨全不怕，要留清白在人间",
        "雄关漫道真如铁，而今迈步从头越",
        "长风万里送秋雁，对此可以酣高楼",
        "莫道桑榆晚，为霞尚满天",
        "业精于勤荒于嬉，行成于思毁于随",
        "及时当勉励，岁月不待人",
        "愿君学长松，慎勿作桃李",
        "丈夫志四海，万里犹比邻",
        "疾风知劲草，板荡识诚臣",
        "苟利国家生死以，岂因祸福避趋之",
        "落红不是无情物，化作春泥更护花",
        "我自横刀向天笑，去留肝胆两昆仑",
        "一腔热血勤珍重，洒去犹能化碧涛",
        "愿得此身长报国，何须生入玉门关"
    )

    private fun sayL(entity: Entity) {
        var message = when (wordPattern) {
            WordPatternChoices.POEM -> poems.random()
            WordPatternChoices.CUSTOM -> customMessages.random()
        }
        if (nameInFront) {
            message = "${entity.name.literalString!!} $message"
        }
        if (advertisementInEnd) {
            message += " --BMWClient 1053719666"
        }
        network.sendChatMessage(message)
    }

    override fun enable() {
        enemies.clear()
    }

}
