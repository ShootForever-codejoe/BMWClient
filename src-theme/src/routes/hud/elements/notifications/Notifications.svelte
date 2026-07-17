<script lang="ts">
    import {flip} from "svelte/animate";
    import {listen} from "../../../../integration/ws";
    import {fly} from "svelte/transition";
    import Notification from "./Notification.svelte";
    import type {NotificationEvent} from "../../../../integration/events";

    interface TNotification {
        animationKey: number;
        id: number;
        title: string;
        severity: string;
        message: string;
    }

    let notifications: TNotification[] = [];

    function addNotification(title: string, message: string, severity: string) {
        let animationKey = Date.now();
        const id = animationKey;

        if (severity === "ENABLED" || severity === "DISABLED") {
            // Check if there still exists an enable/disable notification for the same module
            const index = notifications.findIndex((n) => n.message === message)
            if (index !== -1) {
                // Set the id of the new notification to the old notification's id.
                // This will make svelte able to animate it correctly
                animationKey = notifications[index].animationKey;

                // Remove the old notification
                notifications.splice(index, 1);
            }
        }

        notifications = [
            {animationKey, id, title, message, severity},
            ...notifications,
        ];

        setTimeout(() => {
            notifications = notifications.filter((n) => n.id !== id);
        }, 3000);
    }

    listen("notification", (e: NotificationEvent) => {
        addNotification(e.title, e.message, e.severity);
    });
</script>

