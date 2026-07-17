<script lang="ts">
    import {onDestroy, onMount} from "svelte";
    import {
        getModuleSettings,
        setModuleSettings,
    } from "../../integration/rest";
    import type {ConfigurableSetting} from "../../integration/types";
    import GenericSetting from "./setting/common/GenericSetting.svelte";
    import {description as descriptionStore, highlightModuleName} from "./clickgui_store";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";
    import {scaleFactor} from "./clickgui_store";

    export let name: string;
    export let description: string;
    export let aliases: string[];

    let moduleNameElement: HTMLElement;
    let configurable: ConfigurableSetting;
    const path = `clickgui.${name}`;
    let hasSettings = false;
    let settingsRevision = 0;
    let pendingSettings: ConfigurableSetting | null = null;
    let savingSettings = false;
    let highlightTimer: ReturnType<typeof setTimeout> | null = null;

    onMount(async () => {
        await fetchModuleSettings();
    });

    const unsubscribeHighlight = highlightModuleName.subscribe((m) => {
        if (name !== m) {
            return;
        }

        if (highlightTimer !== null) {
            clearTimeout(highlightTimer);
        }
        highlightTimer = setTimeout(() => {
            if (!moduleNameElement) {
                return;
            }
            moduleNameElement.scrollIntoView({
                behavior: "smooth",
                block: "center",
            });
        }, 1000);
    });

    onDestroy(() => {
        unsubscribeHighlight();
        if (highlightTimer !== null) {
            clearTimeout(highlightTimer);
        }
    });

    async function fetchModuleSettings() {
        configurable = await getModuleSettings(name);
        configurable.value = configurable.value.filter(v => v.name !== "Bind");
        hasSettings = configurable.value.length > 0;
        settingsRevision++;
    }

    function updateModuleSettings() {
        pendingSettings = JSON.parse(JSON.stringify(configurable)) as ConfigurableSetting;
        void flushModuleSettings();
    }

    async function flushModuleSettings() {
        if (savingSettings) return;
        savingSettings = true;

        try {
            while (pendingSettings !== null) {
                const settings = pendingSettings;
                pendingSettings = null;
                await setModuleSettings(name, settings);
            }
        } catch (error) {
            console.error(`Failed to save settings for ${name}`, error);
            pendingSettings = null;
            try {
                await fetchModuleSettings();
            } catch (reloadError) {
                console.error(`Failed to reload settings for ${name}`, reloadError);
            }
        } finally {
            savingSettings = false;
            if (pendingSettings !== null) {
                void flushModuleSettings();
            }
        }
    }

    function setDescription() {
        if (!moduleNameElement) return;

        const boundingRect = moduleNameElement.getBoundingClientRect();
        const y = (boundingRect.top + (moduleNameElement.clientHeight / 2)) * (2 / $scaleFactor);

        let moduleDescription = description;
        if (aliases.length > 0) {
            moduleDescription += ` (aka ${aliases.map(name => $spaceSeperatedNames ? convertToSpacedString(name) : name).join(", ")})`;
        }

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
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<div
        class="module"
        class:has-settings={hasSettings}
>
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <div
            class="name"
            on:mouseenter={setDescription}
            on:mouseleave={() => descriptionStore.set(null)}
            bind:this={moduleNameElement}
    >
        {$spaceSeperatedNames ? convertToSpacedString(name) : name}
    </div>

    {#if configurable}
        <div class="settings">
            {#key settingsRevision}
                {#each configurable.value as setting (setting.name)}
                    <GenericSetting {path} bind:setting moduleName={name} on:change={updateModuleSettings}/>
                {/each}
            {/key}
        </div>
    {/if}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .module {
    position: relative;

    .name {
      position: relative;
      color: $clickgui-text-color;
      bottom: 0;
      margin-bottom: 12px;
      padding-bottom: 8px;
      font-size: clamp(17px, 1vw, 20px);
      text-align: left;
      font-weight: 600;
      border-bottom: 1px solid rgba(var(--accent-color), 0.38);
    }

    .settings {
      padding: 10px;
      background: linear-gradient(145deg, rgba(var(--accent-color), 0.1), rgba(0, 0, 0, 0.32));
      border-radius: 18px;
      border: 1px solid rgba(var(--accent-color), 0.2);
      border-right: 2px solid rgba(var(--accent-color), 0.75);
      box-shadow: var(--theme-shadow-soft);
    }
  }
</style>
