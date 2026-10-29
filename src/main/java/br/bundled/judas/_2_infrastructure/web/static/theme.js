// Applies the theme chosen in this browser before the first paint (no defer) and drives the switch (UI-80).
(() => {
    const KEY = "judas.theme";
    const root = document.documentElement;

    const stored = () => {
        try { return localStorage.getItem(KEY) || "auto"; } catch { return "auto"; }
    };

    const apply = (choice) => {
        if (choice === "auto") root.removeAttribute("data-theme"); else root.setAttribute("data-theme", choice);
        document.querySelectorAll("[data-theme-choice]").forEach((button) =>
            button.setAttribute("aria-pressed", String(button.getAttribute("data-theme-choice") === choice)));
    };

    apply(stored());

    document.addEventListener("DOMContentLoaded", () => {
        apply(stored());
        document.querySelectorAll(".theme[hidden]").forEach((group) => group.hidden = false);
    });

    document.addEventListener("click", (event) => {
        const button = event.target.closest("[data-theme-choice]");
        if (!button) return;
        const choice = button.getAttribute("data-theme-choice");
        try { localStorage.setItem(KEY, choice); } catch { /* the choice lasts only for this page */ }
        apply(choice);
    });
})();
