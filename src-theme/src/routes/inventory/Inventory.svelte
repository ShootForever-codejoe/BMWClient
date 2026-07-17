<script>
    import {fade} from 'svelte/transition';
</script>

<span class="watermark" role="img" aria-label="BMWClient"
      in:fade={{ duration: 100 }} out:fade={{ duration: 100 }}></span>

<style>
    .watermark {
        position: absolute;
        bottom: 15px;
        right: 15px;
        width: 165px;
        height: 42px;
        background: rgb(var(--accent-color));
        -webkit-mask: url("/img/lb-logo.svg") center / contain no-repeat;
        mask: url("/img/lb-logo.svg") center / contain no-repeat;
        filter: drop-shadow(0 0 8px rgba(var(--accent-color), 0.2));
        opacity: 0.6;
    }
</style>
