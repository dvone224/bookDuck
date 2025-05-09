document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Elements ---
    const viewerElement = document.getElementById('viewer');
    const loadingIndicator = document.getElementById('loading-indicator');
    const tocList = document.getElementById('toc-list');
    const tocContainer = document.getElementById('toc-container');
    const tocOverlay = document.getElementById('toc-overlay');

    const settingsBar = document.getElementById('settings-bar');
    // null 체크 추가
    const settingsContentTop = settingsBar ? settingsBar.querySelector('.settings-content.top') : null;
    const settingsContentBottom = settingsBar ? settingsBar.querySelector('.settings-content.bottom') : null;

    const toggleDarkModeButton = document.getElementById('toggle-dark-mode');
    const closeSettingsButton = document.getElementById('close-settings');
    const selectionActionButton = document.getElementById('selection-action-button');

    // HTML 구조에 맞춰 children[index] 방식으로 버튼 찾기
    const settingsButtonsContainer = settingsContentBottom ? settingsContentBottom.querySelector('.setting-group:first-of-type') : null;
    const tocButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[1] : null;
    const notesButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[2] : null;
    const viewSettingsButtonInSettings = settingsButtonsContainer ? settingsButtonsContainer.children[3] : null;

    // 진행률 슬라이더 관련 요소
    const progressInfoCurrent = document.getElementById('current-location-value');
    const progressInfoTotal = document.getElementById('total-location-value');
    const progressPercentageDisplay = document.getElementById('progress-percentage-display');
    const progressSliderInput = document.getElementById('progressSliderInput');
    const progressSliderFill = document.getElementById('progressSliderFill');
    const sliderValueTooltip = document.getElementById('sliderValueTooltip');
    const tooltipCurrentValue = document.getElementById('tooltip-current-value');
    const tooltipTotalValue = document.getElementById('tooltip-total-value');
    const currentChapterTitleElement = document.getElementById('current-chapter-title');

    // ★★★ 모달 관련 DOM 요소 추가 ★★★
    const commentModalOverlay = document.getElementById('comment-modal-overlay');
    const commentModal = document.getElementById('comment-modal');
    const modalNotePaper = document.getElementById('modalNotePaper');
    const modalProfileIcon = document.getElementById('modalProfileIcon');
    const modalCommentUser = document.getElementById('modalCommentUser');
    const modalCommentText = document.getElementById('modalCommentText');
    const modalDeleteButton = document.getElementById('modalDeleteButton');
    const modalEditButton = document.getElementById('modalEditButton');
    const modalCloseButtonTop = document.getElementById('modalCloseButtonTop');


    // --- EPUB.js Variables ---
    let book;
    let rendition;

    // --- Thymeleaf Variables ---
    const bookId = (typeof GLOBAL_BOOK_ID !== 'undefined' && GLOBAL_BOOK_ID !== null) ? String(GLOBAL_BOOK_ID) : null;
    const userId = (typeof GLOBAL_USER_ID !== 'undefined' && GLOBAL_USER_ID !== null) ? String(GLOBAL_USER_ID) : null;
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
    let currentHighlightCfis = new Set();
    let currentPercentage = 0;
    let isSliderDragging = false;
    let effectiveEndOfBookCfi = null;
    let isBookMarkedFinished = false;
    let currentOpenCommentId = null; // ★★★ 현재 열린 모달 코멘트 ID 추가 ★★★


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
            const firstFocusableElement = tocContainer.querySelector('a, button, [tabindex]:not([tabindex="-1"])');
            if (firstFocusableElement) {
                firstFocusableElement.focus();
            } else {
                tocContainer.focus();
            }
        } else if (!isTocVisible) {
            document.body.focus();
        }
    };
    const updateProgress = () => {
        if (!book?.locations?.total || book.locations.total === 0 || !rendition?.currentLocation()) {
            if (progressInfoCurrent) progressInfoCurrent.textContent = '-';
            if (progressInfoTotal) progressInfoTotal.textContent = '-';
            if (progressPercentageDisplay) progressPercentageDisplay.textContent = `0%`;
            if (progressSliderInput && !isSliderDragging) progressSliderInput.value = 0;
            if (progressSliderFill && !isSliderDragging) progressSliderFill.style.width = `0%`;
            if (tooltipCurrentValue) tooltipCurrentValue.textContent = '-';
            if (tooltipTotalValue) tooltipTotalValue.textContent = '-';
            return;
        }
        const currentLocation = rendition.currentLocation();
        if (currentLocation?.start) {
            const currentCfi = currentLocation.start.cfi;
            try {
                currentPercentage = book.locations.percentageFromCfi(currentCfi);
                const percentageToShow = Math.round(currentPercentage * 100);
                const currentLocationValue = book.locations.locationFromCfi(currentCfi);
                const totalLocationValue = book.locations.total;
                if (progressInfoCurrent) progressInfoCurrent.textContent = currentLocationValue;
                if (progressInfoTotal) progressInfoTotal.textContent = totalLocationValue;
                if (progressPercentageDisplay) progressPercentageDisplay.textContent = `${percentageToShow}%`;
                if (progressSliderInput && !isSliderDragging) {
                    progressSliderInput.max = 100;
                    progressSliderInput.value = percentageToShow;
                }
                if (progressSliderFill && !isSliderDragging) {
                    progressSliderFill.style.width = `${percentageToShow}%`;
                }
                if (tooltipCurrentValue) tooltipCurrentValue.textContent = currentLocationValue;
                if (tooltipTotalValue) tooltipTotalValue.textContent = totalLocationValue;
                if (currentChapterTitleElement && currentLocation.start.index !== undefined) {
                    const tocItem = book.navigation.get(currentLocation.start.href || currentLocation.start.index);
                    if (tocItem?.label) {
                        currentChapterTitleElement.textContent = tocItem.label.trim();
                    } else {
                        currentChapterTitleElement.textContent = "";
                    }
                }
            } catch (e) {
                console.error("[Progress] 진행률 계산/UI 업데이트 오류:", e, "CFI:", currentCfi);
            }
        } else {
            console.warn("[Progress] 현재 위치 정보 없음.");
        }
    };
    const positionTooltip = () => {
        if (!progressSliderInput || !sliderValueTooltip || !book?.locations?.total) {
            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '0';
            return;
        }
        const sliderRect = progressSliderInput.getBoundingClientRect();
        const thumbPositionRatio = parseFloat(progressSliderInput.value) / 100;
        const thumbWidth = 18;
        const trackWidth = sliderRect.width;
        const thumbCenterOffset = thumbPositionRatio * (trackWidth - thumbWidth) + (thumbWidth / 2);
        sliderValueTooltip.style.left = `${thumbCenterOffset}px`;
    };
    const toggleSettings = (forceState) => {
        clearTimeout(settingsTimeout);
        areSettingsVisible = (forceState !== undefined) ? forceState : !areSettingsVisible;
        if (settingsContentTop) settingsContentTop.classList.toggle('settings-visible', areSettingsVisible);
        if (settingsContentBottom) settingsContentBottom.classList.toggle('settings-visible', areSettingsVisible);
        if (areSettingsVisible) {
            updateProgress();
            if (progressSliderInput && sliderValueTooltip) {
                setTimeout(positionTooltip, 50);
                sliderValueTooltip.style.opacity = '0';
            }
            settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
        } else {
            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '0';
        }
    };
    const attachIframeListeners = () => {
        if (!rendition?.manager?.views) {
            console.warn("[WARN] attachIframeListeners: Rendition 또는 views 준비 안됨.");
            return;
        }
        let currentView = rendition.manager.current() || rendition.manager.views.first();
        if (currentView?.document) {
            const iframeDoc = currentView.document;
            iframeDoc.removeEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.removeEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.removeEventListener('mouseup', handleIframeMouseUp);
            iframeDoc.addEventListener('mousedown', handleIframeMouseDown);
            iframeDoc.addEventListener('mousemove', handleIframeMouseMove);
            iframeDoc.addEventListener('mouseup', handleIframeMouseUp);
            console.log("[DEBUG] Iframe 리스너 부착됨:", currentView.section?.href || 'N/A');
        } else {
            console.warn("[WARN] attachIframeListeners: iframe 문서 가져오기 실패.", {currentView});
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
            console.log("[DEBUG] 드래그 시작됨.");
            if (selectionActionButton?.style.display === 'block') {
                selectionActionButton.style.display = 'none';
            }
        }
    }

    function handleIframeMouseUp(event) {
        let iframeEl = null;
        try {
            iframeEl = event.target?.ownerDocument?.defaultView?.frameElement;
        } catch (e) {
            console.warn("[WARN] handleIframeMouseUp: frameElement 접근 오류:", e);
        }
        if (iframeEl) {
            const iframeRect = iframeEl.getBoundingClientRect();
            lastMouseUpPosition = {
                x: iframeRect.left + event.clientX + window.scrollX,
                y: iframeRect.top + event.clientY + window.scrollY
            };
        } else {
            lastMouseUpPosition = {x: event.clientX + window.scrollX, y: event.clientY + window.scrollY};
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
                const currentScrollX = window.scrollX;
                const currentScrollY = window.scrollY;
                if (buttonLeft + buttonWidth > currentScrollX + bodyClientWidth - 10) buttonLeft = currentScrollX + bodyClientWidth - buttonWidth - 10;
                if (buttonLeft < currentScrollX + 10) buttonLeft = currentScrollX + 10;
                if (buttonTop + buttonHeight > currentScrollY + bodyClientHeight - 10) buttonTop = lastMouseUpPosition.y - buttonHeight - 15;
                if (buttonTop < currentScrollY + 10) buttonTop = currentScrollY + 10;
                selectionActionButton.style.top = `${buttonTop}px`;
                selectionActionButton.style.left = `${buttonLeft}px`;
                selectionActionButton.style.display = 'block';
                selectionActionButton.textContent = `쪽지 쓰기`;
                console.log("[DEBUG] 쪽지 쓰기 버튼 표시됨.");
            }
        }, 0);
    }

    async function saveCurrentPageMark(cfiToSave) {
        if (!userId || !bookId || !cfiToSave) {
            console.warn("페이지 표시 저장 실패: 필수 정보 누락.", {userId, bookId, cfiToSave});
            return;
        }
        console.log(`[DEBUG] 페이지 표시 저장 시도: CFI=${cfiToSave}, bookId=${bookId}, userId=${userId}`);
        try {
            const headers = {'Content-Type': 'application/json',};
            const csrfToken = document.querySelector('meta[name="_csrf"]');
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
            if (csrfToken && csrfHeader) {
                headers[csrfHeader.content] = csrfToken.content;
            }
            const response = await fetch('/book/mark', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({userId: userId, bookId: bookId, mark: cfiToSave})
            });
            if (!response.ok) {
                const errorText = await response.text();
                console.error('페이지 표시 저장 실패(백엔드):', response.status, errorText);
            } else {
                console.log('페이지 표시 저장 성공:', cfiToSave);
            }
        } catch (error) {
            console.error('페이지 표시 저장 중 오류:', error);
        }
    }

    async function fetchInitialCfiFromBackend() {
        if (!userId || !bookId) {
            console.warn("[WARN] 초기 CFI 로드 실패: userId 또는 bookId 없음.");
            return null;
        }
        console.log(`[DEBUG] 백엔드 초기 CFI 로드 시도: userId=${userId}, bookId=${bookId}`);
        try {
            const response = await fetch(`/book/mark?userId=${userId}&bookId=${bookId}`, {
                method: 'GET',
                headers: {'Accept': 'application/json'}
            });
            if (!response.ok) {
                if (response.status === 404) {
                    console.log(`[INFO] 저장된 CFI 없음 (userId=${userId}, bookId=${bookId}).`);
                } else {
                    const errorText = await response.text();
                    console.error('초기 CFI 로드 실패(백엔드):', response.status, errorText);
                }
                return null;
            }
            const data = await response.json();
            if (data?.mark?.trim()) {
                console.log('[DEBUG] 백엔드 초기 CFI 수신:', data.mark);
                return data.mark;
            } else {
                console.log('[INFO] 유효한 CFI 없음. Data:', data);
                return null;
            }
        } catch (error) {
            console.error('초기 CFI 로드 중 오류:', error);
            return null;
        }
    }

    const loadAndApplyHighlights = async (currentBookId, chapterHref) => {
        if (!currentBookId || !chapterHref || !rendition?.annotations) {
            console.warn("[Highlight] 필수 정보 누락.");
            return;
        }
        currentHighlightCfis.forEach(cfiToRemove => {
            try {
                rendition.annotations.remove(cfiToRemove, 'highlight');
            } catch (removeError) {
                console.warn(`[Highlight] 이전 하이라이트 제거 오류 CFI ${cfiToRemove}:`, removeError.message);
            }
        });
        currentHighlightCfis.clear();
        try {
            const encodedHref = encodeURIComponent(chapterHref);
            const apiUrl = `/comments/${currentBookId}/${encodedHref}`;
            console.log(`[Highlight] 하이라이트 요청: ${apiUrl}`);
            const response = await fetch(apiUrl);
            if (!response.ok) {
                if (response.status === 404) {
                    console.log(`[Highlight] 코멘트 없음 ${chapterHref} (404).`);
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const highlights = await response.json();
            if (!Array.isArray(highlights)) {
                console.warn("[Highlight] 잘못된 응답 수신:", highlights);
                return;
            }
            console.log(`[Highlight] ${highlights.length}개 하이라이트 수신 ${chapterHref}`);
            highlights.forEach(hl => {
                if (hl.locationCfi && hl.noteColor) {
                    try {
                        rendition.annotations.add('highlight', hl.locationCfi, {commentId: hl.id}, handleHighlightClick, 'book-highlight', {
                            'fill': hl.noteColor,
                            'fill-opacity': '0.4',
                            'mix-blend-mode': 'multiply'
                        });
                        currentHighlightCfis.add(hl.locationCfi);
                        console.log(`[Highlight] 추가됨 CFI: ${hl.locationCfi}, Color: ${hl.noteColor}`);
                    } catch (annotError) {
                        console.error(`[Highlight] 주석 추가 오류 CFI ${hl.locationCfi}:`, annotError.message);
                    }
                } else {
                    console.warn("[Highlight] CFI 또는 색상 누락으로 하이라이트 생략:", hl);
                }
            });
        } catch (error) {
            console.error("[Highlight] 하이라이트 로드/적용 실패:", error);
        }
    };

    // ★★★ 모달 제어 함수 추가 ★★★
    const showCommentModal = (commentData) => {
        if (!commentModal || !commentModalOverlay || !modalCommentUser || !modalCommentText || !modalNotePaper) {
            console.error("모달 표시 오류: 필수 모달 요소 없음");
            return;
        }
        modalCommentUser.textContent = `${commentData.nickname || '사용자'} 님의 쪽지`;
        modalCommentText.textContent = commentData.comment || '';
        modalNotePaper.style.backgroundColor = commentData.noteColor || '#fffacd';
        if (commentData.fontFamily) modalCommentText.style.fontFamily = `"${commentData.fontFamily}", sans-serif`;
        else modalCommentText.style.fontFamily = '';
        currentOpenCommentId = commentData.id;
        commentModalOverlay.classList.add('visible');
        commentModal.hidden = false;
        setTimeout(() => {
            commentModal.classList.add('visible');
        }, 10);
        commentModal.focus();
        console.log(`[Modal] 코멘트 ID ${currentOpenCommentId} 모달 표시됨.`);
    };

    const hideCommentModal = () => {
        if (!commentModal || !commentModalOverlay) return;
        commentModal.classList.remove('visible');
        commentModalOverlay.classList.remove('visible');
        setTimeout(() => {
            commentModal.hidden = true;
            currentOpenCommentId = null;
        }, 300);
        console.log("[Modal] 모달 숨겨짐.");
    };

    // ★★★ 하이라이트 클릭 핸들러 (모달 호출 포함) ★★★
    const handleHighlightClick = async (e, annotation) => {
        let commentId = annotation?.data?.commentId || e?.target?.dataset?.commentId;
        if (commentId) {
            console.log(`[Highlight Click] 코멘트 ID ${commentId} 상세 정보 요청`);
            showLoading();
            try {
                const response = await fetch(`/comments/detail/${commentId}`);
                if (!response.ok) throw new Error(`댓글 정보 로드 실패: ${response.status}`);
                const commentData = await response.json();
                console.log("[Highlight Click] 수신 데이터:", commentData);
                if (commentData) showCommentModal(commentData); else alert("댓글 정보 없음.");
            } catch (error) {
                console.error("[Highlight Click] 댓글 상세 정보 로드 오류:", error);
                alert(`댓글 정보 로드 오류: ${error.message}`);
            } finally {
                hideLoading();
            }
        } else {
            console.warn("하이라이트 클릭, commentId 추출 불가. Event:", e, "Annotation:", annotation);
            alert("하이라이트 클릭됨! (코멘트 ID 정보 없음)");
        }
    };

    async function determineEffectiveEndOfBook() { /* ... (이전과 동일) ... */
    }

    async function markBookAsFinished() { /* ... (이전과 동일) ... */
    }

    const initEpubViewer = () => {
        console.log("[DEBUG] initEpubViewer: 함수 호출됨.");
        if (!bookUrl || !bookId) {
            if (viewerElement) viewerElement.innerHTML = `<p>책 URL (${bookUrl}) 또는 ID (${bookId})가 없습니다.</p>`;
            hideLoading();
            return;
        }
        showLoading();
        try {
            console.log("[DEBUG] ePub 객체 생성 시도, URL:", bookUrl);
            book = ePub(bookUrl);
            if (book?.opened?.then) book.opened.catch(err => console.error("book.opened REJECTED:", err));

            if (book?.ready?.then) {
                book.ready.then(async () => {
                    console.log("[DEBUG] Book ready. Rendition 생성 및 Locations 생성 시도.");
                    rendition = book.renderTo(viewerElement, {
                        width: "100%",
                        height: "100%",
                        flow: "paginated",
                        allowScriptedContent: false
                    });
                    if (!rendition) {
                        hideLoading();
                        throw new Error("Rendition 생성 실패.");
                    }
                    console.log("[DEBUG] Rendition 생성 완료.");

                    try {
                        console.log("[DEBUG] book.locations 생성 시작...");
                        await book.locations.generate(1650);
                        console.log("[DEBUG] book.locations 생성 완료. 전체 위치 수:", book.locations.total);
                        await determineEffectiveEndOfBook();
                        updateProgress();
                    } catch (e) {
                        console.error("[ERROR] book.locations 생성 중 오류:", e);
                    }

                    rendition.on("relocated", async (location) => {
                        const currentCfi = location.start.cfi;
                        localStorage.setItem(`epub-location-${bookId}`, currentCfi);
                        console.log(`[DEBUG] Relocated. CFI: ${currentCfi}, Chapter Href: ${location.start.href}`);
                        if (location?.start?.href) {
                            currentChapterHrefForComment = location.start.href;
                        }
                        if (currentCfi && userId && bookId) {
                            saveCurrentPageMark(currentCfi);
                        }
                        updateProgress();
                        if (areSettingsVisible && sliderValueTooltip) {
                            setTimeout(positionTooltip, 10);
                        }
                        if (userId && bookId && book.locations?.total > 0 && !isBookMarkedFinished) {
                            let shouldMarkFinished = false;
                            const currentBookPercentage = book.locations.percentageFromCfi(currentCfi);
                            if (effectiveEndOfBookCfi) {
                                const currentLocationIndex = book.locations.locationFromCfi(currentCfi);
                                const endOfBookLocationIndex = book.locations.locationFromCfi(effectiveEndOfBookCfi);
                                if (currentLocationIndex >= endOfBookLocationIndex) {
                                    console.log("[INFO] Landmark 기준 책 끝 도달.");
                                    shouldMarkFinished = true;
                                }
                            }
                            if (!shouldMarkFinished && currentBookPercentage >= 0.99) {
                                console.log("[INFO] 책 99%+ 도달.");
                                shouldMarkFinished = true;
                            }
                            if (shouldMarkFinished) {
                                await markBookAsFinished();
                            }
                        }
                        await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                        setTimeout(attachIframeListeners, 50);
                    });
                    rendition.on("selected", (cfiRange, contents) => {
                        const sel = contents.window.getSelection();
                        const txt = sel.toString().trim();
                        if (txt && sel.rangeCount > 0) {
                            currentSelectionCfiForComment = cfiRange;
                            currentSelectedTextForComment = txt;
                            if (rendition?.currentLocation()?.start) currentChapterHrefForComment = rendition.currentLocation().start.href;
                        } else {
                            currentSelectionCfiForComment = null;
                            currentSelectedTextForComment = null;
                            if (selectionActionButton?.style.display === 'block') selectionActionButton.style.display = 'none';
                        }
                    });
                    rendition.on('displayed', async (section) => {
                        if (section?.href) {
                            currentChapterHrefForComment = section.href;
                            console.log("[DEBUG] Displayed new section, chapterHref:", currentChapterHrefForComment);
                            await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                        }
                        setTimeout(attachIframeListeners, 100);
                    });
                    rendition.on('click', function handleRenditionClick(event) {
                        const clickWasDragEnd = isDragging;
                        try {
                            // 현재 클릭 위치에 해당하는 Annotation이 있는지 확인 시도
                            // Epub.js 버전에 따라 이 기능이 없을 수 있음 - 확인 필요
                            const clickedAnnotations = rendition.annotations.findAnnotationsAtEvent(event); // 가상의 메소드 - 실제 메소드명 확인 필요
                            // 또는 rendition.annotations.getAnnotationByEvent(event) 등

                            // 만약 위와 같은 직접적인 메소드가 없다면, 클릭된 요소의 클래스나 속성을 다시 확인
                            // Epub.js가 하이라이트에 특정 내부 클래스나 속성을 부여할 수 있음 (개발자 도구로 확인)
                            // 예시: event.target.closest('[data-epubcfi]') 등

                            if (clickedAnnotations && clickedAnnotations.length > 0) {
                                // 클릭된 위치에 annotation이 있다면, 그것이 'highlight' 타입인지 확인
                                const isHighlightClick = clickedAnnotations.some(anno => anno.type === 'highlight');
                                if (isHighlightClick) {
                                    console.log("[Click Handler] Click detected on a highlight annotation area. Preventing page turn.");
                                    // isDragging 상태는 여기서 변경하지 않음 (드래그가 아니었으므로)
                                    return; // 페이지 넘김 로직으로 진행하지 않음
                                }
                            }
                            // 또는 event.target 기반 확인 (다시 시도)
                            else if (event.target.closest('.book-highlight')) {
                                console.log("[Click Handler] Click detected via .book-highlight class. Preventing page turn.");
                                return;
                            }
                            // 또는 SVG 관련 클래스 확인 (Epub.js가 SVG를 쓴다면)
                            else if (event.target.closest('svg.epubjs-annotation-hl') || event.target.closest('path[data-annotation-type="highlight"]')) { // 가상의 클래스/속성
                                console.log("[Click Handler] Click detected via SVG highlight element. Preventing page turn.");
                                return;
                            }

                        } catch (findError) {
                            console.warn("[Click Handler] Error finding annotation at event:", findError);
                            // Annotation 확인 중 오류 발생 시, 안전하게 페이지 넘김 로직으로 진행 (혹은 다른 처리)
                        }
                        if (clickWasDragEnd) {
                            isDragging = false;
                            return;
                        }
                        if (event.target.closest('.book-highlight')) {
                            console.log("[Click Handler] Clicked on a highlight element. Preventing page turn/settings toggle.");
                            return;
                        }
                        if (!rendition?.currentLocation()) return;
                        const btnVisible = selectionActionButton?.style.display === 'block';
                        if (btnVisible && event.target !== selectionActionButton && !selectionActionButton.contains(event.target)) {
                            if (selectionActionButton) selectionActionButton.style.display = 'none';
                            return;
                        }
                        if (btnVisible && (event.target === selectionActionButton || selectionActionButton.contains(event.target))) {
                            return;
                        }
                        const origEvent = event.originalEvent || event;
                        if (origEvent.target.closest('#settings-bar') || origEvent.target.closest('.progress-slider-container')) return;
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

                    console.log("[DEBUG] Rendition.display() 호출 (초기)");
                    return rendition.display();
                })
                    .then(async () => {
                        hideLoading();
                        console.log("[DEBUG] 초기 표시 완료. 추가 설정 및 위치 로드.");
                        loadToc();
                        loadPersistentSettings();
                        await determineEffectiveEndOfBook();
                        updateProgress();
                        let initialDisplayTarget = null;
                        if (userId && bookId) initialDisplayTarget = await fetchInitialCfiFromBackend();
                        if (!initialDisplayTarget) {
                            const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                            if (lastLoc) initialDisplayTarget = lastLoc;
                            console.log("[DEBUG] 로컬 스토리지 CFI 사용:", initialDisplayTarget);
                        }
                        if (initialDisplayTarget) {
                            console.log("[DEBUG] 초기 위치로 이동 시도:", initialDisplayTarget);
                            try {
                                await rendition.display(initialDisplayTarget);
                                console.log("[DEBUG] 초기 위치 표시 성공.");
                            } catch (displayError) {
                                console.error("초기 위치 표시 오류:", initialDisplayTarget, displayError);
                                const currentLoc = rendition.currentLocation();
                                if (currentLoc?.start?.href) await loadAndApplyHighlights(bookId, currentLoc.start.href);
                            }
                        } else {
                            const currentLoc = rendition.currentLocation();
                            if (currentLoc?.start?.href) await loadAndApplyHighlights(bookId, currentLoc.start.href);
                            console.log("[DEBUG] 특정 초기 위치 없음. 현재 위치 하이라이트 로드.");
                        }
                        setTimeout(attachIframeListeners, 150);
                        if (book.packaging?.metadata) console.log("책 렌더링됨:", book.packaging.metadata.title);
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

    // --- 나머지 함수들 ---
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
                            rendition.display(item.href).then(() => {
                                currentChapterHrefForComment = item.href;
                            }).catch(err => console.error("TOC 표시 오류:", err));
                        }
                        toggleToc(false);
                        toggleSettings(false);
                    });
                    li.appendChild(a);
                    frag.appendChild(li);
                });
            } else {
                if (tocList) tocList.innerHTML = '<li>목차 없음.</li>';
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
            if (selectionActionButton?.style.display === 'block' && e.target !== selectionActionButton && !selectionActionButton.contains(e.target) && !viewerElement.contains(e.target)) {
                selectionActionButton.style.display = 'none';
                console.log("[Body Click] 쪽지 버튼 숨김.");
            }
        }, true);
    };
    const applyDarkMode = async (isDark) => {
        console.log(`[DEBUG DARKMODE] 다크 모드 적용: ${isDark}`);
        document.body.classList.toggle('dark-mode', isDark);
        if (rendition?.themes) {
            try {
                const commonStyles = {
                    'p': {'color': isDark ? '#e0e0e0 !important' : null},
                    'h1,h2,h3,h4,h5,h6': {'color': isDark ? '#f0f0f0 !important' : null},
                    'a': {'color': isDark ? '#bb86fc !important' : null},
                    'li': {'color': isDark ? '#e0e0e0 !important' : null},
                    'div': {'color': isDark ? '#e0e0e0 !important' : null},
                };
                rendition.themes.override('color', isDark ? '#e0e0e0' : null, true);
                rendition.themes.override('background', isDark ? '#1e1e1e' : null, true);
                for (const selector in commonStyles) {
                    rendition.themes.override(selector, commonStyles[selector]);
                }
                console.log(`[DEBUG DARKMODE] 테마 오버라이드: ${isDark ? 'DARK' : 'LIGHT'}`);
                if (rendition.display) {
                    const currentLocation = rendition.currentLocation();
                    if (currentLocation?.start?.cfi) {
                        await rendition.display(currentLocation.start.cfi);
                    } else {
                        await rendition.display();
                    }
                    console.log("[DEBUG DARKMODE] 테마 변경 후 재표시 완료.");
                }
            } catch (themeError) {
                console.error("다크 모드 테마 적용/재표시 오류:", themeError);
            }
        } else {
            console.warn("[WARN] 다크 모드 적용 불가: Rendition 또는 themes 객체 없음.");
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
        if (notesButtonInSettings) notesButtonInSettings.addEventListener('click', () => {
            alert("쪽지 기능 (구현 예정)");
            clearTimeout(settingsTimeout);
        });
        if (viewSettingsButtonInSettings) viewSettingsButtonInSettings.addEventListener('click', () => {
            alert("보기 설정 (구현 예정)");
            clearTimeout(settingsTimeout);
        });
        if (toggleDarkModeButton) toggleDarkModeButton.addEventListener('click', () => {
            const bodyHasDarkModeClass = document.body.classList.contains('dark-mode');
            applyDarkMode(!bodyHasDarkModeClass);
            clearTimeout(settingsTimeout);
            settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
        });
        if (closeSettingsButton) closeSettingsButton.addEventListener('click', () => toggleSettings(false));
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
                console.log(`[DEBUG] 쪽지 쓰기 이동: ${params.toString()}`);
                window.location.href = `/comments/new?${params.toString()}`;
            } else {
                alert("선택된 내용이 없거나, 위치 정보를 가져올 수 없습니다.");
                console.warn("쪽지 쓰기 이동 불가: 정보 누락", {
                    cfi: currentSelectionCfiForComment,
                    bookId,
                    chapterHref: currentChapterHrefForComment,
                    text: currentSelectedTextForComment
                });
            }
            selectionActionButton.style.display = 'none';
        });
    };

    // ★★★ 슬라이더 이벤트 리스너 (변경 없음) ★★★
    if (progressSliderInput) { /* ... 슬라이더 리스너들 ... */
        progressSliderInput.addEventListener('input', () => {
            if (!isSliderDragging) isSliderDragging = true;
            const percentage = parseInt(progressSliderInput.value, 10);
            if (book?.locations?.total > 0) {
                try {
                    const cfi = book.locations.cfiFromPercentage(percentage / 100);
                    const targetLocation = book.locations.locationFromCfi(cfi);
                    if (progressInfoCurrent) progressInfoCurrent.textContent = targetLocation;
                    if (progressPercentageDisplay) progressPercentageDisplay.textContent = `${percentage}%`;
                    if (tooltipCurrentValue) tooltipCurrentValue.textContent = targetLocation;
                    if (progressSliderFill) progressSliderFill.style.width = `${percentage}%`;
                    if (sliderValueTooltip) positionTooltip();
                } catch (e) {
                    console.warn("슬라이더 input 처리 오류:", e);
                }
            }
            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '1';
        });
        progressSliderInput.addEventListener('change', () => {
            isSliderDragging = false;
            if (book?.locations && rendition) {
                try {
                    const percentage = parseInt(progressSliderInput.value, 10) / 100;
                    const cfi = book.locations.cfiFromPercentage(percentage);
                    if (cfi) {
                        rendition.display(cfi).then(() => {
                            updateProgress();
                            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '0';
                        }).catch(err => console.error("슬라이더 이동 후 display 오류:", err));
                    }
                } catch (e) {
                    console.warn("슬라이더 change 처리 오류:", e);
                }
            }
        });
        progressSliderInput.addEventListener('pointerdown', () => {
            if (sliderValueTooltip && book.locations?.total > 0) {
                positionTooltip();
                sliderValueTooltip.style.opacity = '1';
            }
        });
        progressSliderInput.addEventListener('pointerup', () => {
            if (sliderValueTooltip && !isSliderDragging) {
                sliderValueTooltip.style.opacity = '0';
            }
        });
        progressSliderInput.addEventListener('mouseenter', () => {
            if (sliderValueTooltip && book.locations?.total > 0) {
                positionTooltip();
                sliderValueTooltip.style.opacity = '1';
            }
        });
        progressSliderInput.addEventListener('mouseleave', () => {
            if (sliderValueTooltip && !isSliderDragging) {
                sliderValueTooltip.style.opacity = '0';
            }
        });
        progressSliderInput.addEventListener('focus', () => {
            if (sliderValueTooltip && book.locations?.total > 0) {
                positionTooltip();
                sliderValueTooltip.style.opacity = '1';
            }
        });
        progressSliderInput.addEventListener('blur', () => {
            if (sliderValueTooltip && !isSliderDragging) {
                sliderValueTooltip.style.opacity = '0';
            }
        });
    } else {
        console.warn("Progress slider input element not found.");
    }

    // ★★★ 모달 버튼 이벤트 리스너 추가 ★★★
    if (modalCloseButtonTop) {
        modalCloseButtonTop.addEventListener('click', hideCommentModal);
    }
    if (commentModalOverlay) {
        commentModalOverlay.addEventListener('click', hideCommentModal);
    }
    if (modalEditButton) {
        modalEditButton.addEventListener('click', () => {
            if (currentOpenCommentId) {
                alert(`수정 버튼 클릭됨: Comment ID = ${currentOpenCommentId}. 수정 기능 구현 필요.`);
                hideCommentModal();
            }
        });
    }
    if (modalDeleteButton) {
        modalDeleteButton.addEventListener('click', async () => { // async 추가
            if (currentOpenCommentId) {
                if (confirm(`쪽지(ID: ${currentOpenCommentId})를 정말 삭제하시겠습니까?`)) {
                    showLoading();
                    try {
                        const csrfTokenEl = document.querySelector('meta[name="_csrf"]');
                        const csrfHeaderEl = document.querySelector('meta[name="_csrf_header"]');
                        const headers = {'Content-Type': 'application/json'};
                        if (csrfTokenEl && csrfHeaderEl) {
                            headers[csrfHeaderEl.content] = csrfTokenEl.content;
                        }

                        const response = await fetch(`/comments/${currentOpenCommentId}`, { // API 경로 확인
                            method: 'DELETE',
                            headers: headers
                        });

                        if (response.ok) {
                            alert('쪽지가 삭제되었습니다.');
                            // 뷰에서 해당 하이라이트 제거 (삭제된 CFI를 알아야 함 - 이 부분은 개선 필요)
                            // 가장 간단한 방법은 하이라이트를 다시 로드하는 것
                            if (currentChapterHrefForComment && bookId) {
                                await loadAndApplyHighlights(bookId, currentChapterHrefForComment);
                            }
                            hideCommentModal();
                        } else {
                            const errorMsg = await response.text() || `삭제 실패 (상태: ${response.status})`;
                            alert(errorMsg);
                            console.error("Delete comment failed:", response.status, errorMsg);
                        }
                    } catch (error) {
                        console.error("Error deleting comment:", error);
                        alert('쪽지 삭제 중 오류가 발생했습니다.');
                    } finally {
                        hideLoading();
                    }
                }
            }
        });
    }

    // --- Initialization ---
    console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 시작.");
    console.log("[DEBUG] Thymeleaf 변수 값:", {bookId, userId, bookUrl});

    // 필수 요소 확인 (모달, 슬라이더 관련 요소 포함)
    const requiredElements = [
        viewerElement, loadingIndicator, tocList, tocContainer, tocOverlay, settingsBar,
        settingsContentTop, settingsContentBottom, toggleDarkModeButton, closeSettingsButton,
        selectionActionButton, tocButtonInSettings, notesButtonInSettings, viewSettingsButtonInSettings,
        progressInfoCurrent, progressInfoTotal, progressPercentageDisplay, progressSliderInput,
        progressSliderFill, sliderValueTooltip, tooltipCurrentValue, tooltipTotalValue,
        currentChapterTitleElement,
        commentModalOverlay, commentModal, modalNotePaper, modalProfileIcon, modalCommentUser,
        modalCommentText, modalDeleteButton, modalEditButton, modalCloseButtonTop
    ];
    const missingElements = requiredElements.filter(el => !el);

    if (missingElements.length === 0) {
        if (!userId) console.warn("[WARN] 사용자 ID 없음. 기능 제한될 수 있음.");
        if (!bookId) console.warn("[WARN] 책 ID 없음. 기능 제한될 수 있음.");
        if (!bookUrl) {
            console.error("[CRITICAL] 책 URL 없음.");
            if (viewerElement) viewerElement.innerHTML = `<p>책 URL 오류.</p>`;
            hideLoading();
        } else {
            initEpubViewer();
            setupNavigationAndInteractions();
            setupSettingsControls();
            setupSelectionAction();
            if (settingsContentTop) settingsContentTop.classList.remove('settings-visible');
            if (settingsContentBottom) settingsContentBottom.classList.remove('settings-visible');
            areSettingsVisible = false;
        }
        console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 완료.");
    } else {
        console.error("[CRITICAL] DOMContentLoaded: 필수 UI 요소 누락.");
        missingElements.forEach(el => {
            const variableName = Object.keys(self).find(key => self[key] === el) || 'unknown'; // 변수명 추적 시도
            // el이 null일 수 있으므로 id 접근 전에 확인
            const elementId = el ? el.id || 'ID 없음' : 'null 또는 undefined';
            console.error(` 누락 요소: ${variableName} (ID: ${elementId})`);
        });
        alert("뷰어 초기화 실패: 필수 요소 누락.");
        hideLoading();
    }
});