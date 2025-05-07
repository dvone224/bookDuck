$("#imgUploadBtn").click(()=>{
    //파일데이터 추출
    var file=$("#userImg");
    console.log(file[0]);//순수한 태그
    console.dir(file[0].files[0]); //파일데이터

    //폼태그로 추가
    var formData = new FormData(); //폼객체
    formData.append("file",file[0].files[0]); //name, 값

    $.ajax({
        url:"user/imgupload",
        type:"post",
        data:formData, //보내는데이터 form
        contentType:false, //보내는데이터타입 false->"multipart/form-data"로 선언됩니다.
        processData:false, //폼데이터가 name=값&name=값 형식으로 자동변경되는 것을 막아줍니다.
        success:(result)=>{
            var res = result.split('/')[0];
            var imgname = result.split('/')[1];
            //var imgPath = "'"+img+"'";
            console.log(res);
            console.log(imgname);
            if(res == "success"){
                alert("업로드가 완료되었습니다.");
                $('#uploadImg').attr("src", "/image/getimg?fileName="+imgname);
                $('#img').attr("value",imgname);
            }
        },
        error:(err)=>{
            $('#imgUploadErr').innerHTML="이미지 업로드 오류"
            //alert("업로드 에러발생");
        }

    })
})

const joinNickname = document.getElementById("joinNickname");

let validNickname = false;

const submitFrom = document.getElementById("socialjoinform");
const agree = document.getElementById("agree");

submitFrom.addEventListener("submit",(event)=>{
    event.preventDefault();
    const totalErr = document.getElementById("check validTotal");


    if(!validNickname){
        alert("유효하지 않은 별명 입니다.");
        //totalErr.innerHTML= "유효하지 않은 별명 입니다."
        return;
    }

    if(!agree.checked){
        alert('약관에 대한 동의가 필요합니다.');
        //totalErr.innerHTML= "약관에 대한 동의가 필요합니다."
        return;
    }

    submitFrom.submit();
})

joinNickname.addEventListener("keyup", ()=> {
    validNickname = false;
    const nicknameErr = document.getElementById("check validNickname");
    let nicknameValue = joinNickname.value;
    if(nicknameValue == "") {
        nicknameErr.innerHTML = "";
        return;
    }

    if(checknicknamelength(nicknameValue) === true && checknicknametext(nicknameValue) === true){
        nicknameErr.innerHTML = "";
    }else{
        nicknameErr.innerHTML = "닉네임(2~7자, 한글, 대/소문자, 숫자)";
        return;
    }

    fetch(`/user/searchusernickname?nickname=${encodeURIComponent(nicknameValue)}`, {
        method: 'GET'
    })
        .then((response) => {
            if (response.ok) {
                validNickname = true;
                console.log(validNickname);

            }else{
                nicknameErr.innerHTML = "이미 사용중인 별명입니다.";
                console.log(validNickname);
            }
        })
        .catch((error) => {
            console.error("검색 중 오류 발생:", error);
        });
})

function checknicknamelength(nickname){
    return nickname.length >=2 && nickname.length<=7;
}

function checknicknametext(nickname){
    return /^[ㄱ-ㅎ가-힣a-zA-Z0-9][ㄱ-ㅎ가-힣a-zA-Z0-9]*$/.test(nickname);
}