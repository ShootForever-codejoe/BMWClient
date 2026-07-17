<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {BooleanSetting as TBooleanSetting, ModuleSetting, TogglableSetting,} from "../../../integration/types";
    import ExpandArrow from "./common/ExpandArrow.svelte";
    import GenericSetting from "./common/GenericSetting.svelte";
    import Switch from "./common/Switch.svelte";
    import {setItem} from "../../../integration/persistent_storage";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    export let setting: ModuleSetting;
    export let path: string;
    export let moduleName: string;

    const cSetting = setting as TogglableSetting;
    const thisPath = `${path}.${cSetting.name}`;

    const dispatch = createEventDispatcher();

    const enabledSetting = cSetting.value[0] as TBooleanSetting;

    let nestedSettings = cSetting.value.slice(1);

    let expanded = localStorage.getItem(thisPath) === "true";

    $: setItem(thisPath, expanded.toString());

    function handleChange() {
        cSetting.value = [enabledSetting, ...nestedSettings];
        setting = { ...cSetting };
        dispatch("change");
    }

    function toggleExpanded() {
        expanded = !expanded;
    }
</script>

<div class="setting">
    {#if nestedSettings.length > 0}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div class="head expand" class:expanded on:contextmenu|preventDefault={toggleExpanded}>
            <Switch
                name={$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}
                bind:value={enabledSetting.value}
                on:change={handleChange}
            />
            <ExpandArrow bind:expanded />
        </div>
    {:else}
        <div class="head" class:expanded>
            <Switch
                name={$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}
                bind:value={enabledSetting.value}
                on:change={handleChange}
            />
        </div>
    {/if}

    {#if expanded}
        <div class="nested-settings">
            {#each nestedSettings as setting (setting.name)}
                <GenericSetting path={thisPath} bind:setting moduleName={moduleName} on:change={handleChange} />
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">
    @use "../../../colors.scss" as *;

    .setting {
        padding: 7px 0px;
    }

    .head {
        transition: ease margin-bottom .2s;

        &.expand {
          display: grid;
          grid-template-columns: 1fr max-content;
        }

        &.expanded {
            margin-bottom: 10px;
        }
    }

    .nested-settings {
      background: linear-gradient(145deg, rgba(var(--accent-color), 0.12), rgba(8, 4, 10, 0.82));
      border-radius: 16px;
      border: 1px solid $clickgui-border-color;
      padding: 2.5px 7.5px 2.5px 7.5px;
    }
</style>
