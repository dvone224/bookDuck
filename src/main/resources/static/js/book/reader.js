// --- Elements ---
const bookId = GLOBAL_BOOK_ID;
const viewerElement = document.getElementById('viewer');
const viewerContainer = document.getElementById('viewer-container');
const loadingIndicator = document.getElementById('loading-indicator');
const tocContainer = document.getElementById('toc-container');
const tocList = document.getElementById('toc-list');
const tocOverlay = document.getElementById('toc-overlay');
const settingsBar = document.getElementById('settings-bar');
const decreaseFontButton = document.getElementById('decrease-font');
const increaseFontButton = document.getElementById('increase-font');
const currentFontSizeSpan = document.getElementById('current-font-size');
const closeSettingsButton = document.getElementById('close-settings');
const toggleDarkModeButton = document.getElementById('toggle-dark-mode');

let selectionActionButton = null;

const bookUrl = GLOBAL_BOOK_URL_TEMPLATE;

console.log("reader.js: Script started. Book ID:", bookId);

// --- State Variables ---
let rendition = null;
let book = null;
let currentBookTitle = 'Ebook Reader';
let currentFontSizePercent = 100;
const FONT_SIZE_STEP = 10;
const MIN_FONT_SIZE = 70;
const MAX_FONT_SIZE = 150;
let isDarkMode = false;
let currentSelectedCfiRange = null;
let currentSelectedTextContent = '';
let lastMouseUpPosition = {x: 0, y: 0};
let isMouseDown = false;
let isDragging = false;
let startDragPosition = {x: 0, y: 0};
const DRAG_THRESHOLD = 5;

// --- UI Functions ---
function closeToc() {
    if (tocContainer) tocContainer.style.left = '-85%';
    if (tocOverlay) tocOverlay.style.opacity = '0';
    if (tocOverlay) tocOverlay.style.visibility = 'hidden';
    document.body.classList.remove('toc-visible');
}

function openToc() {
    if (tocContainer) tocContainer.style.left = '0';
    if (tocOverlay) tocOverlay.style.opacity = '1';
    if (tocOverlay) tocOverlay.style.visibility = 'visible';
    document.body.classList.add('toc-visible');
}

function showSettings() {
    if (settingsBar) {
        const contents = settingsBar.querySelectorAll('.settings-content');
        contents.forEach(e => e.classList.add('settings-visible'));
        settingsBar.classList.add('settings-visible');
    }
}

function hideSettings() {
    if (settingsBar) {
        const contents = settingsBar.querySelectorAll('.settings-content');
        contents.forEach(e => e.classList.remove('settings-visible'));
        settingsBar.classList.remove('settings-visible');
    }
}

function applyFontSize() {
    if (rendition && currentFontSizeSpan) {
        const sizeString = `${currentFontSizePercent}%`;
        try {
            rendition.themes.fontSize(sizeString);
            currentFontSizeSpan.textContent = sizeString;
        } catch (e) {
            console.error("Error applying font size:", e);
        }
    } else if (!currentFontSizeSpan && rendition) {
        const sizeString = `${currentFontSizePercent}%`;
        try {
            rendition.themes.fontSize(sizeString);
        } catch (e) {
            console.error("Error applying font size (span not found):", e);
        }
    }
}

function applyDarkMode(enable, suppressRenditionDisplay = false) {
    isDarkMode = enable;
    const themeToSelect = enable ? 'dark' : 'default';
    if (rendition) {
        try {
            rendition.themes.select(themeToSelect);
        } catch (e) {
            console.error(`Error selecting theme '${themeToSelect}':`, e);
        }
    }
    if (enable) {
        document.body.classList.add('dark-mode');
        if (toggleDarkModeButton) toggleDarkModeButton.textContent = "Light Mode";
        try {
            localStorage.setItem('ebookReaderDarkMode', 'enabled');
        } catch (e) {
            console.warn("localStorage unavailable for dark mode state.");
        }
    } else {
        document.body.classList.remove('dark-mode');
        if (toggleDarkModeButton) toggleDarkModeButton.textContent = "Dark Mode";
        try {
            localStorage.setItem('ebookReaderDarkMode', 'disabled');
        } catch (e) {
            console.warn("localStorage unavailable for dark mode state.");
        }
    }
    applyFontSize();
    if (!suppressRenditionDisplay && rendition) {
        const storageKey = `ebookReaderPosition_${bookId}`;
        try {
            const savedCfi = localStorage.getItem(storageKey);
            setTimeout(() => {
                if (savedCfi) {
                    rendition.display(savedCfi);
                } else {
                    rendition.display();
                }
            }, 50);
        } catch (e) {
            console.error("Error re-displaying after theme change:", e);
            rendition.display();
        }
    }
}

