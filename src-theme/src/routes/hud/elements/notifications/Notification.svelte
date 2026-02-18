<script lang="ts">
    export let title: string;
    export let message: string;
    export let severity: string;
</script>

<div class="notification">
    <div class="icon {severity.toString().toLowerCase()}"></div>
    <div class="title">{title}</div>
    <div class="message">{message}</div>
</div>

<style lang="scss">
  .notification {
    display: grid;
    grid-template-areas:
            "a b"
            "a c";
    grid-template-columns: max-content 1fr;
    column-gap: 12px;
    row-gap: 4px;
    background: transparent; /* 使用父容器背景 */
    border-radius: 24px;
    width: 200px;
    padding: 0;
    margin: 0;
  }

  .icon {
    height: 40px;
    width: 40px;
    background-position: center;
    background-repeat: no-repeat;
    border-radius: 12px; /* Android 16 圆角 */
    grid-area: a;
    transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    
    /* 使用 Material Design 图标颜色 */
    &.success {
      background-color: var(--md-sys-color-primary-container);
      color: var(--md-sys-color-on-primary-container);
      &::before {
        content: "✓";
        font-size: 20px;
        font-weight: bold;
      }
    }

    &.error {
      background-color: var(--md-sys-color-error-container);
      color: var(--md-sys-color-on-error-container);
      &::before {
        content: "✕";
        font-size: 20px;
        font-weight: bold;
      }
    }

    &.info {
      background-color: var(--md-sys-color-secondary-container);
      color: var(--md-sys-color-on-secondary-container);
      &::before {
        content: "ℹ";
        font-size: 20px;
        font-weight: bold;
      }
    }

    &.warning {
      background-color: var(--md-sys-color-warning-container);
      color: var(--md-sys-color-on-warning-container);
      &::before {
        content: "⚠";
        font-size: 20px;
        font-weight: bold;
      }
    }

    &.disabled,
    &.enabled {
      background-color: transparent;
      color: var(--md-sys-color-primary);
      
      &::before {
        content: "";
        position: absolute;
        height: 20px;
        width: 36px;
        border-radius: 15px;
        background: var(--md-sys-color-primary);
        transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
        border: 2px solid var(--md-sys-color-outline);
        right: 0;
      }
      
      &::after {
        content: "";
        position: absolute;
        height: 12px;
        width: 12px;
        border-radius: 6px;
        background: var(--md-sys-color-primary);
        transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
        box-shadow: var(--md-sys-elevation-level1);
        right: 18px;
        top: 50%;
        transform: translateY(-50%);
      }
    }

    &.enabled {
      &::before {
        background: var(--md-sys-color-primary);
      }
      
      &::after {
        right: 6px;
        background: white;
      }
    }

    &.disabled {
      &::before {
        background: transparent;
      }
      
      &::after {
        right: 18px;
      }
    }
  }

  .title {
    grid-area: b;
    font-size: 14px;
    color: var(--md-sys-color-primary);
    font-weight: 500;
    line-height: 1.4;
  }

  .message {
    grid-area: c;
    font-size: 12px;
    color: var(--md-sys-color-primary);
    line-height: 1.4;
    opacity: 0.87;
  }
</style>
