// --- Elements ---
const bookId = GLOBAL_BOOK_ID;
const viewerElement = document.getElementById('viewer');
const viewerContainer = document.getElementById('viewer-container');
const loadingIndicator = document.getElementById('loading-indicator');
const tocContainer = document.getElementById('toc-container');
const tocList = document.getElementById('toc-list');
// const toggleTocButton = document.getElementById('toggle-toc'); // Header commented out, so button doesn't exist
const tocOverlay = document.getElementById('toc-overlay');
const settingsBar = document.getElementById('settings-bar');
const decreaseFontButton = document.getElementById('decrease-font');
const increaseFontButton = document.getElementById('increase-font');
const currentFontSizeSpan = document.getElementById('current-font-size');
const closeSettingsButton = document.getElementById('close-settings');
const toggleDarkModeButton = document.getElementById('toggle-dark-mode');

const bookUrl = GLOBAL_BOOK_URL_TEMPLATE;

console.log("Book ID:", bookId);

// --- State Variables ---
let rendition = null;
let book = null;
let currentBookTitle = 'Ebook Reader'; // Default title
let isModifyingSelection = false;
let currentFontSizePercent = 100;
const FONT_SIZE_STEP = 10;
const MIN_FONT_SIZE = 70;
const MAX_FONT_SIZE = 150;
let isDarkMode = false;

// --- UI Functions ---
function closeToc() {
    if (tocContainer) tocContainer.style.left = '-85%';
    if (tocOverlay) tocOverlay.style.opacity = '0';
    if (tocOverlay) tocOverlay.style.visibility = 'hidden';
    document.body.classList.remove('toc-visible');
}
function openToc() {
    // This function might still be useful if triggered by other means in the future,
    // but it won't be triggered by the commented-out button.
    if (tocContainer) tocContainer.style.left = '0';
    if (tocOverlay) tocOverlay.style.opacity = '1';
    if (tocOverlay) tocOverlay.style.visibility = 'visible';
    document.body.classList.add('toc-visible');
}
function showSettings() {
    if (settingsBar){
        settingsBar.classList.add('settings-visible');
        settingsBar.querySelectorAll('.settings-content').forEach(e => e.classList.add('settings-visible')) ;
    }
}
function hideSettings() {
    if (settingsBar){
        settingsBar.classList.remove('settings-visible');
        settingsBar.querySelectorAll('.settings-content').forEach(e => e.classList.remove('settings-visible')) ;
    }
}

// --- Font Size Function ---
function applyFontSize() {
    if (rendition && currentFontSizeSpan) {
        const sizeString = `${currentFontSizePercent}%`;
        try {
            rendition.themes.fontSize(sizeString);
            currentFontSizeSpan.textContent = sizeString;
        } catch (e) {
            console.error("Error applying font size:", e);
        }
    }
}

// --- Dark Mode Functions ---
function applyDarkMode(enable) {
    isDarkMode = enable;
    const themeToSelect = enable ? 'dark' : 'default';
    if (rendition) {
        try {
            rendition.themes.select(themeToSelect);
        } catch(e) {
            console.error(`Error selecting theme '${themeToSelect}':`, e);
        }
    }
    if (enable) {
        document.body.classList.add('dark-mode');
        if (toggleDarkModeButton) toggleDarkModeButton.textContent = "Light Mode";
        try { localStorage.setItem('ebookReaderDarkMode', 'enabled'); } catch (e) {}
    } else {
        document.body.classList.remove('dark-mode');
        if (toggleDarkModeButton) toggleDarkModeButton.textContent = "Dark Mode";
        try { localStorage.setItem('ebookReaderDarkMode', 'disabled'); } catch (e) {}
    }
    applyFontSize(); // Re-apply font size after theme change
}

