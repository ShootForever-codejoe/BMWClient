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
    export let name: string;
    export let selected: boolean;
    let gifError = false;
</script>

{#if selected}
    {#if !gifError}
        <img class="category-icon" src={`img/clickgui/icon-${name.toLowerCase()}.gif`} alt={name} on:error={() => gifError = true} />
    {:else}
        <img class="category-icon" src={`img/clickgui/icon-${name.toLowerCase()}.svg`} alt={name} />
    {/if}
{:else}
    <img class="category-icon" src={`img/clickgui/icon-${name.toLowerCase()}.svg`} alt={name} />
{/if}

<style lang="scss">
.category-icon {
  width: 24px;
  height: 24px;
  margin-right: 10px;
  display: inline-block;
}
</style>
