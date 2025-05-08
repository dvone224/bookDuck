document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Elements (HTML에서 가져올 요소들) ---
    const viewerElement = document.getElementById('viewer');
    const loadingIndicator = document.getElementById('loading-indicator');
    const tocList = document.getElementById('toc-list');
    const tocContainer = document.getElementById('toc-container');
    const tocOverlay = document.getElementById('toc-overlay');

    const settingsBar = document.getElementById('settings-bar');
    const settingsContentBottom = settingsBar.querySelector('.settings-content.bottom');

    const toggleDarkModeButton = document.getElementById('toggle-dark-mode');
    const closeSettingsButton = document.getElementById('close-settings');
    const selectionActionButton = document.getElementById('selection-action-button');

    const settingsButtonsContainer = settingsContentBottom.querySelector('.setting-group:first-of-type');
    const tocButtonInSettings = settingsButtonsContainer.children[1];
    const notesButtonInSettings = settingsButtonsContainer.children[2];
    const viewSettingsButtonInSettings = settingsButtonsContainer.children[3];

    // --- EPUB.js Variables ---
    let book;
    let rendition;

    // --- Thymeleaf Variables ---
    const bookId = (typeof GLOBAL_BOOK_ID !== 'undefined' && GLOBAL_BOOK_ID !== null) ? String(GLOBAL_BOOK_ID) : null;
    const bookUrl = (typeof GLOBAL_BOOK_URL_TEMPLATE !== 'undefined' && bookId !== null) ? GLOBAL_BOOK_URL_TEMPLATE : null;

    // --- State Variables ---
    let isTocVisible = false;
    let areSettingsVisible = false;
    let settingsTimeout;
    let lastMouseUpPosition = {x: 0, y: 0};
    let isMouseDown = false;
    let isDragging = false;
    let startDragPosition = {x: 0, y: 0};
    const DRAG_THRESHOLD = 5;

    // --- Helper Functions ---
    const showLoading = () => {
        if (loadingIndicator) loadingIndicator.style.display = 'block';
    };
    const hideLoading = () => {
        if (loadingIndicator) loadingIndicator.style.display = 'none';
    };

    const toggleToc = (forceState) => {
        isTocVisible = (forceState !== undefined) ? forceState : !isTocVisible;
        document.body.classList.toggle('toc-visible', isTocVisible);
        if (tocContainer) tocContainer.setAttribute('aria-hidden', String(!isTocVisible));
        if (tocOverlay) tocOverlay.setAttribute('aria-hidden', String(!isTocVisible));
        if (isTocVisible && tocContainer) {
            const firstLink = tocContainer.querySelector('a');
            if (firstLink) firstLink.focus(); else tocContainer.focus();
        }
    };

    const toggleSettings = (forceState) => {
        clearTimeout(settingsTimeout);
        areSettingsVisible = (forceState !== undefined) ? forceState : !areSettingsVisible;
        if (settingsContentBottom) settingsContentBottom.classList.toggle('settings-visible', areSettingsVisible);
        if (areSettingsVisible) {
            settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
        }
    };

    const attachIframeListeners = () => {
        if (!rendition || !rendition.manager || !rendition.manager.views) {
            return;
        }
        let currentViewContents;
        if (rendition.manager.current) {
            currentViewContents = rendition.manager.current();
        } else if (rendition.manager.views?.first) {
            currentViewContents = rendition.manager.views.first();
        }

        if (currentViewContents && currentViewContents.document) {
            const iframeDoc = currentViewContents.document;
            iframeDoc.removeEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.removeEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.removeEventListener('mouseup', handleIframeMouseUp);
            iframeDoc.addEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.addEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.addEventListener('mouseup', handleIframeMouseUp);
        }
    };

    function handleIframeMouseDown(event) {
        isMouseDown = true;
        isDragging = false;
        startDragPosition = {x: event.clientX, y: event.clientY};
    }

    function handleIframeMouseMove(event) {
        if (!isMouseDown) return;
        const dx = Math.abs(event.clientX - startDragPosition.x);
        const dy = Math.abs(event.clientY - startDragPosition.y);
        if (!isDragging && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
            isDragging = true;
        }
    }

    function handleIframeMouseUp(event) {
        let iframeEl = null;
        try {
            if (event.target?.ownerDocument?.defaultView) {
                iframeEl = event.target.ownerDocument.defaultView.frameElement;
            } else if (event.view?.frameElement) {
                iframeEl = event.view.frameElement;
            }
        } catch (e) {
            console.warn("[WARN] handleIframeMouseUp: Error accessing frameElement:", e);
        }

        if (iframeEl) {
            const iframeRect = iframeEl.getBoundingClientRect();
            const scrollX = window.scrollX || window.pageXOffset;
            const scrollY = window.scrollY || window.pageYOffset;
            lastMouseUpPosition = {
                x: iframeRect.left + event.clientX + scrollX,
                y: iframeRect.top + event.clientY + scrollY
            };
        } else {
            lastMouseUpPosition = {
                x: event.clientX + (window.scrollX || window.pageXOffset),
                y: event.clientY + (window.scrollY || window.pageYOffset)
            };
        }

        const wasDraggingOnMouseUp = isDragging;

        if (wasDraggingOnMouseUp) {
            setTimeout(() => {
                let currentSelectedText = '';
                let currentSelectionRangeCount = 0;
                let currentViewForSelection = null;
                if (rendition?.manager) {
                    if (rendition.manager.current) {
                        currentViewForSelection = rendition.manager.current();
                    } else if (rendition.manager.views?.first) {
                        currentViewForSelection = rendition.manager.views.first();
                    }
                }
                if (currentViewForSelection?.window) {
                    try {
                        const selectionInIframe = currentViewForSelection.window.getSelection();
                        currentSelectedText = selectionInIframe.toString().trim();
                        currentSelectionRangeCount = selectionInIframe.rangeCount;
                    } catch (e) {
                        console.error("[ERROR] handleIframeMouseUp: Error getting selection:", e);
                    }
                }
                if (currentSelectedText && currentSelectionRangeCount > 0 && selectionActionButton) {
                    let buttonTop = lastMouseUpPosition.y + 15;
                    let buttonLeft = lastMouseUpPosition.x - (selectionActionButton.offsetWidth / 2);
                    const buttonWidth = selectionActionButton.offsetWidth;
                    const buttonHeight = selectionActionButton.offsetHeight;
                    const bodyClientWidth = document.documentElement.clientWidth;
                    const bodyClientHeight = document.documentElement.clientHeight;
                    const currentScrollX = window.scrollX || window.pageXOffset;
                    const currentScrollY = window.scrollY || window.pageYOffset;
                    if (buttonLeft + buttonWidth > currentScrollX + bodyClientWidth - 10) {
                        buttonLeft = currentScrollX + bodyClientWidth - buttonWidth - 10;
                    }
                    if (buttonLeft < currentScrollX + 10) {
                        buttonLeft = currentScrollX + 10;
                    }
                    if (buttonTop + buttonHeight > currentScrollY + bodyClientHeight - 10) {
                        buttonTop = lastMouseUpPosition.y - buttonHeight - 15;
                    }
                    if (buttonTop < currentScrollY + 10) {
                        buttonTop = currentScrollY + 10;
                    }
                    selectionActionButton.style.top = `${buttonTop}px`;
                    selectionActionButton.style.left = `${buttonLeft}px`;
                    selectionActionButton.style.display = 'block';
                    selectionActionButton.textContent = `액션`;
                }
            }, 0);
        }
        isMouseDown = false;
    }


    const initEpubViewer = () => {
        console.log("[DEBUG] initEpubViewer: Function called.");
        if (!bookUrl || !bookId) {
            if (viewerElement) viewerElement.innerHTML = `<p>Book URL/ID missing.</p>`;
            hideLoading();
            return;
        }
        showLoading();
        try {
            book = ePub(bookUrl);
            if (bookUrl) { /* Manual fetch (optional) */
            }
            if (book?.opened?.then) book.opened.catch(err => console.error("book.opened REJECTED:", err));

            if (book?.ready?.then) {
                book.ready.then(() => {
                    rendition = book.renderTo(viewerElement, {
                        width: "100%", height: "100%", flow: "paginated", allowScriptedContent: false,
                    });
                    if (!rendition) {
                        throw new Error("Rendition creation failed.");
                    }

                    rendition.on("relocated", loc => {
                        localStorage.setItem(`epub-location-${bookId}`, loc.start.cfi);
                        setTimeout(attachIframeListeners, 50);
                    });

                    rendition.on("selected", (cfi, contents) => {
                        const sel = contents.window.getSelection();
                        const txt = sel.toString().trim();
                        if (!(txt && sel.rangeCount > 0) && selectionActionButton?.style.display === 'block') {
                            selectionActionButton.style.display = 'none';
                        }
                    });

                    // defineEpubThemes()는 override 방식에서는 호출하지 않음.
                    // defineEpubThemes();

                    rendition.on('click', function handleRenditionClick(event) {
                        const clickWasDragEnd = isDragging;
                        if (clickWasDragEnd) {
                            isDragging = false;
                            return;
                        }
                        let currentView = rendition?.manager?.current ? rendition.manager.current() : rendition?.manager?.views?.first();
                        if (!rendition || !rendition.currentLocation() || !currentView) return;

                        const btnVisible = selectionActionButton?.style.display === 'block';
                        if (!clickWasDragEnd && btnVisible) {
                            if (event.target !== selectionActionButton && !selectionActionButton.contains(event.target)) {
                                if (selectionActionButton) selectionActionButton.style.display = 'none';
                                return;
                            } else {
                                return;
                            }
                        }

                        const origEvent = event.originalEvent || event;
                        if (origEvent.target.closest('#settings-bar')) return;
                        if (origEvent.target.closest('a[href]')) return;

                        const clickX_page = lastMouseUpPosition.x;
                        const scrollX = window.scrollX || window.pageXOffset;
                        const clickX_viewport = clickX_page - scrollX;
                        const vpWidth = window.innerWidth;
                        if (clickX_viewport < vpWidth / 3) {
                            rendition.prev();
                            toggleSettings(false);
                        } else if (clickX_viewport > vpWidth * 2 / 3) {
                            rendition.next();
                            toggleSettings(false);
                        } else {
                            toggleSettings();
                        }
                    });

                    return rendition.display();
                })
                    .then((section) => {
                        hideLoading();
                        loadToc();
                        loadPersistentSettings(); // 여기서 applyDarkMode가 호출됨
                        setTimeout(attachIframeListeners, 100);
                        if (book.packaging?.metadata) console.log("Book rendered:", book.packaging.metadata.title);
                        const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                        if (lastLoc) return rendition.display(lastLoc);
                    })
                    .catch(err => {
                        console.error("EPUB processing chain error:", err);
                        hideLoading();
                    });
            } else {
                console.error("book.ready not available.");
                hideLoading();
            }
        } catch (e) {
            console.error("ePub instantiation error:", e);
            hideLoading();
        }
    };

    const loadToc = () => {
        if (!book?.loaded?.navigation?.then) {
            if (tocList) tocList.innerHTML = '<li>TOC load fail.</li>';
            return;
        }
        book.loaded.navigation.then(nav => {
            if (!tocList) return;
            const actualToc = nav.toc;
            const frag = document.createDocumentFragment();
            if (Array.isArray(actualToc)) {
                actualToc.forEach(item => {
                    const li = document.createElement('li');
                    const a = document.createElement('a');
                    a.textContent = (item.label || "Untitled").trim();
                    a.href = item.href;
                    a.addEventListener('click', e => {
                        e.preventDefault();
                        if (rendition) rendition.display(item.href);
                        toggleToc(false);
                    });
                    li.appendChild(a);
                    frag.appendChild(li);
                });
            } else {
                if (tocList) tocList.innerHTML = '<li>Bad TOC format.</li>';
                return;
            }
            tocList.innerHTML = '';
            tocList.appendChild(frag);
        }).catch(err => {
            if (tocList) tocList.innerHTML = '<li>TOC error.</li>';
            console.error("TOC load error:", err);
        });
    };

    const setupNavigationAndInteractions = () => {
        if (!viewerElement) return;
        document.addEventListener('keydown', e => {
            if (!rendition || document.activeElement?.matches('input, textarea, [contenteditable]')) return;
            if (isTocVisible || (areSettingsVisible && e.key === 'Escape')) {
                toggleSettings(false);
                e.preventDefault();
                return;
            }
            if (e.key === 'ArrowLeft') {
                rendition.prev();
                toggleSettings(false);
                e.preventDefault();
            } else if (e.key === 'ArrowRight') {
                rendition.next();
                toggleSettings(false);
                e.preventDefault();
            }
        });
        document.body.addEventListener('click', e => {
            if (selectionActionButton?.style.display === 'block' &&
                e.target !== selectionActionButton && !selectionActionButton.contains(e.target) &&
                !viewerElement.contains(e.target)) {
                selectionActionButton.style.display = 'none';
            }
        }, true);
    };

    // --- Settings ---
    const applyDarkMode = (isDark) => {
        console.log(`[DEBUG DARKMODE] applyDarkMode called with isDark: ${isDark}`);
        document.body.classList.toggle('dark-mode', isDark);

        if (rendition?.themes) {
            try {
                if (isDark) {
                    console.log(`[DEBUG DARKMODE] Overriding theme to DARK`);
                    rendition.themes.override('color', '#e0e0e0', true);
                    rendition.themes.override('background', '#1e1e1e', true);
                    rendition.themes.override('p', {'color': '#e0e0e0 !important'});
                    rendition.themes.override('h1, h2, h3, h4, h5, h6', {'color': '#f0f0f0 !important'});
                    rendition.themes.override('a', {'color': '#bb86fc !important'});
                    rendition.themes.override('li', {'color': '#e0e0e0 !important'});
                    // --- div 태그에 대한 스타일 override 추가 ---
                    rendition.themes.override('div', {'color': '#e0e0e0 !important'});

                } else {
                    console.log(`[DEBUG DARKMODE] Overriding theme to LIGHT (default)`);
                    rendition.themes.override('color', null, true);
                    rendition.themes.override('background', null, true);
                    rendition.themes.override('p', {'color': null});
                    rendition.themes.override('h1, h2, h3, h4, h5, h6', {'color': null});
                    rendition.themes.override('a', {'color': null});
                    rendition.themes.override('li', {'color': null});
                    // --- div 태그 스타일 복원 추가 ---
                    rendition.themes.override('div', {'color': null});
                }

                if (rendition.display && typeof rendition.display === 'function') {
                    const currentLocation = rendition.currentLocation();
                    if (currentLocation?.start?.cfi) {
                        rendition.display(currentLocation.start.cfi)
                            .catch(err => console.error("[ERROR DARKMODE] Error re-displaying with CFI after override:", err));
                    } else {
                        rendition.display()
                            .catch(err => console.error("[ERROR DARKMODE] Error re-displaying (no CFI) after override:", err));
                    }
                }
            } catch (themeError) {
                console.error("[ERROR] applyDarkMode: Error overriding EPUB.js theme:", themeError);
            }
        } else {
            console.warn("[WARN] applyDarkMode: Rendition or themes object not available.");
        }

        if (toggleDarkModeButton) {
            toggleDarkModeButton.textContent = isDark ? 'Light Mode' : 'Dark Mode';
        }
        localStorage.setItem('epub-dark-mode', String(isDark));
    };

    /*
    const defineEpubThemes = () => {
        // override 방식을 사용하므로, 이 함수는 현재 사용되지 않음.
        // 필요시 register 방식으로 돌아갈 때 사용.
    };
    */

    const loadPersistentSettings = () => {
        applyDarkMode(localStorage.getItem('epub-dark-mode') === 'true');
    };

    const setupSettingsControls = () => {
        if (tocOverlay) tocOverlay.addEventListener('click', () => toggleToc(false));
        if (tocButtonInSettings) tocButtonInSettings.addEventListener('click', () => {
            toggleSettings(false);
            setTimeout(() => toggleToc(true), 50);
        });
        if (toggleDarkModeButton) {
            toggleDarkModeButton.addEventListener('click', () => {
                const bodyHasDarkModeClass = document.body.classList.contains('dark-mode');
                applyDarkMode(!bodyHasDarkModeClass);
                clearTimeout(settingsTimeout);
                settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
            });
        }
        if (closeSettingsButton) closeSettingsButton.addEventListener('click', () => toggleSettings(false));
        if (viewSettingsButtonInSettings) viewSettingsButtonInSettings.addEventListener('click', () => {
            alert("View settings (TBD)");
            clearTimeout(settingsTimeout);
        });
        if (notesButtonInSettings) notesButtonInSettings.addEventListener('click', () => {
            alert("Notes (TBD)");
            clearTimeout(settingsTimeout);
        });
    };

    const setupSelectionAction = () => {
        if (!selectionActionButton) return;
        selectionActionButton.addEventListener('click', (event) => {
            event.stopPropagation();
            let txt = '';
            let view = rendition?.manager?.current ? rendition.manager.current() : rendition?.manager?.views?.first();
            if (view?.window) try {
                txt = view.window.getSelection().toString().trim();
            } catch (e) {
            }
            alert(txt ? `Selected: "${txt.substring(0, 50)}..."` : "Action (no text).");
            selectionActionButton.style.display = 'none';
        });
    };

    console.log("[DEBUG] DOMContentLoaded: Starting initialization sequence.");
    if (viewerElement && loadingIndicator && tocList && tocContainer && tocOverlay && settingsBar && selectionActionButton) {
        initEpubViewer();
        setupNavigationAndInteractions();
        setupSettingsControls();
        setupSelectionAction();
        if (settingsContentBottom) settingsContentBottom.classList.remove('settings-visible');
        areSettingsVisible = false;
        console.log("[DEBUG] DOMContentLoaded: Initialization sequence complete.");
    } else {
        console.error("[CRITICAL] DOMContentLoaded: One or more critical UI elements missing.");
        // ... (누락 요소 로깅 및 사용자 메시지 표시는 이전과 동일하게 유지)
    }
});