function toggleDarkMode() {
    applyDarkMode(!isDarkMode, false);
}

function applyInitialDarkModeState() {
    let savedMode = null;
    try {
        savedMode = localStorage.getItem('ebookReaderDarkMode');
    } catch (e) {
    }
    applyDarkMode(savedMode === 'enabled', true);
}

// --- DOMContentLoaded 리스너 ---
console.log("reader.js: Setting up DOMContentLoaded listener.");
document.addEventListener('DOMContentLoaded', () => {
    console.log("reader.js: DOMContentLoaded event fired.");
    selectionActionButton = document.getElementById('selection-action-button');
    console.log("reader.js: Inside DOMContentLoaded, selectionActionButton =", selectionActionButton);
    if (selectionActionButton) {
        console.log("reader.js: selectionActionButton found. Adding click listener.");
        selectionActionButton.addEventListener('click', () => {
            if (currentSelectedTextContent) {
                alert(`선택된 텍스트: "${currentSelectedTextContent}"\nCFI: ${currentSelectedCfiRange}\n여기에 원하는 기능을 구현하세요.`);
                console.log("Action button clicked. Selected Text:", currentSelectedTextContent, "CFI:", currentSelectedCfiRange);
            }
            selectionActionButton.style.display = 'none';
            if (rendition && rendition.manager && rendition.manager.getContents && rendition.manager.getContents().length > 0) {
                const iframeWindow = rendition.manager.getContents()[0].window;
                if (iframeWindow && iframeWindow.getSelection) {
                    try {
                        iframeWindow.getSelection().removeAllRanges();
                    } catch (e) {
                    }
                }
            }
        });
    } else {
        console.error("reader.js: CRITICAL - selection-action-button NOT FOUND in DOM after DOMContentLoaded.");
    }
    if (typeof initializeReader === 'function') {
        console.log("reader.js: Calling initializeReader from DOMContentLoaded.");
        initializeReader();
    } else {
        console.error("reader.js: ERROR - initializeReader function is not defined at DOMContentLoaded.");
    }
});

