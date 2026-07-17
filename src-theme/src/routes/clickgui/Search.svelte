<script lang="ts">
    import type {ConfigurableSetting, Module} from "../../integration/types";
    import {getModuleSettings, setModuleEnabled, setTyping} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {ClickGuiValueChangeEvent, KeyboardKeyEvent, ModuleToggleEvent} from "../../integration/events";
    import {highlightModuleName} from "./clickgui_store";
    import {onMount} from "svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";

    export let modules: Module[];
    export let onJumpToModule: (moduleName: string) => void;

    let resultElements: HTMLElement[] = [];
    let searchContainerElement: HTMLElement;
    let autoFocus: boolean = true
    let searchInputElement: HTMLElement;
    let query: string;
    let filteredModules: Module[] = [];
    let selectedIndex = -1;
    let isFocused = false;

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
        selectedIndex = -1;

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
                selectedIndex = selectedIndex <= 0 ? filteredModules.length - 1 : selectedIndex - 1;
                break;
            case "key.keyboard.enter":
                if (selectedIndex < 0 || selectedIndex >= filteredModules.length) {
                    break;
                }
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
        try {
            await setModuleEnabled(name, enabled);
        } catch (error) {
            console.error(`Failed to update module ${name}`, error);
        }
    }

    function handleWindowClick(e: MouseEvent) {
        if (searchContainerElement && !searchContainerElement.contains(e.target as Node)) {
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
        modules = modules;
        filteredModules = filteredModules;
    });

    listen("keyboardKey", handleKeyDown);

    listen("clickGuiValueChange", (e: ClickGuiValueChangeEvent) => {
        applyValues(e.configurable);
    });
</script>

<svelte:window on:click={handleWindowClick} on:keydown={handleWindowKeyDown} on:contextmenu={handleWindowClick}/>