function toggleDarkMode() {
    applyDarkMode(!isDarkMode);
    console.log('!!!!!!!!!!!!!Dark Mode!!!!!!!!!!!!!!!!!!!');
    // Re-display to ensure theme applies correctly immediately, using saved location
    const storageKey = `ebookReaderPosition_${bookId}`;
    try {
        const savedCfi = localStorage.getItem(storageKey);
        if (savedCfi) {
            rendition.display(savedCfi);
        } else {
            rendition.display(); // Display from start if no saved CFI
        }
    } catch(e) {
        console.error("Error re-displaying after theme change:", e);
        rendition.display(); // Fallback
    }
}

function applyInitialDarkModeState() {
    let savedMode = null;
    try { savedMode = localStorage.getItem('ebookReaderDarkMode'); } catch (e) {}
    if (savedMode === 'enabled') {
        applyDarkMode(true);
    } else {
        applyDarkMode(false);
    }
}

// --- Main Initialization Logic ---
if (bookId && bookId !== 'default_id' && bookUrl && !bookUrl.includes('default_id')) {
    loadingIndicator.style.display = 'block';
    try {
        book = ePub(bookUrl, { openAs: "epub" });
        console.log("epub.js book object created.");

        book.ready.then(() => {
            console.log(">>> book.ready resolved.");
            const metadata = book.package.metadata;
            // Update the document title, but not the non-existent header h1
            if (metadata.title) {
                currentBookTitle = metadata.title;
                // document.querySelector('.app-header h1').textContent = currentBookTitle; // Header is commented out
                document.title = currentBookTitle + " (Reader)"; // Set browser tab title
            }
            const toc = book.navigation.toc;
            tocList.innerHTML = ''; const fragment = document.createDocumentFragment();
            toc.forEach(function(chapter) {
                const li = document.createElement('li'); const link = document.createElement('a');
                link.textContent = chapter.label.trim(); link.href = chapter.href;
                link.onclick = function(event) { event.preventDefault(); if (rendition) { rendition.display(chapter.href).then(closeToc); } };
                li.appendChild(link); fragment.appendChild(li);
            });
            tocList.appendChild(fragment);
            console.log("TOC processed.");
            // toggleTocButton.disabled = false; // Button doesn't exist
        }).catch(err => {
            console.error("Error book.ready:", err);
            // toggleTocButton.disabled = true; // Button doesn't exist
        });

        rendition = book.renderTo("viewer", {
            width: "100%", height: "100%", spread: "none", allowScriptedContent: true
        });
        console.log("epub.js rendition object created.");

        // --- Register Dark Theme ---
        rendition.themes.register("dark", {
            "body": { "background": "#121212 !important", "color": "#e0e0e0 !important" },
            "p, li, div, span, blockquote, pre": { "color": "#e0e0e0 !important" },
            "h1, h2, h3, h4, h5, h6": { "color": "#f0f0f0 !important" },
            "a": { "color": "#bb86fc !important", "text-decoration": "underline !important" },
            "a:hover": { "color": "#d0a0ff !important" }
        });
        console.log("Registered 'dark' theme.");

        // --- Default Theme with Light Mode Styles ---
        rendition.themes.default({
            "p, div, li": { "text-align": "left !important" },
            "body": { "background": "#ffffff !important", "color": "#000000 !important" },
            "p, li, div, span, blockquote, pre": { "color": "#000000 !important" },
            "h1, h2, h3, h4, h5, h6": { "color": "#000000 !important" },
            "a": { "color": "#0000ee !important", "text-decoration": "underline !important" },
            "a:hover": { "color": "#551a8b !important" }
        });
        console.log("Applied base theme overrides (left-align + light mode styles).");

        // --- Apply Initial Dark Mode State ---
        applyInitialDarkModeState(); // Also applies initial font size via applyDarkMode

        const storageKey = `ebookReaderPosition_${bookId}`;

        // *** 저장된 위치 불러오기 시도 ***
        let startCfi = null;
        try {
            startCfi = localStorage.getItem(storageKey);
            if (startCfi) {
                console.log("Found saved position (CFI):", startCfi);
            }
        } catch (e) {
            console.error("Error reading saved position from localStorage:", e);
        }


        // *** Rendition Display and Event Listener Attachment ***
        rendition.display(startCfi).then(() => {
            console.log("Book rendered. Attaching listeners...");
            loadingIndicator.style.display = 'none';

            // --- Keyboard Navigation ---
            rendition.on("keyup", handleKeyPress);

            rendition.on("relocated", function(location) {
                if (location && location.start && location.start.cfi) {
                    const currentCfi = location.start.cfi;
                    // console.log("Relocated to:", currentCfi); // 디버깅 로그
                    try {
                        // 현재 위치의 시작 CFI를 localStorage에 저장
                        localStorage.setItem(storageKey, currentCfi);
                        // console.log("Saved position (CFI):", currentCfi); // 저장 확인 로그 (선택 사항)
                    } catch (e) {
                        console.error("Error saving position to localStorage:", e);
                    }
                }
            });

            // --- *** Rendition 'click' listener (using offsetX) with Settings Toggle *** ---
            rendition.on('click', function handleRenditionClick(event) {
                // console.log("================ Rendition Click ================");
                if (!rendition || !rendition.currentLocation()) { return; }

                const originalEvent = event.originalEvent || event;
                // console.log("   Target:", originalEvent.target ? originalEvent.target.tagName : 'null');

                // Check if click is on the settings bar itself
                let target = originalEvent.target;
                while (target && target !== document.body) {
                    if (target === settingsBar) { return; } // Ignore clicks originating from settings bar
                    target = target.parentElement;
                }

                // Link Check
                let targetElement = originalEvent.target; // Renamed to avoid conflict
                let isActualLink = false;
                let linkCheckDepth = 0;
                while (targetElement && targetElement !== viewerElement && linkCheckDepth < 10) {
                    if (targetElement.tagName === 'A' && targetElement.hasAttribute('href')) { isActualLink = true; break; }
                    if (!targetElement.parentElement || targetElement.parentElement === targetElement.ownerDocument) break;
                    targetElement = targetElement.parentElement; linkCheckDepth++;
                }
                if (isActualLink) { return; }

                // Coordinate Calculation using offsetX/layerX
                let relativeClickX = -1;
                let usedMethod = "None";
                if (typeof originalEvent.offsetX !== 'undefined') {
                    relativeClickX = originalEvent.offsetX; usedMethod = "offsetX";
                } else if (typeof originalEvent.layerX !== 'undefined') {
                    relativeClickX = originalEvent.layerX; usedMethod = "layerX";
                } else {
                    console.error("Cannot get relative X (offsetX/layerX)."); return;
                }

                // Navigation / Settings Toggle Logic
                try {
                    const viewerRect = viewerElement.getBoundingClientRect();
                    if (!viewerRect || viewerRect.width <= 0) { return; }
                    const viewerWidth = viewerRect.width; // Use parent viewer width for zones

                    const prevZoneEnd = viewerWidth / 3;
                    const nextZoneStart = viewerWidth * 2 / 3;

                    const targetTag = originalEvent.target ? originalEvent.target.tagName.toUpperCase() : '';
                    const isRootTarget = targetTag === 'HTML' || targetTag === 'BODY';

                    if (isRootTarget) {
                        const clickX_viewport = originalEvent.clientX;
                        const viewerLeft = viewerRect.left;
                        const relativeToViewerX = clickX_viewport - viewerLeft;
                        if (relativeToViewerX >= prevZoneEnd && relativeToViewerX <= nextZoneStart) {
                            // Toggle settings on middle click for root target
                            if (settingsBar.classList.contains('settings-visible')) { hideSettings(); } else { showSettings(); }
                        }
                    } else {
                        // Use relativeClickX for specific element clicks
                        if (relativeClickX >= 0 && relativeClickX < prevZoneEnd) {
                            rendition.prev(); hideSettings();
                        } else if (relativeClickX > nextZoneStart && relativeClickX <= viewerWidth) {
                            rendition.next(); hideSettings();
                        } else if (relativeClickX >= prevZoneEnd && relativeClickX <= nextZoneStart) {
                            // Toggle settings on middle click for specific elements
                            if (settingsBar.classList.contains('settings-visible')) { hideSettings(); } else { showSettings(); }
                        }
                    }
                } catch(navError) { console.error("Nav/Settings logic error:", navError); }
            });
            console.log("Attached 'click' listener (using offsetX) with Settings Toggle.");


            // --- Settings Button Listeners ---
            if (decreaseFontButton) {
                decreaseFontButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (currentFontSizePercent > MIN_FONT_SIZE) {
                        currentFontSizePercent -= FONT_SIZE_STEP;
                        applyFontSize();
                    }
                });
            }
            if (increaseFontButton) {
                increaseFontButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (currentFontSizePercent < MAX_FONT_SIZE) {
                        currentFontSizePercent += FONT_SIZE_STEP;
                        applyFontSize();
                    }
                });
            }
            if (toggleDarkModeButton) {
                toggleDarkModeButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    toggleDarkMode();
                });
            }
            if (closeSettingsButton) {
                closeSettingsButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    hideSettings();
                });
            }
            console.log("Attached settings button listeners.");

            // --- Word Selection Handler (Optional) ---
            let isModifyingSelectionWord = false;
            rendition.on('displayed', function(view) {
                const iframeDoc = view.document;
                const iframeWin = view.window;
                if (!iframeDoc || !iframeWin) return;
                const handleSelectionChange = () => {
                    if (isModifyingSelectionWord) return;
                    const selection = iframeWin.getSelection();
                    if (selection && !selection.isCollapsed && selection.rangeCount > 0) {
                        isModifyingSelectionWord = true;
                        try {
                            selection.modify("move", "backward", "word");
                            selection.modify("extend", "forward", "word");
                        } catch (e) { /* Ignore */ }
                        finally { setTimeout(() => { isModifyingSelectionWord = false; }, 0); }
                    }
                };
                iframeDoc.addEventListener('selectionchange', handleSelectionChange);
            });
            // console.log("Setup word selection handling.");


        }).catch(err => {
            console.error("Error rendering book:", err);
            viewerElement.innerHTML = "<p style='padding: 20px; text-align: center;'>Error rendering book.</p>";
            loadingIndicator.style.display = 'none';
        });

        // --- Existing Event Listeners (TOC overlay etc.) ---
        /* // This listener is for the button which is now commented out
        toggleTocButton.addEventListener("click", function() {
            if (document.body.classList.contains('toc-visible')) { closeToc(); } else { openToc(); }
        });
        */
        tocOverlay.addEventListener("click", closeToc); // Keep this, TOC might open other ways
        rendition.on("relocated", function(location){
            // console.log("Relocated to:", location.start.cfi); // Optional log
        });

    } catch (e) {
        console.error("Error during epub.js initialization:", e);
        viewerElement.innerHTML = "<p style='padding: 20px; text-align: center;'>Failed to initialize reader.</p>";
        loadingIndicator.style.display = 'none';
        // toggleTocButton.disabled = true; // Button doesn't exist
    }

} else {
    console.error("Invalid bookId or bookUrl.");
    viewerElement.innerHTML = "<p style='padding: 20px; text-align: center;'>Invalid Book ID.</p>";
    loadingIndicator.style.display = 'none';
    // toggleTocButton.disabled = true; // Button doesn't exist
}

// --- Keyboard Navigation Function ---
function handleKeyPress(event) {
    if (rendition) {
        if (settingsBar && settingsBar.classList.contains('settings-visible')) {
            // Optionally block keyboard nav when settings are open
            // return;
        }

        if (event.key === "ArrowLeft") {
            event.preventDefault();
            rendition.prev();
            hideSettings(); // Hide settings on key nav
        }
        if (event.key === "ArrowRight") {
            event.preventDefault();
            rendition.next();
            hideSettings(); // Hide settings on key nav
        }
    }
}

// toggleTocButton.disabled = true; // Initially disabled - Button doesn't exist
