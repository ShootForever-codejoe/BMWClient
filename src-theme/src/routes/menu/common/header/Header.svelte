<script lang="ts">
    import Account from "./account/Account.svelte";
    import Notifications from "./Notifications.svelte";
    import {listen} from "../../../../integration/ws";
    import type {
        AccountManagerAdditionEvent,
        AccountManagerLoginEvent,
        AccountManagerMessageEvent
    } from "../../../../integration/events";
    import {notification} from "./notification_store";

    listen("accountManagerAddition", (e: AccountManagerAdditionEvent) => {
        if (!e.error) {
            notification.set({
                title: "AltManager",
                message: `Successfully added account ${e.username}`,
                error: false
            });
        } else {
            notification.set({
                title: "AltManager",
                message: e.error,
                error: true
            });
        }
    });

    listen("accountManagerMessage", (e: AccountManagerMessageEvent) => {
        notification.set({
            title: "AltManager",
            message: e.message,
            error: false
        });
    });

    listen("accountManagerLogin", (e: AccountManagerLoginEvent) => {
        if (!e.error) {
            notification.set({
                title: "AltManager",
                message: `Successfully logged in to account ${e.username}`,
                error: false
            });
        } else {
            notification.set({
                title: "AltManager",
                message: e.error,
                error: true
            });
        }
    });
</script>

<div class="header">
    <span class="logo" role="img" aria-label="BMWClient"></span>

    <Notifications />

    <Account/>
</div>

<style lang="scss">
  .header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 60px;
    align-items: center;
  }

  .logo {
    display: block;
    width: 165px;
    height: 42px;
    background: rgb(var(--accent-color));
    -webkit-mask: url("/img/lb-logo.svg") left center / contain no-repeat;
    mask: url("/img/lb-logo.svg") left center / contain no-repeat;
    filter: drop-shadow(0 0 8px rgba(var(--accent-color), 0.2));
  }
</style>
