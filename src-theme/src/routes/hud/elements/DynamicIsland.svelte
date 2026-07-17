<script lang="ts">
    import { listen } from "../../../integration/ws";
    import { getClientInfo, getSession, getModules } from "../../../integration/rest";
    import type { ClientInfo, Session, PlayerData } from "../../../integration/types";
    import { onMount, tick } from "svelte";
    import { flip } from "svelte/animate";
    import Notification from "./notifications/Notification.svelte";
    import type { NotificationEvent, BlockCountChangeEvent, ClientPlayerDataEvent } from "../../../integration/events";
    import { scale } from "svelte/transition";
    import { expoInOut } from "svelte/easing";

    let clientInfo: ClientInfo | null = null;
    let session: Session | null = null;
    let notification = false;
    let showUsername = false;
    interface TNotification { animationKey: number; id: number; title: string; severity: string; message: string; }
    let notifications: TNotification[] = [];
    let containerEl: HTMLDivElement;
    let notifyWrapEl: HTMLDivElement;
    let infoWrapperEl: HTMLDivElement;

    let activeContentType: 'notifications' | 'blockCounter' = 'notifications';

    let count: number | undefined;
    let playerData: PlayerData | null = null;
    let lastX = 0;
    let lastZ = 0;

    // 外部显示相关
    let fps = 60;
    let currentTime = '14:30';

    function getCurrentTime(): string {
        const now = new Date();
        return now.toLocaleTimeString('zh-CN', {
            hour12: false,
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function updateExternalDisplays() {
        currentTime = getCurrentTime();
    }

    listen("fps", (event: { fps: number }) => {
        fps = event.fps;
    });

    async function updateClientInfo() { clientInfo = await getClientInfo(); }
    async function updateSession() { session = await getSession(); }
    async function canShowUsername() {
        const modules = await getModules();
        return modules.some(module => module.name === "NameProtect" && !module.enabled);
    }

    // 修复后的 Dynamic Island 尺寸计算函数
    async function updateIslandSize() {
        await tick();
        if (!containerEl) return;

        // 获取容器实际的内边距（上下和左右总和）
        const containerStyle = window.getComputedStyle(containerEl);
        const paddingTop = parseFloat(containerStyle.paddingTop) || 0;
        const paddingBottom = parseFloat(containerStyle.paddingBottom) || 0;
        const paddingLeft = parseFloat(containerStyle.paddingLeft) || 0;
        const paddingRight = parseFloat(containerStyle.paddingRight) || 0;
        const containerPaddingV = paddingTop + paddingBottom;
        const containerPaddingH = paddingLeft + paddingRight;

        if (notification) {
            let contentWrapper: HTMLElement | null = null;
            if (activeContentType === 'notifications') {
                contentWrapper = notifyWrapEl;
            } else {
                contentWrapper = containerEl.querySelector('.block-counter-content');
            }
            if (!contentWrapper) return;

            // 获取内容包装器的外边距
            const wrapperStyle = window.getComputedStyle(contentWrapper);
            const marginTop = parseFloat(wrapperStyle.marginTop) || 0;
            const marginBottom = parseFloat(wrapperStyle.marginBottom) || 0;

            // 高度 = 内容包装器实际占用的高度（包含内边距） + 上下外边距 + 容器的上下内边距
            const contentHeight = contentWrapper.offsetHeight;
            const totalHeight = contentHeight + marginTop + marginBottom + containerPaddingV;
            const targetH = Math.min(Math.max(40, totalHeight), 500);

            // 宽度 = 子元素中最大的不换行宽度 + 内容包装器的左右内边距 + 容器的左右内边距
            const childWidths = Array.from(contentWrapper.children).map(child => child.scrollWidth);
            const maxChildWidth = Math.max(0, ...childWidths);
            const wrapperPaddingLeft = parseFloat(wrapperStyle.paddingLeft) || 0;
            const wrapperPaddingRight = parseFloat(wrapperStyle.paddingRight) || 0;
            const totalWidth = maxChildWidth + wrapperPaddingLeft + wrapperPaddingRight + containerPaddingH;
            const targetW = Math.min(Math.max(320, totalWidth), 350);

            containerEl.style.setProperty("--w", `${targetW}px`);
            containerEl.style.setProperty("--h", `${targetH}px`);
        } else {
            // 非通知状态：根据 infoWrapper 调整宽度，高度固定
            if (infoWrapperEl) {
                const infoWidth = infoWrapperEl.scrollWidth; // 使用 scrollWidth 避免受父容器宽度影响
                const totalWidth = infoWidth + containerPaddingH;
                const targetW = Math.min(totalWidth, 450);
                containerEl.style.setProperty("--w", `${targetW}px`);
                containerEl.style.setProperty("--h", "40px");
            }
        }
    }

    function addNotificationForIsland(title: string, message: string, severity: string) {
        let animationKey = Date.now();
        const id = animationKey;
        if (severity === "ENABLED" || severity === "DISABLED") {
            const index = notifications.findIndex((n) => n.message === message);
            if (index !== -1) {
                animationKey = notifications[index].animationKey;
                notifications.splice(index, 1);
            }
        }
        notifications = [{ animationKey, id, title, message, severity }, ...notifications];

        activeContentType = 'notifications';
        notification = true;
        updateIslandSize();

        setTimeout(() => {
            notifications = notifications.filter((n) => n.id !== id);
            if (notifications.length === 0) {
                notification = false;
            }
            updateIslandSize();
        }, 3000);
    }

    function getProgressPercentage(value: number): number { return (value % 64) / 64 * 100; };
    function roundToDecimal(value: number, decimal: number) { return Math.round(value * Math.pow(10, decimal)) / Math.pow(10, decimal) }
    function getBPS(lastX: number, currentX: number, lastZ: number, currentZ: number, tickrate: number): number {
        const deltaX = currentX - lastX;
        const deltaZ = currentZ - lastZ;
        const distanceMoved = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return distanceMoved * tickrate;
    }

    $: if (session || clientInfo) {
        if (!notification) {
            tick().then(updateIslandSize);
        }
    }

    listen("notification", (e: NotificationEvent) => {
        addNotificationForIsland(e.title, e.message, e.severity);
    });
    listen("session", async () => { await updateSession(); });
    listen("blockCountChange", (data: BlockCountChangeEvent) => {
        count = data.count;
        if (count !== undefined && count !== null) {
            activeContentType = 'blockCounter';
            notification = true;
        } else {
            notification = false;
        }
        updateIslandSize();
    });
    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        if (playerData) {
            lastX = playerData.position.x;
            lastZ = playerData.position.z;
        }
        playerData = event.playerData;
    });

    onMount(async () => {
        updateSession();
        updateClientInfo();
        showUsername = await canShowUsername();

        updateExternalDisplays();

        const onResize = () => updateIslandSize();
        window.addEventListener("resize", onResize);

        setInterval(async () => {
            updateSession();
            updateClientInfo();
        }, 1000);

        setInterval(() => {
            updateExternalDisplays();
        }, 1000);

        return () => window.removeEventListener("resize", onResize);
    });
</script>

<div class="hud-container">
    <div class="external-display fps-display">{fps || 60} FPS</div>

    <div class="DynamicIsland">
        <div class="main-content" class:notification bind:this={containerEl}>
            {#if notification}
                {#if activeContentType === 'notifications'}
                    <div class="notifications-wrapper" bind:this={notifyWrapEl}>
                        {#each notifications as { title, message, severity, animationKey } (animationKey)}
                            <div animate:flip={{ duration: 200 }}>
                                {#if severity === "ENABLED" || severity === "DISABLED"}
                                    <!-- 简化版模块开关通知 -->
                                    <div class="simple-notification {severity}">
                                        <div class="notification-icon">
                                            <div class="toggle-switch"></div>
                                        </div>
                                        <div class="notification-content">
                                            <div class="notification-title">Module Toggled</div>
                                            <div class="notification-message">{message}</div>
                                        </div>
                                    </div>
                                {:else}
                                    <Notification {title} {message} {severity} />
                                {/if}
                            </div>
                        {/each}
                    </div>

                {:else if activeContentType === 'blockCounter' && count !== undefined}
                    <div class="block-counter-content" in:scale={{ duration: 500, easing: expoInOut }}>
                        <div class="header">
                            <span class="accent">scaffold</span>
                        </div>
                        <div class="info">
                            {count} blocks left
                            {#if playerData}
                                | {roundToDecimal(getBPS(lastX, playerData.position.x, lastZ, playerData.position.z, 20), 2)}m/s
                            {/if}
                        </div>
                        <div class="progress-bar">
                            <div class="progress" style="width: {getProgressPercentage(count)}%"></div>
                        </div>
                    </div>
                {/if}
            {:else}
                <div class="info-wrapper" bind:this={infoWrapperEl}>
                    <div class="client-icon">
                        <span class="client-logo" role="img" aria-label="BMWClient"></span>
                    </div>
                    <div class="client-name">BMWClient</div>
                </div>
            {/if}
        </div>
    </div>

    <div class="external-display time-display">{currentTime || '14:30'}</div>
</div>

<style>
    /* 整体 HUD 容器 */
    .hud-container {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 16px;
        width: 100%;
    }

    .external-display {
        font-size: 14px;
        font-weight: 500;
        background: var(--md-sys-color-surface-container);
        padding: 6px 12px;
        border-radius: 16px;
        border: 1px solid var(--md-sys-color-outline-variant);
        box-shadow: var(--md-sys-elevation-level1);
        backdrop-filter: blur(12px);
        min-width: 60px;
        text-align: center;
        transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    }

    .fps-display {
        color: var(--md-sys-color-secondary);
    }

    .time-display {
        color: var(--md-sys-color-on-surface-variant);
    }

    /* 简化版通知样式 */
    .simple-notification {
        background: var(--md-sys-color-surface-container);
        border-radius: 16px;
        padding: 12px 16px;
        font-size: 14px;
        font-weight: 500;
        color: var(--md-sys-color-on-surface);
        box-shadow: var(--md-sys-elevation-level1);
        min-width: 200px;
        display: flex;
        align-items: center;
        gap: 8px;
        height: 40px;
        box-sizing: border-box;
    }

    .simple-notification .notification-icon {
        flex-shrink: 0;
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .simple-notification .toggle-switch {
        width: 24px;
        height: 14px;
        background: var(--md-sys-color-outline-variant);
        border-radius: 7px;
        position: relative;
        display: flex;
        align-items: center;
    }

    .simple-notification .toggle-switch::before {
        content: '';
        width: 12px;
        height: 12px;
        background: var(--md-sys-color-surface);
        border-radius: 50%;
        position: absolute;
        left: 1px;
        top: 1px;
        transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);
    }

    .simple-notification.ENABLED .toggle-switch::before {
        transform: translateX(10px);
        background: var(--md-sys-color-primary);
    }

    .simple-notification .notification-content {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 2px;
        min-width: 0; /* 防止 flex 子项溢出 */
    }

    .simple-notification .notification-title {
        font-size: 12px;
        color: var(--md-sys-color-on-surface-variant);
        font-weight: 400;
    }

    .simple-notification .notification-message {
        font-size: 13px;
        color: var(--md-sys-color-on-surface);
        font-weight: 500;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 180px; /* 限制最大宽度，避免撑爆容器 */
    }

    .simple-notification.ENABLED .notification-message {
        color: var(--md-sys-color-primary);
    }

    .simple-notification.DISABLED .notification-message {
        color: var(--md-sys-color-on-surface-variant);
    }

    /* Android 16 Material Design 3 Dark Theme Variables */
    :global(:root) {
        --md-sys-color-primary: rgb(var(--accent-color));
        --md-sys-color-on-primary: #381E72;
        --md-sys-color-primary-container: rgba(var(--accent-color), 0.28);
        --md-sys-color-on-primary-container: #EADDFF;

        --md-sys-color-secondary: rgba(var(--accent-color), 0.7);
        --md-sys-color-on-secondary: #332D41;
        --md-sys-color-secondary-container: rgba(var(--accent-color), 0.22);
        --md-sys-color-on-secondary-container: #E8DEF8;

        --md-sys-color-surface: rgba(8, 4, 10, 0.94);
        --md-sys-color-surface-container: rgba(var(--accent-color), 0.13);
        --md-sys-color-surface-container-high: rgba(var(--accent-color), 0.18);
        --md-sys-color-surface-container-highest: rgba(var(--accent-color), 0.24);
        --md-sys-color-on-surface: #E6E0E9;
        --md-sys-color-on-surface-variant: #CAC4D0;

        --md-sys-color-outline: rgba(var(--accent-color), 0.55);
        --md-sys-color-outline-variant: rgba(var(--accent-color), 0.28);

        --md-sys-elevation-level1: var(--theme-elevation-1);
        --md-sys-elevation-level2: var(--theme-elevation-2);
        --md-sys-elevation-level3: var(--theme-elevation-3);
        --md-sys-elevation-level4: var(--theme-elevation-4);
        --md-sys-elevation-level5: var(--theme-elevation-5);
    }

    .DynamicIsland {
        color: var(--md-sys-color-on-surface);
    }

    .main-content {
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--md-sys-color-surface-container-high);
        border-radius: 28px;
        padding: 8px;
        width: var(--w, 320px);
        height: var(--h, 40px);
        transition: all 0.4s cubic-bezier(0.2, 0, 0, 1);
        will-change: width, height, transform;
        overflow: hidden;
        box-shadow: var(--md-sys-elevation-level2);
        border: 1px solid var(--md-sys-color-outline-variant);
        backdrop-filter: blur(16px);
        box-sizing: border-box; /* 确保 padding 包含在宽高内 */
    }
    .main-content.notification {
        max-width: 350px;
        max-height: 600px;
        border-radius: 28px;
        animation: md3SmoothExpand 0.28s cubic-bezier(0.2, 0, 0, 1) forwards;
        box-shadow: var(--md-sys-elevation-level4);
        background: var(--md-sys-color-surface);
    }
    .main-content:not(.notification) {
        animation: md3SmoothCollapse 0.3s cubic-bezier(0.2, 0, 0, 1) forwards;
        box-shadow: var(--md-sys-elevation-level1);
    }
    .notifications-wrapper {
        margin-top: 12px;
        width: 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        gap: 8px;
        padding: 0 4px;
        max-width: 320px;
        box-sizing: border-box;
    }
    .info-wrapper {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
    }

    .client-icon {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .client-logo {
        width: 24px;
        height: 24px;
        display: block;
        background: rgb(var(--accent-color));
        -webkit-mask: url("/img/clickgui/icon-client.svg") center / contain no-repeat;
        mask: url("/img/clickgui/icon-client.svg") center / contain no-repeat;
        filter: drop-shadow(0 0 6px rgba(var(--accent-color), 0.24));
    }

    .client-name {
        font-size: 16px;
        font-weight: 600;
        color: var(--md-sys-color-primary);
        letter-spacing: 0.25px;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
        line-height: 1;
        margin-top: 1px;
    }
    .block-counter-content {
        background: var(--md-sys-color-surface-container);
        border-radius: 24px;
        padding: 16px;
        min-width: 200px;
        color: var(--md-sys-color-on-surface);
        width: 100%;
        border: 1px solid var(--md-sys-color-outline-variant);
        box-shadow: var(--md-sys-elevation-level1);
        box-sizing: border-box;
    }
    .block-counter-content .header {
        display: flex;
        align-items: center;
        font-size: 16px;
        font-weight: 500;
        color: var(--md-sys-color-primary);
        letter-spacing: 0.1px;
    }
    .block-counter-content .accent {
        color: var(--md-sys-color-primary);
        font-weight: 600;
    }
    .block-counter-content .info {
        font-size: 14px;
        margin-top: 8px;
        color: var(--md-sys-color-on-surface-variant);
        font-weight: 400;
        line-height: 1.4;
    }
    .block-counter-content .progress-bar {
        margin-top: 12px;
        background: var(--md-sys-color-surface-container-highest);
        border-radius: 6px;
        height: 8px;
        overflow: hidden;
        border: 1px solid var(--md-sys-color-outline-variant);
    }
    .block-counter-content .progress {
        background: linear-gradient(90deg,
        var(--md-sys-color-primary) 0%,
        var(--md-sys-color-secondary) 100%);
        height: 100%;
        transition: width 0.3s cubic-bezier(0.2, 0, 0, 1);
        border-radius: 5px;
    }
    @keyframes md3SmoothExpand {
        0% {
            transform: scale(1);
            box-shadow: var(--md-sys-elevation-level2);
        }
        40% {
            transform: scale(1.02);
            box-shadow: var(--md-sys-elevation-level5);
        }
        100% {
            transform: scale(1);
            box-shadow: var(--md-sys-elevation-level4);
        }
    }

    @keyframes md3SmoothCollapse {
        0% {
            transform: scale(1);
            box-shadow: var(--md-sys-elevation-level4);
        }
        60% {
            transform: scale(0.98);
            box-shadow: var(--md-sys-elevation-level1);
        }
        100% {
            transform: scale(1);
            box-shadow: var(--md-sys-elevation-level2);
        }
    }

    .main-content:hover {
        box-shadow: var(--md-sys-elevation-level3);
    }

    .main-content.notification:hover {
        box-shadow: var(--md-sys-elevation-level5);
    }

    .main-content > * {
        transform: translateZ(0);
        will-change: transform;
    }

    .notifications-wrapper > div {
        animation: md3FadeInUp 0.2s cubic-bezier(0.2, 0, 0, 1) forwards;
    }

    @keyframes md3FadeInUp {
        0% {
            opacity: 0;
            transform: translateY(8px);
        }
        100% {
            opacity: 1;
            transform: translateY(0);
        }
    }

    .Logoimg,
    .icon {
        animation: 3s ease-in-out infinite subtlePulse;
        transform: translateZ(0);
    }

    @keyframes subtlePulse {
        0%,
        100% {
            opacity: 0.85;
            transform: scale(0.995) translateZ(0);
        }
        50% {
            opacity: 1;
            transform: scale(1.005) translateZ(0);
        }
    }
</style>
