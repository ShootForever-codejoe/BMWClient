<script lang="ts">
    import { listen } from "../../../integration/ws.js";
    import { fly } from "svelte/transition";
    import type { AlinkUpdateEvent } from "../../../integration/events";

    let alinkTicks: number = 0;
    let alinkMaxDelay: number = 1;
    let visible: boolean = false;

    listen("alinkUpdate", (data: AlinkUpdateEvent) => {
        alinkTicks = data.alinkTicks;
        alinkMaxDelay = data.alinkMaxDelay;
        visible = data.visible;
    });
</script>

{#if visible}
    <div class="alink-container" transition:fly={{ y: -10, duration: 200 }}>
        <div class="alink-box">
            <div class="alink-title">Alink</div>
            <div class="progress-container">
                <div class="progress-bar" style="width: {(alinkTicks / alinkMaxDelay * 100)}%"></div>
            </div>
        </div>
    </div>
{/if}

<style lang="scss">
  @import "../../../colors.scss";

  .alink-container {
    position: fixed;
    top: 60%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 1000;
  }

  .alink-box {
    background:
            linear-gradient(135deg, rgba(var(--accent-color), 0.16), rgba(var(--accent-color), 0.04)),
            rgba(10, 9, 13, 0.72);
    border-radius: 12px;
    padding: 6px 12px;
    min-width: 200px;
    box-shadow: var(--theme-shadow);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(var(--accent-color), 0.26);
  }

  .alink-title {
    color: #E6E0E9;
    font-size: 14px;
    font-weight: 600;
    text-align: center;
    margin-bottom: 4px;
    text-shadow: 0 1px 1px rgba(0, 0, 0, 0.3);
  }

  .progress-container {
    width: 100%;
    height: 8px;
    background: rgba(var(--accent-color), 0.1);
    border: 1px solid rgba(var(--accent-color), 0.12);
    border-radius: 4px;
    overflow: hidden;
    position: relative;
  }

  .progress-bar {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg,
            rgba(var(--accent-color), 0.55) 0%,
            rgb(var(--accent-color)) 25%,
            #ffffff 50%,
            rgb(var(--accent-color)) 75%,
            rgba(var(--accent-color), 0.55) 100%);
    background-size: 200% 100%;
    animation: gradientShift 2s ease-in-out infinite;
    transition: width 0.1s ease-out;
    box-shadow: 0 0 8px rgba(var(--accent-color), 0.18);
  }

  @keyframes gradientShift {
    0% {
      background-position: 0 50%;
    }
    50% {
      background-position: 100% 50%;
    }
    100% {
      background-position: 0 50%;
    }
  }
</style>
