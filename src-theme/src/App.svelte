<script lang="ts">
    import Router, {push} from "svelte-spa-router";
    import ClickGui from "./routes/clickgui/ClickGui.svelte";
    import Hud from "./routes/hud/Hud.svelte";
    import {getVirtualScreen} from "./integration/rest";
    import {cleanupListeners, listenAlways} from "./integration/ws";
    import {onDestroy, onMount} from "svelte";
    import {insertPersistentData} from "./integration/persistent_storage";
    import {isStatic} from "./integration/host";
    import Inventory from "./routes/inventory/Inventory.svelte";
    import Title from "./routes/menu/title/Title.svelte";
    import Multiplayer from "./routes/menu/multiplayer/Multiplayer.svelte";
    import AltManager from "./routes/menu/altmanager/AltManager.svelte";
    import Singleplayer from "./routes/menu/singleplayer/Singleplayer.svelte";
    import ProxyManager from "./routes/menu/proxymanager/ProxyManager.svelte";
    import None from "./routes/none/None.svelte";
    import Disconnected from "./routes/menu/disconnected/Disconnected.svelte";
    import Browser from "./routes/browser/Browser.svelte";
    import {accentColorStore, refreshAccentColor} from "./theme/accentColorStore";
    import {hexToRgbString} from "./integration/util";


    const routes = {
        "/clickgui": ClickGui,
        "/hud": Hud,
        "/inventory": Inventory,
        "/title": Title,
        "/multiplayer": Multiplayer,
        "/altmanager": AltManager,
        "/singleplayer": Singleplayer,
        "/proxymanager": ProxyManager,
        "/none": None,
        "/disconnected": Disconnected,
        "/browser": Browser
    };

    const unsubscribeAccent = accentColorStore.subscribe((color: string) => {
        document.documentElement.style.setProperty("--accent-color", hexToRgbString(color));
        document.documentElement.style.setProperty("--accent-color-hex", color);
    });

    onDestroy(unsubscribeAccent);

    async function changeRoute(name: string) {
        cleanupListeners();
        console.log(`[Router] Redirecting to ${name}`);
        await push(`/${name}`);
    }

    onMount(async () => {
        await insertPersistentData();
        // 启动时先加载持久化设置 再刷新主题色 避免菜单使用默认颜色
        refreshAccentColor();

        if (isStatic) {
            return;
        }

        listenAlways("socketReady", async () => {
            const virtualScreen = await getVirtualScreen();
            await changeRoute(virtualScreen.name || "none");
        });

        listenAlways("virtualScreen", async (event: any) => {
            console.log(`[Router] Virtual screen change to ${event.screenName}`);
            const action = event.action;

            switch (action) {
                case "close":
                    await changeRoute("none");
                    break;
                case "open":
                    await changeRoute(event.screenName || "none");
                    break;
            }
        });

        const virtualScreen = await getVirtualScreen();
        await changeRoute(virtualScreen.name || "none");
    });
</script>

<main>
    <Router {routes}/>
</main>
