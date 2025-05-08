document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Elements (HTML에서 가져올 요소들) ---
    const viewerElement = document.getElementById('viewer');
    const loadingIndicator = document.getElementById('loading-indicator');
    const tocList = document.getElementById('toc-list');
    const tocContainer = document.getElementById('toc-container');
    const tocOverlay = document.getElementById('toc-overlay');

    const settingsBar = document.getElementById('settings-bar');
    const settingsContentTop = settingsBar.querySelector('.settings-content.top');
    const settingsContentBottom = settingsBar.querySelector('.settings-content.bottom');

    const toggleDarkModeButton = document.getElementById('toggle-dark-mode');
    const closeSettingsButton = document.getElementById('close-settings');
    const selectionActionButton = document.getElementById('selection-action-button');

    const tocButtonInSettings = document.getElementById('toc-button-in-settings');
    const notesButtonInSettings = document.getElementById('notes-button-in-settings');
    const viewSettingsButtonInSettings = document.getElementById('view-settings-button-in-settings');

    const progressInfoCurrent = document.getElementById('current-location-value');
    const progressInfoTotal = document.getElementById('total-location-value');
    const progressPercentageDisplay = document.getElementById('progress-percentage-display');
    const progressSliderInput = document.getElementById('progressSliderInput');
    const progressSliderFill = document.getElementById('progressSliderFill'); // 채워지는 부분
    const sliderValueTooltip = document.getElementById('sliderValueTooltip');
    const tooltipCurrentValue = document.getElementById('tooltip-current-value');
    const tooltipTotalValue = document.getElementById('tooltip-total-value');
    const currentChapterTitleElement = document.getElementById('current-chapter-title');


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
    let currentPercentage = 0; // 0~1 사이의 값
    let isSliderDragging = false; // 슬라이더 드래그 상태
    let effectiveEndOfBookCfi = null; // 실질적인 책의 끝 CFI를 저장할 변수
    let isBookMarkedFinished = false; // 클라이언트 측에서 완료 처리 여부 플래그 (중복 호출 방지용)


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

    const updateProgress = () => {
        if (!book || !book.locations || !book.locations.total || !rendition || !rendition.currentLocation()) {
            return;
        }
        const currentLocation = rendition.currentLocation();
        if (currentLocation && currentLocation.start) {
            const currentCfi = currentLocation.start.cfi;
            currentPercentage = book.locations.percentageFromCfi(currentCfi);
            const percentageToShow = Math.round(currentPercentage * 100);
            const currentLocationValue = book.locations.locationFromCfi(currentCfi);
            const totalLocationValue = book.locations.total;

            if (progressInfoCurrent) progressInfoCurrent.textContent = currentLocationValue;
            if (progressInfoTotal) progressInfoTotal.textContent = totalLocationValue;
            if (progressPercentageDisplay) progressPercentageDisplay.textContent = `${percentageToShow}%`;

            if (progressSliderInput && !isSliderDragging) {
                progressSliderInput.value = percentageToShow;
            }
            if (progressSliderFill && !isSliderDragging) {
                progressSliderFill.style.width = `${percentageToShow}%`;
            }

            if (tooltipCurrentValue) tooltipCurrentValue.textContent = currentLocationValue;
            if (tooltipTotalValue) tooltipTotalValue.textContent = totalLocationValue;

            if (currentChapterTitleElement && currentLocation.start.index !== undefined) {
                const tocItem = book.navigation.get(currentLocation.start.href || currentLocation.start.index);
                if (tocItem && tocItem.label) {
                    currentChapterTitleElement.textContent = tocItem.label.trim();
                } else {
                    currentChapterTitleElement.textContent = "";
                }
            }
        }
    };

    const positionTooltip = () => {
        if (!progressSliderInput || !sliderValueTooltip || !book.locations || !book.locations.total) {
            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '0';
            return;
        }
        const sliderRect = progressSliderInput.getBoundingClientRect();
        const thumbPositionRatio = parseFloat(progressSliderInput.value) / 100;
        const thumbWidth = 18; // CSS에서 핸들 너비와 일치
        const trackWidth = sliderRect.width;
        const thumbCenterOffset = thumbPositionRatio * (trackWidth - thumbWidth) + (thumbWidth / 2);
        sliderValueTooltip.style.left = `${thumbCenterOffset}px`;
        sliderValueTooltip.style.opacity = '1';
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
            }
            settingsTimeout = setTimeout(() => toggleSettings(false), 7000);
        } else {
            if (sliderValueTooltip) sliderValueTooltip.style.opacity = '0';
        }
    };

    const attachIframeListeners = () => {
        if (!rendition || !rendition.manager || !rendition.manager.views) {
            console.warn("[WARN] attachIframeListeners: Rendition 또는 views가 준비되지 않았습니다.");
            return;
        }
        let currentViewContents;
        try {
            if (rendition.manager.current) currentViewContents = rendition.manager.current();
            else if (rendition.manager.views?.first) currentViewContents = rendition.manager.views.first();
        } catch (e) {
            console.warn("[WARN] attachIframeListeners: currentViewContents 가져오기 실패", e);
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
            console.warn("[WARN] attachIframeListeners: iframe 문서를 가져올 수 없습니다.", {currentViewContents});
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

    async function saveCurrentPageMark(cfiToSave) {
        if (!userId || !bookId || !cfiToSave) {
            console.warn("현재 페이지 표시를 저장할 수 없습니다: userId, bookId 또는 CFI 정보가 누락되었습니다.", {userId, bookId, cfiToSave});
            return;
        }
        console.log(`[DEBUG] 페이지 표시 저장 시도: CFI=${cfiToSave}, bookId=${bookId}, userId=${userId}`);
        try {
            const headers = {'Content-Type': 'application/json',};
            const response = await fetch('/book/mark', { // API 엔드포인트 확인
                method: 'POST',
                headers: headers,
                body: JSON.stringify({userId: userId, bookId: bookId, mark: cfiToSave})
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

    async function fetchInitialCfiFromBackend() {
        if (!userId || !bookId) {
            console.warn("[WARN] 초기 CFI를 가져올 수 없습니다: userId 또는 bookId가 없습니다.");
            return null;
        }
        console.log(`[DEBUG] 백엔드에서 초기 CFI 가져오기 시도: userId=${userId}, bookId=${bookId}`);
        try {
            const response = await fetch(`/book/mark?userId=${userId}&bookId=${bookId}`, {
                method: 'GET',
                headers: {'Accept': 'application/json'}
            });
            if (!response.ok) {
                if (response.status === 404) {
                    console.log(`[INFO] userId=${userId}, bookId=${bookId}에 대한 저장된 CFI가 없습니다.`);
                } else {
                    const errorText = await response.text();
                    console.error('초기 CFI 가져오기 실패 (백엔드):', response.status, errorText);
                }
                return null;
            }
            const data = await response.json();
            if (data && data.mark && data.mark.trim() !== "") {
                console.log('[DEBUG] 백엔드로부터 초기 CFI 수신:', data.mark);
                return data.mark;
            } else {
                console.log('[INFO] 백엔드로부터 받은 데이터에 유효한 CFI가 없습니다. Data:', data);
                return null;
            }
        } catch (error) {
            console.error('초기 CFI 가져오는 중 네트워크 오류 또는 JavaScript 오류 발생:', error);
            return null;
        }
    }

    // ★★★ 실질적인 책 끝 지점을 결정하는 함수 (우선순위 1만 적용) ★★★
    async function determineEffectiveEndOfBook() {
        if (!book || !book.navigation || !book.locations || !book.locations.total) {
            console.warn("[WARN] 실질적인 책 끝 지점을 결정하기 위한 정보가 부족합니다.");
            return null;
        }
        effectiveEndOfBookCfi = null; // 함수 호출 시마다 초기화

        // 우선순위 1: Landmarks (EPUB3)
        if (book.navigation.landmarks && book.navigation.landmarks.length > 0) {
            const backmatterTypes = ["backmatter", "rearnotes", "colophon", "copyright-page", "acknowledgments"];
            let foundLandmarkCfi = null;

            for (const type of backmatterTypes) {
                // landmark item의 epubtype 속성을 확인 (EPUB.js 버전에 따라 속성 이름이 다를 수 있음, 예: type, epubtype)
                const landmark = book.navigation.landmarks.find(item =>
                    (item.type?.includes(type) || item.epubtype?.includes(type))
                );
                if (landmark && landmark.href) {
                    try {
                        const section = book.spine.get(landmark.href);
                        if (section && section.cfiBase) {
                            foundLandmarkCfi = section.cfiBase;
                            console.log(`[INFO] Landmarks ('${type}')를 통해 실질적 책 끝 추정 (Backmatter 시작):`, foundLandmarkCfi);
                            break;
                        }
                    } catch (e) {
                        console.warn(`Landmark href '${landmark.href}'(type:${type})로 CFI 변환 중 오류`, e);
                    }
                }
            }
            effectiveEndOfBookCfi = foundLandmarkCfi;
        }

        if (!effectiveEndOfBookCfi) {
            console.log("[INFO] Landmarks에서 실질적인 책 끝 지점을 찾지 못했습니다.");
        }
        return effectiveEndOfBookCfi;
    }

    // ★★★ 책 완료 처리 API 호출 함수 ★★★
    async function markBookAsFinished() {
        if (!userId || !bookId || isBookMarkedFinished) { // userId, bookId 없거나 이미 완료 처리된 경우 중단
            if (isBookMarkedFinished) console.log("[INFO] 이미 완료 처리 요청됨.");
            return;
        }

        isBookMarkedFinished = true; // 중복 호출 방지 플래그 설정
        console.log(`[DEBUG] 책 완료 처리 시도: userId=${userId}, bookId=${bookId}`);
        try {
            const response = await fetch('/book/finish', { // 백엔드 API 엔드포인트
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({userId: userId, bookId: bookId})
            });
            if (response.ok) {
                console.log("[INFO] 책이 성공적으로 '완료' 처리되었습니다.");
                // 필요시 UI 업데이트 (예: 완료 아이콘 표시)
            } else {
                const errorText = await response.text();
                console.error("[ERROR] 책 완료 처리 실패 (백엔드):", response.status, errorText);
                isBookMarkedFinished = false; // 실패 시 플래그 리셋 (재시도 가능하도록)
            }
        } catch (error) {
            console.error("[ERROR] 책 완료 처리 중 네트워크/JS 오류:", error);
            isBookMarkedFinished = false; // 실패 시 플래그 리셋
        }
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
                        await book.locations.generate(1000); // locations 생성
                        console.log("[DEBUG] book.locations 생성 완료. 전체 위치 수:", book.locations.total);
                        await determineEffectiveEndOfBook(); // ★★★ 실질적인 책 끝 지점 결정
                        updateProgress(); // locations 생성 후 초기 진행률 업데이트
                    } catch (e) {
                        console.error("[ERROR] book.locations 생성 중 오류:", e);
                    }

                    rendition.on("relocated", async loc => { // async 추가
                        const currentCfi = loc.start.cfi;
                        localStorage.setItem(`epub-location-${bookId}`, currentCfi);
                        console.log(`[DEBUG] Relocated. CFI: ${currentCfi}, Chapter Href: ${loc.start.href}`);

                        if (loc && loc.start && loc.start.href) {
                            currentChapterHrefForComment = loc.start.href;
                        }
                        if (currentCfi && userId && bookId) {
                            saveCurrentPageMark(currentCfi);
                        }
                        updateProgress();
                        if (areSettingsVisible && sliderValueTooltip) {
                            setTimeout(positionTooltip, 10); // 약간의 딜레이 후 툴팁 위치 업데이트
                        }

                        // ★★★ 책 완료 처리 로직 (우선순위 1 + 안전 장치) ★★★
                        if (userId && bookId && book.locations?.total > 0 && !isBookMarkedFinished) {
                            let shouldMarkFinished = false;
                            const currentBookPercentage = book.locations.percentageFromCfi(currentCfi);

                            if (effectiveEndOfBookCfi) {
                                // CFI 비교는 location 인덱스로 하는 것이 더 안정적
                                const currentLocationIndex = book.locations.locationFromCfi(currentCfi);
                                const endOfBookLocationIndex = book.locations.locationFromCfi(effectiveEndOfBookCfi);

                                // effectiveEndOfBookCfi는 본문 이후 내용의 *시작* 지점이므로,
                                // 현재 위치가 이 지점과 같거나 클 때 완료로 간주
                                if (currentLocationIndex >= endOfBookLocationIndex) {
                                    console.log("[INFO] Landmark 기준 실질적인 책의 끝 지점에 도달했습니다.");
                                    shouldMarkFinished = true;
                                }
                            }

                            // 안전 장치: Landmark가 없거나 부정확할 경우, 거의 끝까지 읽었으면 완료 처리
                            if (!shouldMarkFinished && currentBookPercentage >= 0.99) { // 예: 99% 이상 읽었을 때
                                console.log("[INFO] 책의 거의 마지막 부분(99%+)에 도달했습니다.");
                                shouldMarkFinished = true;
                            }

                            if (shouldMarkFinished) {
                                await markBookAsFinished(); // 완료 처리 API 호출
                            }
                        }
                        // ★★★ 완료 처리 로직 끝 ★★★

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
                        } else {
                            currentSelectionCfiForComment = null;
                            currentSelectedTextForComment = null;
                            if (selectionActionButton?.style.display === 'block') {
                                selectionActionButton.style.display = 'none';
                            }
                        }
                    });

                    rendition.on('displayed', (section) => {
                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
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
                        if (origEvent.target.closest('#settings-bar') || origEvent.target.closest('.progress-slider-container')) {
                            return;
                        }
                        if (origEvent.target.closest('a[href]')) {
                            return;
                        }

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

                    console.log("[DEBUG] Rendition.display() 호출하여 책의 첫 부분 표시 시도.");
                    return rendition.display();
                })
                    .then(async (section) => {
                        hideLoading();
                        console.log("[DEBUG] 책 초기 표시 완료. 추가 설정 및 초기 위치 로드 시작.");
                        loadToc();
                        loadPersistentSettings();
                        await determineEffectiveEndOfBook(); // 초기 로드 후에도 확인
                        updateProgress();

                        if (section && section.href) {
                            currentChapterHrefForComment = section.href;
                        } else if (rendition && rendition.currentLocation() && rendition.currentLocation().start) {
                            currentChapterHrefForComment = rendition.currentLocation().start.href;
                        }
                        setTimeout(attachIframeListeners, 150);
                        if (book.packaging?.metadata) console.log("책 렌더링됨:", book.packaging.metadata.title);

                        if (userId && bookId) {
                            const backendCfi = await fetchInitialCfiFromBackend();
                            if (backendCfi) {
                                console.log("[DEBUG] 백엔드에서 가져온 CFI로 이동 시도:", backendCfi);
                                return rendition.display(backendCfi);
                            } else {
                                console.log("[INFO] 백엔드에 저장된 CFI가 없거나 가져오지 못했습니다.");
                            }
                        } else {
                            console.log("[INFO] 비로그인 사용자 또는 bookId 없음. 로컬 스토리지 확인.");
                            const lastLoc = localStorage.getItem(`epub-location-${bookId}`);
                            if (lastLoc) {
                                console.log("[DEBUG] 로컬 스토리지에 저장된 위치로 이동 시도 (비로그인):", lastLoc);
                                return rendition.display(lastLoc);
                            }
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
                        toggleSettings(false);
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
        document.body.classList.toggle('dark-mode', isDark);
        if (rendition?.themes) {
            try {
                const commonStyles = { /* ... CSS overrides ... */
                    'p': {'color': isDark ? '#e0e0e0 !important' : null},
                    'h1, h2, h3, h4, h5, h6': {'color': isDark ? '#f0f0f0 !important' : null},
                    'a': {'color': isDark ? '#bb86fc !important' : null},
                    'li': {'color': isDark ? '#e0e0e0 !important' : null},
                    'div': {'color': isDark ? '#e0e0e0 !important' : null},
                };
                rendition.themes.override('color', isDark ? '#e0e0e0' : null, true);
                rendition.themes.override('background', isDark ? '#1e1e1e' : null, true);
                for (const selector in commonStyles) rendition.themes.override(selector, commonStyles[selector]);
                if (rendition.display && typeof rendition.display === 'function') {
                    const currentLocation = rendition.currentLocation();
                    if (currentLocation?.start?.cfi) rendition.display(currentLocation.start.cfi).catch(err => console.error("Error re-displaying CFI after dark mode:", err));
                    else rendition.display().catch(err => console.error("Error re-displaying (no CFI) after dark mode:", err));
                }
            } catch (themeError) {
                console.error("Error applying dark mode theme:", themeError);
            }
        }
        if (toggleDarkModeButton) toggleDarkModeButton.textContent = isDark ? '라이트 모드' : '다크 모드';
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
                window.location.href = `/comments/new?${params.toString()}`;
            } else {
                alert("선택된 내용이 없거나, 위치 정보를 가져올 수 없습니다. 다시 시도해 주세요.");
            }
            selectionActionButton.style.display = 'none';
        });
    };

    if (progressSliderInput) {
        progressSliderInput.addEventListener('input', () => {
            isSliderDragging = true;
            const percentage = parseInt(progressSliderInput.value, 10);
            if (book && book.locations && book.locations.total > 0) {
                const cfi = book.locations.cfiFromPercentage(percentage / 100);
                const targetLocation = book.locations.locationFromCfi(cfi);
                if (progressInfoCurrent) progressInfoCurrent.textContent = targetLocation;
                if (progressPercentageDisplay) progressPercentageDisplay.textContent = `${percentage}%`;
                if (tooltipCurrentValue) tooltipCurrentValue.textContent = targetLocation;
                if (progressSliderFill) {
                    progressSliderFill.style.width = `${percentage}%`;
                }
                if (sliderValueTooltip) positionTooltip();
            }
        });
        progressSliderInput.addEventListener('change', () => {
            isSliderDragging = false;
            if (book && book.locations && rendition) {
                const percentage = parseInt(progressSliderInput.value, 10) / 100;
                const cfi = book.locations.cfiFromPercentage(percentage);
                if (cfi) {
                    rendition.display(cfi).then(() => {
                        updateProgress();
                        if (sliderValueTooltip) positionTooltip();
                    });
                }
            }
        });
        progressSliderInput.addEventListener('mouseenter', () => {
            if (areSettingsVisible && sliderValueTooltip && book.locations?.total > 0) {
                positionTooltip();
                sliderValueTooltip.style.opacity = '1';
            }
        });
        progressSliderInput.addEventListener('focus', () => {
            if (areSettingsVisible && sliderValueTooltip && book.locations?.total > 0) {
                positionTooltip();
                sliderValueTooltip.style.opacity = '1';
            }
        });
        progressSliderInput.addEventListener('mouseleave', () => {
            if (sliderValueTooltip && !isSliderDragging) sliderValueTooltip.style.opacity = '0';
        });
        progressSliderInput.addEventListener('blur', () => {
            if (sliderValueTooltip && !isSliderDragging) sliderValueTooltip.style.opacity = '0';
        });
    }

    console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 시작.");
    console.log("[DEBUG] Thymeleaf 변수 값:", {bookId, userId, bookUrl});

    const requiredElements = [viewerElement, loadingIndicator, tocList, tocContainer, tocOverlay, settingsBar, settingsContentTop, settingsContentBottom, toggleDarkModeButton, closeSettingsButton, selectionActionButton, tocButtonInSettings, notesButtonInSettings, viewSettingsButtonInSettings, progressInfoCurrent, progressInfoTotal, progressPercentageDisplay, progressSliderInput, progressSliderFill, sliderValueTooltip, tooltipCurrentValue, tooltipTotalValue, currentChapterTitleElement];
    const missingElements = requiredElements.filter(el => !el);

    if (missingElements.length === 0) {
        if (!userId) console.warn("[WARN] 사용자 ID를 가져올 수 없습니다. 페이지 표시 저장/로드 기능이 제한될 수 있습니다.");
        if (!bookId) console.warn("[WARN] 책 ID를 가져올 수 없습니다. 페이지 표시 저장/로드 및 기타 책 관련 기능이 제한될 수 있습니다.");
        if (!bookUrl) {
            console.error("[CRITICAL] 책 URL(bookUrl)을 결정할 수 없습니다. 뷰어를 초기화할 수 없습니다.");
            if (viewerElement) viewerElement.innerHTML = `<p>책 내용을 불러올 수 없습니다. (URL 구성 오류)</p>`;
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
        console.log("[DEBUG] DOMContentLoaded: 초기화 시퀀스 완료 (또는 URL 문제로 부분 완료).");
    } else {
        console.error("[CRITICAL] DOMContentLoaded: 하나 이상의 필수 UI 요소가 누락되었습니다.");
        missingElements.forEach(el => console.error(`누락된 요소 ID (또는 참조): ${el === viewerElement ? 'viewerElement' : (el ? el.id || 'ID 없음 (JS 변수명 확인)' : '정의되지 않음')}`));
        alert("뷰어 초기화에 필요한 중요 요소가 없어 페이지를 정상적으로 표시할 수 없습니다.");
    }
});