// --- 메인 리더 초기화 함수 ---
function initializeReader() {
    console.log("reader.js: initializeReader function called.");
    if (!viewerElement) {
        console.error("reader.js: Viewer element (#viewer) not found.");
        if (loadingIndicator) loadingIndicator.style.display = 'none';
        return;
    }
    if (bookId && bookId !== 'default_id' && bookUrl && !bookUrl.includes('default_id')) {
        if (loadingIndicator) loadingIndicator.style.display = 'block';
        try {
            book = ePub(bookUrl, {openAs: "epub"});
            console.log("reader.js: epub.js book object created.");
            rendition = book.renderTo(viewerElement, {width: "100%", height: "100%", allowScriptedContent: true});
            console.log("reader.js: epub.js rendition object created with allowScriptedContent:true.");
            rendition.themes.register("dark", { /* ... */});
            rendition.themes.default({ /* ... */});

            book.ready.then(() => {
                console.log("reader.js: book.ready resolved.");
                const metadata = book.package.metadata;
                if (metadata.title) {
                    currentBookTitle = metadata.title;
                    document.title = currentBookTitle + " (Reader)";
                }
                const toc = book.navigation.toc;
                if (tocList) {
                    tocList.innerHTML = '';
                    const fragment = document.createDocumentFragment();
                    toc.forEach(function (chapter) {
                        const li = document.createElement('li');
                        const link = document.createElement('a');
                        link.textContent = chapter.label.trim();
                        link.href = chapter.href;
                        link.onclick = function (event) {
                            event.preventDefault();
                            if (rendition) {
                                rendition.display(chapter.href).then(closeToc);
                            }
                        };
                        li.appendChild(link);
                        fragment.appendChild(li);
                    });
                    tocList.appendChild(fragment);
                } else {
                    console.warn("reader.js: TOC list element not found.");
                }
                applyInitialDarkModeState();
                const storageKey = `ebookReaderPosition_${bookId}`;
                let startCfi = null;
                try {
                    startCfi = localStorage.getItem(storageKey);
                    if (startCfi) {
                        console.log("reader.js: Found saved position (CFI):", startCfi);
                    } else {
                        console.log("reader.js: No saved position found.");
                    }
                } catch (e) {
                    console.error("reader.js: Error reading saved position:", e);
                }
                console.log(`reader.js: Attempting to display rendition at: ${startCfi || 'start'}`);
                return rendition.display(startCfi);
            })
                .then(() => { // 책 표시 완료 후
                    console.log("reader.js: Book rendered. Attaching event listeners.");
                    if (loadingIndicator) loadingIndicator.style.display = 'none';
                    document.addEventListener("keydown", (event) => {
                        if (event.key === "ArrowLeft" || event.key === "ArrowRight" || event.key === "Escape") {
                            handleKeyPress(event);
                        }
                    });

                    // relocated 이벤트 리스너
                    rendition.on("relocated", function (location) {
                        console.log("reader.js: 'relocated' event triggered.");
                        attachIframeListeners(); // mousedown, mousemove, mouseup 리스너 등록
                        if (location && location.start && location.start.cfi) {
                            const currentCfi = location.start.cfi;
                            const storageKey = `ebookReaderPosition_${bookId}`;
                            try {
                                localStorage.setItem(storageKey, currentCfi);
                            } catch (e) {
                                console.error("reader.js: Error saving position:", e);
                            }
                        }
                    });

                    // 초기 뷰 리스너 등록
                    setTimeout(attachIframeListeners, 100);

                    // --- [!!! 수정됨: 클릭 핸들러에서 lastMouseUpPosition 사용 !!!] ---
                    rendition.on('click', function handleRenditionClick(event) {
                        console.log("reader.js: rendition 'click' event triggered.");
                        if (isDragging) {
                            console.log("reader.js: Click ignored, was dragging.");
                            return;
                        } // 드래그 중이면 무시
                        if (!rendition || !rendition.currentLocation()) {
                            console.log("reader.js: Click ignored - rendition not ready.");
                            return;
                        }
                        const originalEvent = event.originalEvent || event;
                        let target = originalEvent.target;
                        while (target && target !== document.body) {
                            if (target === settingsBar) {
                                console.log("reader.js: Click ignored - on settings bar.");
                                return;
                            }
                            target = target.parentElement;
                        }
                        let targetElement = originalEvent.target;
                        let isActualLink = false;
                        let linkCheckDepth = 0;
                        while (targetElement && targetElement !== viewerElement && linkCheckDepth < 10) {
                            if (targetElement.tagName === 'A' && targetElement.hasAttribute('href')) {
                                isActualLink = true;
                                break;
                            }
                            if (!targetElement.parentElement || targetElement.parentElement === targetElement.ownerDocument) break;
                            targetElement = targetElement.parentElement;
                            linkCheckDepth++;
                        }
                        if (isActualLink) {
                            console.log("reader.js: Click ignored - on a link.");
                            return;
                        }

                        // 네비게이션/설정 토글 로직
                        try {
                            // <<< lastMouseUpPosition 사용 >>>
                            const clickX_page = lastMouseUpPosition.x; // 문서 기준 X 좌표
                            const scrollX = window.scrollX || window.pageXOffset;
                            const clickX_viewport = clickX_page - scrollX; // 뷰포트 기준 X 좌표로 변환

                            const viewportWidth = window.innerWidth;
                            const prevZoneEnd_viewport = viewportWidth / 3;
                            const nextZoneStart_viewport = viewportWidth * 2 / 3;

                            console.log(`reader.js: Click check - Viewport X (from lastMouseUp): ${clickX_viewport.toFixed(2)}, Viewport Width: ${viewportWidth.toFixed(2)}, PrevZoneEnd: ${prevZoneEnd_viewport.toFixed(2)}, NextZoneStart: ${nextZoneStart_viewport.toFixed(2)}`);

                            // 뷰포트 기준 X 좌표로 비교
                            if (clickX_viewport >= 0 && clickX_viewport < prevZoneEnd_viewport) { // 왼쪽 영역
                                console.log("reader.js: <<< Navigating Prev >>>");
                                rendition.prev();
                                hideSettings();
                            } else if (clickX_viewport > nextZoneStart_viewport && clickX_viewport <= viewportWidth) { // 오른쪽 영역
                                console.log("reader.js: <<< Navigating Next >>>");
                                rendition.next();
                                hideSettings();
                            } else if (clickX_viewport >= prevZoneEnd_viewport && clickX_viewport <= nextZoneStart_viewport) { // 가운데 영역
                                console.log("reader.js: Toggling Settings");
                                if (settingsBar && settingsBar.classList.contains('settings-visible')) {
                                    hideSettings();
                                } else {
                                    showSettings();
                                }
                            } else {
                                console.log("reader.js: Click detected outside viewport zones?");
                            }

                        } catch (navError) {
                            console.error("reader.js: Nav/Settings logic error in click handler:", navError);
                        }
                    });
                    // --- click 리스너 끝 ---


                    // --- 텍스트 선택 감지 및 액션 버튼 표시 로직 (mouseup 기준) ---
                    if (selectionActionButton) {
                        console.log("reader.js: Attaching 'selected' event listener (only for hiding).");
                        rendition.on("selected", (cfiRange, contents) => {
                            console.log("reader.js: rendition 'selected' event triggered!");
                            const iframeWindow = contents.window;
                            if (!iframeWindow) {
                                console.error("reader.js: contents.window is undefined!");
                                if (selectionActionButton) selectionActionButton.style.display = 'none';
                                return;
                            }
                            const selection = iframeWindow.getSelection();
                            const selectedText = selection.toString().trim();
                            console.log("reader.js: Selected text in 'selected' event: '", selectedText, "'");
                            currentSelectedCfiRange = cfiRange;
                            currentSelectedTextContent = selectedText; // 정보 저장
                            if (!(selectedText && selection.rangeCount > 0)) { // 선택 해제 시 버튼 숨김
                                console.log("reader.js: No valid text selected or selection cleared in 'selected'. Hiding button.");
                                if (selectionActionButton && selectionActionButton.style.display === 'block') {
                                    selectionActionButton.style.display = 'none';
                                }
                            }
                        });
                    } else {
                        console.error("reader.js: ERROR in .then() - selectionActionButton is null.");
                    }
                    // --- 끝: 텍스트 선택 감지 ---

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

                }).catch(err => { /* ... 에러 처리 ... */
            });
            if (tocOverlay) tocOverlay.addEventListener("click", closeToc);
        } catch (e) { /* ... 에러 처리 ... */
        }
    } else { /* ... 에러 처리 ... */
    }
}

