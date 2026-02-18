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
        --md-sys-color-primary: #6750A4;
        --md-sys-color-on-primary: #FFFFFF;
        --md-sys-color-primary-container: #EADDFF;
        --md-sys-color-on-primary-container: #21005D;
        
        /* 次要颜色 */
        --md-sys-color-secondary: #625B71;
        --md-sys-color-on-secondary: #FFFFFF;
        --md-sys-color-secondary-container: #E8DEF8;
        --md-sys-color-on-secondary-container: #1D192B;
        
        /* 表面颜色 */
        --md-sys-color-surface: #FFFBFE;
        --md-sys-color-surface-dim: #DED8E1;
        --md-sys-color-surface-bright: #FFFBFE;
        --md-sys-color-surface-container-lowest: #FFFFFF;
        --md-sys-color-surface-container-low: #F7F2FA;
        --md-sys-color-surface-container: #F0EBF4;
        --md-sys-color-surface-container-high: #ECE6F0;
        --md-sys-color-surface-container-highest: #E6E0E9;
        --md-sys-color-on-surface: #1C1B1F;
        --md-sys-color-on-surface-variant: #49454F;
        
        /* 其他颜色 */
        --md-sys-color-outline: #79747E;
        --md-sys-color-outline-variant: #CAC4D0;
        --md-sys-color-shadow: #000000;
        --md-sys-color-scrim: #000000;
        
        /* 状态颜色 */
        --md-sys-color-success: #2E7D32;
        --md-sys-color-warning: #ED6C02;
        --md-sys-color-error: #B3261E;
        
        /* 海拔阴影 */
        --md-sys-elevation-level1: 0 1px 2px rgba(0, 0, 0, 0.3), 0 1px 3px 1px rgba(0, 0, 0, 0.15);
        --md-sys-elevation-level2: 0 1px 2px rgba(0, 0, 0, 0.3), 0 2px 6px 2px rgba(0, 0, 0, 0.15);
        --md-sys-elevation-level3: 0 4px 8px 3px rgba(0, 0, 0, 0.15), 0 1px 3px rgba(0, 0, 0, 0.3);
        --md-sys-elevation-level4: 0 6px 10px 4px rgba(0, 0, 0, 0.15), 0 2px 4px rgba(0, 0, 0, 0.3);
        --md-sys-elevation-level5: 0 8px 12px 6px rgba(0, 0, 0, 0.15), 0 4px 4px rgba(0, 0, 0, 0.3);
    }

    /* Android 16 深色模式 */
    @media (prefers-color-scheme: dark) {
        :global(body) {
            /* 主要颜色 */
            --md-sys-color-primary: #D0BCFF;
            --md-sys-color-on-primary: #381E72;
            --md-sys-color-primary-container: #4F378B;
            --md-sys-color-on-primary-container: #EADDFF;
            
            /* 次要颜色 */
            --md-sys-color-secondary: #CCC2DC;
            --md-sys-color-on-secondary: #332D41;
            --md-sys-color-secondary-container: #4A4458;
            --md-sys-color-on-secondary-container: #E8DEF8;
            
            /* 表面颜色 */
            --md-sys-color-surface: #141218;
            --md-sys-color-surface-dim: #141218;
            --md-sys-color-surface-bright: #3B383E;
            --md-sys-color-surface-container-lowest: #0F0D13;
            --md-sys-color-surface-container-low: #1D1B20;
            --md-sys-color-surface-container: #211F26;
            --md-sys-color-surface-container-high: #2B2930;
            --md-sys-color-surface-container-highest: #36343B;
            --md-sys-color-on-surface: #E6E0E9;
            --md-sys-color-on-surface-variant: #CAC4D0;
            
            /* 其他颜色 */
            --md-sys-color-outline: #938F99;
            --md-sys-color-outline-variant: #49454F;
            --md-sys-color-shadow: #000000;
            --md-sys-color-scrim: #000000;
            
            /* 海拔阴影 */
            --md-sys-elevation-level1: 0 1px 2px rgba(0, 0, 0, 0.3), 0 1px 3px 1px rgba(0, 0, 0, 0.15);
            --md-sys-elevation-level2: 0 1px 2px rgba(0, 0, 0, 0.3), 0 2px 6px 2px rgba(0, 0, 0, 0.15);
            --md-sys-elevation-level3: 0 4px 8px 3px rgba(0, 0, 0, 0.15), 0 1px 3px rgba(0, 0, 0, 0.3);
            --md-sys-elevation-level4: 0 6px 10px 4px rgba(0, 0, 0, 0.15), 0 2px 4px rgba(0, 0, 0, 0.3);
            --md-sys-elevation-level5: 0 8px 12px 6px rgba(0, 0, 0, 0.15), 0 4px 4px rgba(0, 0, 0, 0.3);
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
