<script lang="ts">
    import {onMount, onDestroy, tick} from "svelte";
    import CategoryList from "./CategoryList.svelte";
    import ModuleComponent from "./Module.svelte";
    import ModuleCard from "./ModuleCard.svelte";
    import {getModules, getClientInfo, setModuleEnabled} from "../../integration/rest";
    import {groupByCategory, hexToRgbString} from "../../integration/util";
    import type {Module, ClientInfo} from "../../integration/types";
    import Search from "./Search.svelte";
    import {listen} from "../../integration/ws";
    import AccentColorPicker from "./AccentColorPicker.svelte";
    import {accentColorStore} from '../../theme/accentColorStore';
    import {getClickGuiColor} from '../../integration/persistent_storage';

    const categoryColors: Record<string, string> = {
        "Combat": "#3A86FF",
        "Player": "#FFBE0B",
        "Movement": "#FB5607",
        "Render": "#FF006E",
        "Misc": "#8338EC",
        "World": "#00B4D8",
        "Exploit": "#FF4D6D",
        "Client": "#43AA8B",
        "Fun": "#FFD60A"
    };

    let categories: { name: string, color: string, count: number }[] = [];
    let modulesByCategory: {[cat: string]: Module[]} = {};
    let selectedCategory = "";
    let selectedModule: Module | null = null;
    let clientInfo: ClientInfo | null = null;
    let allModules: Module[] = [];
    let moduleSettingsCount: {[name: string]: number} = {};

    let menuX = 0;
    let menuY = 0;
    let dragging = false;
    let offsetX = 0;
    let offsetY = 0;
    let lastMouseX = 0;
    let lastMouseY = 0;
    let animationFrame: number | null = null;

    let accentColor = "dodgerblue";
    const unsubscribeAccent = accentColorStore.subscribe((color: string) => {
        accentColor = color;
        document.documentElement.style.setProperty('--accent-color', hexToRgbString(accentColor));
    });

    async function refreshModules() {
        const modules = await getModules();
        const grouped = groupByCategory(modules);
        for (const cat of Object.keys(grouped)) {
            grouped[cat] = grouped[cat].map(m => ({...m}));
        }
        modulesByCategory = {...grouped};
        categories = Object.keys(grouped).map(cat => ({
            name: cat,
            color: categoryColors[cat] ?? "#888",
            count: grouped[cat].length
        }));
        allModules = Object.values(grouped).flat();
        clientInfo = await getClientInfo();
        for (const cat of Object.keys(grouped)) {
            for (const mod of grouped[cat]) {
                const settings = await import("../../integration/rest").then(m => m.getModuleSettings(mod.name));
                moduleSettingsCount[mod.name] = settings.value.filter(s => s.name !== "Bind" && s.name !== "Hidden").length;
            }
        }
    }

    onMount(async () => {
        accentColorStore.set(getClickGuiColor() ?? '#1e90ff');

        const menuWidth = 1200;
        const menuHeight = 1200;
        menuX = Math.max(0, (window.innerWidth - menuWidth) / 2);
        menuY = Math.max(0, (window.innerHeight - menuHeight) / 2);

        window.addEventListener('openClickGui', refreshModules);
        window.addEventListener('refreshModules', refreshModules);
        window.addEventListener('moduleSettingsChanged', refreshModules);

        const modules = await getModules();
        const grouped = groupByCategory(modules);
        modulesByCategory = grouped;
        categories = Object.keys(grouped).map(cat => ({
            name: cat,
            color: categoryColors[cat] ?? "#888",
            count: grouped[cat].length
        }));
        const savedCategory = localStorage.getItem("lb_selectedCategory");
        if (savedCategory && categories.some(c => c.name === savedCategory)) {
            selectedCategory = savedCategory;
        } else {
            const defaultCategory = "Combat";
            selectedCategory = categories.find(c => c.name === defaultCategory)?.name || categories[0]?.name || "";
        }
        allModules = Object.values(grouped).flat();
        clientInfo = await getClientInfo();

        for (const cat of Object.keys(grouped)) {
            for (const mod of grouped[cat]) {
                const settings = await import("../../integration/rest").then(m => m.getModuleSettings(mod.name));
                moduleSettingsCount[mod.name] = settings.value.filter(s => s.name !== "Bind" && s.name !== "Hidden").length;
            }
        }

        listen("moduleToggle", (e) => {
            for (const cat of Object.keys(modulesByCategory)) {
                modulesByCategory[cat] = modulesByCategory[cat].map(m => m.name === e.moduleName ? { ...m, enabled: e.enabled } : { ...m });
            }
            allModules = allModules.map(m => m.name === e.moduleName ? { ...m, enabled: e.enabled } : { ...m });
            if (selectedModule && selectedModule.name === e.moduleName) {
                selectedModule = { ...selectedModule, enabled: e.enabled };
            }
        });
    });

    onDestroy(() => {
        window.removeEventListener('openClickGui', refreshModules);
        window.removeEventListener('refreshModules', refreshModules);
        window.removeEventListener('moduleSettingsChanged', refreshModules);
        unsubscribeAccent();
    });

    let moduleGridPanel: HTMLElement;
    let highlightedModule: string | null = null;

    async function handleCategorySelect(name: string) {
        selectedCategory = name;
        selectedModule = null;
        window.dispatchEvent(new Event('closeBindPanels'));
        if (moduleGridPanel) moduleGridPanel.scrollTop = 0;
    }
    async function handleModuleSettings(module: Module) {
        selectedModule = module;
    }
    function smoothScroll(container: HTMLElement, target: HTMLElement, duration = 500) {
        const start = container.scrollTop;
        const end = target.offsetTop - container.clientHeight / 2 + target.clientHeight / 2;
        const distance = end - start;
        let startTime: number | null = null;

        function step(timestamp: number) {
            if (!startTime) startTime = timestamp;
            const progress = Math.min((timestamp - startTime) / duration, 1);
            const ease = 0.5 - Math.cos(progress * Math.PI) / 2;
            container.scrollTop = start + distance * ease;
            if (progress < 1) requestAnimationFrame(step);
        }

        requestAnimationFrame(step);
    }
    async function jumpToModule(moduleName: string) {
        for (const cat of Object.keys(modulesByCategory)) {
            const mod = modulesByCategory[cat].find(m => m.name === moduleName);
            if (mod) {
                selectedCategory = cat;
                selectedModule = mod;
                await handleModuleSettings(mod);
                await tick();

                const grid = moduleGridPanel?.querySelector('.module-grid');
                if (grid) {
                    const card = Array.from(grid.children).find(
                        (el: any) => el.querySelector('.name')?.textContent === moduleName
                    ) as HTMLElement | undefined;

                    if (card) {
                        smoothScroll(moduleGridPanel, card, 1000);
                    }
                }
                highlightedModule = moduleName;
                setTimeout(() => {
                    if (highlightedModule === moduleName) highlightedModule = null;
                }, 1000);
                break;
            }
        }
    }
    async function handleModuleToggle(module: Module) {
        module.enabled = !module.enabled;
        if (typeof setModuleEnabled === 'function') {
            await setModuleEnabled(module.name, module.enabled);
        }
        const modules = await getModules();
        const grouped = groupByCategory(modules);
        modulesByCategory = grouped;
        allModules = Object.values(grouped).flat();
    }

    $: if (selectedCategory) {
        localStorage.setItem("lb_selectedCategory", selectedCategory);
    }

    function onMouseDown(event: MouseEvent) {
        const target = event.target as HTMLElement;
        if (target.closest('.easter-egg-img')) return;
        if (target.closest('.lb-watermark')) return;
        if (target.closest('.color-picker')) return;
        if (target.closest('.search-block')) return;
        dragging = true;
        offsetX = event.clientX - menuX;
        offsetY = event.clientY - menuY;
        lastMouseX = event.clientX;
        lastMouseY = event.clientY;
        window.addEventListener('mousemove', onMouseMove, { passive: false });
        window.addEventListener('mouseup', onMouseUp);
    }
    function onMouseMove(event: MouseEvent) {
        event.preventDefault();
        lastMouseX = event.clientX;
        lastMouseY = event.clientY;
        if (!animationFrame) {
            animationFrame = requestAnimationFrame(updateMenuPosition);
        }
    }
    function updateMenuPosition() {
        if (dragging) {
            menuX = lastMouseX - offsetX;
            menuY = lastMouseY - offsetY;
            animationFrame = requestAnimationFrame(updateMenuPosition);
        } else {
            animationFrame = null;
        }
    }
    function onMouseUp() {
        dragging = false;
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);
        if (animationFrame) {
            cancelAnimationFrame(animationFrame);
            animationFrame = null;
        }
    }

