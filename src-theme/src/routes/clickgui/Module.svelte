<script lang="ts">
    import {onMount} from "svelte";
    import {
        getModuleSettings,
        setModuleSettings,
        setModuleEnabled,
    } from "../../integration/rest";
    import type {ConfigurableSetting} from "../../integration/types";
    import GenericSetting from "./setting/common/GenericSetting.svelte";
    import {slide} from "svelte/transition";
    import {quintOut} from "svelte/easing";
    import {description as descriptionStore, highlightModuleName} from "./clickgui_store";
    import {setItem} from "../../integration/persistent_storage";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";
    import {scaleFactor} from "./clickgui_store";

    export let name: string;
    export let enabled: boolean;
    export let description: string;
    export let aliases: string[];

    let moduleNameElement: HTMLElement;
    let configurable: ConfigurableSetting;
    const path = `clickgui.${name}`;
    let expanded = false;
    let hasSettings = false;

    onMount(async () => {
        await fetchModuleSettings();

        setTimeout(() => {
            expanded = localStorage.getItem(path) === "true"
        }, 500);
    });

    highlightModuleName.subscribe((m) => {
        if (name !== m) {
            return;
        }

        setTimeout(() => {
            if (!moduleNameElement) {
                return;
            }
            moduleNameElement.scrollIntoView({
                behavior: "smooth",
                block: "center",
            });
        }, 1000);
    });

    async function fetchModuleSettings() {
        configurable = await getModuleSettings(name);
        hasSettings = configurable.value.filter(v => v.name !== "Bind" && v.name !== "Hidden").length > 0;
    }

    async function updateModuleSettings() {
        await setModuleSettings(name, configurable);
        await fetchModuleSettings();
    }

    async function toggleModule() {
        await setModuleEnabled(name, !enabled);
    }

    function setDescription() {
        if (!moduleNameElement) return;

        const boundingRect = moduleNameElement.getBoundingClientRect();
        const y = (boundingRect.top + (moduleNameElement.clientHeight / 2)) * (2 / $scaleFactor);

        let moduleDescription = description;
        if (aliases.length > 0) {
            moduleDescription += ` (aka ${aliases.map(name => $spaceSeperatedNames ? convertToSpacedString(name) : name).join(", ")})`;
        }

        // If element is less than 300px from the right, display description on the left
        if (window.innerWidth - boundingRect.right > 300) {
            const x = boundingRect.right * (2 / $scaleFactor);
            descriptionStore.set({
                x,
                y,
                anchor: "right",
                description: moduleDescription
            });
        } else {
            const x = boundingRect.left * (2 / $scaleFactor);

            descriptionStore.set({
                x,
                y,
                anchor: "left",
                description: moduleDescription
            });
        }
    }

    async function toggleExpanded() {
        expanded = !expanded;
        await setItem(path, expanded.toString());
    }
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<div
        class="module"
        class:expanded
        class:has-settings={hasSettings}
        in:slide={{ duration: 500, easing: quintOut }}
        out:slide={{ duration: 500, easing: quintOut }}
>
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <div
            class="name"
            on:contextmenu|preventDefault={toggleExpanded}
            on:click={toggleModule}
            on:mouseenter={setDescription}
            on:mouseleave={() => descriptionStore.set(null)}
            bind:this={moduleNameElement}
            class:enabled
            class:highlight={name === $highlightModuleName}
    >
        {$spaceSeperatedNames ? convertToSpacedString(name) : name}
    </div>

    {#if expanded && configurable}
        <div class="settings">
            {#each configurable.value as setting (setting.name)}
                <GenericSetting {path} bind:setting on:change={updateModuleSettings}/>
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .module {
    position: relative;
    margin: 4px 8px;
    border-radius: 16px;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    
    &:first-child {
      margin-top: 8px;
    }
    
    &:last-child {
      margin-bottom: 8px;
    }

    .name {
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
      color: var(--md-sys-color-on-surface-variant);
      text-align: left;
      font-size: 14px;
      font-weight: 500;
      position: relative;
      padding: 14px 16px;
      border-radius: 16px;
      background-color: transparent;
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 48px;

      &.highlight {
        background-color: var(--md-sys-color-secondary-container);
        color: var(--md-sys-color-on-secondary-container);
        box-shadow: var(--md-sys-elevation-level1);
        transform: scale(1.02);
        
        &::before {
          content: "";
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          border: 2px solid var(--md-sys-color-primary);
          border-radius: 16px;
          pointer-events: none;
          animation: pulse 2s infinite;
        }
      }

      &:hover {
        background-color: var(--md-sys-color-surface-container-highest);
        color: var(--md-sys-color-on-surface);
        transform: translateX(4px);
      }

      &:active {
        transform: scale(0.98) translateX(4px);
      }

      &.enabled {
        color: var(--md-sys-color-primary);
        background-color: rgba(var(--md-sys-color-primary-container), 0.3);
        
        &:hover {
          background-color: rgba(var(--md-sys-color-primary-container), 0.5);
        }
      }
    }

    .settings {
      background-color: var(--md-sys-color-surface-container);
      border-top: 1px solid var(--md-sys-color-outline-variant);
      padding: 12px 16px;
      border-radius: 0 0 16px 16px;
      margin-top: 0;
    }

    &.has-settings {
      .name::after {
        content: "";
        display: block;
        position: absolute;
        height: 20px;
        width: 20px;
        right: 16px;
        top: 50%;
        background-image: url("/img/clickgui/icon-settings-expand.svg");
        background-position: center;
        background-repeat: no-repeat;
        background-size: 16px;
        opacity: 0.6;
        transform-origin: 50% 50%;
        transform: translateY(-50%) rotate(-90deg);
        transition: all 0.4s cubic-bezier(0.2, 0, 0, 1);
        filter: invert(60%) sepia(0%) saturate(0%) hue-rotate(144deg) brightness(95%) contrast(85%);
      }

      &.expanded .name::after {
        transform: translateY(-50%) rotate(0);
        opacity: 1;
        filter: invert(30%) sepia(9%) saturate(2807%) hue-rotate(213deg) brightness(95%) contrast(88%);
      }
    }
  }

  @keyframes pulse {
    0% {
      box-shadow: 0 0 0 0 rgba(var(--md-sys-color-primary), 0.7);
    }
    70% {
      box-shadow: 0 0 0 8px rgba(var(--md-sys-color-primary), 0);
    }
    100% {
      box-shadow: 0 0 0 0 rgba(var(--md-sys-color-primary), 0);
    }
  }
</style>
