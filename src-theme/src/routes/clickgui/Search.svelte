<script lang="ts">
    import type {ConfigurableSetting, Module} from "../../integration/types";
    import {getModuleSettings, setModuleEnabled, setTyping} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {ClickGuiValueChangeEvent, KeyboardKeyEvent, ModuleToggleEvent} from "../../integration/events";
    import {highlightModuleName} from "./clickgui_store";
    import {onMount} from "svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";

    export let modules: Module[];

    let resultElements: HTMLElement[] = [];
    let searchContainerElement: HTMLElement;
    let autoFocus: boolean = true
    let searchInputElement: HTMLElement;
    let query: string;
    let filteredModules: Module[] = [];
    let selectedIndex = 0;

    function reset() {
        filteredModules = [];
        query = "";
        $highlightModuleName = null;
    }

    function filterModules() {
        if (!query) {
            reset();
            return;
        }

        selectedIndex = 0;

        const pureQuery = query.toLowerCase().replaceAll(" ", "");

        filteredModules = modules.filter((m) => m.name.toLowerCase().includes(pureQuery)
            || m.aliases.some(a => a.toLowerCase().includes(pureQuery))
        );
    }

    async function handleKeyDown(e: KeyboardKeyEvent) {
        if (e.screen === undefined || !e.screen.class.startsWith("net.ccbluex.liquidbounce") ||
            !(e.screen.title === "ClickGUI" || e.screen.title === "VS-CLICKGUI")) {
            return;
        }

        if (filteredModules.length === 0 || e.action === 0) {
            return;
        }

        switch (e.key) {
            case "key.keyboard.down":
                selectedIndex = (selectedIndex + 1) % filteredModules.length;
                break;
            case "key.keyboard.up":
                selectedIndex =
                    (selectedIndex - 1 + filteredModules.length) %
                    filteredModules.length;
                break;
            case "key.keyboard.enter":
                await toggleModule(
                    filteredModules[selectedIndex].name,
                    !filteredModules[selectedIndex].enabled,
                );
                break;
            case "key.keyboard.tab":
                const m = filteredModules[selectedIndex]?.name;
                if (m) {
                    $highlightModuleName = m;
                }
                break;
        }

        resultElements[selectedIndex]?.scrollIntoView({
            behavior: "smooth",
            block: "nearest",
        });
    }

    function handleBrowserKeyDown(e: KeyboardEvent) {
        if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Tab") {
            e.preventDefault();
        }
    }

    async function toggleModule(name: string, enabled: boolean) {
        await setModuleEnabled(name, enabled);
    }

    function handleWindowClick(e: MouseEvent) {
        if (!searchContainerElement.contains(e.target as Node)) {
            reset();
        }
    }

    function handleWindowKeyDown() {
        if (document.activeElement !== document.body) {
            return;
        }

        if (autoFocus) {
            searchInputElement.focus();
        }
    }

    function applyValues(configurable: ConfigurableSetting) {
        autoFocus = configurable.value.find(v => v.name === "SearchBarAutoFocus")?.value as boolean ?? true;
    }

    onMount(async () => {
        const clickGuiSettings = await getModuleSettings("ClickGUI");
        applyValues(clickGuiSettings);

        if (autoFocus) {
            searchInputElement.focus();
        }
    });

    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const mod = modules.find((m) => m.name === e.moduleName);
        if (!mod) {
            return;
        }
        mod.enabled = e.enabled;
        filteredModules = filteredModules;
    });

    listen("keyboardKey", handleKeyDown);

    listen("clickGuiValueChange", (e: ClickGuiValueChangeEvent) => {
        applyValues(e.configurable);
    });
</script>

<svelte:window on:click={handleWindowClick} on:keydown={handleWindowKeyDown} on:contextmenu={handleWindowClick}/>

<div
        class="search"
        class:has-results={query}
        bind:this={searchContainerElement}
