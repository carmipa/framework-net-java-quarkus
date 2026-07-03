(function (w) {
    "use strict";

    function esc(s) {
        if (s == null || s === "") {
            return "";
        }
        return String(s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    w.HtmlEscape = { esc: esc };
})(window);
