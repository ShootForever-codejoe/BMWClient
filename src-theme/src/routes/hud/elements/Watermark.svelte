<script lang="ts">
    import { getClientInfo, getSession, getServers, getModules } from "../../../integration/rest";
    import type { ClientInfo, Session, Server } from "../../../integration/types";
    import { onMount } from "svelte";
    import { listen } from "../../../integration/ws";

    let clientInfo: ClientInfo | null = null;
    let session: Session | null = null;

    let showUsername = true;
    let protectedName = "ProtectedUser";

    async function updateClientInfo() {
        clientInfo = await getClientInfo();
    }

    async function updateSession() {
        session = await getSession();
    }

    async function updateShowUsername() {
        const modules = await getModules();
        const np = modules.find(m => m.name === "NameProtect");
        showUsername = !(np && np.enabled);
    }

    onMount(async () => {
        await updateClientInfo();
        await updateSession();
        await updateShowUsername();
        setInterval(async () => {
            await updateClientInfo();
            await updateSession();
            await updateShowUsername();
        }, 1000);
    });

    listen("session", async () => {
        await updateSession();
    });

</script>

<div class="watermark">
    <div class="watermark-content">
        <div class="logo">
            <img src="img/clickgui/icon-client.svg" alt="icon"/>
        </div>
        {#if session }
            <div class="information">
                <svg xmlns="http://www.w3.org/2000/svg" height="12" width="10.5" viewBox="0 0 448 512"><!--!Font Awesome Free 6.7.2 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free Copyright 2025 Fonticons, Inc.--><path fill="#ffffff" d="M224 256A128 128 0 1 0 224 0a128 128 0 1 0 0 256zm-45.7 48C79.8 304 0 383.8 0 482.3C0 498.7 13.3 512 29.7 512l388.6 0c16.4 0 29.7-13.3 29.7-29.7C448 383.8 368.2 304 269.7 304l-91.4 0z"/></svg>
                {#if showUsername}
                    {session.username}
                {:else}
                    {protectedName}
                {/if}
            </div>
        {/if}
        {#if clientInfo}
            <div class="information">
                <svg xmlns="http://www.w3.org/2000/svg" height="12" width="12" viewBox="0 0 512 512"><!--!Font Awesome Free 6.7.2 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free Copyright 2025 Fonticons, Inc.--><path fill="#ffffff" d="M64 64c0-17.7-14.3-32-32-32S0 46.3 0 64L0 400c0 44.2 35.8 80 80 80l400 0c17.7 0 32-14.3 32-32s-14.3-32-32-32L80 416c-8.8 0-16-7.2-16-16L64 64zm406.6 86.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L320 210.7l-57.4-57.4c-12.5-12.5-32.8-12.5-45.3 0l-112 112c-12.5 12.5-12.5 32.8 0 45.3s32.8 12.5 45.3 0L240 221.3l57.4 57.4c12.5 12.5 32.8 12.5 45.3 0l128-128z"/></svg>
                <span class="fps-value">{clientInfo.fps}</span>
            </div>
        {/if}
    </div>
</div>

<style lang="scss">
  @use "../../../colors.scss" as *;

  .watermark {
    padding: 8px 14px;
    background: rgba($md-dark-on-primary, 0.45);
    border: 1px solid rgba(255, 255, 255, 0.15);
    box-shadow: 0 4px 14px rgba($md-dark-primary, 0.5);
    border-radius: 7.5px;
    color: white;
    font-size: 14px;
    font-weight: 600;
    display: flex;
    align-items: center;
  }

  .watermark-content {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .logo img {
    display: block;
    width: 24px;
    height: 24px;
    filter: drop-shadow(0 0 10px rgba(var(--accent-color), 0.5));
  }

  .information {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 3px 6px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.1);
    transition: 0.25s ease;
  }

  .fps-value {
    display: inline-block;
    min-width: 25px;
    max-width: 40px;
    text-align: center;
  }

  svg {
    color: $hotbar-text-color;
  }
</style>
