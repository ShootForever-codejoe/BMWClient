<script lang="ts">
    import {onMount, onDestroy, tick} from "svelte";
    import CategoryList from "./CategoryList.svelte";
    import ModuleComponent from "./Module.svelte";
    import ModuleCard from "./ModuleCard.svelte";
    import {getModules, getClientInfo, getModuleSettings, setModuleEnabled} from "../../integration/rest";
    import {groupByCategory} from "../../integration/util";
    import type {Module, ClientInfo} from "../../integration/types";
    import Search from "./Search.svelte";
    import {listen} from "../../integration/ws";
    import AccentColorPicker from "./AccentColorPicker.svelte";
    import {refreshAccentColor} from '../../theme/accentColorStore';

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
    let refreshRevision = 0;

    const VIEWPORT_PADDING = 24;
    let menuElement: HTMLElement;
    let menuX = 0;
    let menuY = 0;
    let dragging = false;
    let offsetX = 0;
    let offsetY = 0;
    let lastMouseX = 0;
    let lastMouseY = 0;
    let animationFrame: number | null = null;

    async function refreshModules() {
        const revision = ++refreshRevision;
        const modules = await getModules();
        const grouped = groupByCategory(modules);
        for (const cat of Object.keys(grouped)) {
            grouped[cat] = grouped[cat].map(m => ({...m}));
        }

        if (revision !== refreshRevision) return;

        modulesByCategory = {...grouped};
        categories = Object.keys(grouped).map(cat => ({
            name: cat,
            color: categoryColors[cat] ?? "#888",
            count: grouped[cat].length
        }));
        allModules = Object.values(grouped).flat();

        if (!selectedCategory || !grouped[selectedCategory]) {
            const savedCategory = localStorage.getItem("lb_selectedCategory");
            selectedCategory = savedCategory && grouped[savedCategory]
                ? savedCategory
                : grouped.Combat ? "Combat" : Object.keys(grouped)[0] ?? "";
        }

        if (selectedModule) {
            selectedModule = allModules.find(mod => mod.name === selectedModule?.name) ?? null;
        }

        void refreshSettingsCounts(modules, revision);
    }

    async function refreshSettingsCounts(modules: Module[], revision: number) {
        const countEntries = await Promise.all(modules.map(async (mod) => {
            try {
                const settings = await getModuleSettings(mod.name);
                const count = settings.value.filter(s => s.name !== "Bind" && s.name !== "Hidden").length;
                return [mod.name, count] as const;
            } catch (error) {
                console.error(`Failed to load settings count for ${mod.name}`, error);
                return [mod.name, -1] as const;
            }
        }));

        if (revision === refreshRevision) {
            moduleSettingsCount = Object.fromEntries(countEntries);
        }
    }

    onMount(async () => {
        window.addEventListener('openClickGui', refreshModules);
        window.addEventListener('refreshModules', refreshModules);
        window.addEventListener('moduleSettingsChanged', refreshModules);
        window.addEventListener('resize', keepMenuInViewport);

        refreshAccentColor();
        await Promise.all([
            refreshModules(),
            getClientInfo().then(info => clientInfo = info)
        ]);
        await centerMenu();
    });

    listen("moduleToggle", (event) => {
        updateLocalModuleState(event.moduleName, event.enabled);
    });

    onDestroy(() => {
        window.removeEventListener('openClickGui', refreshModules);
        window.removeEventListener('refreshModules', refreshModules);
        window.removeEventListener('moduleSettingsChanged', refreshModules);
        window.removeEventListener('resize', keepMenuInViewport);
        onMouseUp();
    });

    async function centerMenu() {
        await tick();
        if (!menuElement) return;

        menuX = Math.max(VIEWPORT_PADDING, (window.innerWidth - menuElement.offsetWidth) / 2);
        menuY = Math.max(VIEWPORT_PADDING, (window.innerHeight - menuElement.offsetHeight) / 2);
    }

    function keepMenuInViewport() {
        if (!menuElement) return;

        const maxX = Math.max(VIEWPORT_PADDING, window.innerWidth - menuElement.offsetWidth - VIEWPORT_PADDING);
        const maxY = Math.max(VIEWPORT_PADDING, window.innerHeight - menuElement.offsetHeight - VIEWPORT_PADDING);
        menuX = Math.min(Math.max(VIEWPORT_PADDING, menuX), maxX);
        menuY = Math.min(Math.max(VIEWPORT_PADDING, menuY), maxY);
    }

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
        const enabled = !module.enabled;
        updateLocalModuleState(module.name, enabled);

        try {
            await setModuleEnabled(module.name, enabled);
        } catch (error) {
            console.error(`Failed to update module ${module.name}`, error);
            await refreshModules();
        }
    }

    function updateLocalModuleState(moduleName: string, enabled: boolean) {
        const grouped = {...modulesByCategory};
        for (const category of Object.keys(grouped)) {
            grouped[category] = grouped[category].map(mod =>
                mod.name === moduleName ? {...mod, enabled} : mod
            );
        }
        modulesByCategory = grouped;
        allModules = allModules.map(mod => mod.name === moduleName ? {...mod, enabled} : mod);
        if (selectedModule?.name === moduleName) {
            selectedModule = {...selectedModule, enabled};
        }
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
            keepMenuInViewport();
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

<div
    class="clickgui-menu"
    style="position: absolute; left: {menuX}px; top: {menuY}px;"
    bind:this={menuElement}
>
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div class="top-panel" on:mousedown={onMouseDown} style="cursor: grab; user-select: none;" role="region">
        <div class="window-controls">
            <span class="lb-watermark" role="img" aria-label="BMWClient logo"></span>
        </div>
        <div class="title-block">
            <span class="title">BMWClient</span>
        </div>
        <div class="search-block">
            <AccentColorPicker />
            <Search modules={allModules} onJumpToModule={jumpToModule} />
        </div>
    </div>
    <div class="main-content">
        <div class="category-list-panel">
            <CategoryList categories={categories} selected={selectedCategory} onSelect={handleCategorySelect}/>
        </div>
        <div class="module-grid-panel" bind:this={moduleGridPanel}>
            <div class="module-grid">
                {#each modulesByCategory[selectedCategory] ?? [] as module (module.name)}
                    <ModuleCard
                        module={{
                            ...module,
                            settingsCount: moduleSettingsCount[module.name] ?? 0,
                            color: categories.find(c => c.name === selectedCategory)?.color ?? "#888",
                        }}
                        onSettings={() => handleModuleSettings(module)}
                        onToggle={() => handleModuleToggle(module)}
                        selected={selectedModule?.name === module.name}
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
                        description={selectedModule.description}
                        aliases={selectedModule.aliases}
                    />
                {/key}
            {:else}
                <div class="settings-desc">Select a module to view its settings</div>
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
    background: linear-gradient(
      145deg,
      rgba(var(--accent-color), 0.2) 0%,
      rgba($clickgui-base-color, 0.96) 24%,
      rgba($clickgui-base-color, 0.96) 72%,
      rgba(var(--accent-color), 0.12) 100%
    );
    backdrop-filter: blur(18px);
    border: 1px solid rgba(var(--accent-color), 0.32);
    border-right: 3px solid rgba(var(--accent-color), 0.9);
    box-shadow: 0 20px 70px rgba(0, 0, 0, 0.62), 0 0 32px rgba(var(--accent-color), 0.14);
    border-radius: 32px;
    display: flex;
    flex-direction: column;
    position: relative;
    width: clamp(980px, 78vw, 1560px);
    max-width: calc(100vw - 48px);
    height: clamp(620px, 78vh, 960px);
    max-height: calc(100vh - 48px);
    overflow: hidden;
    margin: 0;
    font-family: "Axiforma", sans-serif;
    transition: none !important;
  }

  .top-panel {
    display: flex;
    cursor: grab;
    align-items: center;
    min-height: 68px;
    height: 8.5%;
    padding: 12px 18px;
    background: linear-gradient(90deg, rgba(var(--accent-color), 0.16), rgba(0, 0, 0, 0.34) 38%);
    border-bottom: 1px solid rgba(var(--accent-color), 0.5);
    .window-controls {
      display: flex;
      align-items: center;
      .lb-watermark {
        width: 38px;
        height: 38px;
        display: block;
        background: rgb(var(--accent-color));
        filter: drop-shadow(0 0 7px rgba(var(--accent-color), 0.26));
        -webkit-mask: url("/img/lb-watermark.svg") center / contain no-repeat;
        mask: url("/img/lb-watermark.svg") center / contain no-repeat;
        user-select: none;
        cursor: pointer;
      }
    }
    .title-block {
      margin-left: 15px;
      flex: 1;
      display:flex;
      flex-direction:column;
      .title {
        font-size: clamp(16px, 0.95vw, 20px);
        font-weight: 600;
        color: $clickgui-text-color;
        letter-spacing: 0.2px;
      }
    }
    .search-block {
      margin-left: auto;
      display: flex;
      align-items: center;
      min-width: 280px;
      width: min(32%, 430px);
      max-width: 430px;
      height: 100%;
      justify-content: flex-end;
    }
  }

  .main-content {
    display: flex;
    flex: 1;
    overflow: hidden;
    background: linear-gradient(135deg, rgba(var(--accent-color), 0.045), rgba(0, 0, 0, 0.16));
  }

  .category-list-panel {
    width: clamp(190px, 14vw, 230px);
    padding: 12px;
    background: linear-gradient(180deg, rgba(var(--accent-color), 0.09), rgba(0, 0, 0, 0.32));
    border-right: 1px solid rgba(var(--accent-color), 0.2);
    display: flex;
    flex-direction: column;
    height: 100%;
    position: relative;
  }

  .module-grid-panel {
    flex: 2;
    padding: 12px;
    overflow-y: auto;
    scroll-behavior: smooth;
    scrollbar-width: none;
    -ms-overflow-style: none;
    &::-webkit-scrollbar {
      display: none;
    }
    .module-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 8px;
    }
  }

  .settings-panel {
    width: clamp(300px, 22vw, 380px);
    padding: 14px;
    background: linear-gradient(180deg, rgba(var(--accent-color), 0.08), rgba(0, 0, 0, 0.3));
    border-left: 1px solid rgba(var(--accent-color), 0.2);
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
      font-size: 14px;
      line-height: 1.5;
      margin: auto;
      max-width: 190px;
      text-align: center;
    }
  }

      @media (max-width: 1100px) {
        .clickgui-menu {
          width: calc(100vw - 32px);
          max-width: none;
          height: calc(100vh - 32px);
          max-height: none;
        }
        .category-list-panel {
          width: 175px;
          padding: 10px;
        }
        .settings-panel {
          width: 280px;
          padding: 10px;
        }
      }

      @media (max-width: 760px) {
        .clickgui-menu {
          width: calc(100vw - 16px);
          height: calc(100vh - 16px);
          border-radius: 24px;
        }
        .top-panel {
          min-height: 58px;
          padding: 8px 10px;
        }
        .title-block {
          display: none !important;
        }
        .category-list-panel {
          width: 140px;
        }
        .settings-panel {
          display: none;
        }
        .search-block {
          width: min(70%, 360px) !important;
          min-width: 0 !important;
        }
      }
</style>
