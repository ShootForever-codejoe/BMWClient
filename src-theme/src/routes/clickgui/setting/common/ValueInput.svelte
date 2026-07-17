<script lang="ts">
    import {createEventDispatcher} from "svelte";

    export let value: number;
    export let valueType: "int" | "float";

    let inputElement: HTMLElement;
    let inputValue = "";

    $: {
        if (document.activeElement !== inputElement) {
            inputValue = value.toString();
        }
    }

    const dispatch = createEventDispatcher<{
        change: { value: number }
    }>();

    function handleInput() {
        let parsed: number;
        if (valueType === "float") {
            parsed = parseFloat(inputValue);
        } else {
            parsed = parseInt(inputValue);
        }

        if (!isNaN(parsed)) {
            dispatch("change", {value: parsed});
        }
    }

    function handleKeyDown(e: KeyboardEvent) {
        if (e.key === "Enter") {
            e.preventDefault();
        }
    }
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<span contenteditable="true" class="value" bind:innerText={inputValue} on:input={handleInput} on:keydown={handleKeyDown} bind:this={inputElement}></span>

<style lang="scss">
  @use "../../../../colors.scss" as *;

  .value {
    font-family: monospace;
    color: $clickgui-text-color;
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.12), rgba(8, 4, 10, 0.96));
    border-radius: 5px;
    padding: 2.5px 3.5px 2.5px 3.5px;
    font-weight: 500;
    font-size: 12px;
    border: 1px solid $clickgui-border-color;
    display: inline-block;
  }
</style>
