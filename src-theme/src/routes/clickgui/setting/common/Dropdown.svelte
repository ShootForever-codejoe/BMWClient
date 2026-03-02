<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../../theme/theme_config";

    export let name: string | null;
    export let options: string[];
    export let value: string;

    const dispatch = createEventDispatcher();

    let expanded = false;
    let dropdownHead: HTMLElement;

    function windowClickHide(e: MouseEvent) {
        if (!dropdownHead.contains(e.target as Node)) {
            expanded = false;
        }
    }

    function updateValue(v: string) {
        value = v;
        dispatch("change");
    }
</script>

<svelte:window on:click={windowClickHide}/>
<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="dropdown" class:expanded on:click={() => (expanded = !expanded)}>
    <div class="head" bind:this={dropdownHead}>
        {#if name !== null}
            <span class="text">{$spaceSeperatedNames ? convertToSpacedString(name) : name}
                &bull; {$spaceSeperatedNames ? convertToSpacedString(value) : value}</span>
        {:else}
            <span class="text">{$spaceSeperatedNames ? convertToSpacedString(value) : value}</span>
        {/if}
    </div>

    {#if expanded}
        <div class="options">
            {#each options as o}
                <div
                        class="option"
                        class:active={o === value}
                        on:click={() => updateValue(o)}
                >
                    {$spaceSeperatedNames ? convertToSpacedString(o) : o}
                </div>
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">
  @use "../../../../colors.scss" as *;

  .dropdown {
    position: relative;

    &.expanded {
      .text::after {
        transform: translateY(-50%) rotate(-180deg);
        opacity: 1;
      }

      .head {
      }
    }
  }

  .head {
    background: $clickgui-settings-color;
    border: 1px solid $clickgui-border-color;
    padding: 8px;
    display: flex;
    align-items: center;
    position: relative;
    cursor: pointer;
    border-radius: 7.5px;
    transition: color 0.2s ease, border-color 0.2s ease;

    .text {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      color: $clickgui-text-color;
      font-size: 12px;
      font-weight: 500;
      margin-right: 20px;
    }

    .text::after {
      content: "";
      display: block;
      position: absolute;
      height: 10px;
      width: 10px;
      right: 10px;
      top: 50%;
      background-image: url("/img/clickgui/icon-settings-expand.svg");
      background-position: center;
      background-repeat: no-repeat;
      transform-origin: 50% 50%;
      transform: translateY(-50%) rotate(-0deg);
      transition: ease opacity 0.2s,
      ease transform 0.3s;
    }
  }

  .options {
    position: absolute;
    top: 35px;
    width: 100%;
    background: $clickgui-settings-color;
    border: 1px solid $clickgui-border-color;
    border-radius: 10px;
    box-shadow: 0 4px 8px rgba(0,0,0,0.35);
    z-index: 10;
    overflow: hidden;
    color: rgba(255, 255, 255, 0.4);

    .option {
      padding: 5px 10px;
      cursor: pointer;
      transition: background 0.15s ease, color 0.15s ease;

      &:hover {
        background: rgba(var(--accent-color), 0.15);
        color: $clickgui-text-color;
      }

      &.active {
        color: rgba(var(--accent-color), 1);
        text-shadow: 0 0 10px rgba(var(--accent-color), 0.5);
      }
    }
  }
</style>
