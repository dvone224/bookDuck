const agree = document.getElementById('agree');

function leave(){
    if(!agree.checked){
        alert('내용확인을 체크해 주세요');
        return;
    }

    const id = document.getElementById('delBtn').getAttribute('data-id');

    fetch(`/user/del/${id}`, {
        method: 'DELETE',
        // headers:{
        //     "Content-Type": "application/x-www-form-urlencoded",
        //     "X-CSRF-TOKEN": csrfToken // CSRF 토큰 추가
        // }
    })
        .then((response) => {
            if (response.ok) {
                location.href='/user/delSuccess';
            }
        })
        .catch((error) => {
            console.error("삭제 중 오류 발생:", error);
            alert("삭제 중 오류가 발생했습니다.");
        });

}