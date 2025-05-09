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

    // null 체크 추가
    const settingsButtonsContainer = settingsContentBottom ? settingsContentBottom.querySelector('.setting-group:first-of-type') : null;
    const tocButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[1] : null;
    const notesButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[2] : null;
    const viewSettingsButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[3] : null;

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

    // 쪽지 기능 관련 상태 변수
    let currentSelectionCfiForComment = null;
    let currentChapterHrefForComment = null;
    let currentSelectedTextForComment = null;
    let currentHighlightCfis = new Set(); // 현재 화면에 표시된 하이라이트 CFI 목록 (중복 방지용)

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
            // 포커스 이동 로직 추가
            const firstFocusableElement = tocContainer.querySelector('a, button, [tabindex]:not([tabindex="-1"])');
            if (firstFocusableElement) {
                firstFocusableElement.focus();
            } else {
                tocContainer.focus(); // 포커스 가능 요소가 없으면 컨테이너에 포커스
            }
        } else if (!isTocVisible) {
            // TOC가 닫힐 때 이전 포커스 요소로 돌아가거나 기본 요소(예: body)로 이동
            // 여기서는 간단히 body로 포커스 이동 (개선 필요 시 이전 포커스 저장/복원 로직 추가)
            document.body.focus();
        }
    };

    const toggleSettings = (forceState) => {
        clearTimeout(settingsTimeout);
        areSettingsVisible = (forceState !== undefined) ? forceState : !areSettingsVisible;
        if (settingsContentBottom) settingsContentBottom.classList.toggle('settings-visible', areSettingsVisible);
        if (areSettingsVisible) {
            settingsTimeout = setTimeout(() => toggleSettings(false), 7000); // 7초 후 자동 닫힘
        }
    };

    const attachIframeListeners = () => {
        if (!rendition || !rendition.manager || !rendition.manager.views) {
            console.warn("[WARN] attachIframeListeners: Rendition or views not ready.");
            return;
        }
        // 현재 활성화된 View(iframe)를 가져옴
        let currentView = rendition.manager.current();
        // Fallback: current() 메소드가 없을 경우 첫 번째 뷰 사용 시도
        if (!currentView && rendition.manager.views?.first) {
            currentView = rendition.manager.views.first();
        }

        if (currentView && currentView.document) {
            const iframeDoc = currentView.document;
            // 리스너 중복 부착 방지를 위해 먼저 제거
            iframeDoc.removeEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.removeEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.removeEventListener('mouseup', handleIframeMouseUp);
            // 새 리스너 부착
            iframeDoc.addEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.addEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.addEventListener('mouseup', handleIframeMouseUp);
            console.log("[DEBUG] Iframe listeners attached to view for href:", currentView.section?.href || 'N/A');
        } else {
            console.warn("[WARN] attachIframeListeners: Could not get iframe document for the current view.");
        }
    };

    function handleIframeMouseDown(event) {
        isMouseDown = true;
        isDragging = false; // 마우스 누르는 순간 드래그 상태 초기화
        startDragPosition = {x: event.clientX, y: event.clientY};
    }

    function handleIframeMouseMove(event) {
        if (!isMouseDown) return; // 마우스를 누른 상태가 아니면 무시
        // 시작점과 현재 위치 계산
        const dx = Math.abs(event.clientX - startDragPosition.x);
        const dy = Math.abs(event.clientY - startDragPosition.y);
        // 아직 드래그 상태가 아니고, 이동 거리가 임계값을 넘으면 드래그 시작으로 간주
        if (!isDragging && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
            isDragging = true;
            console.log("[DEBUG] Dragging started.");
            // 드래그 시작 시 '쪽지 쓰기' 버튼 숨김
            if (selectionActionButton && selectionActionButton.style.display === 'block') {
                selectionActionButton.style.display = 'none';
            }
        }
    }

    function handleIframeMouseUp(event) {
        let iframeEl = null;
        try {
            // 이벤트가 발생한 iframe 요소를 찾음
            iframeEl = event.target?.ownerDocument?.defaultView?.frameElement;
        } catch (e) {
            console.warn("[WARN] handleIframeMouseUp: Error accessing frameElement:", e);
        }

        // 마우스 뗀 위치 계산 (페이지 전체 기준 좌표)
        if (iframeEl) {
            const iframeRect = iframeEl.getBoundingClientRect();
            lastMouseUpPosition = {
                x: iframeRect.left + event.clientX + window.scrollX,
                y: iframeRect.top + event.clientY + window.scrollY
            };
        } else {
            // iframe 참조 실패 시 이벤트 좌표 사용
            lastMouseUpPosition = {
                x: event.clientX + window.scrollX,
                y: event.clientY + window.scrollY
            };
        }

        const wasDraggingOnMouseUp = isDragging; // 현재 isDragging 상태 저장
        isMouseDown = false; // 마우스 버튼 뗌
        // isDragging 상태는 rendition.on('click')에서 처리하도록 유지

        // 드래그 후 && 텍스트가 실제로 선택되었을 때 (selected 이벤트 후) 버튼 표시
        // setTimeout을 사용하여 selection 상태가 업데이트될 시간을 확보
        setTimeout(() => {
            // currentSelectedTextForComment는 rendition.on("selected")에서 업데이트됨
            if (wasDraggingOnMouseUp && currentSelectedTextForComment && selectionActionButton) {
                let buttonTop = lastMouseUpPosition.y + 15;
                let buttonLeft = lastMouseUpPosition.x - (selectionActionButton.offsetWidth / 2);
                const buttonWidth = selectionActionButton.offsetWidth;
                const buttonHeight = selectionActionButton.offsetHeight;
                const bodyClientWidth = document.documentElement.clientWidth;
                const bodyClientHeight = document.documentElement.clientHeight;
                const currentScrollX = window.scrollX;
                const currentScrollY = window.scrollY;

                // 버튼 위치 조정 (화면 밖으로 나가지 않도록)
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
                console.log("[DEBUG] Selection action button displayed.");
            }
        }, 0); // 비동기 처리 큐에 넣음
    }

    // ★★★ 하이라이트 로드 및 적용 함수 ★★★
    const loadAndApplyHighlights = async (currentBookId, chapterHref) => {
        if (!currentBookId || !chapterHref || !rendition || !rendition.annotations) {
            console.warn("[Highlight] Missing bookId, chapterHref, or rendition/annotations object.");
            return;
        }

        // 현재 챕터의 기존 하이라이트 제거
        currentHighlightCfis.forEach(cfiToRemove => {
            try {
                rendition.annotations.remove(cfiToRemove, 'highlight');
            } catch (removeError) {
                console.warn(`[Highlight] Error removing previous highlight for CFI ${cfiToRemove}:`, removeError.message);
            }
        });
        currentHighlightCfis.clear(); // 목록 초기화

        try {
            const encodedHref = encodeURIComponent(chapterHref);
            const apiUrl = `/api/comments/${currentBookId}/${encodedHref}`;
            console.log(`[Highlight] Fetching highlights from: ${apiUrl}`);

            const response = await fetch(apiUrl);
            if (!response.ok) {
                if (response.status === 404) {
                    console.log(`[Highlight] No comments found for ${chapterHref} (404).`);
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const highlights = await response.json();

            if (!Array.isArray(highlights)) {
                console.warn("[Highlight] Received non-array response for highlights:", highlights);
                return;
            }
            console.log(`[Highlight] Received ${highlights.length} highlights for ${chapterHref}`);

            highlights.forEach(hl => {
                if (hl.locationCfi && hl.noteColor) {
                    try {
                        rendition.annotations.add('highlight', hl.locationCfi,
                            {commentId: hl.id},
                            handleHighlightClick,
                            'book-highlight', // CSS 클래스 추가
                            {'fill': hl.noteColor, 'fill-opacity': '0.4', 'mix-blend-mode': 'multiply'} // 투명도 약간 조정
                        );
                        currentHighlightCfis.add(hl.locationCfi); // 관리 목록에 추가
                        console.log(`[Highlight] Added highlight for CFI: ${hl.locationCfi} with color: ${hl.noteColor}`);
                    } catch (annotError) {
                        console.error(`[Highlight] Error adding annotation for CFI ${hl.locationCfi}:`, annotError.message);
                    }
                } else {
                    console.warn("[Highlight] Skipping highlight due to missing CFI or color:", hl);
                }
            });

        } catch (error) {
            console.error("[Highlight] Failed to load or apply highlights:", error);
        }
    };

    // ★★★ 하이라이트 클릭 시 실행될 콜백 함수 ★★★
    const handleHighlightClick = (e, annotation) => {
        let commentId = null;
        // Epub.js 버전에 따라 annotation 객체가 콜백의 두 번째 인자로 전달될 수 있음
        if (annotation && annotation.data && annotation.data.commentId) {
            commentId = annotation.data.commentId;
            console.log("Highlight clicked. Annotation data found:", annotation.data);
        } else if (e && e.target && e.target.dataset.commentId) {
            // data-* 속성 사용 시 (CSS 클래스를 통해 data 속성을 추가했다면)
            commentId = e.target.dataset.commentId;
            console.log("Highlight clicked. Found commentId in dataset:", commentId);
        } else {
            // Fallback: 이벤트 객체나 다른 방법으로 정보 추출 시도 (필요시)
            console.log("Highlight clicked, but could not extract commentId. Event:", e, "Annotation:", annotation);
        }

        if (commentId) {
            alert(`Comment ID: ${commentId} 클릭됨! 실제 코멘트 내용을 보여주는 로직 추가 필요.`);
            // TODO: 코멘트 상세 보기 UI 구현 (예: 모달, 팝오버)
            // fetch(`/api/comments/detail/${commentId}`).then(...)
        } else {
            alert("하이라이트 클릭됨! (코멘트 ID 정보 없음)");
        }
    };


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
            if (book?.opened?.then) book.opened.catch(err => console.error("book.opened REJECTED:", err));

            if (book?.ready?.then) {
                book.ready.then(() => {
                    rendition = book.renderTo(viewerElement, {
                        width: "100%", height: "100%", flow: "paginated", allowScriptedContent: false,
                    });
                    if (!rendition) {
                        throw new Error("Rendition creation failed.");
                    }

                    // --- Rendition Event Listeners ---

                    rendition.on("relocated", async (location) => { // async 추가
                        console.log("[DEBUG] Relocated to:", location.start.cfi);
                        localStorage.setItem(`epub-location-${bookId}`, location.start.cfi);
                        if (location && location.start && location.start.href) {
                            currentChapterHrefForComment = location.start.href;
                            // 위치 이동 시 하이라이트 로드
                            await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                        }
                        // 리스너는 항상 재부착 시도 (View 객체가 바뀔 수 있으므로)
                        setTimeout(attachIframeListeners, 50);
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

                    rendition.on('displayed', async (section) => { // async 추가
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] Displayed new section, chapterHref:", currentChapterHrefForComment);
                            // ★★★ 새 섹션 표시 시 하이라이트 로드 및 적용 ★★★
                            await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                        }
                        // 리스너는 하이라이트 로드와 별개로 부착
                        setTimeout(attachIframeListeners, 100);
                    });

                    // ★★★ rendition.on('click', ...) 페이지 이동 로직 (원래 버전) ★★★
                    rendition.on('click', function handleRenditionClick(event) {
                        const clickWasDragEnd = isDragging;
                        // 클릭 시작 시 isDragging 상태 로깅
                        // console.log("[Click Handler] Start. isDragging:", isDragging, "clickWasDragEnd:", clickWasDragEnd);

                        if (clickWasDragEnd) {
                            isDragging = false; // 드래그였으면 상태 리셋
                            console.log("[Click Handler] Click was drag end. Returning.");
                            return; // 드래그 후 클릭은 페이지 넘김/설정 토글 안 함
                        }

                        if (!rendition || !rendition.currentLocation()) {
                            console.warn("[Click Handler] Rendition or current location not available.");
                            return;
                        }

                        const btnVisible = selectionActionButton?.style.display === 'block';
                        // 버튼 관련 처리
                        if (btnVisible && event.target !== selectionActionButton && !selectionActionButton.contains(event.target)) {
                            selectionActionButton.style.display = 'none';
                            console.log("[Click Handler] Selection action button hidden by clicking outside.");
                            return; // 버튼 숨기고 다른 액션 방지
                        }
                        if (btnVisible && (event.target === selectionActionButton || selectionActionButton.contains(event.target))) {
                            console.log("[Click Handler] Clicked on selection action button or its child. Own handler will execute.");
                            return; // 버튼 자체 클릭은 버튼 핸들러가 처리
                        }

                        // 기타 요소 클릭 방지
                        const origEvent = event.originalEvent || event;
                        if (origEvent.target.closest('#settings-bar')) {
                            console.log("[Click Handler] Clicked inside settings-bar.");
                            return;
                        }
                        if (origEvent.target.closest('a[href]')) {
                            console.log("[Click Handler] Clicked on a link inside EPUB.");
                            return; // EPUB 내부 링크는 기본 동작 따름
                        }

                        // 페이지 넘김/설정 토글 로직 (lastMouseUpPosition 사용)
                        const clickX_page = lastMouseUpPosition.x;
                        const scrollX = window.scrollX || window.pageXOffset;
                        const clickX_viewport = clickX_page - scrollX;
                        const vpWidth = window.innerWidth;
                        // console.log("[Click Handler] vpWidth:", vpWidth, "clickX_viewport:", clickX_viewport);

                        if (clickX_viewport < vpWidth / 3) {
                            console.log("[Click Handler] Navigating prev.");
                            rendition.prev();
                            toggleSettings(false);
                        } else if (clickX_viewport > vpWidth * 2 / 3) {
                            console.log("[Click Handler] Navigating next.");
                            rendition.next();
                            toggleSettings(false);
                        } else {
                            console.log("[Click Handler] Toggling settings.");
                            toggleSettings();
                        }
                    });

                    // ★★★ 초기 디스플레이 및 하이라이트 로드 ★★★
                    return rendition.display(); // 초기 디스플레이 시작
                })
                    .then(async (section) => { // async 추가
                        hideLoading();
                        loadToc();
                        loadPersistentSettings();

                        // 초기 표시된 섹션 정보 가져오기
                        const initialLocation = rendition.currentLocation();
                        if (initialLocation && initialLocation.start && initialLocation.start.href) {
                            currentChapterHrefForComment = initialLocation.start.href;
                            console.log("[DEBUG] Initial display href:", currentChapterHrefForComment);
                            // ★★★ 초기 로드 시 하이라이트 적용 ★★★
                            await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                        } else {
                            console.warn("[WARN] Could not determine initial chapter href after display.");
                        }

                        // attachIframeListeners는 여기서도 호출 (초기 View에 대한 리스너)
                        setTimeout(attachIframeListeners, 100);

                        if (book.packaging?.metadata) console.log("Book rendered:", book.packaging.metadata.title);

                        // 저장된 위치로 이동 (이동 후 'relocated' 이벤트 발생하여 하이라이트 처리)
                        const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                        if (lastLoc) {
                            console.log("[DEBUG] Displaying last location:", lastLoc);
                            return rendition.display(lastLoc);
                        }
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

    // --- 나머지 함수들 ---

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
                            rendition.display(item.href)
                                .then(() => {
                                    // TOC 클릭 후에도 displayed 이벤트가 발생하므로 하이라이트는 거기서 처리됨
                                    currentChapterHrefForComment = item.href; // href는 업데이트
                                })
                                .catch(err => console.error("Error displaying from TOC:", err));
                        }
                        toggleToc(false);
                    });
                    li.appendChild(a);
                    frag.appendChild(li);
                });
            } else {
                if (tocList) tocList.innerHTML = '<li>Table of Contents is empty or invalid.</li>';
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
        // 뷰어 외부 클릭 시 액션 버튼 숨기기
        document.body.addEventListener('click', e => {
            if (selectionActionButton?.style.display === 'block' &&
                e.target !== selectionActionButton && !selectionActionButton.contains(e.target) &&
                !viewerElement.contains(e.target)) { // 뷰어 영역 클릭은 rendition click 핸들러가 담당
                selectionActionButton.style.display = 'none';
                console.log("[Body Click] Hiding selection action button.");
            }
        }, true); // 캡처링 단계에서 처리
    };

    const applyDarkMode = async (isDark) => { // async 추가
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

                // 테마 변경 후 현재 위치에서 다시 렌더링 (렌더링 후 displayed 이벤트에서 하이라이트 재적용됨)
                if (rendition.display && typeof rendition.display === 'function') {
                    const currentLocation = rendition.currentLocation();
                    if (currentLocation?.start?.cfi) {
                        await rendition.display(currentLocation.start.cfi); // await 추가
                    } else {
                        await rendition.display(); // await 추가
                    }
                    console.log("[DEBUG DARKMODE] Re-displayed after theme change.");
                }
            } catch (themeError) {
                console.error("[ERROR] applyDarkMode: Error overriding or re-displaying:", themeError);
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
        if (!settingsButtonsContainer) {
            console.warn("Settings buttons container not found.");
            return;
        }
        if (tocOverlay) tocOverlay.addEventListener('click', () => toggleToc(false));
        if (tocButtonInSettings) {
            tocButtonInSettings.addEventListener('click', () => {
                toggleSettings(false);
                setTimeout(() => toggleToc(true), 50);
            });
        }
        if (toggleDarkModeButton) {
            toggleDarkModeButton.addEventListener('click', () => {
                const bodyHasDarkModeClass = document.body.classList.contains('dark-mode');
                applyDarkMode(!bodyHasDarkModeClass); // 비동기로 처리될 수 있음
                clearTimeout(settingsTimeout);
                settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
            });
        }
        if (closeSettingsButton) closeSettingsButton.addEventListener('click', () => toggleSettings(false));
        if (viewSettingsButtonInSettings) {
            viewSettingsButtonInSettings.addEventListener('click', () => {
                alert("View settings (TBD)");
                clearTimeout(settingsTimeout);
            });
        }
        if (notesButtonInSettings) {
            notesButtonInSettings.addEventListener('click', () => {
                alert("Notes / My Comments List (TBD)");
                clearTimeout(settingsTimeout);
            });
        }
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
                console.log(`[DEBUG] Navigating to /comments/new with params: ${params.toString()}`);
                window.location.href = `/comments/new?${params.toString()}`;
            } else {
                alert("선택된 내용이 없거나, 위치 정보를 가져올 수 없습니다. 다시 시도해 주세요.");
                console.warn("Cannot navigate to comment form. Missing info:", {
                    cfi: currentSelectionCfiForComment,
                    bookId: bookId,
                    chapterHref: currentChapterHrefForComment,
                    text: currentSelectedTextForComment
                });
            }
            selectionActionButton.style.display = 'none';
        });
    };

    // --- Initialization ---
    console.log("[DEBUG] DOMContentLoaded: Starting initialization sequence.");
    // 필수 DOM 요소 다시 확인 (settingsButtonsContainer 등 null 체크 추가)
    const criticalElements = {
        viewerElement, loadingIndicator, tocList, tocContainer, tocOverlay,
        settingsBar, settingsContentBottom, selectionActionButton, toggleDarkModeButton,
        closeSettingsButton, // settingsButtonsContainer는 위에서 null 체크
        tocButtonInSettings, notesButtonInSettings, viewSettingsButtonInSettings
    };
    let allCriticalElementsPresent = true;
    for (const key in criticalElements) {
        // settingsButtonsContainer 내의 버튼들은 container가 null이면 자동으로 null이므로,
        // criticalElements[key] 가 null인지 체크하면 됨.
        if (!criticalElements[key]) {
            console.error(`[CRITICAL] DOMContentLoaded: Missing critical UI element: ${key}`);
            allCriticalElementsPresent = false;
        }
    }
    // settingsButtonsContainer 자체도 확인
    if (!settingsButtonsContainer) {
        console.error("[CRITICAL] DOMContentLoaded: Missing critical UI element: settingsButtonsContainer (parent of settings buttons)");
        allCriticalElementsPresent = false;
    }


    if (allCriticalElementsPresent) {
        initEpubViewer(); // 비동기 로딩 시작
        setupNavigationAndInteractions();
        setupSettingsControls();
        setupSelectionAction();
        if (settingsContentBottom) settingsContentBottom.classList.remove('settings-visible');
        areSettingsVisible = false;
        console.log("[DEBUG] DOMContentLoaded: Initialization sequence initiated.");
    } else {
        console.error("[CRITICAL] DOMContentLoaded: Initialization aborted due to missing critical UI elements.");
        if (loadingIndicator) hideLoading();
        if (viewerElement) viewerElement.innerHTML = "<p>Viewer cannot be initialized. Required page elements are missing. Please contact support.</p>";
    }
});