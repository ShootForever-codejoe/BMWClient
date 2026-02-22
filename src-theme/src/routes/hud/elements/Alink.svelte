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
    background: rgba($md-dark-on-primary, 0.45);
    border-radius: 8px;
    padding: 6px 12px;
    min-width: 200px;
    box-shadow: 0 4px 14px rgba($md-dark-primary, 0.5);
    backdrop-filter: blur(8px);
    border: 1px solid rgba(73, 69, 79, 0.6);
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
    background: rgba(54, 52, 59, 0.6);
    border-radius: 4px;
    overflow: hidden;
    position: relative;
  }

  .progress-bar {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg,
            #D0BCFF 0%,
            #CCC2DC 25%,
            #43e97b 50%,
            #38f9d7 75%,
            #D0BCFF 100%);
    background-size: 200% 100%;
    animation: gradientShift 2s ease-in-out infinite;
    transition: width 0.1s ease-out;
    box-shadow: 0 0 6px rgba(208, 188, 255, 0.3);
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
