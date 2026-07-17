<script lang="ts">
    import {onDestroy, onMount, tick} from "svelte";
    import type {Module} from "../../../integration/types";
    import {getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {getTextWidth} from "../../../integration/text_measurement";
    import {flip} from "svelte/animate";
    import {fly} from "svelte/transition";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    const MODULE_FONT = '500 14px "Axiforma"';
    let enabledModules: Module[] = [];
    let updateRevision = 0;

    async function updateEnabledModules() {
        const revision = ++updateRevision;
        await document.fonts.ready;

        const modules = await getModules();
        if (revision !== updateRevision) return;
        const visibleModules = modules.filter(m => m.enabled && !m.hidden);

        const modulesWithWidths = visibleModules.map(module => {
                let formattedName = $spaceSeperatedNames ? convertToSpacedString(module.name) : module.name;
                let fullName = module.tag == null ? formattedName : formattedName + " " + module.tag;

                return {
                    ...module,
                    width: getTextWidth(fullName, MODULE_FONT)
                };
            }
        );

        modulesWithWidths.sort((a, b) => b.width - a.width || a.name.localeCompare(b.name));

        enabledModules = modulesWithWidths;
        await tick();
    }

    const unsubscribeNames = spaceSeperatedNames.subscribe(async () => {
        await updateEnabledModules();
    });

    onDestroy(unsubscribeNames);

    onMount(async () => {
        await updateEnabledModules();
    });

    listen("moduleToggle", async () => {
        await updateEnabledModules();
    });

    listen("refreshArrayList", async () => {
        await updateEnabledModules();
    });
</script>

<div class="arraylist">
    {#each enabledModules as {name, tag} (name)}
        <div class="module" animate:flip={{ duration: 200 }} transition:fly={{ x: 50, duration: 200 }}>
            {$spaceSeperatedNames ? convertToSpacedString(name) : name}
            {#if tag}
                <span class="tag">{tag}</span>
            {/if}
        </div>
    {/each}
</div>

<style lang="scss">
  @use "../../../colors.scss" as *;

  .arraylist {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 0;
  }

  .module {
    display: flex;
    align-items: baseline;
    justify-content: flex-end;
    gap: 4px;
    background: linear-gradient(90deg, rgba($arraylist-base-color, 0.38), rgba(var(--accent-color), 0.14));
    color: $arraylist-text-color;
    font-family: "Axiforma", sans-serif;
    font-size: 14px;
    line-height: 1.15;
    border-radius: 8px 0 0 8px;
    padding: 5px 8px;
    border-right: 4px solid rgb(var(--accent-color));
    width: max-content;
    font-weight: 500;
    white-space: nowrap;
    box-shadow: var(--theme-shadow-soft);
  }

  .tag {
    color: rgb(var(--accent-color));
  }
</style>