</script>

<div class="clickgui-menu" style="position: absolute; left: {menuX}px; top: {menuY}px;">
    <div class="top-panel" on:mousedown={onMouseDown} style="cursor: grab; user-select: none;" role="region">
        <div class="window-controls">
            <img class="lb-watermark" src={`img/lb-watermark.svg`} style="cursor: pointer;" />
        </div>
        <div class="title-block">
            <span class="title">BMWClient</span>
        </div>
        <div class="search-block">
            <AccentColorPicker value={accentColor} on:change={e => accentColor = e.detail} />
            <Search modules={allModules} onJumpToModule={jumpToModule} />
        </div>
    </div>
    <div class="main-content">
        <div class="category-list-panel">
            <CategoryList categories={categories} selected={selectedCategory} onSelect={handleCategorySelect}/>
        </div>
        <div class="module-grid-panel" bind:this={moduleGridPanel}>
            <div class="module-grid">
                {#each modulesByCategory[selectedCategory] as module, index}
                    <ModuleCard
                        module={{
                            ...module,
                            settingsCount: moduleSettingsCount[module.name] ?? 0,
                            color: categories.find(c => c.name === selectedCategory)?.color ?? "#888",
                        }}
                        onSettings={() => handleModuleSettings(module)}
                        onToggle={() => handleModuleToggle(module)}
                        highlighted={highlightedModule === module.name}
                    />
                {/each}
            </div>
        </div>
        <div class="settings-panel">
            {#if selectedModule}
                {#key selectedModule.name}
                    <ModuleComponent
                        name={selectedModule.name}
                        enabled={selectedModule.enabled}
                        description={selectedModule.description}
                        aliases={selectedModule.aliases}
                    />
                {/key}
            {:else}
                <div class="settings-desc">Here's a modules settings</div>
            {/if}
        </div>
    </div>
    <div class="footer-panel">
<!--        <span class="footer-text">LiquidBounce Nextgen</span>-->
<!--        <span class="footer-version">V{clientInfo ? clientInfo.clientVersion : "..."}</span>-->
    </div>
</div>

<style lang="scss">
  @use "../../colors.scss" as *;

  .clickgui-menu {
    //background: $clickgui-base-color;
    background: linear-gradient(
                    145deg,
                    #0c0f14 0%,
                    #141923 50%,
                    #0a0c10 100%
    );
    border: 1px solid $clickgui-border-color;
    //box-shadow: 0 0 25px rgba($clickgui-base-color, 0.75);
    box-shadow: 0 4px 4px #00000030, 0 10px 10px #00000015;
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    position: relative;
    width: 1200px;
    max-width: 1500px;
    height: 650px;
    max-height: 1000px;
    overflow: hidden;
    margin: 17.5vh auto;
    font-family: "Inter", sans-serif;
    transition: none !important;
  }

  .top-panel {
    display: flex;
    cursor: grab;
    align-items: center;
    height: 70px;
    padding: 12px 16px;
    //background: $clickgui-base-color;
    border-bottom: 1px solid $clickgui-border-color;
    .window-controls {
      display: flex;
      align-items: center;
      .lb-watermark {
        width: 40px;
        height: 40px;
        user-select: none;
        /* pointer-events: none; */
      }
    }
    .title-block {
      margin-left: 15px;
      flex: 1;
      display:flex;
      flex-direction:column;
      .title {
        font-size: 16px;
        font-weight: 1000;
        color: $clickgui-text-color;
      }
    }
    .search-block {
      margin-left: auto;
      display: flex;
      align-items: center;
      min-width: 250px;
      max-width: 400px;
      height: 100%;
      justify-content: flex-end;
    }
  }

  .main-content {
    display: flex;
    flex: 1;
    overflow: hidden;
    //background: $clickgui-base-color;
  }

  .category-list-panel {
    width: 200px;
    padding: 10px;
    //background: $clickgui-base-color;
    border-right: 1px solid $clickgui-border-color;
    display: flex;
    flex-direction: column;
    height: 100%;
    position: relative;
  }

  .module-grid-panel {
    flex: 2;
    padding: 7.5px 7.5px;
    overflow-y: auto;
    scroll-behavior: smooth;
    scrollbar-width: none;
    -ms-overflow-style: none;
    &::-webkit-scrollbar {
      display: none;
    }
    .module-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 10px;
    }
  }

  .settings-panel {
    width: 300px;
    padding: 10px;
    //background: $clickgui-base-color;
    border-left: 1px solid $clickgui-border-color;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
    scroll-behavior: smooth;
    &::-webkit-scrollbar {
      display: none;
    }
    .settings-desc {
      color: $clickgui-text-dimmed-color;
      font-size: 22.5px;
      line-height: 450px;
    }
  }

  .easter-egg-img {
    position: absolute;
    left: 1px;
    top: 65px;
    z-index: 12;
    img {
      max-width: 300px;
      max-height: 275px;
      border-radius: 10px;
    }
  }

      @media (max-width: 900px) {
        .clickgui-menu {
          width: 95vw;
          height: 90vh;
        }
        .category-list-panel {
          width: 200px;
          padding: 20px;
        }
        .settings-panel {
          width: 300px;
          padding: 20px;
        }
      }

      @media (max-width: 600px) {
        .clickgui-menu {
          flex-direction: column;
          height: auto;
          min-height: 100vh;
        }
        .main-content {
          flex-direction: column;
        }
        .category-list-panel, .settings-panel {
          width: 100%;
          border: none;
          padding: 20px;
        }
        .module-grid-panel {
          padding: 20px;
        }
      }
</style>
