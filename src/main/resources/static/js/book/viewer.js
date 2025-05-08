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
    let lastMouseUpPosition = {x: 0, y: 0}; // 페이지 클릭 위치 판단에 사용
    let isMouseDown = false;
    let isDragging = false;
    let startDragPosition = {x: 0, y: 0};
    const DRAG_THRESHOLD = 5;

    // ★★★ 쪽지 기능 관련 상태 변수 (추가) ★★★
    let currentSelectionCfiForComment = null;
    let currentChapterHrefForComment = null;
    let currentSelectedTextForComment = null;

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
            console.warn("[WARN] attachIframeListeners: Rendition or views not ready.");
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
        } else {
            console.warn("[WARN] attachIframeListeners: Could not get iframe document.");
        }
    };

    function handleIframeMouseDown(event) {
        isMouseDown = true;
        isDragging = false; // 드래그 시작 시 false로 초기화
        startDragPosition = {x: event.clientX, y: event.clientY};
    }

    function handleIframeMouseMove(event) {
        if (!isMouseDown) return;
        const dx = Math.abs(event.clientX - startDragPosition.x);
        const dy = Math.abs(event.clientY - startDragPosition.y);
        if (!isDragging && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
            isDragging = true;
            // 드래그 시작 시 액션 버튼이 보이면 숨김
            if (selectionActionButton && selectionActionButton.style.display === 'block') {
                selectionActionButton.style.display = 'none';
            }
        }
    }

    // handleIframeMouseUp은 주로 lastMouseUpPosition 설정과
    // 드래그 후 '쪽지 쓰기' 버튼 표시에 관여합니다.
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
        isMouseDown = false;
        // isDragging 상태는 rendition.on('click')에서 판단 후 필요시 초기화

        setTimeout(() => {
            // currentSelectedTextForComment는 rendition.on("selected")에서 업데이트됨
            if (wasDraggingOnMouseUp && currentSelectedTextForComment && selectionActionButton) {
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
                selectionActionButton.textContent = `쪽지 쓰기`; // 버튼 텍스트 변경
            }
        }, 0);
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
            // if (bookUrl) { /* Manual fetch (optional) */ } // 이 줄은 특별한 의미가 없어 보임
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
                        if (loc && loc.start && loc.start.href) {
                            currentChapterHrefForComment = loc.start.href;
                        }
                        setTimeout(attachIframeListeners, 50);
                    });

                    // ★★★ rendition.on("selected", ...) 쪽지 기능 위한 상태 변수 업데이트 ★★★
                    rendition.on("selected", (cfiRange, contents) => {
                        const sel = contents.window.getSelection();
                        const txt = sel.toString().trim();

                        if (txt && sel.rangeCount > 0) {
                            currentSelectionCfiForComment = cfiRange;
                            currentSelectedTextForComment = txt;
                            if (rendition && rendition.currentLocation() && rendition.currentLocation().start) {
                                currentChapterHrefForComment = rendition.currentLocation().start.href;
                            }
                            console.log("[DEBUG] Selected:", {
                                cfi: cfiRange,
                                text: txt,
                                chapter: currentChapterHrefForComment
                            });
                        } else {
                            currentSelectionCfiForComment = null;
                            currentSelectedTextForComment = null;
                            if (selectionActionButton?.style.display === 'block') {
                                selectionActionButton.style.display = 'none';
                            }
                            console.log("[DEBUG] Selection cleared.");
                        }
                    });


                    rendition.on('displayed', (section) => {
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] Displayed new section, chapterHref:", currentChapterHrefForComment);
                        }
                        setTimeout(attachIframeListeners, 100);
                    });

                    // ★★★ rendition.on('click', ...) 페이지 이동 로직 원복 ★★★
                    // 이 부분은 초기 제공 코드의 로직을 따릅니다.
                    // 즉, iframe 내부의 클릭 좌표가 아닌, lastMouseUpPosition (전체 페이지 기준 좌표)를 사용합니다.
                    rendition.on('click', function handleRenditionClick(event) {
                        const clickWasDragEnd = isDragging;
                        if (clickWasDragEnd) {
                            isDragging = false; // 드래그였으면 다음 클릭을 위해 isDragging 리셋
                            return; // 드래그 후의 클릭은 페이지 넘김/설정 토글을 하지 않음
                        }

                        // currentView는 이 로직에서 직접 사용되지 않지만, 참조를 위해 남겨둘 수 있음
                        // let currentView = rendition?.manager?.current ? rendition.manager.current() : rendition?.manager?.views?.first();
                        if (!rendition || !rendition.currentLocation()) return;

                        const btnVisible = selectionActionButton?.style.display === 'block';
                        // 쪽지 쓰기 버튼이 보이고, 클릭 대상이 버튼 자신이거나 그 자식 요소가 *아닌 경우*에만 버튼을 숨김.
                        // 그리고 페이지 넘김/설정 토글 로직으로 넘어가지 않도록 return.
                        if (btnVisible && event.target !== selectionActionButton && !selectionActionButton.contains(event.target)) {
                            if (selectionActionButton) selectionActionButton.style.display = 'none';
                            return;
                        }
                        // 쪽지 쓰기 버튼이 보이고, 클릭 대상이 버튼 자신이거나 그 자식 요소인 경우, 아무것도 안 함 (버튼의 자체 클릭 이벤트가 처리).
                        if (btnVisible && (event.target === selectionActionButton || selectionActionButton.contains(event.target))) {
                            return;
                        }

                        const origEvent = event.originalEvent || event;
                        if (origEvent.target.closest('#settings-bar')) return;
                        if (origEvent.target.closest('a[href]')) return; // 링크 클릭은 EPUB.js가 처리

                        // 페이지 넘김 로직 (초기 제공 코드 기준)
                        const clickX_page = lastMouseUpPosition.x; // mouseup 시점의 전체 페이지 x 좌표
                        const scrollX = window.scrollX || window.pageXOffset;
                        const clickX_viewport = clickX_page - scrollX; // 뷰포트 기준 x 좌표
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
                        loadPersistentSettings();
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] Initial display, chapterHref:", currentChapterHrefForComment);
                        } else if (rendition && rendition.currentLocation() && rendition.currentLocation().start) {
                            currentChapterHrefForComment = rendition.currentLocation().start.href;
                            console.log("[DEBUG] Initial display (fallback), chapterHref:", currentChapterHrefForComment);
                        }
                        setTimeout(attachIframeListeners, 100);
                        if (book.packaging?.metadata) console.log("Book rendered:", book.packaging.metadata.title);
                        const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                        if (lastLoc) return rendition.display(lastLoc);
                    })
                    .catch(err => {
                        console.error("EPUB processing chain error:", err);
                        hideLoading();
                        if (viewerElement) viewerElement.innerHTML = `<p>Error loading EPUB: ${err.message || err}</p>`;
                    });
            } else {
                console.error("book.ready not available.");
                hideLoading();
                if (viewerElement) viewerElement.innerHTML = `<p>EPUB book object not ready.</p>`;
            }
        } catch (e) {
            console.error("ePub instantiation error:", e);
            hideLoading();
            if (viewerElement) viewerElement.innerHTML = `<p>Error instantiating EPUB: ${e.message || e}</p>`;
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
            if (Array.isArray(actualToc) && actualToc.length > 0) {
                actualToc.forEach(item => {
                    const li = document.createElement('li');
                    const a = document.createElement('a');
                    a.textContent = (item.label || "Untitled").trim();
                    a.href = item.href;
                    a.addEventListener('click', e => {
                        e.preventDefault();
                        if (rendition) {
                            rendition.display(item.href);
                            currentChapterHrefForComment = item.href;
                        }
                        toggleToc(false);
                    });
                    li.appendChild(a);
                    frag.appendChild(li);
                });
            } else {
                if (tocList) tocList.innerHTML = '<li>Table of Contents is empty or in an invalid format.</li>';
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
            if (e.key === 'Escape') {
                if (isTocVisible) {
                    toggleToc(false);
                    e.preventDefault();
                    return;
                }
                if (areSettingsVisible) {
                    toggleSettings(false);
                    e.preventDefault();
                    return;
                }
            }
            if (!isTocVisible && !areSettingsVisible) {
                if (e.key === 'ArrowLeft') {
                    rendition.prev();
                    toggleSettings(false);
                    e.preventDefault();
                } else if (e.key === 'ArrowRight') {
                    rendition.next();
                    toggleSettings(false);
                    e.preventDefault();
                }
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
                const commonStyles = {
                    'p': {'color': isDark ? '#e0e0e0 !important' : null},
                    'h1, h2, h3, h4, h5, h6': {'color': isDark ? '#f0f0f0 !important' : null},
                    'a': {'color': isDark ? '#bb86fc !important' : null},
                    'li': {'color': isDark ? '#e0e0e0 !important' : null},
                    'div': {'color': isDark ? '#e0e0e0 !important' : null},
                };
                rendition.themes.override('color', isDark ? '#e0e0e0' : null, true);
                rendition.themes.override('background', isDark ? '#1e1e1e' : null, true);
                for (const selector in commonStyles) {
                    rendition.themes.override(selector, commonStyles[selector]);
                }
                console.log(`[DEBUG DARKMODE] Overriding theme to ${isDark ? 'DARK' : 'LIGHT'}`);
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

    // ★★★ setupSelectionAction 함수 수정 (페이지 이동 로직) ★★★
    const setupSelectionAction = () => {
        if (!selectionActionButton) return;
        selectionActionButton.addEventListener('click', (event) => {
            event.stopPropagation(); // 이벤트 버블링 방지

            // 쪽지 작성에 필요한 정보들이 모두 유효한지 확인
            if (currentSelectionCfiForComment && bookId && currentChapterHrefForComment && currentSelectedTextForComment) {
                // URL 파라미터를 만들기 위한 객체 생성
                const params = new URLSearchParams({
                    bookId: String(bookId),
                    cfi: currentSelectionCfiForComment,
                    selectedText: currentSelectedTextForComment.substring(0, 200), // URL 길이 고려
                    chapterHref: currentChapterHrefForComment
                });

                // 쪽지 작성 페이지로 이동
                console.log(`[DEBUG] Navigating to /comments/new with params: ${params.toString()}`);
                window.location.href = `/comments/new?${params.toString()}`;
            } else {
                // 필요한 정보가 하나라도 누락된 경우 사용자에게 알림
                alert("선택된 내용이 없거나, 위치 정보를 가져올 수 없습니다. 다시 시도해 주세요.");
                console.warn("Cannot navigate to comment form. Missing info:", {
                    cfi: currentSelectionCfiForComment,
                    bookId: bookId,
                    chapterHref: currentChapterHrefForComment,
                    text: currentSelectedTextForComment
                });
            }
            selectionActionButton.style.display = 'none'; // 버튼 숨김
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