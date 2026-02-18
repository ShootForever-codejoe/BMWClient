<script lang="ts">
    import MainButton from "./buttons/MainButton.svelte";
    import ChildButton from "./buttons/ChildButton.svelte";
    import ButtonContainer from "../common/buttons/ButtonContainer.svelte";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import IconButton from "../common/buttons/IconButton.svelte";
    import {
        browse,
        exitClient,
        getClientUpdate,
        openScreen,
        toggleBackgroundShaderEnabled
    } from "../../../integration/rest";
    import Menu from "../common/Menu.svelte";
    import {fly} from "svelte/transition";
    import {onMount} from "svelte";
    import {notification} from "../common/header/notification_store";

    let regularButtonsShown = true;
    let clientButtonsShown = false;

    // Date and time state
    let currentDate = new Date();
    let formattedDate = "";
    let formattedTime = "";

    // Update date and time every second
    onMount(() => {
        // Initial update
        updateDateTime();
        
        // Set up interval to update every second
        const interval = setInterval(() => {
            updateDateTime();
        }, 1000);

        // Cleanup on unmount
        return () => clearInterval(interval);
    });

    function updateDateTime() {
        const now = new Date();
        currentDate = now;
        
        // Format date: "Saturday, February 14"
        const days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
        const months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
        
        const dayName = days[now.getDay()];
        const monthName = months[now.getMonth()];
        const day = now.getDate();
        
        formattedDate = `${dayName}, ${monthName} ${day}`;
        
        // Format time: "11:20"
        const hours = now.getHours().toString().padStart(2, '0');
        const minutes = now.getMinutes().toString().padStart(2, '0');
        formattedTime = `${hours}:${minutes}`;
    }

    function toggleButtons() {
        if (clientButtonsShown) {
            clientButtonsShown = false;
            setTimeout(() => {
                regularButtonsShown = true;
            }, 750);
        } else {
            regularButtonsShown = false;
            setTimeout(() => {
                clientButtonsShown = true;
            }, 750);
        }
    }
</script>

<Menu>
    <div class="content">
        <!-- Background overlay with anime image -->
        <div class="background-overlay"></div>
        
        <!-- Date and Time Display -->
        <div class="date-time-container">
            <div class="time">{formattedTime}</div>
            <div class="date">{formattedDate}</div>
        </div>

        <!-- Main Buttons -->
        <div class="main-buttons">
            {#if regularButtonsShown}
                <MainButton title="Singleplayer" icon="singleplayer" index={0}
                            on:click={() => openScreen("singleplayer")}/>
                
                <MainButton title="Multiplayer" icon="multiplayer" let:parentHovered
                            on:click={() => openScreen("multiplayer")} index={1}>
                    <ChildButton title="Realms" icon="realms" {parentHovered}
                                 on:click={() => openScreen("multiplayer_realms")}/>
                </MainButton>
                <MainButton title="Alts" icon="liquidbounce" on:click={toggleButtons} index={2}/>
                <MainButton title="Options" icon="options" on:click={() => openScreen("options")} index={3}/>
            {:else if clientButtonsShown}
                <MainButton title="Proxy Manager" icon="proxymanager" on:click={() => openScreen("proxymanager")}
                            index={0}/>
                <MainButton title="Click GUI" icon="clickgui" on:click={() => openScreen("clickgui")} index={1}/>
                <!-- <MainButton title="Scripts" icon="scripts" index={2}/> -->
                <MainButton title="Back" icon="back-large" on:click={toggleButtons} index={2}/>
            {/if}
        </div>

        <!-- Bottom Menu Buttons -->
        <div class="bottom-buttons" transition:fly|global={{duration: 700, y: 100}}>
            <ButtonContainer>
                <IconTextButton icon="icon-exit.svg" title="Exit" on:click={exitClient}/>
                <IconTextButton icon="icon-change-background.svg" title="Toggle Shader"
                                on:click={toggleBackgroundShaderEnabled}/>
            </ButtonContainer>
        </div>
    </div>
</Menu>

<style>
    .content {
        flex: 1;
        display: grid;
        grid-template-areas:
            "date"
            "main"
            "bottom";
        grid-template-rows: auto 1fr auto;
        position: relative;
        overflow: hidden;
    }

    /* Background overlay with anime image */
    .background-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-size: cover;
        background-position: center;
        z-index: -1;
        opacity: 0.8;
    }

    /* Date and Time Container */
    .date-time-container {
        grid-area: date;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 20px;
        text-align: center;
        z-index: 10;
    }

    .date {
        font-size: 24px;
        color: white;
        font-weight: 400;
        margin-bottom: 8px;
        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
    }

    .time {
        font-size: 48px;
        color: white;
        font-weight: 600;
        text-shadow: 0 4px 8px rgba(0, 0, 0, 0.7);
        font-family: 'Axiforma', sans-serif;
    }

    /* Main Buttons Area */
    .main-buttons {
        grid-area: main;
        display: flex;
        flex-direction: column;
        row-gap: 25px;
        align-items: center;
        padding: 40px 0;
    }

    /* Bottom Buttons */
    .bottom-buttons {
        grid-area: bottom;
        display: flex;
        justify-content: center;
        padding: 20px;
        z-index: 10;
    }

    /* Responsive adjustments */
    @media (max-width: 768px) {
        .date {
            font-size: 20px;
        }
        
        .time {
            font-size: 36px;
        }
        
        .main-buttons {
            padding: 20px 0;
        }
    }
</style>
