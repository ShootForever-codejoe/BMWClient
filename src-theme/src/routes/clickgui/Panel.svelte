<script lang="ts">
    import {onMount} from "svelte";
    import type {Module as TModule} from "../../integration/types";
    import {listen} from "../../integration/ws";
    import Module from "./Module.svelte";
    import type {ModuleToggleEvent} from "../../integration/events";
    import {fade} from "svelte/transition";
    import {quintOut} from "svelte/easing";
    import {
        gridSize,
        highlightModuleName,
        maxPanelZIndex,
        scaleFactor,
        showGrid,
        snappingEnabled
    } from "./clickgui_store";
    import {setItem} from "../../integration/persistent_storage";

    export let category: string;
    export let modules: TModule[];
    export let panelIndex: number;

    let panelElement: HTMLElement;
    let modulesElement: HTMLElement;
    let expandButtonElement: HTMLElement;

    let moving = false;
    let offsetX = 0;
    let offsetY = 0;

    let scrollPositionSaveTimeout: number | undefined;

    const panelConfig = loadPanelConfig();

    let ignoreGrid = false;

    interface PanelConfig {
        top: number;
        left: number;
        expanded: boolean;
        scrollTop: number;
        zIndex: number;
    }

    function clamp(number: number, min: number, max: number) {
        return Math.max(min, Math.min(number, max));
    }

    function loadPanelConfig(): PanelConfig {
        const localStorageItem = localStorage.getItem(
            `clickgui.panel.${category}`,
        );

        if (!localStorageItem) {
            return {
                top: panelIndex * 50 + 20,
                left: 20,
                expanded: false,
                scrollTop: 0,
                zIndex: 0
            };
        } else {
            const config: PanelConfig = JSON.parse(localStorageItem);

            // Migration
            if (!config.zIndex) {
                config.zIndex = 0;
            }

            if (config.zIndex > $maxPanelZIndex) {
                $maxPanelZIndex = config.zIndex;
            }

            return config;
        }
    }

    async function savePanelConfig() {
        await setItem(
            `clickgui.panel.${category}`,
            JSON.stringify(panelConfig),
        );
    }

    function fixPosition() {
        panelConfig.left = clamp(panelConfig.left, 0, document.documentElement.clientWidth * (2 / $scaleFactor) - panelElement.offsetWidth);
        panelConfig.top = clamp(panelConfig.top, 0, document.documentElement.clientHeight * (2 / $scaleFactor) - panelElement.offsetHeight);
    }

    function onMouseDown(e: MouseEvent) {
        if (e.button !== 0 && e.button !== 1) return;

        moving = true;
        offsetX = e.clientX * (2 / $scaleFactor) - panelConfig.left;
        offsetY = e.clientY * (2 / $scaleFactor) - panelConfig.top;
        panelConfig.zIndex = ++$maxPanelZIndex;
        
        $showGrid = $snappingEnabled && !expandButtonElement.contains(e.target as HTMLElement);
    }

    function onMouseMove(e: MouseEvent) {
        if (moving) {
            const newLeft = (e.clientX * (2 / $scaleFactor) - offsetX);
            const newTop = (e.clientY * (2 / $scaleFactor) - offsetY);

            panelConfig.left = snapToGrid(newLeft);
            panelConfig.top = snapToGrid(newTop);

            fixPosition();
        }
    }

    function onMouseUp() {
        if (moving) {
            savePanelConfig();
        }
        moving = false;
        $showGrid = false;
    }

    function toggleExpanded() {
        panelConfig.expanded = !panelConfig.expanded;

        fixPosition();
        savePanelConfig();
    }

    function handleModulesScroll() {
        panelConfig.scrollTop = modulesElement.scrollTop;

        if (scrollPositionSaveTimeout !== undefined) {
            clearTimeout(scrollPositionSaveTimeout);
        }
        scrollPositionSaveTimeout = setTimeout(() => {
            savePanelConfig();
        }, 500)
    }

    highlightModuleName.subscribe((name) => {
        const highlightModule = modules.find(
            (m) => m.name === name,
        );
        if (highlightModule) {
            panelConfig.zIndex = ++$maxPanelZIndex;
            panelConfig.expanded = true;
            savePanelConfig();
        }
    });

    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const moduleName = e.moduleName;
        const moduleEnabled = e.enabled;

        const mod = modules.find((m) => m.name === moduleName);
        if (!mod) return;

        mod.enabled = moduleEnabled;
        modules = modules;
    });

    onMount(() => {
        if (!modulesElement) {
            return;
        }

        modulesElement.scrollTo({
            top: panelConfig.scrollTop,
            behavior: "smooth"
        });
    });

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === "Shift") {
            ignoreGrid = true;
        }
    }

    function handleKeyup(e: KeyboardEvent) {
        if (e.key === "Shift") {
            ignoreGrid = false;
        }
    }

    function snapToGrid(value: number): number {
        if (ignoreGrid || !$snappingEnabled) return value;

        return Math.round(value / $gridSize) * $gridSize;
    }
