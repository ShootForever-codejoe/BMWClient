<script lang="ts">
    import {onMount} from "svelte";
    import {getGameWindow, getModules, getModuleSettings, setTyping} from "../../integration/rest";
    import {groupByCategory} from "../../integration/util";
    import type {ConfigurableSetting, GroupedModules, Module, TogglableSetting} from "../../integration/types";
    import Panel from "./Panel.svelte";
    import Search from "./Search.svelte";
    import Description from "./Description.svelte";
    import {fade} from "svelte/transition";
    import {listen} from "../../integration/ws";
    import type {ClickGuiValueChangeEvent, ScaleFactorChangeEvent} from "../../integration/events";
    import {gridSize, scaleFactor, showGrid, snappingEnabled} from "./clickgui_store";

    let categories: GroupedModules = {};
    let modules: Module[] = [];
    let minecraftScaleFactor = 2;
    let clickGuiScaleFactor = 1;
    $: {
        scaleFactor.set(minecraftScaleFactor * clickGuiScaleFactor);
    }

    function applyValues(configurable: ConfigurableSetting) {
        clickGuiScaleFactor = configurable.value.find(v => v.name === "Scale")?.value as number ?? 1;

        const snappingValue = configurable.value.find(v => v.name === "Snapping") as TogglableSetting;

        $snappingEnabled = snappingValue?.value.find(v => v.name === "Enabled")?.value as boolean ?? true;
        $gridSize = snappingValue?.value.find(v => v.name === "GridSize")?.value as number ?? 10;
    }

    onMount(async () => {
        const gameWindow = await getGameWindow();
        minecraftScaleFactor = gameWindow.scaleFactor;

        modules = await getModules();
        categories = groupByCategory(modules);

        const clickGuiSettings = await getModuleSettings("ClickGUI");
        applyValues(clickGuiSettings);

        await setTyping(false);
    });

    listen("scaleFactorChange", (e: ScaleFactorChangeEvent) => {
        minecraftScaleFactor = e.scaleFactor;
    });

    listen("clickGuiValueChange", (e: ClickGuiValueChangeEvent) => {
        applyValues(e.configurable);
    });
</script>

<div class="clickgui" class:grid={$showGrid} transition:fade|global={{duration: 200}}
     style="transform: scale({$scaleFactor * 50}%); width: {2 / $scaleFactor * 100}vw; height: {2 / $scaleFactor * 100}vh;
     background-size: {$gridSize}px {$gridSize}px;">
    <Description/>
    <Search modules={structuredClone(modules)}/>

    {#each Object.entries(categories) as [category, modules], panelIndex}
        <Panel {category} {modules} {panelIndex}/>
    {/each}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  $GRID_SIZE: 10px;

  /* Android 16 MD3 色彩系统 */
  :global(body) {
    /* 亮色主题 */
    --md-sys-color-primary: #6750A4;
    --md-sys-color-on-primary: #FFFFFF;
    --md-sys-color-primary-container: #EADDFF;
    --md-sys-color-on-primary-container: #21005D;
    --md-sys-color-secondary: #625B71;
    --md-sys-color-on-secondary: #FFFFFF;
    --md-sys-color-secondary-container: #E8DEF8;
    --md-sys-color-on-secondary-container: #1D192B;
    --md-sys-color-surface: #FFFBFE;
    --md-sys-color-surface-container: #F0EBF4;
    --md-sys-color-surface-container-high: #ECE6F0;
    --md-sys-color-surface-container-highest: #E6E0E9;
    --md-sys-color-on-surface: #1C1B1F;
    --md-sys-color-on-surface-variant: #49454F;
    --md-sys-color-outline: #79747E;
    --md-sys-color-outline-variant: #CAC4D0;
    
    /* 状态颜色 */
    --md-sys-color-success: #2E7D32;
    --md-sys-color-on-success: #FFFFFF;
    --md-sys-color-success-container: #C1E1C1;
    --md-sys-color-on-success-container: #0A1F0B;
    --md-sys-color-warning: #ED6C02;
    --md-sys-color-on-warning: #FFFFFF;
    --md-sys-color-warning-container: #FFDDB3;
    --md-sys-color-on-warning-container: #4E1D00;
    --md-sys-color-error: #B3261E;
    --md-sys-color-on-error: #FFFFFF;
    --md-sys-color-error-container: #F9DEDC;
    --md-sys-color-on-error-container: #410E0B;
    
    /* 阴影 */
    --md-sys-elevation-level1: 0 1px 2px rgba(0, 0, 0, 0.3), 0 1px 3px 1px rgba(0, 0, 0, 0.15);
    --md-sys-elevation-level2: 0 1px 2px rgba(0, 0, 0, 0.3), 0 2px 6px 2px rgba(0, 0, 0, 0.15);
    --md-sys-elevation-level3: 0 4px 8px 3px rgba(0, 0, 0, 0.15), 0 1px 3px rgba(0, 0, 0, 0.3);
    --md-sys-elevation-level4: 0 6px 10px 4px rgba(0, 0, 0, 0.15), 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  /* Android 16 深色模式 */
  @media (prefers-color-scheme: dark) {
    :global(body) {
      --md-sys-color-primary: #D0BCFF;
      --md-sys-color-on-primary: #381E72;
      --md-sys-color-primary-container: #4F378B;
      --md-sys-color-on-primary-container: #EADDFF;
      --md-sys-color-secondary: #CCC2DC;
      --md-sys-color-on-secondary: #332D41;
      --md-sys-color-secondary-container: #4A4458;
      --md-sys-color-on-secondary-container: #E8DEF8;
      --md-sys-color-surface: #141218;
      --md-sys-color-surface-container: #211F26;
      --md-sys-color-surface-container-high: #2B2930;
      --md-sys-color-surface-container-highest: #36343B;
      --md-sys-color-on-surface: #E6E0E9;
      --md-sys-color-on-surface-variant: #CAC4D0;
      --md-sys-color-outline: #938F99;
      --md-sys-color-outline-variant: #49454F;
      
      /* 深色状态颜色 */
      --md-sys-color-success: #A5D6A7;
      --md-sys-color-on-success: #0A1F0B;
      --md-sys-color-success-container: #1B5E20;
      --md-sys-color-on-success-container: #C1E1C1;
      --md-sys-color-warning: #FFB74D;
      --md-sys-color-on-warning: #4E1D00;
      --md-sys-color-warning-container: #6F2C00;
      --md-sys-color-on-warning-container: #FFDDB3;
      --md-sys-color-error: #F2B8B5;
      --md-sys-color-on-error: #601410;
      --md-sys-color-error-container: #8C1D18;
      --md-sys-color-on-error-container: #F9DEDC;
    }
  }

  .clickgui {
    background-color: rgba(var(--md-sys-color-surface-container-high), 0.85);
    backdrop-filter: blur(24px);
    overflow: hidden;
    position: absolute;
    will-change: opacity;
    transform-origin: top left;
    left: 0;
    top: 0;
    border-radius: 28px; /* Android 16 大圆角 */
    box-shadow: var(--md-sys-elevation-level2);
    border: 1px solid var(--md-sys-color-outline-variant);

    &.grid {
      background-image: 
        linear-gradient(to right, var(--md-sys-color-outline-variant) 1px, transparent 1px),
        linear-gradient(to bottom, var(--md-sys-color-outline-variant) 1px, transparent 1px);
      background-size: $GRID_SIZE $GRID_SIZE;
    }
  }
</style>
