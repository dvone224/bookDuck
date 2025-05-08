$("#imgUploadBtn").click(()=>{
    //파일데이터 추출
    var file=$("#userImg");
    //console.log(file[0]);//순수한 태그
    //console.dir(file[0].files[0]); //파일데이터

    //폼태그로 추가
    var formData = new FormData(); //폼객체
    formData.append("file",file[0].files[0]); //name, 값

    $.ajax({
        url:"image/upload",
        type:"post",
        data:formData, //보내는데이터 form
        contentType:false, //보내는데이터타입 false->"multipart/form-data"로 선언됩니다.
        processData:false, //폼데이터가 name=값&name=값 형식으로 자동변경되는 것을 막아줍니다.
        success: (result) => {
            console.log(result); // Logs the S3 URL (e.g., https://aws-bookduck...)
            if (result !== "fail") {
                alert("업로드가 완료되었습니다.");
                $('#uploadImg').attr("src", result);
                $('#img').attr("value", result);
            }
        },
        error: (err) => {
            $('#imgUploadErr').text("이미지 업로드 오류");
        }

    })
})


const originNickname = document.getElementById('originNickname').value;
const originEmail = document.getElementById('originEmail').value;
const originImg = document.getElementById('originImg').value;

console.log(originNickname);
console.log(originEmail);
console.log(originImg);

if (originImg.length != 0) {
    // 이미지 URL 설정
    console.log(originImg);
    $('#uploadImg')
        .attr("src", originImg)
        .on("load", function () {
            //console.log("Image loaded successfully: " + originImg);
        })
        .on("error", function () {
            console.log("Image failed to load: " + originImg);
            $(this).attr("src", "/img/bookduck_login.png");
        });
} else {
    $('#uploadImg').attr("src", "/img/bookduck_login.png"); // 기본 이미지 설정
}

let validPw = true;
let validEmail = false;
let validNickname = true;
let validCode = false;

let useableEmail = true;


const updatePw = document.getElementById("updatePw");
const updateNickname = document.getElementById("updateNickname");
const updateEmail = document.getElementById("updateEmail");
const updateCode = document.getElementById("updateCode");

const submitForm = document.getElementById("userUpdateform");

submitForm.addEventListener("submit",(event)=>{
    event.preventDefault();
    const totalErr = document.getElementById("check validTotal");


    if(!validPw){
        alert('유효하지 않은 비밀번호 입니다.')
        //totalErr.innerHTML= "유효하지 않은 비밀번호 입니다."
        return;
    }
    if(!validNickname){
        alert('유효하지 않은 별명 입니다.')
        //totalErr.innerHTML= "유효하지 않은 별명 입니다."
        return;
    }
    if(!useableEmail){
        alert('이메일 검증이 필요합니다.')
        //totalErr.innerHTML= "이메일 검증이 필요합니다."
        return;
    }

    submitForm.submit();
})

updateNickname.addEventListener("keyup", ()=> {
    validNickname = false;
    const nicknameErr = document.getElementById("check validNickname");
    let nicknameValue = updateNickname.value;
    if(nicknameValue == originNickname || nicknameValue == "") {
        nicknameErr.innerHTML = "";
        validNickname = true;
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

updatePw.addEventListener("keyup", ()=>{
    validPw = false;
    let pwErr= document.getElementById("check validPw")
    let pwValue = updatePw.value;

    if(pwValue == null || pwValue == ""){
        pwErr.innerHTML = "";
        validPw = true;
        return;
    }

    if(pwTextCheck(pwValue)){
        pwErr.innerHTML = "";
    }else{
        pwErr.innerHTML = "PASSWORD(영문 대/소문자, 숫자, 특수문자(사용가능 특수문자: @ $ ! % * # ? &))"
        return;
    }

    validPw = true;

})

function pwTextCheck(pw){
    return /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,}$/.test(pw);
}



updateEmail.addEventListener("keyup", ()=> {
    validEmail = false;
    validCode = false;
    useableEmail = false;

    const emailErr = document.getElementById("check validEmail");
    let emailValue = updateEmail.value;
    if(emailValue == originEmail) {
        emailErr.innerHTML = "";
        useableEmail = true;
        return;
    }

    if(checkemail(emailValue) === true){
        emailErr.innerHTML = "";
    }else{
        emailErr.innerHTML = "유효한 형식의 이메일이 아닙니다";
        return;
    }

    fetch(`/user/searchuseremail?email=${encodeURIComponent(emailValue)}`, {
        method: 'GET'
    })
        .then((response) => {
            if (response.ok) {
                validEmail = true;
                console.log(validEmail);

            }else{
                emailErr.innerHTML = "이미 사용중인 이메일입니다.";
                console.log(validEmail);
            }
        })
        .catch((error) => {
            console.error("검색 중 오류 발생:", error);
        });
})

function checkemail(email){
    return /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/.test(email);
}

function sendEmail(){
    const emailErr = document.getElementById("check validEmail");

    let emailValue = updateEmail.value;
    if(emailValue == originEmail) {
        alert("기존 메일과 동일해 메일검증이 필요하지 않습니다.")
        return;
    }

    if(!validEmail){
        emailErr.innerHTML = "유효한 이메일이 아닙니다";
        return;
    }

    let formData = new FormData();
    formData.append('mail', updateEmail.value);

    fetch('/mailapi/sendmail',{
        method: 'POST',
        cache: 'no-cache',
        body: formData
    })
        .then((response) => response.json())
        .then((data) => {
            console.log(data);
            alert('인증번호가 발송 되었습니다');
        });
}

updateCode.addEventListener("keyup", ()=>{
    validCode = false;

    const codeErr = document.getElementById("check validCode");
    let codeValue = updateCode.value;

    let emailValue = updateEmail.value;
    if(emailValue == originEmail) {
        alert("기존 메일과 동일해 메일검증이 필요하지 않습니다.");
        updateCode.value = "";
        return;
    }


    if(codeValue == "") {
        codeErr.innerHTML = "";
        return;
    }

    if(checkcodetext(codeValue) === true && checkcodelength(codeValue) === true){
        codeErr.innerHTML = "";
    }else{
        codeErr.innerHTML = "인증코드는 6자리 숫자입니다.";
        return;
    }

    validCode = true;

})

function checkcodetext(code){
    return /^[0-9][0-9]*$/.test(code);
}

function checkcodelength(code){
    return code.length == 6;
}


function checkCode(){
    const codeErr = document.getElementById("check validCode");

    if(!validEmail){
        codeErr.innerHTML = "유효한 이메일이 아닙니다";
        return;
    }

    if(!validCode){
        codeErr.innerHTML = "유효한 인증번호가 아닙니다";
        return;
    }

    let formData = new FormData();
    formData.append('mail', updateEmail.value);
    formData.append('code', updateCode.value);

    fetch('/mailapi/verifycode',{
        method: 'POST',
        cache: 'no-cache',
        body: formData
    })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then((data) => {
            console.log(data);
            if (data.status === 'Verified') {
                useableEmail = true;
                codeErr.innerHTML = "이메일 인증에 성공하였습니다.";
            } else {
                codeErr.innerHTML = "이메일 인증에 실패하였습니다.(인증번호의 유효시간은 30분 입니다.)";
            }
        })
        .catch((error) => {
            console.error('Error:', error);
            codeErr.innerHTML = "인증 중 오류가 발생했습니다. 다시 시도하세요.";
        });

}