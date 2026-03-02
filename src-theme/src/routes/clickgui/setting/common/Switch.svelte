<script lang="ts">
    import {createEventDispatcher} from "svelte";

    export let value: boolean;
    export let name: string;

    const dispatch = createEventDispatcher();
</script>

<label class="checkbox-container">
    <div class="checkbox-wrapper">
        <input type="checkbox" bind:checked={value} on:change={() => dispatch("change")}/>
        <span class="checkbox"></span>
    </div>

    <div class="name">{name}</div>
</label>

<style lang="scss">
  @use "sass:color";
  @use "../../../../colors.scss" as *;

  .checkbox-container {
    display: flex;
    align-items: center;
    cursor: pointer;
  }

  .name {
    font-weight: 500;
    color: $clickgui-text-color;
    font-size: 12px;
    margin-left: 7px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .checkbox-wrapper {
    position: relative;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .checkbox {
    position: absolute;
    top: 0;
    left: 0;
    width: 21px;
    height: 21px;
    background: $clickgui-settings-color;
    border: 1px solid $clickgui-border-color;
    border-radius: 5px;
    box-sizing: border-box;
    transition: background 0.5s;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  input {
    display: none;
  }

  input:checked + .checkbox {
    background: rgba(255, 255, 255, 0.01);
  }

  .checkbox::after {
    content: "";
    display: none;
    position: absolute;
    top: 1px;
    width: 4px;
    height: 11px;
    border: solid rgba(var(--accent-color), 1);
    border-width: 0 3px 3px 0;
    transform: rotate(45deg);
  }

  input:checked + .checkbox::after {
    display: block;
  }
</style>
