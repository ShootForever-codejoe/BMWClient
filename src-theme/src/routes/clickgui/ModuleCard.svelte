<!--
  - This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
  -
  - Copyright (c) 2015 - 2025 CCBlueX
  -
  - LiquidBounce is free software: you can redistribute it and/or modify
  - it under the terms of the GNU General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - LiquidBounce is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  - GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License
  - along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
  -->

<script lang="ts">
  import BindSetting from "./setting/BindSetting.svelte";
  import { getModuleSettings, setModuleSettings } from "../../integration/rest";
  import { onMount, onDestroy } from "svelte";
  export let module: { name: string, color: string, enabled: boolean, settingsCount: number, description?: string, bind?: string | null };
  export let onSettings: () => void;
  export let onToggle: () => void;
  export let selected: boolean = false;
  export let highlighted: boolean = false;

  let showBindPanel = false;
  let bindSetting: any = null;
  let loadingBind = false;
  let bindPanelContainer: HTMLElement;

  let enabled = module.enabled;

  $: if (module.enabled !== enabled) enabled = module.enabled;

  function handleToggle() {
    enabled = !enabled;
    onToggle();
  }

  function openBindPanel(event: MouseEvent) {
    event.stopPropagation();
    showBindPanel = !showBindPanel;
    if (showBindPanel) {
      loadingBind = true;
      getModuleSettings(module.name).then(settings => {
        bindSetting = settings.value.find((s: any) => s.name === "Bind");
        loadingBind = false;
      });
    }
  }

  async function handleBindChange() {
    if (!bindSetting) return;
    const allSettings = await getModuleSettings(module.name);
    const newSettings = allSettings.value.map((s: any) =>
      s.name === "Bind" ? bindSetting : s
    );
    await setModuleSettings(module.name, { ...allSettings, value: newSettings });
    const settings = await getModuleSettings(module.name);
    bindSetting = settings.value.find((s: any) => s.name === "Bind");
    module = { ...module, bind: bindSetting?.value?.boundKey };
  }

  function handleClickOutsideBindPanel(event: MouseEvent) {
    if (!showBindPanel || !bindPanelContainer) return;
    const bindBtn = bindPanelContainer.parentElement?.querySelector('.bind-btn');
    if (bindBtn && bindBtn.contains(event.target as Node)) return;
    if (!bindPanelContainer.contains(event.target as Node)) {
      showBindPanel = false;
    }
  }

  $: if (showBindPanel) {
    window.addEventListener('mousedown', handleClickOutsideBindPanel);
  } else {
    window.removeEventListener('mousedown', handleClickOutsideBindPanel);
  }

  onMount(() => {
    function closeBindPanelOnMenuClose() {
      showBindPanel = false;
    }
    window.addEventListener('closeClickGui', closeBindPanelOnMenuClose);
    window.addEventListener('closeBindPanels', closeBindPanelOnMenuClose);
    onDestroy(() => {
      window.removeEventListener('closeClickGui', closeBindPanelOnMenuClose);
      window.removeEventListener('closeBindPanels', closeBindPanelOnMenuClose);
    });
  });
</script>

