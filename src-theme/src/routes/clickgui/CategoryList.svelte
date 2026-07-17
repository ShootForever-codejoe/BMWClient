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
  import CategoryIcon from "./CategoryIcon.svelte";
  export let categories: { name: string, color: string }[];
  export let selected: string;
  export let onSelect: (name: string) => void;

  const categoryOrder = [
    "Combat",
    "Player",
    "Movement",
    "Render",
    "Misc",
    "World",
    "Exploit",
    "Fun",
    "Client"
  ];

  $: sortedCategories = [...categories].sort((a, b) => {
    const ai = categoryOrder.indexOf(a.name);
    const bi = categoryOrder.indexOf(b.name);
    if (ai === -1 && bi === -1) return a.name.localeCompare(b.name);
    if (ai === -1) return 1;
    if (bi === -1) return -1;
    return ai - bi;
  });

</script>

<div class="category-list">
  {#each sortedCategories as cat (cat.name)}
    <button type="button" class="category-item {selected === cat.name ? 'selected' : ''}"
            on:click={() => onSelect(cat.name)}>
      <CategoryIcon name={cat.name} selected={selected === cat.name} />
      <span class="name">{cat.name}</span>
    </button>
  {/each}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

.category-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 0 0 0 0;
}
.category-item {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 42px;
  background: linear-gradient(110deg, rgba(var(--accent-color), 0.07), rgba(0, 0, 0, 0.24));
  border: 1px solid transparent;
  border-right: 2px solid transparent;
  border-radius: 18px;
  padding: 8px 10px;
  cursor: pointer;
  width: 100%;
  color: inherit;
  font: inherit;
  text-align: left;
  transition: background 0.18s, border-color 0.18s;
  position: relative;
  &:hover,
  &.selected {
    background: rgba(var(--accent-color), 0.1);
    border-color: rgba(var(--accent-color), 0.22);
    border-right-color: rgba(var(--accent-color), 0.95);
    box-shadow: var(--theme-shadow-soft);
  }
  .name {
    flex: 1;
    font-weight: 500;
    color: $clickgui-text-color;
    font-size: clamp(14px, 0.8vw, 17px);
  }
  &.selected {
    background: rgba(var(--accent-color), 0.16);
    box-shadow: var(--theme-shadow);
    .name {
      color: $clickgui-text-color;
      font-weight: 600;
    }
  }
}
</style>
