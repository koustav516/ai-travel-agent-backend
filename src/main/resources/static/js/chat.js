(function () {
    const form = document.getElementById("chat-form");
    const input = document.getElementById("message");
    const sendButton = document.getElementById("send");
    const messages = document.getElementById("messages");
    const scroller = document.getElementById("scroller");
    const welcome = document.getElementById("welcome");
    const themeToggle = document.getElementById("theme-toggle");
    const apiUrl = form.dataset.api || "/chat";

    let busy = false;

    // ---------- Theme ----------

    themeToggle.addEventListener("click", function () {
        const isDark = document.documentElement.classList.toggle("dark");
        try { localStorage.setItem("theme", isDark ? "dark" : "light"); } catch (e) { }
    });

    // ---------- Rendering ----------

    function escapeHtml(text) {
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    // Tiny, safe markdown: the text is escaped first, then only bold, inline code and bullets are allowed.
    function formatReply(text) {
        return escapeHtml(text)
            .replace(/\*\*(.+?)\*\*/g, '<strong class="font-semibold text-stone-900 dark:text-stone-50">$1</strong>')
            .replace(/`([^`]+)`/g, '<code class="rounded bg-stone-100 px-1.5 py-0.5 text-[13px] dark:bg-stone-800">$1</code>')
            .replace(/^[ \t]*[-*][ \t]+(.*)$\n?/gm, '<span class="flex gap-2"><span class="text-brass-500">&bull;</span><span>$1</span></span>');
    }

    function scrollToBottom() {
        scroller.scrollTo({ top: scroller.scrollHeight, behavior: "smooth" });
    }

    function addUserMessage(text) {
        const row = document.createElement("div");
        row.className = "flex justify-end animate-rise";

        const bubble = document.createElement("div");
        bubble.className = "max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-stone-900 px-4 py-2.5 text-[15px] leading-relaxed text-stone-50 dark:bg-stone-100 dark:text-stone-900";
        bubble.textContent = text;

        row.appendChild(bubble);
        messages.appendChild(row);
        scrollToBottom();
    }

    function addAssistantRow() {
        const row = document.createElement("div");
        row.className = "flex gap-3 animate-rise";
        row.innerHTML =
            '<div class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-brass-500/40 bg-brass-500/10">' +
            '<span class="font-serif text-sm italic leading-none text-brass-600 dark:text-brass-400">C</span></div>' +
            '<div class="reply min-w-0 flex-1 whitespace-pre-wrap pt-0.5 text-[15px] leading-relaxed text-stone-700 dark:text-stone-300"></div>';
        messages.appendChild(row);
        scrollToBottom();
        return row.querySelector(".reply");
    }

    function showTyping(target) {
        target.innerHTML =
            '<span class="inline-flex items-center gap-1 py-2" aria-label="Assistant is typing">' +
            '<span class="h-1.5 w-1.5 animate-dot-1 rounded-full bg-stone-400"></span>' +
            '<span class="h-1.5 w-1.5 animate-dot-2 rounded-full bg-stone-400"></span>' +
            '<span class="h-1.5 w-1.5 animate-dot-3 rounded-full bg-stone-400"></span></span>';
    }

    function showError(target, message) {
        target.innerHTML =
            '<span class="inline-flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">' +
            escapeHtml(message) + "</span>";
    }

    // ---------- Sending ----------

    async function send(text) {
        const message = text.trim();
        if (!message || busy) return;

        busy = true;
        sendButton.disabled = true;
        if (welcome) welcome.remove();

        addUserMessage(message);
        input.value = "";
        resizeInput();

        const reply = addAssistantRow();
        showTyping(reply);

        try {
            const response = await fetch(apiUrl, {
                method: "POST",
                headers: { "Content-Type": "text/plain" },
                body: message,
            });

            if (!response.ok) {
                throw new Error("The server returned " + response.status + ". Check the backend logs.");
            }

            const text = await response.text();
            reply.innerHTML = formatReply(text || "(No response)");
        } catch (error) {
            const offline = error instanceof TypeError;
            showError(reply, offline ? "Can't reach the server. Is the app running?" : error.message);
        } finally {
            busy = false;
            updateSendButton();
            scrollToBottom();
            input.focus();
        }
    }

    // ---------- Input behaviour ----------

    function resizeInput() {
        input.style.height = "auto";
        input.style.height = Math.min(input.scrollHeight, 160) + "px";
    }

    function updateSendButton() {
        sendButton.disabled = busy || input.value.trim() === "";
    }

    input.addEventListener("input", function () {
        resizeInput();
        updateSendButton();
    });

    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            send(input.value);
        }
    });

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        send(input.value);
    });

    document.querySelectorAll(".suggestion").forEach(function (button) {
        button.addEventListener("click", function () {
            send(button.dataset.suggestion);
        });
    });

    updateSendButton();
    input.focus();
})();
