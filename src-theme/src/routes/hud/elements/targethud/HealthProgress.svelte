<script lang="ts">
    export let maxHealth: number;
    export let health: number;

    let width = Math.ceil((health / maxHealth) * 100);
    let prevWidth = width;

    $: {
        let newWidth = Math.ceil((health / maxHealth) * 100);

        if (newWidth < prevWidth) {
            setTimeout(() => prevWidth = newWidth, 500);
        } else {
            prevWidth = newWidth;
        }

        width = newWidth;
    }
</script>

<div class="health-progress">
    <div class="delayed-thumb" style="width: {prevWidth}%;"></div>
    <div class="thumb" style="width: {width}%;"></div>
</div>

<style lang="scss">;
    @use "../../../../colors.scss" as *;

.health-progress {
  position: relative;
  width: 100%;
  height: 10px;
  border-radius: 10px;
  background: rgba(0, 15, 25, 0.6);
  overflow: hidden;
}

.delayed-thumb {
  position: absolute;
  height: 100%;
//   background: linear-gradient(90deg, rgba($accent-color, 0.3), rgba($accent-color-2, 0.3));
  background-color: rgba(255, 59, 59, 0.726);
  transition: width 0.1s ease-out;
  clip-path: inset(0 0 0 0 round 10px);
}

.thumb {
  position: absolute;
  height: 100%;
//   background: linear-gradient(90deg, rgba($accent-color, 0.8), rgba($accent-color-2, 0.8));
  background-color: rgba(255, 59, 59, 0.692);
  transition: width 0.2s ease-in-out;
  clip-path: inset(0 0 0 0 round 10px);
}
</style>