<div class="search-bar-wrapper">
  <div
    class="search {query && filteredModules.length > 0 ? 'open' : ''}"
    class:has-results={query}
    bind:this={searchContainerElement}
  >
    <div class="search-input-wrapper">
      <span class="search-icon-wrapper">
        {#if isFocused}
          <img src="./img/clickgui/icon-search.gif" alt="search" class="search-icon-img" />
        {:else}
          <img src="./img/clickgui/icon-search.svg" alt="search" class="search-icon-img" />
        {/if}
      </span>
      <input
        type="text"
        class="search-input"
        placeholder="Search modules..."
        spellcheck="false"
        bind:value={query}
        bind:this={searchInputElement}
        on:input={filterModules}
        on:keydown={handleBrowserKeyDown}
        on:focusin={() => { isFocused = true; setTyping(true); }}
        on:focusout={() => { isFocused = false; setTyping(false); }}
      />
    </div>
    {#if query}
      <div class="results open">
        {#if filteredModules.length > 0}
          {#each filteredModules as {name, enabled, aliases}, index (name)}
            <button type="button" class="result"
    class:enabled
    on:click={() => toggleModule(name, !enabled)}
    on:contextmenu|preventDefault={() => { onJumpToModule && onJumpToModule(name); reset(); }}
    class:selected={selectedIndex === index}
    on:mouseenter={() => selectedIndex = index}
    on:mouseleave={() => selectedIndex = -1}
    bind:this={resultElements[index]}
            >
              <div class="module-name">
                {name}
              </div>
              <div class="aliases">
                {#if aliases.length > 0}
                  (aka {aliases.map(name => $spaceSeperatedNames ? convertToSpacedString(name) : name).join(", ")})
                {/if}
              </div>
            </button>
          {/each}
        {:else}
          <div class="placeholder">No modules found...<img src="./img/clickgui/icon-nomodules.gif" alt="search" class="search-nomodules-found"/></div>
        {/if}
      </div>
    {/if}
  </div>
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .search-bar-wrapper {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    width: 100%;
    height: 100%;
    padding-right: 0;
  }
  .search {
    width: 100%;
    max-width: 430px;
    background: linear-gradient(110deg, rgba(var(--accent-color), 0.09), rgba(0, 0, 0, 0.38));
    box-shadow: var(--theme-shadow-soft);
    border-radius: 18px;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    margin: 0;
    position: relative;
    border: 1px solid $clickgui-border-color;
    transition: border-color 0.18s, border-radius 0.18s;
    &:focus-within, &.open {
      border-color: rgba(var(--accent-color), 0.8);
      border-right: 2px solid rgba(var(--accent-color), 0.95);
      box-shadow: var(--theme-glow);
      border-radius: 18px 18px 0 0;
    }
    &.open {
      border-radius: 18px 18px 0 0;
    }
  }
  .search-input-wrapper {
    display: flex;
    align-items: center;
    padding: 0 10px;
    height: 38px;
    position: relative;
  }
  .search-icon-wrapper {
    position: absolute;
    left: 12.5px;
    top: 18px;
    transform: translateY(-50%);
    opacity: 0.5;
    pointer-events: none;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .search-icon-img {
    width: 24px;
    height: 24px;
    display: block;
  }
  .search-input {
    width: 100%;
    padding: 10px 18px 10px 32.5px;
    font-size: 15px;
    background: transparent;
    color: $clickgui-text-color;
    border: none;
    outline: none;
    border-radius: 18px;
    transition: background 0.2s;
    &::placeholder {
      color: $clickgui-text-dimmed-color;
      opacity: 0.7;
      font-size: 15px;
    }
  }
  .results {
    position: absolute;
    left: 0;
    top: 100%;
    width: 100%;
    z-index: 10;
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.12), rgba(8, 4, 10, 0.96));
    border-radius: 0 0 18px 18px;
    max-height: 225px;
    overflow-y: auto;
    border: 1px solid rgba(var(--accent-color), 0.55);
    border-right: 2px solid rgba(var(--accent-color), 0.95);
    margin-top: 0;
    animation: fadeIn 0.18s;
    transition: box-shadow 0.2s, border-radius 0.2s;
    &.open {
      box-shadow: var(--theme-shadow-raised);
    }
    scrollbar-width: none;
    -ms-overflow-style: none;
    &::-webkit-scrollbar {
      display: none;
    }
  }
  @keyframes fadeIn {
    from { opacity: 0; transform: translateY(-8px); }
    to { opacity: 1; transform: translateY(0); }
  }
  .result {
    width: calc(100% - 4px);
    border: 0;
    text-align: left;
    font: inherit;
    padding: 10px 10px;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    transition: background 0.15s;
    border-radius: 12px;
    overflow: hidden;
    margin: 2px 2px;
    color: $clickgui-text-color;
    &:hover, &.selected {
      background: rgba(var(--accent-color), 0.13);
    }
    &.enabled {
      color: rgb(var(--accent-color));

      .module-name {
        color: rgb(var(--accent-color));
      }
    }
  }
  .module-name {
    font-weight: 600;
    font-size: 15px;
    color: $clickgui-text-color;
  }
  .aliases {
    font-size: 13px;
    color: $clickgui-text-dimmed-color;
  }
  .placeholder {
    padding: 12px 18px;
    color: $clickgui-text-dimmed-color;
    text-align: center;
  }
  .search-nomodules-found {
    margin-left: -1px;
    margin-bottom: -2px;
    width: 20px;
    height: 20px;
  }
  @media (max-width: 700px) {
    .search-bar-wrapper {
      width: 100%;
      padding: 0;
    }
    .search {
      width: 100%;
      min-width: 0;
      max-width: 100%;
      border-radius: 18px;
    }
    .search-input {
      font-size: 15px;
      padding-left: 38px;
      border-radius: 18px;
    }
    .results {
      border-radius: 0 0 18px 18px;
      max-height: 120px;
    }
  }
</style>
