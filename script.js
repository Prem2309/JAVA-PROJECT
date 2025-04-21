function login() {
    const u = document.getElementById('username').value;
    const p = document.getElementById('password').value;
    fetch("/login", {
        method: "POST",
        body: `${u},${p}`
    }).then(res => {
        if (res.ok) {
            window.location.href = "home.html";
        } else {
            document.getElementById("msg").innerText = "Login failed!";
        }
    });
}