// --- iframe 내부 이벤트 리스너 등록 함수 ---
function attachIframeListeners() {
    let currentView = null;
    if (rendition.manager && rendition.manager.currentView) {
        currentView = rendition.manager.currentView();
    } else if (rendition.manager && rendition.manager.getContents) {
        const contents = rendition.manager.getContents();
        if (contents.length > 0) {
            currentView = contents[0];
        }
    }
    if (currentView && currentView.document) {
        const iframeDoc = currentView.document;
        console.log("reader.js: Attaching/Reattaching mousedown/move/up listeners to iframe document.");
        iframeDoc.removeEventListener('mousedown', handleIframeMouseDown);
        iframeDoc.removeEventListener('mousemove', handleIframeMouseMove);
        iframeDoc.removeEventListener('mouseup', handleIframeMouseUp);
        iframeDoc.addEventListener('mousedown', handleIframeMouseDown);
        iframeDoc.addEventListener('mousemove', handleIframeMouseMove);
        iframeDoc.addEventListener('mouseup', handleIframeMouseUp);
    } else {
        console.warn("reader.js: Could not get current iframe document to attach listeners.");
    }
}

// --- iframe 내부 mousedown 이벤트 핸들러 함수 ---
function handleIframeMouseDown(event) {
    isMouseDown = true;
    isDragging = false;
    startDragPosition = {x: event.clientX, y: event.clientY};
}

// --- iframe 내부 mousemove 이벤트 핸들러 함수 ---
function handleIframeMouseMove(event) {
    if (!isMouseDown) return;
    const dx = Math.abs(event.clientX - startDragPosition.x);
    const dy = Math.abs(event.clientY - startDragPosition.y);
    if (!isDragging && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
        console.log("reader.js: Dragging detected.");
        isDragging = true;
    }
}

