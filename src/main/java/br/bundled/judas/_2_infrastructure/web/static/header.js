// Keeps --top-height equal to the header's real height and marks the menu link of the screen in #content (UI-10, UI-12).
(() => {
    const measure = (header) =>
        document.documentElement.style.setProperty("--top-height", header.getBoundingClientRect().height + "px");

    const mark = (content) => {
        document.querySelectorAll(".menu a[data-screen]").forEach((link) => {
            if (link.dataset.screen === content.dataset.screen) link.setAttribute("aria-current", "page");
            else link.removeAttribute("aria-current");
        });
    };

    document.addEventListener("DOMContentLoaded", () => {
        const header = document.querySelector("header.top");
        if (header) {
            measure(header);
            new ResizeObserver(() => measure(header)).observe(header);
        }
    });

    document.addEventListener("htmx:load", (event) => {
        if (event.target.id === "content") mark(event.target);
    });
})();
