<script lang="ts">
    import ArmorStatus from "./ArmorStatus.svelte";
    import { listen } from "../../../../integration/ws.js";
    import type { PlayerData, Vec3 } from "../../../../integration/types";
    import { REST_BASE } from "../../../../integration/host";
    import { fly } from "svelte/transition";
    import HealthProgress from "./HealthProgress.svelte";
    import type { TargetChangeEvent } from "../../../../integration/events";

    let target: PlayerData | null = null;
    let visible = true;
    let playerPosition: Vec3 = { x: 0, y: 0, z: 0 };
    let lastX = 0, lastZ = 0;
    let hideTimeout: number;
    let playerData: PlayerData | null = null;

    function startHideTimeout() {
        hideTimeout = setTimeout(() => {
            visible = false;
        }, 500);
    }

    listen("targetChange", (data: TargetChangeEvent) => {
        target = data.target;
        visible = true;
        clearTimeout(hideTimeout);
        startHideTimeout();
    });

    listen("clientPlayerData", (event: any) => {
        playerData = event.playerData;
        lastX = playerPosition.x;
        lastZ = playerPosition.z;
        playerPosition = playerData.position;
    });

    function calculateDistance(pos1: Vec3, pos2: Vec3): number {
        const dx = pos1.x - pos2.x;
        const dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    function getDistanceToTarget(): number {
        if (target) {
            return calculateDistance(playerPosition, target.position);
        }
        return 0;
    }

    function getHealthStatus(): { letter: string, color: string } {
        if (playerData && target) {
            const playerHealth = playerData.actualHealth + playerData.absorption;
            const targetHealth = target.actualHealth + target.absorption;
            const letter = playerHealth > targetHealth ? "W" : "L";
            const color = letter === "W" ? "#00FF00" : "#FF0000"; // Яркие зеленый и красный
            return { letter, color };
        }
        return { letter: "", color: "" };
    }

    startHideTimeout();
</script>

{#if visible && target != null}
    <div class="targethud" transition:fly={{ y: -10, duration: 200 }}>
        <div class="avatar"></div>
        <div class="info">
            <div class="name-status">
                <span class="name">{target.username}</span>
                <span class="health-status" style="color: {getHealthStatus().color};">
                    {getHealthStatus().letter}
                </span>
            </div>
            <HealthProgress maxHealth={target.maxHealth + target.absorption} health={target.actualHealth + target.absorption} />
            <div class="stats-line">
                <div class="hp-container">
                    <span class="hp">
                        {((target.actualHealth + target.absorption) / (target.maxHealth + target.absorption) * 20).toFixed(1)}
                        <span class="heart">♥</span>
                    </span>
                </div>
                <span class="distance">{getDistanceToTarget().toFixed(1)}m</span>
                <div class="armor-inventory">
                    {#each [...target.armorItems].reverse() as item}
                        <div class="armor-slot">
                            {#if item}
                                <div class="icon-container">
                                    <img class="armor-icon" src="{REST_BASE}/api/v1/client/resource/itemTexture?id={item.identifier}" alt={item.identifier} />
                                    {#if item.hasEnchantment}
                                        <div class="enchantment-glow"></div>
                                    {/if}
                                </div>
                            {/if}
                        </div>
                    {/each}
                </div>
            </div>
        </div>
    </div>
{/if}

<style lang="scss">
  @import "../../../../colors.scss";

  .targethud {
    display: flex;
    align-items: center;
    gap: 5px;
    width: 260px;
    padding: 6.5px;
    border-radius: 15px;
    background: rgb($hotbar-base-color, 0.4);
    // box-shadow: 20px 5px 40px rgba($accent-color-2, 0.5),
    //             -20px -5px 40px rgba($accent-color, 0.5);
    box-shadow:2px 2px 30px 0px rgba(0,0,0,0.75);
    backdrop-filter: blur(10px);
  }

  .avatar {
    width: 55px;
    height: 55px;
    border-radius: 5px;
    overflow: hidden;
    image-rendering: pixelated;
    background-image: url("/img/steve.png");
    background-repeat: no-repeat;
    background-size: cover;
    flex-shrink: 0;
  }

  .info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 3px;
  }

  .name-status {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 16px;
    font-weight: 500;
    color: rgb(255, 255, 255);
    width: 100%;
  }

  .name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .health-status {
    font-size: 16px;
    font-weight: bold;
    margin-left: 4px;
  }

  .stats-line {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 14px;
    color: white;
  }

  .hp-container {
    display: flex;
    align-items: center;
    gap: 3px;
  }

  .heart {
    color: rgb(255, 59, 59);
  }

  .armor-inventory {
    display: flex;
    gap: 2px;
    margin-left: auto;
  }

  .armor-slot {
    width: 20px;
    height: 20px;
    background: rgba(15, 15, 15, 0.5);
    border-radius: 3px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .icon-container {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
  }

  .armor-icon {
    width: 16px;
    height: 16px;
    image-rendering: pixelated;
  }

  .enchantment-glow {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: radial-gradient(circle, rgba(112, 48, 160, 0.8), rgba(255, 105, 180, 0) 70%);
    mix-blend-mode: screen;
    pointer-events: none;
  }
</style>