// --- iframe 내부 mouseup 이벤트 핸들러 함수 ---
function handleIframeMouseUp(event) {
    // 좌표 캡처
    let iframeEl = null;
    try {
        if (event.target && event.target.ownerDocument && event.target.ownerDocument.defaultView) {
            iframeEl = event.target.ownerDocument.defaultView.frameElement;
        }
    } catch (e) {
        console.warn("reader.js: Error accessing frameElement:", e);
    }
    if (iframeEl) {
        const iframeRect = iframeEl.getBoundingClientRect();
        const scrollX = window.scrollX || window.pageXOffset;
        const scrollY = window.scrollY || window.pageYOffset;
        const calculatedPageX = iframeRect.left + event.clientX + scrollX;
        const calculatedPageY = iframeRect.top + event.clientY + scrollY;
        lastMouseUpPosition = {x: calculatedPageX, y: calculatedPageY};
        console.log("reader.js: Mouse up captured inside iframe (calculated pageX, pageY):", lastMouseUpPosition);
    } else {
        console.warn("reader.js: Could not find iframe element in handleIframeMouseUp. Using event.pageX/Y as fallback.");
        if (typeof event.pageX !== 'undefined') {
            lastMouseUpPosition = {x: event.pageX, y: event.pageY};
            console.log("reader.js: Mouse up captured inside iframe (using event.pageX/Y as fallback):", lastMouseUpPosition);
        } else {
            console.warn("reader.js: event.pageX/Y also unavailable in handleIframeMouseUp.");
        }
    }

    // 버튼 표시 로직 (mouseup 시점 선택 상태 확인)
    setTimeout(() => {
        let currentSelectionText = '';
        let currentRangeCount = 0;
        try {
            let currentViewWin = null;
            if (rendition.manager && rendition.manager.currentView) {
                currentViewWin = rendition.manager.currentView().window;
            } else if (rendition.manager && rendition.manager.getContents) {
                const contents = rendition.manager.getContents();
                if (contents.length > 0) currentViewWin = contents[0].window;
            }
            if (currentViewWin && currentViewWin.getSelection) {
                const selection = currentViewWin.getSelection();
                currentSelectionText = selection.toString().trim();
                currentRangeCount = selection.rangeCount;
                console.log("reader.js: Selection check inside mouseup timeout: '", currentSelectionText, "'");
            }
        } catch (e) {
            console.error("Error getting current selection in mouseup timeout:", e);
        }

        if (currentSelectionText && currentRangeCount > 0 && selectionActionButton) {
            console.log("reader.js: Valid selection confirmed in mouseup timeout. Showing button at:", lastMouseUpPosition);
            let buttonTop = lastMouseUpPosition.y + 10;
            let buttonLeft = lastMouseUpPosition.x + 10;
            let buttonWidth = selectionActionButton.offsetWidth;
            let buttonHeight = selectionActionButton.offsetHeight;
            if (buttonWidth === 0 || buttonHeight === 0) {
                buttonWidth = 60;
                buttonHeight = 30;
            }
            const scrollX = window.scrollX || window.pageXOffset;
            const scrollY = window.scrollY || window.pageYOffset;
            const viewportRight = scrollX + window.innerWidth;
            const viewportBottom = scrollY + window.innerHeight;
            if (buttonLeft + buttonWidth > viewportRight - 10) buttonLeft = viewportRight - buttonWidth - 10;
            if (buttonLeft < scrollX + 10) buttonLeft = scrollX + 10;
            if (buttonTop + buttonHeight > viewportBottom - 10) buttonTop = lastMouseUpPosition.y - buttonHeight - 10;
            if (buttonTop < scrollY + 10) buttonTop = scrollY + 10;
            selectionActionButton.style.top = `${buttonTop}px`;
            selectionActionButton.style.left = `${buttonLeft}px`;
            selectionActionButton.style.display = 'block';
            console.log("reader.js: selectionActionButton displayed via mouseup timeout at:", {
                top: buttonTop,
                left: buttonLeft
            });
        } else {
            console.log("reader.js: No valid selection on mouseup. Button not shown by mouseup handler.");
        }
    }, 0);

    // 드래그 상태 초기화
    setTimeout(() => {
        isDragging = false;
    }, 0);
    isMouseDown = false;
}

// --- Keyboard Navigation Function ---
function handleKeyPress(event) {
    if (rendition) {
        if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA' || document.activeElement.isContentEditable)) {
            return;
        }
        if (settingsBar && settingsBar.classList.contains('settings-visible')) {
            if (event.key === "Escape") {
                hideSettings();
                event.preventDefault();
            }
            return;
        }
        if (event.key === "ArrowLeft") {
            event.preventDefault();
            rendition.prev();
            hideSettings();
        }
        if (event.key === "ArrowRight") {
            event.preventDefault();
            rendition.next();
            hideSettings();
        }
    }
}

// --- 문서 외부 클릭 시 선택 액션 버튼 숨김 (보조 역할) ---
console.log("reader.js: Setting up mousedown listener for hiding selection button.");
document.addEventListener('mousedown', (event) => {
    if (selectionActionButton && selectionActionButton.style.display === 'block') {
        if (event.target !== selectionActionButton && !selectionActionButton.contains(event.target) && (!viewerElement || !viewerElement.contains(event.target))) {
            console.log("reader.js: Mousedown outside button/viewer detected, hiding button.");
            selectionActionButton.style.display = 'none';
            currentSelectedCfiRange = null;
            currentSelectedTextContent = '';
        }
    }
}, true); // capture phase

console.log("reader.js: Script finished setup.");