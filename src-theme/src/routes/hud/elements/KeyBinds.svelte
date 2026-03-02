<script lang="ts">
    import {onMount} from "svelte";
    import {getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import type {Module} from "../../../integration/types";
    import {UNKNOWN_KEY} from "../../../util/keybind_utils";
    import BindDisplay from "../../clickgui/setting/bind/BindDisplay.svelte";

    // 使用 Svelte 5 Runes
    let modules: Module[] = $state([]);

    async function updateModulesWithBinds() {
        modules = (await getModules()).filter(m => m.enabled && m.keyBind.boundKey !== UNKNOWN_KEY);
    }

    listen("moduleToggle", updateModulesWithBinds);
    listen("valueChanged", async (e) => {
        if (e.value.name === "Bind") {
            await updateModulesWithBinds();
        }
    })

    onMount(async () => {
        await updateModulesWithBinds();
    });
</script>

<div class="md3-widget">
    <div class="header">
        <div class="icon-container">
            <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z"/>
            </svg>
        </div>
        <span class="title">KeyBinds</span>
    </div>

    <div class="list-content">
        {#each modules as m (m.name)}
            <div class="list-item" class:active={m.enabled}>
                <div class="state-layer"></div>
                <div class="item-label">
                    {$spaceSeperatedNames ? convertToSpacedString(m.name) : m.name}
                </div>
                <div class="key-chip" class:active-chip={m.enabled}>
                    <BindDisplay boundKey={m.keyBind.boundKey} modifiers={m.keyBind.modifiers}/>
                </div>
            </div>
        {:else}
            <div class="empty-state">
                <div class="empty-icon">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2z"/>
                    </svg>
                </div>
                <span class="empty-text">No active modules</span>
            </div>
        {/each}
    </div>
</div>

<style lang="scss">
  /* Android 16 / MD3 Design Tokens - Dark Theme */
  :global(body) {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-on-primary: #381E72;
    --md-sys-color-primary-container: #4F378B;
    --md-sys-color-on-primary-container: #EADDFF;

    --md-sys-color-secondary-container: #4A4458;
    --md-sys-color-on-secondary-container: #E8DEF8;

    --md-sys-color-surface: #141218;
    --md-sys-color-surface-container: #211F26;
    --md-sys-color-surface-container-high: #2B2930;
    --md-sys-color-surface-container-highest: #36343B;

    --md-sys-color-on-surface: #E6E0E9;
    --md-sys-color-on-surface-variant: #CAC4D0;
    --md-sys-color-outline-variant: #49454F;

    /* 字体 */
    --md-sys-typescale-body-large: 16px;
    --md-sys-typescale-body-medium: 14px;
    --md-sys-typescale-title-medium: 16px;
  }

  .md3-widget {
    background-color: transparent;
    border-radius: 24px; /* MD3 标准圆角 */
    width: 240px;
    padding-bottom: 8px;
    font-family: "google", "sans-serif";
    display: flex;
    flex-direction: column;
    overflow: hidden;
    /* 透明背景下的阴影调整 */
    box-shadow: 0 1px 3px 1px rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.2);
  }

  .header {
    font-family: "google", "sans-serif";
    display: flex;
    align-items: center;
    padding: 16px 20px 12px 20px;
    gap: 12px;
    color: var(--md-sys-color-on-surface);

    .icon-container {
      font-family: "google", "sans-serif";
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(74, 68, 88);

      svg {
        width: 24px;
        height: 24px;
      }
    }

    .title {
      font-family: "google", "sans-serif";
      font-size: var(--md-sys-typescale-title-medium);
      font-weight: 500;
      letter-spacing: 0.15px;
    }
  }

  .list-content {
    font-family: "google", "sans-serif";
    display: flex;
    flex-direction: column;
    padding: 0 8px;
    gap: 2px;
  }

  /* 列表项 - 胶囊形状 */
  .list-item {
    position: relative;
    font-family: "google", "sans-serif";
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 48px; /* 标准触摸高度 */
    padding: 0 16px;
    border-radius: 24px; /* 全圆角/胶囊状 */
    color: var(--md-sys-color-on-surface);
    background-color: transparent;
    transition: background-color 0.2s cubic-bezier(0.2, 0, 0, 1), color 0.2s;
    overflow: hidden;

    /* State Layer (悬停效果) */
    .state-layer {
      font-family: "google", "sans-serif";
      position: absolute;
      inset: 0;
      background-color: var(--md-sys-color-on-surface);
      opacity: 0;
      transition: opacity 0.2s;
      pointer-events: none;
    }

    &:hover .state-layer {
      opacity: 0.12; /* 在透明背景下稍微增强悬停效果 */
    }

    /* 选中/启用状态 */
    &.active {
      font-family: "google", "sans-serif";
      color: var(--md-sys-color-on-secondary-container);

      .state-layer {
        background-color: var(--md-sys-color-on-secondary-container);
      }

      .item-label {
        font-weight: 600;
      }
    }

    .item-label {
      font-size: var(--md-sys-typescale-body-medium);
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      z-index: 1;
      flex: 1;
      margin-right: 12px;
    }

    /* 按键显示 Chip */
    .key-chip {
      font-family: "google", "sans-serif";
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0 10px;
      height: 28px;
      border-radius: 8px;
      background-color: var(--md-sys-color-surface-container-highest);
      color: var(--md-sys-color-on-surface-variant);
      font-size: 12px;
      font-weight: 500;
      z-index: 1;
      transition: all 0.2s;
      min-width: 24px;

      &.active-chip {
        /* 在深色 active 背景上，chip 稍微加深以保持对比度 */
        background-color: rgba(0, 0, 0, 0.15);
        color: var(--md-sys-color-on-secondary-container);
      }
    }
  }

  .empty-state {
    font-family: "google", "sans-serif";
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24px 0;
    gap: 8px;
    opacity: 0.7;

    .empty-icon {
      width: 32px;
      height: 32px;
      color: var(--md-sys-color-outline-variant);
    }

    .empty-text {
      font-size: 13px;
      color: var(--md-sys-color-on-surface-variant);
    }
  }
</style>
