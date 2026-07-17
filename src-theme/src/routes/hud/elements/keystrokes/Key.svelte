<script lang="ts">
    import {listen} from "../../../../integration/ws";
    import type {KeyEvent} from "../../../../integration/events";
    import type {MinecraftKeybind} from "../../../../integration/types";

    export let gridArea: string;
    export let key: MinecraftKeybind | undefined;

    let active = false;

    listen("key", (e: KeyEvent) => {
        if (e.key !== key?.key.translationKey) {
            return;
        }

        active = e.action === 1 || e.action === 2;
    });

    function getDisplayText(): string {
        if (!key) return "???";
        
        const translationKey = key.key.translationKey;
        const localizedText = key.key.localized;
        
        // 处理空格键显示为长横线
        if (translationKey === "key.keyboard.space") {
            return "────────"; // 长横线
        }
        
        // 处理鼠标按键
        if (translationKey.startsWith("key.mouse.")) {
            const mouseButton = translationKey.split(".")[2];
            switch (mouseButton) {
                case "left": return "LMB";
                case "right": return "RMB";
                case "middle": return "MMB";
                default: return localizedText;
            }
        }
        
        // 其他按键保持原有显示
        return localizedText;
    }
</script>

<div class="key" style="grid-area: {gridArea};" class:active>
    {getDisplayText()}
</div>

<style lang="scss">
  @use "../../../../colors.scss" as *;

  .key {
    height: 50px;
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.12), rgba($keystrokes-base-color, 0.72));
    color: $keystrokes-text-color;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    font-size: 14px;
    font-weight: 500;
    transition: ease box-shadow .2s;
    position: relative;
    box-shadow: inset 0 0 0 0 rgb(var(--accent-color)), var(--theme-shadow-soft);
    text-align: center;

    &.active {
    box-shadow: inset 0 0 0 25px rgb(var(--accent-color)), var(--theme-glow);
    }
  }
</style>
