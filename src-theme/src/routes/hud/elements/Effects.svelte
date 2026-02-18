<script lang="ts">
    import { listen } from "../../../integration/ws";
    import type { ClientPlayerDataEvent } from "../../../integration/events";
    import type { StatusEffect } from "../../../integration/types";
    import { fly } from "svelte/transition";
    import { expoInOut } from "svelte/easing";
    import { REST_BASE } from "../../../integration/host";
    import type { fromStore } from "svelte/store";

    let effects: StatusEffect[] = [];
    // 一个对象，用来存储每个药水效果的最大持续时间
    let maxDurations: { [key: string]: number } = {};

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        const newEffects = event.playerData.effects;
        const newEffectIds = new Set(newEffects.map(e => e.effect));

        // 清理 maxDurations 中已经消失的效果
        for (const effectId in maxDurations) {
            if (!newEffectIds.has(effectId)) {
                delete maxDurations[effectId];
            }
        }

        // 遍历当前所有效果
        for (const effect of newEffects) {
            // 如果是新效果，记录其最大时长
            if (effect.effect && !maxDurations[effect.effect]) {
                maxDurations[effect.effect] = effect.duration;
            }
        }

        // 触发 Svelte 的响应式更新
        maxDurations = maxDurations;
        effects = newEffects;
    });

    // 辅助函数，将16进制颜色码转换为带有透明度的 RGBA 格式
    function hexToRgba(hex: number, alpha: number): string {
        if (typeof hex !== 'number') return `rgba(255, 255, 255, ${alpha})`;
        const r = (hex >> 16) & 255;
        const g = (hex >> 8) & 255;
        const b = hex & 255;
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }

    // 根据效果计算背景样式的函数
    function getProgressStyle(effect: StatusEffect): string {
        const max = maxDurations[effect.effect];
        const current = effect.duration;

        // 如果没有最大时长或时长为0，直接返回默认背景色
        if (!max || max <= 0) {
            return 'background-color: rgba(20, 20, 20, 0.5);';
        }

        const progress = (current / max) * 100;

        // 修改：将进度条颜色固定为半透明黑色
        const progressColor = 'rgba(0, 0, 0, 0.55)';
        // 进度条的底色（已消耗部分）保持原来的颜色
        const bgColor = 'rgba(20, 20, 20, 0.5)';

        // 使用 linear-gradient 创建进度条效果
        return `background: linear-gradient(to right, ${progressColor} ${progress}%, ${bgColor} ${progress}%);`;
    }

    // --- 原有的函数保持不变 ---
    function formatTime(duration: number): string {
        if (typeof duration !== 'number' || duration < 0) return "00:00";
        return new Date(((duration / 20) | 0) * 1000).toISOString().substring(14, 19);
    }

    function formatAmplifier(amplifier: number): string {
        if (typeof amplifier !== 'number' || amplifier < 0) return "";
        const level = amplifier + 1;
        const roman = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];
        if (level > 0 && level <= roman.length) {
            return roman[level - 1];
        }
        return level > 0 ? level.toString() : "";
    }
</script>

<div class="effects">
    {#each effects as e (e.effect)}
        {#if e && e.effect}
            <div class="main">
                <!-- 在这里通过 style 属性动态应用我们计算好的背景样式 -->
                <div class="effect"
                     transition:fly={{duration: 700, y: 50, easing: expoInOut}}
                     style={getProgressStyle(e)}>
                    <div class="text-info">
                    <span class="name" style="color: {'#' + (e.color?.toString(16).padStart(6, '0') || 'FFFFFF')}">
                        {e.localizedName || 'Unknown Effect'} {formatAmplifier(e.amplifier)}
                    </span>
                        <!-- 你仍然可以取消注释这行来显示时间 -->
                        <!-- <span class="duration">{formatTime(e.duration)}</span> -->
                    </div>
                </div>
            </div>
        {/if}
    {/each}
</div>

<style lang="scss">
  @import "../../../colors.scss";

  .main {
    display: flex;
    align-items: center;
  }
  .effects {
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: fit-content;
  }

  .effect {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
    /* 移除固定的 background-color，因为它现在由 style 属性动态控制 */
    box-shadow: 0 0 5px 3px rgba(rgb(0, 0, 0), 0.3);
    border-radius: 10px;
    width: 160px;

    /* 为背景变化添加平滑的过渡效果 */
    transition: background 0.2s linear;
  }

  .icon-con {
    width: 34px;
    height:34px;
    background-color: rgba(20, 20, 20, 0.5);
    box-shadow: 0 0 5px 3px rgba(rgb(0, 0, 0), 0.3);
    border-radius: 10px;
    margin-right: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .icon {
    width: 25px;
    height: 25px;
    object-fit: contain;
    margin: 0;
    padding: 0;
  }

  .text-info {
    display: flex;
    flex-direction: column;
    padding: 0 5px;
  }

  .name {
    font-weight: 600;
    font-size: 14px;
    color: white;
    margin-left: 6px;
  }

  .duration {
    color: #cccccc;
    font-size: 12px;
  }
</style>

