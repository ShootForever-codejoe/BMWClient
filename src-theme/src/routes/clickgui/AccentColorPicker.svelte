<!--
  - This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
  -
  - Copyright (c) 2015 - 2025 CCBlueX
  -
  - LiquidBounce is free software: you can redistribute it and/or modify
  - it under the terms of the GNU General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - LiquidBounce is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  - GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License
  - along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
  -->

<script lang="ts">
    import "@simonwep/pickr/dist/themes/classic.min.css";
    import "./setting/pickr.scss";
    import { createEventDispatcher, onMount, onDestroy } from "svelte";
    import Pickr from "@simonwep/pickr";
    import { setAccentColor, accentColorStore } from '../../theme/accentColorStore';
    import { saveClickGuiColor } from '../../integration/persistent_storage';

    const dispatch = createEventDispatcher();
    let colorPicker: HTMLElement;
    let pickr: Pickr;
    let hidden = true;
    let hex: string;

    let r = 255, g = 255, b = 255, a = 1;
    let unsubscribeAccent: () => void;

    export let value: string;

    function parseHexToRgb(hex: string) {
        if (hex.startsWith('#')) {
            let h = hex.replace('#', '');
            if (h.length === 3) h = h.split('').map(x => x + x).join('');
            if (h.length !== 6) return { r: 255, g: 255, b: 255 };
            const num = parseInt(h, 16);
            return {
                r: (num >> 16) & 255,
                g: (num >> 8) & 255,
                b: num & 255
            };
        }
        return { r: 255, g: 255, b: 255 };
    }

    function updateInputsFromPickr(v: any) {
        const arr = v.toRGBA();
        r = Math.round(arr[0]);
        g = Math.round(arr[1]);
        b = Math.round(arr[2]);
        a = Math.round(arr[3] * 100) / 100;
    }

    function updatePickrFromInputs() {
        if (pickr) {
            const hexStr = pickr.getColor().toHEXA().toString();
            hex = hexStr;
            setAccentColor(hexStr);
            dispatch("change", hexStr);
        }
    }

    function rgbToHex(r: number, g: number, b: number): string {
        return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
    }

    function onRgbInput() {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        const hexStr = rgbToHex(r, g, b);
        hex = hexStr;
        if (pickr) pickr.setColor(hexStr);
        setAccentColor(hexStr);
        saveClickGuiColor(hexStr);
        dispatch("change", hexStr);
    }

    onMount(() => {
        unsubscribeAccent = accentColorStore.subscribe((color: string) => {
            hex = color;
            const rgb = parseHexToRgb(hex);
            r = rgb.r;
            g = rgb.g;
            b = rgb.b;
            if (pickr) pickr.setColor(hex);
        });
        pickr = Pickr.create({
            el: colorPicker,
            theme: "classic",
            showAlways: true,
            inline: true,
            default: hex,
            components: {
                preview: false,
                opacity: false,
                hue: true,
                interaction: {
                    hex: false,
                    rgba: false,
                    hsla: false,
                    hsva: false,
                    cmyk: false,
                    input: false,
                    clear: false,
                    save: false,
                },
            },
        });
        pickr.on("change", (v: any) => {
            updateInputsFromPickr(v);
            const hexStr = v.toHEXA().toString();
            hex = hexStr;
            setAccentColor(hexStr);
            saveClickGuiColor(hexStr);
            dispatch("change", hexStr);
        });
        pickr.setColor(hex);
    });

    onDestroy(() => {
        if (pickr) pickr.destroyAndRemove();
        if (unsubscribeAccent) unsubscribeAccent();
        window.removeEventListener('mousedown', handleClickOutside);
    });

    let pickerContainer: HTMLElement;

    function handleClickOutside(event: MouseEvent) {
        if (!pickerContainer) return;
        if (!pickerContainer.contains(event.target as Node)) {
            hidden = true;
        }
    }

    $: if (!hidden) {
        window.addEventListener('mousedown', handleClickOutside);
    } else {
        window.removeEventListener('mousedown', handleClickOutside);
    }

    function togglePicker() {
        hidden = !hidden;
    }
</script>

<div style="display: flex; align-items: center; position: relative; gap: 10px;" bind:this={pickerContainer}>
    <div style="display: flex; flex-direction: column; align-items: center;">
        <button class="accent-color-icon-btn {hidden ? '' : 'active'}" on:click={togglePicker} aria-label="Choose color of theme">
            {#if hidden}
                <img src="./img/clickgui/icon-colorpicker.svg" alt="palette" class="palette-icon-img" />
            {:else}
                <img src="./img/clickgui/icon-colorpicker.gif" alt="palette" class="palette-icon-img" />
            {/if}
        </button>
        <div class="color-picker {hidden ? 'hidden' : ''}" style="position: absolute; left: -80px; top: 35px; width: 200px; z-index: 1000;">
            <div bind:this={colorPicker}></div>
            <div class="rgba-inputs">
                <label>R <input type="number" min="0" max="255" bind:value={r} on:input={onRgbInput} /></label>
                <label>G <input type="number" min="0" max="255" bind:value={g} on:input={onRgbInput} /></label>
                <label>B <input type="number" min="0" max="255" bind:value={b} on:input={onRgbInput} /></label>
            </div>
        </div>
    </div>
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .color-picker {
    background: $clickgui-settings-color;
    border: 1px solid $clickgui-border-color;
    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.35);
    border-radius: 7.5px;
    padding: 2.5px 7.5px 5px 7.5px;
    margin-top: 5px;
    margin-left: -10px;
  }

.accent-color-icon-btn {
    background: none;
    border: none;
    padding: 2px;
    margin-right: 10px;
    cursor: pointer;
    border-radius: 5px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;
    &.active {
        background: #2a2b38;
    }
    &:hover {
        background: #2a2b38;
    }
    svg {
        display: block;
    }
}

.color-picker.hidden {
    display: none;
}

.rgba-inputs {
    display: flex;
    gap: 5px;
    margin-top: 8px;
    justify-content: space-between;
    label {
        color: $clickgui-text-color;
        font-size: 12px;
        display: flex;
        flex-direction: column;
        align-items: center;
        input {
            width: 35px;
            border-radius: 5px;
            border: 1px solid $clickgui-border-color;
            background: $clickgui-settings-color;
            color: $clickgui-text-color;
            padding: 2px;
            text-align: center;
        }
        input[type=number]::-webkit-outer-spin-button,
        input[type=number]::-webkit-inner-spin-button {
            -webkit-appearance: none;
            margin: 0;
        }
    }
}

.palette-icon-img {
    width: 26px;
    height: 26px;
    display: block;
}
</style>