</script>

<svelte:window on:mouseup={onMouseUp} on:mousemove={onMouseMove} on:keydown={handleKeydown} on:keyup={handleKeyup}/>

<div
        class="panel"
        style="left: {panelConfig.left}px; top: {panelConfig.top}px; z-index: {panelConfig.zIndex};"
        bind:this={panelElement}
        transition:fade|global={{duration: 200, easing: quintOut}}
>
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div
            class="title"
            on:mousedown={onMouseDown}
            on:contextmenu|preventDefault={toggleExpanded}
    >
        <img
                class="icon"
                src="img/clickgui/icon-{category.toLowerCase()}.svg"
                alt="icon"
        />
        <span class="category">{category}</span>

        <!-- svelte-ignore a11y_consider_explicit_label -->
        <button class="expand-toggle" on:click={toggleExpanded} bind:this={expandButtonElement}>
            <div class="icon" class:expanded={panelConfig.expanded}></div>
        </button>
    </div>

    <div
            class="modules"
            class:expanded={panelConfig.expanded}
            on:scroll={handleModulesScroll}
            bind:this={modulesElement}
    >
        {#each modules as {name, enabled, description, aliases} (name)}
            <Module {name} {enabled} {description} {aliases}/>
        {/each}
    </div>
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .panel {
    border-radius: 24px; /* Android 16 MD3 圆角 */
    width: 280px;
    position: absolute;
    overflow: hidden;
    box-shadow: var(--md-sys-elevation-level3);
    will-change: transform;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    user-select: none;
    background-color: var(--md-sys-color-surface-container);
    border: 1px solid var(--md-sys-color-outline-variant);
    backdrop-filter: blur(24px);
    
    &:hover {
      box-shadow: var(--md-sys-elevation-level4);
      transform: translateY(-2px);
    }
  }

  .title {
    display: grid;
    grid-template-columns: max-content 1fr max-content;
    align-items: center;
    column-gap: 16px;
    background: linear-gradient(135deg, 
      var(--md-sys-color-primary-container) 0%, 
      var(--md-sys-color-secondary-container) 100%);
    padding: 16px 20px;
    cursor: grab;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    
    &:active {
      cursor: grabbing;
      transform: scale(0.98);
    }

    .icon {
      width: 24px;
      height: 24px;
      filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
    }

    .category {
      font-size: 16px;
      color: var(--md-sys-color-on-primary-container);
      font-weight: 500;
      letter-spacing: 0.1px;
      text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
    }
  }

  .modules {
    transition: max-height 0.4s cubic-bezier(0.2, 0, 0, 1), 
                opacity 0.3s ease;
    scroll-behavior: smooth;
    max-height: 0;
    opacity: 0;
    overflow-y: auto;
    overflow-x: hidden;
    background-color: var(--md-sys-color-surface-container-high);
    border-top: 1px solid var(--md-sys-color-outline-variant);
    
    &.expanded {
      max-height: 580px;
      opacity: 1;
    }
  }

  .modules::-webkit-scrollbar {
    width: 8px;
  }

  .modules::-webkit-scrollbar-track {
    background: transparent;
  }

  .modules::-webkit-scrollbar-thumb {
    background-color: var(--md-sys-color-outline);
    border-radius: 4px;
    transition: all 0.2s ease;
    
    &:hover {
      background-color: var(--md-sys-color-on-surface-variant);
    }
  }

  .expand-toggle {
    background-color: rgba(255, 255, 255, 0.2);
    border: none;
    cursor: pointer;
    border-radius: 16px;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    backdrop-filter: blur(12px);
    
    &:hover {
      background-color: rgba(255, 255, 255, 0.3);
      transform: scale(1.05);
    }
    
    &:active {
      transform: scale(0.95);
    }

    .icon {
      height: 16px;
      width: 16px;
      position: relative;
      transition: transform 0.4s cubic-bezier(0.2, 0, 0, 1);

      &::before {
        content: "";
        position: absolute;
        background-color: var(--md-sys-color-on-primary-container);
        transition: all 0.4s cubic-bezier(0.2, 0, 0, 1);
        top: 0;
        left: 50%;
        width: 2px;
        height: 100%;
        margin-left: -1px;
        border-radius: 1px;
      }

      &::after {
        content: "";
        position: absolute;
        background-color: var(--md-sys-color-on-primary-container);
        transition: all 0.4s cubic-bezier(0.2, 0, 0, 1);
        top: 50%;
        left: 0;
        width: 100%;
        height: 2px;
        margin-top: -1px;
        border-radius: 1px;
      }

      &.expanded {
        transform: rotate(45deg);
        &::before {
          transform: rotate(90deg);
          background-color: var(--md-sys-color-primary);
        }

        &::after {
          transform: rotate(180deg);
          background-color: var(--md-sys-color-primary);
        }
      }
    }
  }
</style>