>
    <input
            type="text"
            class="search-input"
            placeholder="Search"
            spellcheck="false"
            bind:value={query}
            bind:this={searchInputElement}
            on:input={filterModules}
            on:keydown={handleBrowserKeyDown}
            on:focusin={async () => await setTyping(true)}
            on:focusout={async () => await setTyping(false)}
    />

    {#if query}
        <div class="results">
            {#if filteredModules.length > 0}
                {#each filteredModules as {name, enabled, aliases}, index (name)}
                    <!-- svelte-ignore a11y-click-events-have-key-events -->
                    <!-- svelte-ignore a11y-no-static-element-interactions -->
                    <div
                            class="result"
                            class:enabled
                            on:click={() => toggleModule(name, !enabled)}
                            on:contextmenu|preventDefault={() => $highlightModuleName = name}
                            class:selected={selectedIndex === index}
                            bind:this={resultElements[index]}
                    >
                        <div class="module-name">
                            {$spaceSeperatedNames ? convertToSpacedString(name) : name}
                        </div>
                        <div class="aliases">
                            {#if aliases.length > 0}
                                (aka {aliases.map(name => $spaceSeperatedNames ? convertToSpacedString(name) : name).join(", ")})
                            {/if}
                        </div>
                    </div>
                {/each}
            {:else}
                <div class="placeholder">No modules found</div>
            {/if}
        </div>
    {/if}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .search {
    position: fixed;
    left: 50%;
    top: 40px;
    transform: translateX(-50%);
    background-color: var(--md-sys-color-surface-container-high);
    width: 640px;
    border-radius: 28px;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    box-shadow: var(--md-sys-elevation-level3);
    border: 1px solid var(--md-sys-color-outline-variant);
    backdrop-filter: blur(24px);

    &.has-results {
      border-radius: 28px 28px 16px 16px;
      box-shadow: var(--md-sys-elevation-level4);
    }

    &:focus-within {
      z-index: 9999999999;
      transform: translateX(-50%) translateY(-2px);
      box-shadow: var(--md-sys-elevation-level4);
    }
  }

  .results {
    border-top: 2px solid var(--md-sys-color-primary);
    padding: 8px 24px;
    max-height: 280px;
    overflow: auto;
    background-color: var(--md-sys-color-surface-container);

    .result {
      font-size: 15px;
      padding: 12px 16px;
      transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
      cursor: pointer;
      display: flex;
      align-items: center;
      border-radius: 16px;
      margin: 4px 0;
      
      .module-name {
        color: var(--md-sys-color-on-surface-variant);
        transition: all 0.2s ease;
        font-weight: 500;
        flex: 1;
      }

      &.enabled {
        background-color: rgba(var(--md-sys-color-primary-container), 0.3);
        .module-name {
          color: var(--md-sys-color-primary);
        }
      }

      .aliases {
        color: var(--md-sys-color-outline);
        margin-left: 12px;
        font-size: 13px;
        font-weight: 400;
      }

      &.selected {
        background-color: var(--md-sys-color-secondary-container);
        .module-name {
          color: var(--md-sys-color-on-secondary-container);
        }
        transform: translateX(8px);
      }

      &:hover {
        background-color: var(--md-sys-color-surface-container-highest);
        color: var(--md-sys-color-on-surface);
        transform: translateX(4px);
        
        &::after {
          content: "右键定位模块";
          color: var(--md-sys-color-outline);
          font-size: 12px;
          margin-left: 16px;
          font-weight: 400;
        }
      }
      
      &:active {
        transform: scale(0.98) translateX(4px);
      }
    }

    .placeholder {
      color: var(--md-sys-color-on-surface-variant);
      font-size: 15px;
      padding: 16px;
      text-align: center;
      font-style: italic;
    }

    &::-webkit-scrollbar {
      width: 8px;
    }
    
    &::-webkit-scrollbar-track {
      background: transparent;
    }
    
    &::-webkit-scrollbar-thumb {
      background-color: var(--md-sys-color-outline);
      border-radius: 4px;
      transition: all 0.2s ease;
      
      &:hover {
        background-color: var(--md-sys-color-on-surface-variant);
      }
    }
  }

  .search-input {
    padding: 16px 24px;
    background-color: transparent;
    border: none;
    font-family: "Axiforma", sans-serif;
    font-size: 16px;
    color: var(--md-sys-color-on-surface);
    width: 100%;
    transition: all 0.2s ease;
    
    &::placeholder {
      color: var(--md-sys-color-outline);
    }
    
    &:focus {
      outline: none;
      background-color: rgba(var(--md-sys-color-primary-container), 0.1);
    }
  }
</style>
