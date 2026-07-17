<script lang="ts">
    import type {ItemStack} from "../../../../integration/types";
    import {listen} from "../../../../integration/ws";
    import type {ClientPlayerInventoryEvent, PlayerInventory} from "../../../../integration/events";
    import ItemStackView from "./ItemStackView.svelte";
    import {onMount} from "svelte";
    import {getPlayerInventory} from "../../../../integration/rest";

    let stacks: ItemStack[] = [];

    function updateStacks(inventory: PlayerInventory) {
        stacks = inventory.main.slice(9);
    }

    listen("clientPlayerInventory", (data: ClientPlayerInventoryEvent) => {
        updateStacks(data.inventory);
    });

    onMount(async () => {
        const inventory = await getPlayerInventory();
        updateStacks(inventory);
    });
</script>

<div class="container">
    {#each stacks as stack (stack)}
        <ItemStackView {stack}/>
    {/each}
</div>

<style lang="scss">
  @use "../../../../colors" as *;

  .container {
    background: linear-gradient(145deg, rgba(var(--accent-color), 0.13), rgba($hotbar-base-color, 0.58));
    box-shadow: var(--theme-shadow);
    padding: 4px;
    border-radius: 5px;
    display: grid;
    grid-template-columns: repeat(9, 1fr);
    gap: 0.5rem;
  }
</style>