<div class="notifications-container">
    {#each notifications as {title, message, severity, animationKey} (animationKey)}
        <div
                class="notification-card"
                data-severity={severity}
                animate:flip={{ duration: 200 }}
                in:fly={{ x: 30, duration: 200 }}
                out:fly={{ x: 30, duration: 200 }}
        >
            <Notification {title} {message} {severity}/>
        </div>
    {/each}
</div>

<style>
    .notifications-container {
        position: fixed;
        bottom: 16px;
        right: 16px;
        display: flex;
        flex-direction: column-reverse;
        gap: 12px;
        z-index: 1000;
        /* Android 16 状态栏安全区域 */
        padding-top: env(safe-area-inset-top);
    }

    .notification-card {
        background-color: var(--md-sys-color-surface-container-high);
        color: var(--md-sys-color-on-surface);
        border-radius: 28px; /* Android 16 更大的圆角 */
        padding: 12px 20px;
        box-shadow: var(--md-sys-elevation-level3); /* 更深的阴影 */
        transition: all 0.3s cubic-bezier(0.2, 0, 0, 1); /* Android 标准缓动 */
        max-width: 360px;
        word-wrap: break-word;
        border: 1px solid var(--md-sys-color-outline-variant); /* 细边框 */
        backdrop-filter: blur(24px); /* 毛玻璃效果 */
        transform-origin: right top;
    }

    /* Android 16 色彩系统 */
    :global(body) {
        /* 主要颜色 */
        --md-sys-color-primary: rgb(var(--accent-color));
        --md-sys-color-on-primary: #FFFFFF;
        --md-sys-color-primary-container: rgba(var(--accent-color), 0.22);
        --md-sys-color-on-primary-container: #21005D;
        
        /* 次要颜色 */
        --md-sys-color-secondary: rgba(var(--accent-color), 0.65);
        --md-sys-color-on-secondary: #FFFFFF;
        --md-sys-color-secondary-container: rgba(var(--accent-color), 0.18);
        --md-sys-color-on-secondary-container: #1D192B;
        
        /* 表面颜色 */
        --md-sys-color-surface: rgba(8, 4, 10, 0.94);
        --md-sys-color-surface-dim: rgba(var(--accent-color), 0.08);
        --md-sys-color-surface-bright: rgba(var(--accent-color), 0.22);
        --md-sys-color-surface-container-lowest: rgba(var(--accent-color), 0.07);
        --md-sys-color-surface-container-low: rgba(var(--accent-color), 0.1);
        --md-sys-color-surface-container: rgba(var(--accent-color), 0.13);
        --md-sys-color-surface-container-high: rgba(var(--accent-color), 0.18);
        --md-sys-color-surface-container-highest: rgba(var(--accent-color), 0.24);
        --md-sys-color-on-surface: #1C1B1F;
        --md-sys-color-on-surface-variant: #49454F;
        
        /* 其他颜色 */
        --md-sys-color-outline: rgba(var(--accent-color), 0.55);
        --md-sys-color-outline-variant: rgba(var(--accent-color), 0.3);
        --md-sys-color-shadow: rgb(var(--accent-color));
        --md-sys-color-scrim: #000000;
        
        /* 状态颜色 */
        --md-sys-color-success: #2E7D32;
        --md-sys-color-warning: #ED6C02;
        --md-sys-color-error: #B3261E;
        
        /* 海拔阴影 */
        --md-sys-elevation-level1: var(--theme-elevation-1);
        --md-sys-elevation-level2: var(--theme-elevation-2);
        --md-sys-elevation-level3: var(--theme-elevation-3);
        --md-sys-elevation-level4: var(--theme-elevation-4);
        --md-sys-elevation-level5: var(--theme-elevation-5);
    }

    /* Android 16 深色模式 */
    @media (prefers-color-scheme: dark) {
        :global(body) {
            /* 主要颜色 */
            --md-sys-color-primary: rgb(var(--accent-color));
            --md-sys-color-on-primary: #381E72;
            --md-sys-color-primary-container: rgba(var(--accent-color), 0.28);
            --md-sys-color-on-primary-container: #EADDFF;
            
            /* 次要颜色 */
            --md-sys-color-secondary: rgba(var(--accent-color), 0.65);
            --md-sys-color-on-secondary: #332D41;
            --md-sys-color-secondary-container: rgba(var(--accent-color), 0.18);
            --md-sys-color-on-secondary-container: #E8DEF8;
            
            /* 表面颜色 */
            --md-sys-color-surface: rgba(8, 4, 10, 0.94);
            --md-sys-color-surface-dim: rgba(var(--accent-color), 0.08);
            --md-sys-color-surface-bright: rgba(var(--accent-color), 0.22);
            --md-sys-color-surface-container-lowest: rgba(var(--accent-color), 0.07);
            --md-sys-color-surface-container-low: rgba(var(--accent-color), 0.1);
            --md-sys-color-surface-container: rgba(var(--accent-color), 0.13);
            --md-sys-color-surface-container-high: rgba(var(--accent-color), 0.18);
            --md-sys-color-surface-container-highest: rgba(var(--accent-color), 0.24);
            --md-sys-color-on-surface: #E6E0E9;
            --md-sys-color-on-surface-variant: #CAC4D0;
            
            /* 其他颜色 */
            --md-sys-color-outline: rgba(var(--accent-color), 0.55);
            --md-sys-color-outline-variant: rgba(var(--accent-color), 0.3);
            --md-sys-color-shadow: rgb(var(--accent-color));
            --md-sys-color-scrim: #000000;
            
            /* 海拔阴影 */
            --md-sys-elevation-level1: var(--theme-elevation-1);
            --md-sys-elevation-level2: var(--theme-elevation-2);
            --md-sys-elevation-level3: var(--theme-elevation-3);
            --md-sys-elevation-level4: var(--theme-elevation-4);
            --md-sys-elevation-level5: var(--theme-elevation-5);
        }
    }

    /* 不同严重程度的通知样式 */
    .notification-card[data-severity="INFO"] {
        border-right: 4px solid var(--md-sys-color-primary);
    }

    .notification-card[data-severity="SUCCESS"] {
        border-right: 4px solid var(--md-sys-color-success);
    }

    .notification-card[data-severity="WARNING"] {
        border-right: 4px solid var(--md-sys-color-warning);
    }

    .notification-card[data-severity="ERROR"] {
        border-right: 4px solid var(--md-sys-color-error);
    }

    .notification-card[data-severity="ENABLED"],
    .notification-card[data-severity="DISABLED"] {
        background-color: var(--md-sys-color-surface-container);
        border-right: 4px solid var(--md-sys-color-secondary);
    }

    /* 悬停效果 */
    .notification-card:hover {
        transform: translateY(-2px);
        box-shadow: var(--md-sys-elevation-level4);
    }

    /* 触摸反馈 */
    .notification-card:active {
        transform: scale(0.98);
    }
</style>
