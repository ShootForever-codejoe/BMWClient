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
    <div class="category-item {selected === cat.name ? 'selected' : ''}"
         on:click={() => onSelect(cat.name)}>
      <CategoryIcon name={cat.name} selected={selected === cat.name} />
      <span class="name">{cat.name}</span>
    </div>
  {/each}
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

.category-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 0 0 0;
}
.category-item {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.05);
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.2);
  border-radius: 7.5px;
  padding: 7.5px 10px;
  cursor: pointer;
  transition: background 0.25s, box-shadow 0.25s, transform 0.25s ease;
  position: relative;
  &:hover,
  &.selected {
    background: rgba(var(--accent-color), 0.25);
    box-shadow: 0 0 10px rgba(var(--accent-color), 0.25);
  }
  .name {
    flex: 1;
    font-weight: 500;
    color: $clickgui-text-color;
    text-shadow: 0 3px 6px rgba(0, 0, 0, 0.5);
    font-size: 16px;
  }
  &.selected {
    background: rgba(var(—accent-color), 0.5);
    box-shadow: 0 0 10px rgba(var(--accent-color), 0.35);
    transform: translateX(2.5px);
    transition: 0.25s ease;
    .name {
      color: $clickgui-text-color;
      font-weight: 500;
    }
  }
}
</style>