<div class="module-card" class:selected-module={selected} class:highlighted={highlighted}>
  <div class="main">
    <span class="name {module.enabled ? 'enabled' : ''}">{module.name}</span>
    {#if module.settingsCount > -1}
      <button class="settings-icon" on:click={onSettings} aria-label="Settings">
        <svg xmlns="http://www.w3.org/2000/svg" height="20" width="20" viewBox="0 0 640 640"><path fill="#ffffff" d="M415.9 274.5C428.1 271.2 440.9 277 446.4 288.3L465 325.9C475.3 327.3 485.4 330.1 494.9 334L529.9 310.7C540.4 303.7 554.3 305.1 563.2 314L582.4 333.2C591.3 342.1 592.7 356.1 585.7 366.5L562.4 401.4C564.3 406.1 566 411 567.4 416.1C568.8 421.2 569.7 426.2 570.4 431.3L608.1 449.9C619.4 455.5 625.2 468.3 621.9 480.4L614.9 506.6C611.6 518.7 600.3 526.9 587.7 526.1L545.7 523.4C539.4 531.5 532.1 539 523.8 545.4L526.5 587.3C527.3 599.9 519.1 611.3 507 614.5L480.8 621.5C468.6 624.8 455.9 619 450.3 607.7L431.7 570.1C421.4 568.7 411.3 565.9 401.8 562L366.8 585.3C356.3 592.3 342.4 590.9 333.5 582L314.3 562.8C305.4 553.9 304 540 311 529.5L334.3 494.5C332.4 489.8 330.7 484.9 329.3 479.8C327.9 474.7 327 469.6 326.3 464.6L288.6 446C277.3 440.4 271.6 427.6 274.8 415.5L281.8 389.3C285.1 377.2 296.4 369 309 369.8L350.9 372.5C357.2 364.4 364.5 356.9 372.8 350.5L370.1 308.7C369.3 296.1 377.5 284.7 389.6 281.5L415.8 274.5zM448.4 404C424.1 404 404.4 423.7 404.5 448.1C404.5 472.4 424.2 492 448.5 492C472.8 492 492.5 472.3 492.5 448C492.4 423.6 472.7 404 448.4 404zM224.9 18.5L251.1 25.5C263.2 28.8 271.4 40.2 270.6 52.7L267.9 94.5C276.2 100.9 283.5 108.3 289.8 116.5L331.8 113.8C344.3 113 355.7 121.2 359 133.3L366 159.5C369.2 171.6 363.5 184.4 352.2 190L314.5 208.6C313.8 213.7 312.8 218.8 311.5 223.8C310.2 228.8 308.4 233.8 306.5 238.5L329.8 273.5C336.8 284 335.4 297.9 326.5 306.8L307.3 326C298.4 334.9 284.5 336.3 274 329.3L239 306C229.5 309.9 219.4 312.7 209.1 314.1L190.5 351.7C184.9 363 172.1 368.7 160 365.5L133.8 358.5C121.6 355.2 113.5 343.8 114.3 331.3L117 289.4C108.7 283 101.4 275.6 95.1 267.4L53.1 270.1C40.6 270.9 29.2 262.7 25.9 250.6L18.9 224.4C15.7 212.3 21.4 199.5 32.7 193.9L70.4 175.3C71.1 170.2 72.1 165.2 73.4 160.1C74.8 155 76.4 150.1 78.4 145.4L55.1 110.5C48.1 100 49.5 86.1 58.4 77.2L77.6 58C86.5 49.1 100.4 47.7 110.9 54.7L145.9 78C155.4 74.1 165.5 71.3 175.8 69.9L194.4 32.3C200 21 212.7 15.3 224.9 18.5zM192.4 148C168.1 148 148.4 167.7 148.4 192C148.4 216.3 168.1 236 192.4 236C216.7 236 236.4 216.3 236.4 192C236.4 167.7 216.7 148 192.4 148z"/></svg>
      </button>
    {/if}
      <button class="bind-btn" on:click={openBindPanel} aria-label="Bind">
          <svg xmlns="http://www.w3.org/2000/svg" height="20" width="20" viewBox="0 0 640 640"><!--!Font Awesome Free v7.0.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free Copyright 2025 Fonticons, Inc.--><path fill="#ffffff" d="M96 128C60.7 128 32 156.7 32 192L32 448C32 483.3 60.7 512 96 512L544 512C579.3 512 608 483.3 608 448L608 192C608 156.7 579.3 128 544 128L96 128zM112 192L144 192C152.8 192 160 199.2 160 208L160 240C160 248.8 152.8 256 144 256L112 256C103.2 256 96 248.8 96 240L96 208C96 199.2 103.2 192 112 192zM96 304C96 295.2 103.2 288 112 288L144 288C152.8 288 160 295.2 160 304L160 336C160 344.8 152.8 352 144 352L112 352C103.2 352 96 344.8 96 336L96 304zM208 192L240 192C248.8 192 256 199.2 256 208L256 240C256 248.8 248.8 256 240 256L208 256C199.2 256 192 248.8 192 240L192 208C192 199.2 199.2 192 208 192zM192 304C192 295.2 199.2 288 208 288L240 288C248.8 288 256 295.2 256 304L256 336C256 344.8 248.8 352 240 352L208 352C199.2 352 192 344.8 192 336L192 304zM208 384L432 384C440.8 384 448 391.2 448 400L448 432C448 440.8 440.8 448 432 448L208 448C199.2 448 192 440.8 192 432L192 400C192 391.2 199.2 384 208 384zM288 208C288 199.2 295.2 192 304 192L336 192C344.8 192 352 199.2 352 208L352 240C352 248.8 344.8 256 336 256L304 256C295.2 256 288 248.8 288 240L288 208zM304 288L336 288C344.8 288 352 295.2 352 304L352 336C352 344.8 344.8 352 336 352L304 352C295.2 352 288 344.8 288 336L288 304C288 295.2 295.2 288 304 288zM384 208C384 199.2 391.2 192 400 192L432 192C440.8 192 448 199.2 448 208L448 240C448 248.8 440.8 256 432 256L400 256C391.2 256 384 248.8 384 240L384 208zM400 288L432 288C440.8 288 448 295.2 448 304L448 336C448 344.8 440.8 352 432 352L400 352C391.2 352 384 344.8 384 336L384 304C384 295.2 391.2 288 400 288zM480 208C480 199.2 487.2 192 496 192L528 192C536.8 192 544 199.2 544 208L544 240C544 248.8 536.8 256 528 256L496 256C487.2 256 480 248.8 480 240L480 208zM496 288L528 288C536.8 288 544 295.2 544 304L544 336C544 344.8 536.8 352 528 352L496 352C487.2 352 480 344.8 480 336L480 304C480 295.2 487.2 288 496 288z"/></svg>
      </button>
      <label class="switch">
          <input type="checkbox" bind:checked={module.enabled} on:change={onToggle} />
          <span class="slider"></span>
      </label>
  </div>
  {#if module.description}
    <div class="module-description">{module.description}</div>
  {/if}
  {#if showBindPanel}
    <div class="bind-panel" bind:this={bindPanelContainer}>
      {#if loadingBind}
        <div class="bind-loading">Loading bind...</div>
      {:else if bindSetting}
        <BindSetting bind:setting={bindSetting} moduleName={module.name} on:change={handleBindChange} />
      {:else}
        <div class="bind-loading">Bind not found</div>
      {/if}
    </div>
  {/if}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

.module-card {
  background: $modulecard-main-color;
  border-radius: 10px;
  padding: 7.5px 7.5px 15px 7.5px;
  border: 1px solid $clickgui-border-color;
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
  min-width: 220px;
  box-shadow: 0 4px 8px rgba($modulecard-main-color, 0.5);
  transition: box-shadow 0.2s, background 0.2s, transform 0.2s ease;
  &:hover {
    box-shadow: 0 4px 8px rgba(var(--accent-color), 0.5);
    border-color: rgba(var(--accent-color), 1);
    background: $modulecard-hover-color;
  }
  &.highlighted {
    border-color: rgba(var(--accent-color), 1);
    box-shadow: 0 0 10px rgba(var(--accent-color), 0.5);
  }
  .main {
    display: flex;
    align-items: center;
    gap: 5px;
    .name {
      font-size: 18px;
      font-weight: 700;
      color: $clickgui-text-color;
      text-shadow: 0 4px 8px rgba(0, 0, 0, 0.5);
      flex: 1;
      transition: color 0.2s;
      &.enabled {
        color: rgba(var(--accent-color), 0.75);
        text-shadow: 0 0 10px rgba(var(--accent-color), 0.5);
      }
    }
    .bind-btn,
    .settings-icon {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid $clickgui-border-color;
      border-radius: 5px;
      padding: 2px;
      width: 24px;
      height: 24px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.25s ease;
      svg {
        width: 24px;
        height: 24px;
      }
      &:hover {
        background: rgba(var(--accent-color), 0.25);
        border-color: rgba(var(--accent-color), 1);
        box-shadow: 0 0 8px rgba(var(--accent-color), 0.5);
      }
    }
    .switch {
      position: relative;
      display: inline-block;
      width: 38px;
      height: 22px;
      input {
        opacity: 0;
        width: 0;
        height: 0;
      }
      .slider {
        position: absolute;
        cursor: pointer;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(var(--accent-color), 0.15);
        border-radius: 50px;
        transition: .35s;
      }
      input:checked + .slider {
        background: rgba(var(--accent-color), 0.75);
        box-shadow: 0 0 10px rgba(var(--accent-color), 0.5);
      }
      .slider:before {
        position: absolute;
        content: "";
        height: 16px;
        width: 16px;
        left: 3px;
        bottom: 3px;
        background: #fff;
        border-radius: 50%;
        transition: .35s;
      }
      input:checked + .slider:before {
        transform: translateX(16px);
      }
    }
  }
  .module-description {
    font-size: 12px;
    color: $clickgui-text-dimmed-color;
    margin-top: 1px;
    margin-left: 1.5px;
    margin-bottom: 0;
    line-height: 1;
    font-weight: 400;
    word-break: break-word;
    min-height: 28.5px;
    max-height: 28.5px;
  }
  .bind-panel {
    position: absolute;
    top: 35px;
    left: 100px;
    z-index: 10;
    background: $clickgui-settings-color;
    border: 1px solid $clickgui-border-color;
    border-radius: 10px;
    box-shadow: 0 5px 5px rgba($clickgui-settings-color, 0.3);
    padding: 6px 8px;
    min-width: 200px;
    max-width: 200px;
    animation: fadeIn 0.18s;
  }
  .bind-loading {
    color: #aaa;
    font-size: 14px;
    padding: 10px 0;
    text-align: center;
  }
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
