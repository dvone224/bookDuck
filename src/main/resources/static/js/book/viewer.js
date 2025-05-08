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

    const settingsButtonsContainer = settingsContentBottom ? settingsContentBottom.querySelector('.setting-group:first-of-type') : null;
    const tocButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[1] : null;
    const notesButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[2] : null;
    const viewSettingsButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[3] : null;


    // --- EPUB.js Variables ---
    let book;
    let rendition;

    // --- Thymeleaf Variables ---
    // GLOBAL_BOOK_ID와 GLOBAL_USER_ID는 HTML의 <script th:inline="javascript"> 블록에서 이미 const로 선언됨
    // GLOBAL_BOOK_URL_TEMPLATE도 마찬가지
    const bookId = (typeof GLOBAL_BOOK_ID !== 'undefined' && GLOBAL_BOOK_ID !== null) ? String(GLOBAL_BOOK_ID) : null;
    const userId = (typeof GLOBAL_USER_ID !== 'undefined' && GLOBAL_USER_ID !== null) ? String(GLOBAL_USER_ID) : null;
    // bookUrl은 GLOBAL_BOOK_URL_TEMPLATE을 사용합니다.
    const bookUrl = (typeof GLOBAL_BOOK_URL_TEMPLATE !== 'undefined' && GLOBAL_BOOK_URL_TEMPLATE !== null) ? GLOBAL_BOOK_URL_TEMPLATE : null;


    // --- State Variables ---
    let isTocVisible = false;
    let areSettingsVisible = false;
    let settingsTimeout;
    let lastMouseUpPosition = {x: 0, y: 0};
    let isMouseDown = false;
    let isDragging = false;
    let startDragPosition = {x: 0, y: 0};
    const DRAG_THRESHOLD = 5;

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
            console.warn("[WARN] attachIframeListeners: Rendition 또는 views가 준비되지 않았습니다.");
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
            console.warn("[WARN] attachIframeListeners: iframe 문서를 가져올 수 없습니다.");
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
            if (selectionActionButton && selectionActionButton.style.display === 'block') {
                selectionActionButton.style.display = 'none';
            }
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
            console.warn("[WARN] handleIframeMouseUp: frameElement 접근 중 오류:", e);
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

        setTimeout(() => {
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
                selectionActionButton.textContent = `쪽지 쓰기`;
            }
        }, 0);
    }

    // ★★★ CFI를 백엔드로 저장하는 함수 (CSRF 관련 코드 제거) ★★★
    async function saveCurrentPageMark(cfiToSave) {
        if (!userId || !bookId || !cfiToSave) {
            console.warn("현재 페이지 표시를 저장할 수 없습니다: userId, bookId 또는 CFI 정보가 누락되었습니다.", {userId, bookId, cfiToSave});
            return;
        }

        console.log(`[DEBUG] 페이지 표시 저장 시도: CFI=${cfiToSave}, bookId=${bookId}, userId=${userId}`);

        try {
            const headers = {
                'Content-Type': 'application/json',
            };
            // CSRF 토큰 관련 헤더 추가 로직 제거

            const response = await fetch('/book/mark', { // 백엔드 API 엔드포인트 (프로젝트에 맞게 수정)
                method: 'POST',
                headers: headers,
                body: JSON.stringify({
                    userId: userId,
                    bookId: bookId,
                    mark: cfiToSave
                })
            });

            if (!response.ok) {
                const errorText = await response.text();
                console.error('페이지 표시 저장 실패 (백엔드):', response.status, errorText);
            } else {
                console.log('페이지 표시가 백엔드에 성공적으로 저장되었습니다:', cfiToSave);
            }
        } catch (error) {
            console.error('페이지 표시 저장 중 네트워크 오류 또는 JavaScript 오류 발생:', error);
        }
    }


    const initEpubViewer = () => {
        console.log("[DEBUG] initEpubViewer: 함수 호출됨.");
        if (!bookUrl || !bookId) { // bookUrl은 GLOBAL_BOOK_URL_TEMPLATE을 사용
            if (viewerElement) viewerElement.innerHTML = `<p>책 URL (${bookUrl}) 또는 ID (${bookId})가 없습니다.</p>`;
            hideLoading();
            return;
        }
        showLoading();
        try {
            console.log("[DEBUG] ePub 객체 생성 시도, URL:", bookUrl);
            book = ePub(bookUrl); // ePub(url, options) 형태. options는 필요시 추가.
            if (book?.opened?.then) book.opened.catch(err => console.error("book.opened REJECTED:", err));

            if (book?.ready?.then) {
                book.ready.then(() => {
                    console.log("[DEBUG] Book ready. Rendition 생성 시도.");
                    rendition = book.renderTo(viewerElement, {
                        width: "100%", height: "100%", flow: "paginated", allowScriptedContent: false, // 보안상 false 권장
                    });
                    if (!rendition) {
                        throw new Error("Rendition 생성 실패.");
                    }
                    console.log("[DEBUG] Rendition 생성 완료.");

                    rendition.on("relocated", loc => {
                        const currentCfi = loc.start.cfi;
                        localStorage.setItem(`epub-location-${bookId}`, currentCfi);
                        console.log(`[DEBUG] Relocated. CFI: ${currentCfi}, Chapter Href: ${loc.start.href}`);


                        if (loc && loc.start && loc.start.href) {
                            currentChapterHrefForComment = loc.start.href;
                        }

                        // ★★★ 페이지 이동 시 CFI를 백엔드에 저장 ★★★
                        if (currentCfi && userId && bookId) {
                            saveCurrentPageMark(currentCfi);
                        }
                        // ★★★ CFI 저장 로직 끝 ★★★

                        setTimeout(attachIframeListeners, 50); // iframe 이벤트 리스너 재부착
                    });

                    rendition.on("selected", (cfiRange, contents) => {
                        const sel = contents.window.getSelection();
                        const txt = sel.toString().trim();

                        if (txt && sel.rangeCount > 0) {
                            currentSelectionCfiForComment = cfiRange;
                            currentSelectedTextForComment = txt;
                            if (rendition && rendition.currentLocation() && rendition.currentLocation().start) {
                                currentChapterHrefForComment = rendition.currentLocation().start.href;
                            }
                            console.log("[DEBUG] 선택됨:", {
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
                            console.log("[DEBUG] 선택 해제됨.");
                        }
                    });

                    rendition.on('displayed', (section) => {
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] 새 섹션 표시됨, chapterHref:", currentChapterHrefForComment);
                        }
                        setTimeout(attachIframeListeners, 100);
                    });

                    rendition.on('click', function handleRenditionClick(event) {
                        const clickWasDragEnd = isDragging;
                        if (clickWasDragEnd) {
                            isDragging = false;
                            return;
                        }

                        if (!rendition || !rendition.currentLocation()) return;

                        const btnVisible = selectionActionButton?.style.display === 'block';
                        if (btnVisible && event.target !== selectionActionButton && !selectionActionButton.contains(event.target)) {
                            if (selectionActionButton) selectionActionButton.style.display = 'none';
                            return;
                        }
                        if (btnVisible && (event.target === selectionActionButton || selectionActionButton.contains(event.target))) {
                            return;
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

                    console.log("[DEBUG] Rendition.display() 호출 시도.");
                    return rendition.display(); // 초기 위치 표시
                })
                    .then((section) => {
                        hideLoading();
                        console.log("[DEBUG] 책 초기 표시 완료.");
                        loadToc();
                        loadPersistentSettings();
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] 초기 표시, chapterHref:", currentChapterHrefForComment);
                        } else if (rendition && rendition.currentLocation() && rendition.currentLocation().start) {
                            currentChapterHrefForComment = rendition.currentLocation().start.href;
                            console.log("[DEBUG] 초기 표시 (폴백), chapterHref:", currentChapterHrefForComment);
                        }
                        setTimeout(attachIframeListeners, 100); // 초기 로드 후 iframe 리스너 부착
                        if (book.packaging?.metadata) console.log("책 렌더링됨:", book.packaging.metadata.title);

                        const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                        if (lastLoc) {
                            console.log("[DEBUG] 저장된 위치로 이동 시도:", lastLoc);
                            return rendition.display(lastLoc);
                        }
                    })
                    .catch(err => {
                        console.error("EPUB 처리 중 오류:", err);
                        hideLoading();
                        if (viewerElement) viewerElement.innerHTML = `<p>EPUB 로딩 오류: ${err.message || err}</p>`;
                    });
            } else {
                console.error("book.ready 사용 불가.");
                hideLoading();
                if (viewerElement) viewerElement.innerHTML = `<p>EPUB 책 객체가 준비되지 않았습니다.</p>`;
            }
        } catch (e) {
            console.error("ePub 인스턴스 생성 오류:", e);
            hideLoading();
            if (viewerElement) viewerElement.innerHTML = `<p>EPUB 생성 오류: ${e.message || e}</p>`;
        }
    };

    const loadToc = () => {
        if (!book?.loaded?.navigation?.then) {
            if (tocList) tocList.innerHTML = '<li>목차 로딩 실패.</li>';
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
                    a.textContent = (item.label || "제목 없음").trim();
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
                if (tocList) tocList.innerHTML = '<li>목차가 비어 있거나 형식이 잘못되었습니다.</li>';
            }
            tocList.innerHTML = '';
            tocList.appendChild(frag);
        }).catch(err => {
            if (tocList) tocList.innerHTML = '<li>목차 로딩 오류.</li>';
            console.error("목차 로딩 오류:", err);
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

    const applyDarkMode = (isDark) => {
        console.log(`[DEBUG DARKMODE] applyDarkMode 호출됨, isDark: ${isDark}`);
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
                console.log(`[DEBUG DARKMODE] 테마를 ${isDark ? 'DARK' : 'LIGHT'}(으)로 변경 중`);
                if (rendition.display && typeof rendition.display === 'function') {
                    const currentLocation = rendition.currentLocation();
                    if (currentLocation?.start?.cfi) {
                        rendition.display(currentLocation.start.cfi)
                            .catch(err => console.error("[ERROR DARKMODE] CFI로 재표시 중 오류(테마 변경 후):", err));
                    } else {
                        rendition.display()
                            .catch(err => console.error("[ERROR DARKMODE] 재표시 중 오류(CFI 없음, 테마 변경 후):", err));
                    }
                }
            } catch (themeError) {
                console.error("[ERROR] applyDarkMode: EPUB.js 테마 변경 중 오류:", themeError);
            }
        } else {
            console.warn("[WARN] applyDarkMode: Rendition 또는 themes 객체 사용 불가.");
        }
        if (toggleDarkModeButton) {
            toggleDarkModeButton.textContent = isDark ? '라이트 모드' : '다크 모드';
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
            alert("보기 설정 (구현 예정)");
            clearTimeout(settingsTimeout);
        });
        if (notesButtonInSettings) notesButtonInSettings.addEventListener('click', () => {
            alert("노트 (구현 예정)");
            clearTimeout(settingsTimeout);
        });
    };

    const setupSelectionAction = () => {
        if (!selectionActionButton) return;
        selectionActionButton.addEventListener('click', (event) => {
            event.stopPropagation();

            if (currentSelectionCfiForComment && bookId && currentChapterHrefForComment && currentSelectedTextForComment) {
                const params = new URLSearchParams({
                    bookId: String(bookId),
                    cfi: currentSelectionCfiForComment,
                    selectedText: currentSelectedTextForComment.substring(0, 200),
                    chapterHref: currentChapterHrefForComment
                });
                console.log(`[DEBUG] 쪽지 작성 페이지로 이동: /comments/new, 파라미터: ${params.toString()}`);
                window.location.href = `/comments/new?${params.toString()}`; // 실제 쪽지 작성 페이지 URL로 변경
            } else {
                alert("선택된 내용이 없거나, 위치 정보를 가져올 수 없습니다. 다시 시도해 주세요.");
                console.warn("쪽지 작성 폼으로 이동 불가. 정보 누락:", {
                    cfi: currentSelectionCfiForComment,
                    bookId: bookId,
                    chapterHref: currentChapterHrefForComment,
                    text: currentSelectedTextForComment
                });
            }
            selectionActionButton.style.display = 'none';
        });
    };

    // 초기화 시작
    console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 시작.");
    console.log("[DEBUG] Thymeleaf 변수 값:", {bookId, userId, bookUrl});

    if (viewerElement && loadingIndicator && tocList && tocContainer && tocOverlay && settingsBar && selectionActionButton) {
        if (!userId) {
            console.warn("[WARN] 사용자 ID를 가져올 수 없습니다. 페이지 표시 저장 기능이 비활성화될 수 있습니다.");
        }
        if (!bookId) {
            console.warn("[WARN] 책 ID를 가져올 수 없습니다. 페이지 표시 저장 및 기타 책 관련 기능이 비활성화될 수 있습니다.");
        }
        if (!bookUrl) {
            console.error("[CRITICAL] 책 URL(bookUrl)을 결정할 수 없습니다. 뷰어를 초기화할 수 없습니다.");
            if (viewerElement) viewerElement.innerHTML = `<p>책 내용을 불러올 수 없습니다. (URL 구성 오류)</p>`;
            hideLoading(); // 로딩 표시 숨김
        } else {
            initEpubViewer();
            setupNavigationAndInteractions();
            setupSettingsControls();
            setupSelectionAction();
            if (settingsContentBottom) settingsContentBottom.classList.remove('settings-visible');
            areSettingsVisible = false;
        }
        console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 완료 (또는 URL 문제로 부분 완료).");
    } else {
        console.error("[CRITICAL] DOMContentLoaded: 하나 이상의 필수 UI 요소가 누락되었습니다.");
        if (!viewerElement) console.error("누락된 요소: viewerElement");
        // ... 기타 요소들
        alert("뷰어 초기화에 필요한 중요 요소가 없어 페이지를 정상적으로 표시할 수 없습니다.");
    }
});