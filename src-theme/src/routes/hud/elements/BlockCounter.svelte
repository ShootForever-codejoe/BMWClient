<script lang="ts">
    import {listen} from "../../../integration/ws";
    import {fly} from "svelte/transition";
    import type {BlockCountChangeEvent} from "../../../integration/events";
    import {mapToColor} from "../../../util/color_utils";

    let count = $state<number | undefined>(undefined);

    listen("blockCountChange", (data: BlockCountChangeEvent) => {
        count = data.count;
    });
</script>

{#if count !== undefined}
    <div class="counter" style="color: {mapToColor(count)}" in:fly={{ y: -5, duration: 200 }}
         out:fly={{ y: -5, duration: 200 }}>
        Amount:{count}
    </div>
{/if}

<style lang="scss">
  @use "../../../colors.scss" as *;

  .counter {
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.16), rgba($blockcounter-base-color, 0.72));
    box-shadow: var(--theme-shadow);
    border-radius: 5px;
    white-space: nowrap;
    padding: 5px 8px;
    font-weight: 500;
    text-align: center;
    width: fit-content;
    transform: translate(-100%);
  }
</style>
