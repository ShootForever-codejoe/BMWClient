<script lang="ts">
    import {fly} from "svelte/transition";
    import {description, type TDescription} from "./clickgui_store";

    let data: TDescription | null = null;

    description.subscribe((v) => {
        data = v;
    });

    let element: HTMLElement | null = null;
    let left = 0;
    let anchor: "right" | "left" = "right";

    $: {
        if (data?.x !== undefined && element !== null) {
            anchor = data.anchor;
            if (data.anchor === "left") {
                left = data.x - element.clientWidth - 20;
            } else {
                left = data.x + 20;
            }
        }
    }
</script>

{#key data}
    {#if data !== null}
        <div transition:fly|global={{duration: 200, x: anchor === "right" ? -15 : 15}} class="description-wrapper"
             style="top: {data.y}px; left: {left}px;" bind:this={element}>
            <div class="description" class:right={anchor === "left"}>
                <div class="text">{data.description}</div>
            </div>
        </div>
    {/if}
{/key}

<style lang="scss">
  @use "../../colors.scss" as *;

  .description-wrapper {
    position: fixed;
    z-index: 999999999999;
    transform: translateY(-50%);
    animation: fadeInSlide 0.2s cubic-bezier(0.2, 0, 0, 1);
  }

  @keyframes fadeInSlide {
    from {
      opacity: 0;
      transform: translateY(-40%) translateX(var(--translate-x, 0));
    }
    to {
      opacity: 1;
      transform: translateY(-50%) translateX(var(--translate-x, 0));
    }
  }

  .description {
    position: relative;
    border-radius: 20px;
    background-color: var(--md-sys-color-surface-container-high);
    box-shadow: var(--md-sys-elevation-level3);
    border: 1px solid var(--md-sys-color-outline-variant);
    backdrop-filter: blur(24px);
    min-width: 200px;
    max-width: 320px;

    &::before {
      content: "";
      display: block;
      position: absolute;
      width: 0;
      height: 0;
      border-top: 10px solid transparent;
      border-bottom: 10px solid transparent;
      border-right: 10px solid var(--md-sys-color-surface-container-high);
      left: -10px;
      top: 50%;
      transform: translateY(-50%);
      filter: drop-shadow(-2px 0 2px var(--md-sys-color-outline-variant));
    }

    &.right {
      &::before {
        transform: translateY(-50%) rotate(180deg);
        left: unset;
        right: -10px;
        filter: drop-shadow(2px 0 2px var(--md-sys-color-outline-variant));
      }
    }
  }

  .text {
    font-size: 13px;
    padding: 14px 18px;
    color: var(--md-sys-color-on-surface);
    line-height: 1.5;
    font-weight: 400;
    letter-spacing: 0.1px;
  }
</style>