<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {BindSetting, ModuleSetting} from "../../../integration/types";
    import {listen} from "../../../integration/ws";
    import {getPrintableKeyName} from "../../../integration/rest";
    import type {KeyboardKeyEvent, MouseButtonEvent} from "../../../integration/events";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import Dropdown from "./common/Dropdown.svelte";
    export let setting: ModuleSetting;
    export let moduleName: string;

    const cSetting = setting as BindSetting;

    const UNKNOWN_KEY = "key.keyboard.unknown";

    const dispatch = createEventDispatcher();

    let isHovered = false;
    let binding = false;
    let printableKeyName = "";

    $: {
        if (cSetting.value.boundKey !== UNKNOWN_KEY) {
            getPrintableKeyName(cSetting.value.boundKey)
                .then(printableKey => {
                    printableKeyName = printableKey.localized;
                });
        }
    }

    listen("keyboardKey", async (e: KeyboardKeyEvent) => {
        if (e.screen === undefined || !e.screen.class.startsWith("net.ccbluex.liquidbounce") ||
            !(e.screen.title === "ClickGUI" || e.screen.title === "VS-CLICKGUI")) {
            return;
        }

        if (!binding) {
            return;
        }

        if (e.keyCode !== 256) {
            cSetting.value.boundKey = e.key;
        } else {
            cSetting.value.boundKey = UNKNOWN_KEY;
        }
        setting = {...cSetting};
        dispatch("change");
        binding = false;
    });

    listen("mouseButton", async (e: MouseButtonEvent) => {
        if (e.screen === undefined || !e.screen.class.startsWith("net.ccbluex.liquidbounce") ||
            !(e.screen.title === "ClickGUI" || e.screen.title === "VS-CLICKGUI")) {
            return;
        }

        if (!binding || (e.button === 0 && isHovered)) {
            return;
        }
        cSetting.value.boundKey = `mouse.${e.button}`;
        setting = {...cSetting};
        dispatch("change");
        binding = false;
    })

    async function toggleBinding() {
        if (binding) {
            cSetting.value.boundKey = UNKNOWN_KEY;
            setting = {...cSetting};
            binding = false;
            dispatch("change");
        } else {
            binding = true;
        }
    }

    function handleActionChange() {
        setting = {...cSetting};
        dispatch("change");
    }
</script>

<div class="setting" class:has-value={cSetting.value.boundKey !== UNKNOWN_KEY}>
    <button
            class="change-bind"
            on:click={toggleBinding}
            on:mouseenter={() => isHovered = true}
            on:mouseleave={() => isHovered = false}
    >
        {#if !binding}
            <div class="name">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}:</div>

            {#if cSetting.value.boundKey === UNKNOWN_KEY}
                <span class="none">None</span>
            {:else}
                <span>{printableKeyName}</span>
            {/if}
        {:else}
            <span>Press any key</span>
        {/if}
    </button>

    {#if cSetting.value.boundKey !== UNKNOWN_KEY}
        <Dropdown name={null} options={["Toggle", "Hold"]} bind:value={cSetting.value.action}
                  on:change={handleActionChange}/>
    {/if}
</div>

<style lang="scss">
  @use "../../../colors.scss" as *;

  .setting {
    padding: 5px 0px;
    display: grid;
    grid-template-columns: 1fr;
    column-gap: 5px;

    &.has-value {
      grid-template-columns: 1fr max-content;
    }
  }

  .change-bind {
    background: $clickgui-settings-color;
    border: solid 1px $clickgui-border-color;
    border-radius: 5px;
    padding: 8px;
    cursor: pointer;
    font-weight: 500;
    justify-content: center;
    align-items: center;
    color: $clickgui-text-color;
    font-size: 12px;
    font-family: "Inter", sans-serif;
    width: 100%;
    display: flex;
    column-gap: 5px;

    .name {
      font-weight: 500;
    }

    .none {
      color: $clickgui-text-dimmed-color;
    }
  }
</style>
