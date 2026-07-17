<script lang="ts">
    import { listen } from "../../../../integration/ws.js";
    import type { PlayerData, TargetData, Vec3 } from "../../../../integration/types";
    import { REST_BASE } from "../../../../integration/host";
    import { fly } from "svelte/transition";
    import HealthProgress from "./HealthProgress.svelte";
    import type { TargetChangeEvent } from "../../../../integration/events";

    let target: TargetData | null = null;
    let visible = true;
    let playerPosition: Vec3 = { x: 0, y: 0, z: 0 };
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
        const currentPlayerData = event.playerData as PlayerData;
        playerData = currentPlayerData;
        playerPosition = currentPlayerData.position;
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

    function getTargetTexture(): string {
        if (!target) return "/img/steve.png";
        if (target.isPlayer && target.avatar) return target.avatar;
        if (target.texture) return `${REST_BASE}/api/v1/client/resource?id=${encodeURIComponent(target.texture)}`;
        return "/img/steve.png";
    }

    function handleTextureError(event: Event) {
        const image = event.currentTarget as HTMLImageElement;
        if (!image.src.endsWith("/img/steve.png")) image.src = "/img/steve.png";
    }

    function getHealthStatus(): { letter: string, color: string } {
        if (playerData && target) {
            const playerHealth = playerData.actualHealth + playerData.absorption;
            const targetHealth = target.actualHealth + target.absorption;
            const letter = playerHealth > targetHealth ? "W" : "L";
            const color = letter === "W" ? "#00FF00" : "#FF0000";
            return { letter, color };
        }
        return { letter: "", color: "" };
    }

    startHideTimeout();
</script>

{#if visible && target != null}
    <div class="targethud" transition:fly={{ y: -10, duration: 200 }}>
        <div class="avatar">
            <img src={getTargetTexture()} alt={target.username} on:error={handleTextureError} />
        </div>
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
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.17), rgb($hotbar-base-color, 0.48));
    box-shadow: var(--theme-shadow-raised);
    backdrop-filter: blur(10px);
  }

  .avatar {
    width: 55px;
    height: 55px;
    border-radius: 5px;
    overflow: hidden;
    image-rendering: pixelated;
    background: url("/img/steve.png") center / cover no-repeat;
    flex-shrink: 0;
  }

  .avatar img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    image-rendering: pixelated;
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
    background: linear-gradient(110deg, rgba(var(--accent-color), 0.1), rgba(15, 15, 15, 0.55));
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
    background: radial-gradient(circle, rgba(var(--accent-color), 0.78), rgba(var(--accent-color), 0) 70%);
    mix-blend-mode: screen;
    pointer-events: none;
  }
</style>
