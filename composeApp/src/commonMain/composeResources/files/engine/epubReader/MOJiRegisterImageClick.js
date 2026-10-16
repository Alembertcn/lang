function previewImage (e) {
    e.preventDefault();//阻止图片其他事件
    window.location.href = 'image-preview:' + e.target.src;
}

function registerImageClickAction() {
    var imgs   = document.getElementsByTagName('img');
    var length = imgs.length;
    
    for (var i = 0; i < length; i++) {
        img = imgs[i];

        //如果该图片不可预览则直接跳过
        if (img.getAttribute("previewdisabled")) continue;
            
        img.onclick = function (e) {
            e.preventDefault();
            window.location.href = 'image-preview:' + this.src;
        }
    }
}