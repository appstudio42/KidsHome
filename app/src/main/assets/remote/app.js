(function () {
  "use strict";

  var STR = {
    en: {
      title: "Remote control",
      subtitle: "Same Wi‑Fi · no cloud · no accounts",
      loginTitle: "Parent login",
      loginHint: "Open the QR from tablet settings, or enter the parent PIN.",
      lblToken: "Pairing token",
      lblPin: "Parent PIN",
      login: "Sign in",
      logout: "Sign out",
      usageTitle: "Today",
      actionsTitle: "Actions",
      appsTitle: "Apps today",
      lock: "Lock now",
      unlock: "Unlock",
      custom: "Custom minutes",
      customPrompt: "Minutes to add (1–240)",
      invalidMinutes: "Enter minutes between 1 and 240",
      loginError: "Could not sign in",
      rateLimited: "Too many PIN attempts. Try again soon.",
      usageFmt: "%used% / %limit% min used · %remaining% left",
      usageOff: "Screen time limit is off today",
      unlimited: "unlimited",
      starsFmt: "★ %total% stars · %today% today",
      noApps: "No per-app usage yet today",
      kidOn: "Kid mode on",
      kidOff: "Kid mode off",
      locked: "Locked",
      unlocked: "Unlocked",
      remote: "Remote lock",
      bedtime: "Bedtime",
      daily: "Daily limit",
      schedule: "Schedule active",
      added: "Time added",
      lockedNow: "Tablet locked",
      unlockedNow: "Remote lock cleared",
      lockBannerTitle: "Locked",
      lockBannerHint: "The tablet is locked. Unlock when ready — you can still add time.",
      appLimit: "limit %n% min"
    },
    "pt-BR": {
      title: "Controle remoto",
      subtitle: "Mesmo Wi‑Fi · sem nuvem · sem contas",
      loginTitle: "Login dos responsáveis",
      loginHint: "Abra o QR nas configurações do tablet ou digite o PIN.",
      lblToken: "Token de pareamento",
      lblPin: "PIN dos responsáveis",
      login: "Entrar",
      logout: "Sair",
      usageTitle: "Hoje",
      actionsTitle: "Ações",
      appsTitle: "Apps hoje",
      lock: "Bloquear agora",
      unlock: "Desbloquear",
      custom: "Minutos personalizados",
      customPrompt: "Minutos para adicionar (1–240)",
      invalidMinutes: "Informe minutos entre 1 e 240",
      loginError: "Não foi possível entrar",
      rateLimited: "Muitas tentativas de PIN. Tente em breve.",
      usageFmt: "%used% / %limit% min usados · %remaining% restantes",
      usageOff: "Limite de tempo de tela desligado hoje",
      unlimited: "ilimitado",
      starsFmt: "★ %total% estrelas · %today% hoje",
      noApps: "Nenhum uso por app hoje",
      kidOn: "Modo criança ligado",
      kidOff: "Modo criança desligado",
      locked: "Bloqueado",
      unlocked: "Desbloqueado",
      remote: "Bloqueio remoto",
      bedtime: "Hora de dormir",
      daily: "Limite diário",
      schedule: "Rotina ativa",
      added: "Tempo adicionado",
      lockedNow: "Tablet bloqueado",
      unlockedNow: "Bloqueio remoto removido",
      lockBannerTitle: "Bloqueado",
      lockBannerHint: "O tablet está bloqueado. Desbloqueie quando quiser — ainda dá para adicionar tempo.",
      appLimit: "limite %n% min"
    }
  };

  function queryParam(name) {
    var m = new RegExp("[?&]" + name + "=([^&]+)").exec(location.search || "");
    return m ? decodeURIComponent(m[1]) : "";
  }

  function pickLang() {
    var forced = (queryParam("lang") || "").toLowerCase();
    if (forced.indexOf("pt") === 0) return "pt-BR";
    if (forced.indexOf("en") === 0) return "en";
    var lang = (navigator.language || "en").toLowerCase();
    if (lang.indexOf("pt") === 0) return "pt-BR";
    return "en";
  }

  var lang = pickLang();
  var t = STR[lang] || STR.en;

  function $(id) { return document.getElementById(id); }

  function applyI18n() {
    $("title").textContent = t.title;
    $("subtitle").textContent = t.subtitle;
    $("loginTitle").textContent = t.loginTitle;
    $("loginHint").textContent = t.loginHint;
    $("lblToken").textContent = t.lblToken;
    $("lblPin").textContent = t.lblPin;
    $("btnLogin").textContent = t.login;
    $("btnLogout").textContent = t.logout;
    $("usageTitle").textContent = t.usageTitle;
    $("actionsTitle").textContent = t.actionsTitle;
    $("appsTitle").textContent = t.appsTitle;
    $("btnLock").textContent = t.lock;
    $("btnUnlock").textContent = t.unlock;
    $("btnCustom").textContent = "…";
    document.documentElement.lang = lang === "pt-BR" ? "pt-BR" : "en";
  }

  function toast(msg) {
    var el = $("toast");
    el.textContent = msg;
    el.hidden = false;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(function () { el.hidden = true; }, 2200);
  }

  function queryToken() {
    var m = /[?&]t=([^&]+)/.exec(location.search || "");
    return m ? decodeURIComponent(m[1]) : "";
  }

  async function api(path, options) {
    options = options || {};
    var opts = {
      method: options.method || "GET",
      credentials: "same-origin",
      headers: options.headers || {}
    };
    if (options.body) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(options.body);
    }
    var res = await fetch(path, opts);
    var text = await res.text();
    var data = {};
    try { data = text ? JSON.parse(text) : {}; } catch (e) { data = { raw: text }; }
    return { ok: res.ok, status: res.status, data: data };
  }

  function showLogin(show) {
    $("login").hidden = !show;
    $("dashboard").hidden = show;
  }

  function fmtUsage(state) {
    if (!state.screenTimeEnabled || state.remainingMinutes < 0) {
      return t.usageOff;
    }
    return t.usageFmt
      .replace("%used%", String(state.usedMinutes))
      .replace("%limit%", String(state.dailyLimitMinutes))
      .replace("%remaining%", String(state.remainingMinutes));
  }

  function lockReasonLabel(state) {
    if (state.blockReason === "remote") return t.remote;
    if (state.blockReason === "bedtime") return t.bedtime;
    if (state.blockReason === "daily_limit") return t.daily;
    return t.locked;
  }

  function renderState(state) {
    var chip = $("stateChip");
    var dash = $("dashboard");
    var banner = $("lockBanner");
    var parts = [];
    parts.push(state.kidMode ? t.kidOn : t.kidOff);
    if (state.locked) {
      parts.push(lockReasonLabel(state));
      chip.className = "chip bad";
      dash.classList.add("is-locked");
      banner.hidden = false;
      $("lockBannerTitle").textContent = t.lockBannerTitle;
      $("lockBannerHint").textContent = t.lockBannerHint;
      // Unlock is the primary action while locked; add-time stays available.
      $("btnUnlock").className = "btn primary";
      $("btnLock").className = "btn secondary";
    } else {
      parts.push(t.unlocked);
      chip.className = "chip ok";
      dash.classList.remove("is-locked");
      banner.hidden = true;
      $("btnUnlock").className = "btn teal";
      $("btnLock").className = "btn danger";
    }
    if (state.scheduleActive) parts.push(t.schedule);
    chip.textContent = parts.join(" · ");

    $("childChip").textContent = state.childName || "KidsHome";
    $("usageLine").textContent = fmtUsage(state);
    $("starsLine").textContent = t.starsFmt
      .replace("%total%", String(state.starsTotal))
      .replace("%today%", String(state.starsToday));

    // dailyLimitMinutes is today's total allowance (base limit + bonus).
    var pct = 0;
    if (state.screenTimeEnabled && state.dailyLimitMinutes > 0) {
      pct = Math.min(100, Math.round((state.usedMinutes / state.dailyLimitMinutes) * 100));
    }
    $("usageFill").style.width = pct + "%";

    var list = $("appsList");
    list.innerHTML = "";
    var apps = state.apps || [];
    apps.sort(function (a, b) { return (b.usedMinutes || 0) - (a.usedMinutes || 0); });
    if (!apps.length) {
      var empty = document.createElement("li");
      empty.textContent = t.noApps;
      list.appendChild(empty);
      return;
    }
    apps.forEach(function (app) {
      if (!app.usedMinutes && (app.limitMinutes == null || app.limitMinutes < 0)) return;
      var li = document.createElement("li");
      var name = document.createElement("span");
      name.textContent = app.label || app.packageName;
      var meta = document.createElement("span");
      meta.className = "meta";
      var limitText = app.limitMinutes >= 0
        ? t.appLimit.replace("%n%", String(app.limitMinutes))
        : t.unlimited;
      meta.textContent = app.usedMinutes + " min · " + limitText;
      li.appendChild(name);
      li.appendChild(meta);
      list.appendChild(li);
    });
    if (!list.children.length) {
      var none = document.createElement("li");
      none.textContent = t.noApps;
      list.appendChild(none);
    }
  }

  async function refresh() {
    var res = await api("/api/state");
    if (res.status === 401) {
      showLogin(true);
      return false;
    }
    if (!res.ok) return false;
    showLogin(false);
    renderState(res.data);
    return true;
  }

  async function login() {
    $("loginError").hidden = true;
    var token = ($("token").value || "").trim() || queryToken();
    var pin = ($("pin").value || "").trim();
    var body = token ? { token: token } : { pin: pin };
    var res = await api("/api/login", { method: "POST", body: body });
    if (res.status === 429) {
      $("loginError").textContent = t.rateLimited;
      $("loginError").hidden = false;
      return;
    }
    if (!res.ok) {
      $("loginError").textContent = t.loginError;
      $("loginError").hidden = false;
      return;
    }
    $("pin").value = "";
    await refresh();
  }

  async function addTime(minutes) {
    var res = await api("/api/add-time", { method: "POST", body: { minutes: minutes } });
    if (res.status === 401) { showLogin(true); return; }
    if (res.ok) {
      renderState(res.data);
      toast(t.added);
    }
  }

  function wire() {
    applyI18n();
    var qt = queryToken();
    if (qt) $("token").value = qt;

    $("btnLogin").addEventListener("click", login);
    $("pin").addEventListener("keydown", function (e) {
      if (e.key === "Enter") login();
    });
    $("btnLogout").addEventListener("click", async function () {
      await api("/api/logout", { method: "POST", body: {} });
      showLogin(true);
    });
    document.querySelectorAll("[data-add]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        addTime(parseInt(btn.getAttribute("data-add"), 10));
      });
    });
    $("btnCustom").addEventListener("click", function () {
      var raw = window.prompt(t.customPrompt, "20");
      if (raw == null) return;
      var n = parseInt(raw, 10);
      if (!(n >= 1 && n <= 240)) {
        toast(t.invalidMinutes);
        return;
      }
      addTime(n);
    });
    $("btnLock").addEventListener("click", async function () {
      var res = await api("/api/lock", { method: "POST", body: {} });
      if (res.status === 401) { showLogin(true); return; }
      if (res.ok) { renderState(res.data); toast(t.lockedNow); }
    });
    $("btnUnlock").addEventListener("click", async function () {
      var res = await api("/api/unlock", { method: "POST", body: {} });
      if (res.status === 401) { showLogin(true); return; }
      if (res.ok) { renderState(res.data); toast(t.unlockedNow); }
    });

    refresh().then(function (ok) {
      if (!ok && qt) {
        // QR landing may already have set the cookie via server; otherwise show login.
        showLogin(true);
      }
    });
    setInterval(function () {
      if (!$("dashboard").hidden) refresh();
    }, 4000);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wire);
  } else {
    wire();
  }
})();
