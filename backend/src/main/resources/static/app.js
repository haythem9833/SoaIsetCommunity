(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const state = {
    token: localStorage.getItem("iset.jwt") || "",
    api: location.protocol === "file:" ? "http://localhost:8081" : location.origin,
    me: null,
    communities: [],
    users: [],
    toastTimer: null
  };

  const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
  const initials = (name) => String(name || "?").trim().split(/\s+/).slice(0, 2).map((part) => part[0] || "").join("").toUpperCase();
  const dateLabel = (value) => {
    if (!value) return "À l’instant";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("fr-FR", { dateStyle: "medium", timeStyle: "short" });
  };

  function setApiStatus(status, label) {
    $("#api-status").textContent = label;
    $("#top-status").textContent = label;
    $("#api-host").textContent = state.api.replace(/^https?:\/\//, "");
    for (const dot of [$("#api-dot"), $("#top-dot")]) {
      dot.classList.remove("online", "offline");
      if (status) dot.classList.add(status);
    }
  }

  function toast(message, error = false) {
    const element = $("#toast");
    element.textContent = message;
    element.classList.toggle("error", error);
    element.classList.remove("hidden");
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => element.classList.add("hidden"), 4200);
  }

  function showResponse(summary, body, statusCode) {
    $("#response-summary").textContent = summary;
    $("#response-body").textContent = typeof body === "string" ? body : JSON.stringify(body, null, 2);
    const indicator = $(".response-indicator");
    indicator.style.background = statusCode >= 400 ? "#e47d6d" : "#8dc4a5";
  }

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
    if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    let response;
    try {
      response = await fetch(`${state.api}${path}`, { ...options, headers });
    } catch (error) {
      const message = `Impossible de joindre ${state.api}. Vérifiez que le backend tourne et que son URL/CORS sont corrects.`;
      showResponse("Erreur réseau", { message, detail: error.message }, 0);
      setApiStatus("offline", "API inaccessible");
      throw new Error(message);
    }
    const contentType = response.headers.get("content-type") || "";
    let payload;
    if (response.status === 204) {
      payload = null;
    } else if (contentType.includes("application/json")) {
      payload = await response.json();
    } else {
      payload = await response.text();
    }
    showResponse(`${options.method || "GET"} ${path} · ${response.status}`, payload, response.status);
    if (!response.ok) {
      const detail = payload && typeof payload === "object" ? payload.message || payload.error || payload.detail : payload;
      throw new Error(detail || `La requête a échoué (${response.status})`);
    }
    setApiStatus("online", "API connectée");
    return payload;
  }

  function setSignedIn(user) {
    state.me = user;
    $("#auth-panel").classList.add("hidden");
    $("#workspace").classList.remove("hidden");
    $("#logout-button").classList.remove("hidden");
    $("#user-name").textContent = user.fullName || user.email || "Utilisateur";
    $("#user-avatar").textContent = initials(user.fullName || user.email);
    $("#users-nav-link").classList.remove("hidden");
    $("#admin-nav-link").classList.toggle("hidden", user.userType !== "ADMIN");
    $("#crumb").textContent = "Fil d’actualité";
  }

  function signOut() {
    state.token = "";
    state.me = null;
    localStorage.removeItem("iset.jwt");
    $("#auth-panel").classList.remove("hidden");
    $("#workspace").classList.add("hidden");
    $("#logout-button").classList.add("hidden");
    $("#users-nav-link").classList.add("hidden");
    $("#admin-nav-link").classList.add("hidden");
    $("#users").classList.add("hidden");
    $("#admin").classList.add("hidden");
    $("#user-detail").classList.add("hidden");
    state.users = [];
    $("#feed-list").innerHTML = '<div class="empty-state">Connectez-vous pour charger les publications.</div>';
    $("#community-list").innerHTML = '<div class="empty-state small-empty">Connectez-vous pour voir les communautés.</div>';
    $("#user-list").innerHTML = '<div class="empty-state">Connectez-vous pour consulter l’annuaire.</div>';
    $("#pending-user-list").innerHTML = '<div class="empty-state small-empty">Connectez-vous comme administrateur.</div>';
    $("#admin-user-list").innerHTML = '<div class="empty-state small-empty">Connectez-vous comme administrateur.</div>';
    $$(".nav-link").forEach((link) => link.classList.toggle("active", link.hash === "#feed"));
    $("#crumb").textContent = "Fil d’actualité";
    $("#stat-posts").textContent = "—";
    $("#stat-communities").textContent = "—";
  }

  async function refreshData() {
    const [postsResult, communitiesResult] = await Promise.allSettled([
      request("/api/publications"),
      request("/api/communities")
    ]);
    if (postsResult.status === "fulfilled") renderPosts(postsResult.value);
    else toast(`Publications : ${postsResult.reason.message}`, true);
    if (communitiesResult.status === "fulfilled") {
      state.communities = communitiesResult.value;
      renderCommunities(state.communities);
    } else toast(`Communautés : ${communitiesResult.reason.message}`, true);
  }

  function renderPosts(posts) {
    $("#stat-posts").textContent = posts.length;
    const list = $("#feed-list");
    if (!posts.length) {
      list.innerHTML = '<div class="empty-state">Aucune publication pour le moment. Soyez le premier à partager une idée.</div>';
      return;
    }
    list.innerHTML = posts.map((post) => {
      const comments = Array.isArray(post.comments) ? post.comments : [];
      const commentsMarkup = comments.map((comment) => `
        <div class="comment-item"><span><strong>${escapeHtml(comment.authorName || "Membre")}</strong> ${escapeHtml(comment.contenu)}</span>
          ${comment.authorId === state.me?.id || state.me?.userType === "ADMIN" ? `<button class="text-button delete-comment" data-id="${escapeHtml(comment.id)}" type="button" aria-label="Supprimer le commentaire">×</button>` : ""}
        </div>`).join("");
      const isOwner = post.authorId === state.me?.id || state.me?.userType === "ADMIN";
      return `<article class="feed-card">
        <div class="post-meta">
          <span class="post-avatar">${escapeHtml(initials(post.authorName))}</span>
          <div class="post-author"><strong>${escapeHtml(post.authorName || "Membre")}</strong><small>${escapeHtml(dateLabel(post.createdAt))}</small></div>
          ${post.communityName ? `<span class="post-community">${escapeHtml(post.communityName)}</span>` : ""}
        </div>
        <h3 class="post-title">${escapeHtml(post.title)}</h3>
        ${post.content ? `<p class="post-content">${escapeHtml(post.content)}</p>` : ""}
        ${post.fileName ? `<a class="post-file" href="#" data-file-id="${escapeHtml(post.id)}">↧ ${escapeHtml(post.fileName)}</a>` : ""}
        ${isOwner ? `<div class="post-actions"><button class="text-button edit-post-toggle" type="button">Modifier</button><button class="text-button delete-post" data-id="${escapeHtml(post.id)}" type="button">Supprimer</button></div>
          <details class="post-edit">
            <summary>Modifier cette publication</summary>
            <form class="post-edit-form" data-id="${escapeHtml(post.id)}">
              <label>Titre<input name="title" maxlength="200" value="${escapeHtml(post.title)}" required></label>
              <label>Contenu<textarea name="content" maxlength="5000" rows="3">${escapeHtml(post.content || "")}</textarea></label>
              <div class="edit-actions"><button class="button button-primary" type="submit">Enregistrer</button><button class="button button-quiet edit-cancel" type="button">Annuler</button></div>
            </form>
          </details>` : ""}
        <div class="comments">${commentsMarkup}
          <form class="comment-form" data-id="${escapeHtml(post.id)}"><input name="contenu" placeholder="Écrire un commentaire…" maxlength="2000" required aria-label="Votre commentaire"><button type="submit">Envoyer</button></form>
        </div>
      </article>`;
    }).join("");
  }

  function renderCommunities(communities) {
    $("#stat-communities").textContent = communities.length;
    const select = $("#post-community");
    const oldSelection = select.value;
    select.innerHTML = '<option value="">Fil général</option>' + communities
      .filter((community) => community.member)
      .map((community) => `<option value="${escapeHtml(community.id)}">${escapeHtml(community.name)}</option>`).join("");
    if ([...select.options].some((option) => option.value === oldSelection)) select.value = oldSelection;

    const list = $("#community-list");
    if (!communities.length) {
      list.innerHTML = '<div class="empty-state small-empty">Aucune communauté pour le moment.</div>';
      return;
    }
    list.innerHTML = communities.map((community) => {
      const isOwner = state.me && community.ownerId === state.me.id;
      const isAdmin = state.me?.userType === "ADMIN";
      const action = community.member && !isOwner
        ? `<button class="community-action leave" data-action="leave" data-id="${escapeHtml(community.id)}" type="button">Quitter</button>`
        : !community.member
          ? `<button class="community-action" data-action="join" data-id="${escapeHtml(community.id)}" data-private="${community.type === "PRIVATE"}" type="button">Rejoindre</button>`
          : "";
      const deleteAction = isOwner || isAdmin
        ? `<button class="community-action delete-community" data-action="delete" data-id="${escapeHtml(community.id)}" type="button" aria-label="Supprimer la communauté">Supprimer</button>`
        : "";
      return `<article class="community-card"><span class="community-avatar">${escapeHtml(initials(community.name))}</span>
        <div class="community-info"><strong>${escapeHtml(community.name)}</strong><small>${escapeHtml(community.type === "PRIVATE" ? "Privée" : "Publique")} · ${escapeHtml(community.membersCount)} membre(s)</small></div>
        <div class="community-actions">${action}${deleteAction}</div></article>`;
    }).join("");
  }

  function userStatusLabel(status) {
    return ({ PENDING: "En attente", APPROVED: "Approuvé", REJECTED: "Refusé" })[status] || status || "Inconnu";
  }

  function renderUsers() {
    const query = $("#user-search").value.trim().toLocaleLowerCase("fr");
    const visibleUsers = state.users.filter((user) =>
      [user.fullName, user.email, user.departement, user.userType, user.status]
        .some((value) => String(value || "").toLocaleLowerCase("fr").includes(query)));
    const list = $("#user-list");
    if (!visibleUsers.length) {
      list.innerHTML = `<div class="empty-state">${state.users.length ? "Aucun utilisateur ne correspond à la recherche." : "Aucun utilisateur trouvé."}</div>`;
      return;
    }
    list.innerHTML = visibleUsers.map((user) => `
      <article class="directory-user">
        <span class="post-avatar">${escapeHtml(initials(user.fullName))}</span>
        <div class="directory-user-info"><strong>${escapeHtml(user.fullName || "Sans nom")}</strong><small>${escapeHtml(user.email || "")} · ${escapeHtml(user.departement || "Département non précisé")}</small></div>
        <span class="user-type">${escapeHtml(user.userType || "USER")}</span>
        <button class="button button-quiet user-details-button" type="button" data-id="${escapeHtml(user.id)}">Détails</button>
      </article>`).join("");
  }

  function renderPendingUsers(users) {
    $("#pending-count").textContent = users.length;
    const list = $("#pending-user-list");
    if (!users.length) {
      list.innerHTML = '<div class="empty-state small-empty">Aucune inscription en attente.</div>';
      return;
    }
    list.innerHTML = users.map((user) => `
      <article class="admin-user-card">
        <span class="post-avatar">${escapeHtml(initials(user.fullName))}</span>
        <div class="admin-user-info"><strong>${escapeHtml(user.fullName || "Sans nom")}</strong><small>${escapeHtml(user.email || "")} · ${escapeHtml(user.departement || "Département non précisé")}</small></div>
        <span class="user-type">${escapeHtml(user.userType || "USER")}</span>
        <div class="admin-actions">
          <button class="button button-approve" type="button" data-admin-action="approve" data-id="${escapeHtml(user.id)}">Approuver</button>
          <button class="button button-reject" type="button" data-admin-action="reject" data-id="${escapeHtml(user.id)}">Refuser</button>
        </div>
      </article>`).join("");
  }

  function renderAdminUsers(users) {
    $("#admin-user-count").textContent = users.length;
    const list = $("#admin-user-list");
    if (!users.length) {
      list.innerHTML = '<div class="empty-state small-empty">Aucun compte utilisateur.</div>';
      return;
    }
    list.innerHTML = users.map((user) => `
      <article class="admin-user-card">
        <span class="post-avatar">${escapeHtml(initials(user.fullName))}</span>
        <div class="admin-user-info"><strong>${escapeHtml(user.fullName || "Sans nom")}</strong><small>${escapeHtml(user.email || "")} · ${escapeHtml(user.departement || "Département non précisé")}</small></div>
        <span class="user-type">${escapeHtml(user.userType || "USER")}</span>
        <span class="status-badge status-${escapeHtml(String(user.status || "").toLowerCase())}">${escapeHtml(userStatusLabel(user.status))}</span>
        ${user.id === state.me?.id ? '<span class="current-user-label">Vous</span>' : `<button class="text-button admin-delete-user" type="button" data-admin-action="delete" data-id="${escapeHtml(user.id)}">Supprimer</button>`}
      </article>`).join("");
  }

  async function loadUsers() {
    try {
      state.users = await request("/api/users");
      renderUsers();
    } catch (error) {
      $("#user-list").innerHTML = `<div class="empty-state">${escapeHtml(error.message)}</div>`;
      toast(`Annuaire : ${error.message}`, true);
    }
  }

  async function loadAdminData() {
    const [pendingResult, usersResult] = await Promise.allSettled([
      request("/api/admin/pending-users"),
      request("/api/admin/users")
    ]);
    if (pendingResult.status === "fulfilled") {
      renderPendingUsers(pendingResult.value);
    } else {
      $("#pending-user-list").innerHTML = `<div class="empty-state small-empty">${escapeHtml(pendingResult.reason.message)}</div>`;
      toast(`Inscriptions en attente : ${pendingResult.reason.message}`, true);
    }
    if (usersResult.status === "fulfilled") {
      renderAdminUsers(usersResult.value);
    } else {
      $("#admin-user-list").innerHTML = `<div class="empty-state small-empty">${escapeHtml(usersResult.reason.message)}</div>`;
      toast(`Comptes administrateur : ${usersResult.reason.message}`, true);
    }
  }

  async function showUserDetails(id) {
    const detail = $("#user-detail");
    detail.classList.remove("hidden");
    detail.innerHTML = '<div class="empty-state small-empty">Chargement du profil…</div>';
    try {
      const user = await request(`/api/users/${encodeURIComponent(id)}`);
      detail.innerHTML = `<div class="detail-heading"><h3>Profil utilisateur</h3><button class="text-button close-user-detail" type="button">Fermer ×</button></div>
        <dl><div><dt>Nom</dt><dd>${escapeHtml(user.fullName || "—")}</dd></div>
        <div><dt>E-mail</dt><dd>${escapeHtml(user.email || "—")}</dd></div>
        <div><dt>Département</dt><dd>${escapeHtml(user.departement || "—")}</dd></div>
        <div><dt>Type</dt><dd>${escapeHtml(user.userType || "—")}</dd></div>
        <div><dt>Statut</dt><dd>${escapeHtml(userStatusLabel(user.status))}</dd></div>
        <div><dt>ID</dt><dd>${escapeHtml(user.id)}</dd></div></dl>`;
    } catch (error) {
      detail.innerHTML = `<div class="empty-state small-empty">${escapeHtml(error.message)}</div>`;
      toast(`Profil utilisateur : ${error.message}`, true);
    }
  }

  async function testApi() {
    setApiStatus("", "Test en cours…");
    try {
      await request("/v3/api-docs");
      setApiStatus("online", "API connectée");
      toast("Le backend répond. OpenAPI est accessible.");
    } catch (error) {
      toast(error.message, true);
    }
  }

  $$(".auth-tab").forEach((tab) => tab.addEventListener("click", () => {
    const register = tab.dataset.mode === "register";
    $$(".auth-tab").forEach((item) => {
      const selected = item === tab;
      item.classList.toggle("selected", selected);
      item.setAttribute("aria-selected", String(selected));
    });
    $("#login-form").classList.toggle("hidden", register);
    $("#register-form").classList.toggle("hidden", !register);
  }));

  $("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = $("button[type=submit]", event.currentTarget);
    button.disabled = true;
    try {
      const auth = await request("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: $("#login-email").value, password: $("#login-password").value })
      });
      state.token = auth.token;
      localStorage.setItem("iset.jwt", state.token);
      const user = await request("/api/auth/me");
      setSignedIn(user);
      toast("Connexion réussie. Votre fil est prêt.");
      await refreshData();
      if (user.userType === "ADMIN") await loadAdminData();
    } catch (error) {
      toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  $("#register-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = $("button[type=submit]", event.currentTarget);
    button.disabled = true;
    try {
      const result = await request("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          fullName: $("#register-name").value,
          departement: $("#register-department").value,
          email: $("#register-email").value,
          password: $("#register-password").value,
          userType: $("#register-type").value
        })
      });
      showResponse("POST /api/auth/register · compte créé", result, 201);
      toast("Compte créé. Connectez-vous après son approbation.");
      $(".auth-tab[data-mode=login]").click();
    } catch (error) {
      toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  $("#post-file").addEventListener("change", (event) => {
    const file = event.target.files[0];
    $("#file-name").textContent = file ? `Fichier sélectionné : ${file.name}` : "";
    $("#file-name").classList.toggle("hidden", !file);
  });

  $("#post-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = $("button[type=submit]", event.currentTarget);
    button.disabled = true;
    const formData = new FormData();
    formData.set("title", $("#post-title").value);
    formData.set("content", $("#post-content").value);
    if ($("#post-community").value) formData.set("communityId", $("#post-community").value);
    if ($("#post-file").files[0]) formData.set("file", $("#post-file").files[0]);
    try {
      await request("/api/publications", { method: "POST", body: formData });
      event.currentTarget.reset();
      $("#file-name").classList.add("hidden");
      toast("Publication créée.");
      await refreshData();
    } catch (error) {
      toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  $("#feed-list").addEventListener("submit", async (event) => {
    if (event.target.matches(".post-edit-form")) {
      event.preventDefault();
      const form = event.target;
      const formData = new FormData(form);
      const button = $("button[type=submit]", form);
      button.disabled = true;
      try {
        await request(`/api/publications/${encodeURIComponent(form.dataset.id)}`, {
          method: "PUT",
          body: JSON.stringify({ title: formData.get("title"), content: formData.get("content") })
        });
        toast("Publication modifiée.");
        await refreshData();
      } catch (error) {
        toast(error.message, true);
      } finally {
        button.disabled = false;
      }
      return;
    }
    if (!event.target.matches(".comment-form")) return;
    event.preventDefault();
    const publicationId = event.target.dataset.id;
    const contenu = new FormData(event.target).get("contenu");
    const button = $("button", event.target);
    button.disabled = true;
    try {
      await request(`/api/publications/${encodeURIComponent(publicationId)}/comments`, {
        method: "POST",
        body: JSON.stringify({ contenu })
      });
      toast("Commentaire ajouté.");
      await refreshData();
    } catch (error) {
      toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  $("#feed-list").addEventListener("click", async (event) => {
    const downloadLink = event.target.closest("[data-file-id]");
    if (downloadLink) {
      event.preventDefault();
      try {
        const response = await fetch(`${state.api}/api/publications/${encodeURIComponent(downloadLink.dataset.fileId)}/file`, {
          headers: { Authorization: `Bearer ${state.token}` }
        });
        if (!response.ok) {
          const text = await response.text();
          showResponse(`GET téléchargement · ${response.status}`, text, response.status);
          throw new Error(text || `Le téléchargement a échoué (${response.status})`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = downloadLink.textContent.replace(/^↧\s*/, "");
        anchor.click();
        URL.revokeObjectURL(url);
        showResponse(`GET téléchargement · ${response.status}`, { file: anchor.download, size: blob.size }, response.status);
      } catch (error) {
        toast(error.message, true);
      }
      return;
    }
    const deletePost = event.target.closest(".delete-post");
    const deleteComment = event.target.closest(".delete-comment");
    const cancelEdit = event.target.closest(".edit-cancel");
    const editToggle = event.target.closest(".edit-post-toggle");
    if (cancelEdit) {
      cancelEdit.closest(".post-edit").open = false;
      return;
    }
    if (editToggle) {
      const edit = editToggle.closest(".feed-card").querySelector(".post-edit");
      edit.open = !edit.open;
      if (edit.open) $("input", edit).focus();
      return;
    }
    const target = deletePost || deleteComment;
    if (!target || !window.confirm(deletePost ? "Supprimer cette publication ?" : "Supprimer ce commentaire ?")) return;
    try {
      await request(deletePost
        ? `/api/publications/${encodeURIComponent(target.dataset.id)}`
        : `/api/comments/${encodeURIComponent(target.dataset.id)}`, { method: "DELETE" });
      toast(deletePost ? "Publication supprimée." : "Commentaire supprimé.");
      await refreshData();
    } catch (error) {
      toast(error.message, true);
    }
  });

  $("#community-type").addEventListener("change", (event) => {
    const privateMode = event.target.value === "PRIVATE";
    $$(".private-fields").forEach((field) => field.classList.toggle("hidden", !privateMode));
    $("#community-key").required = privateMode;
    $("#community-max").required = privateMode;
  });

  $("#community-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = $("button[type=submit]", event.currentTarget);
    button.disabled = true;
    const type = $("#community-type").value;
    const body = { name: $("#community-name").value, type };
    if (type === "PRIVATE") {
      body.key = $("#community-key").value;
      body.nbMax = Number($("#community-max").value);
    }
    try {
      await request("/api/communities", { method: "POST", body: JSON.stringify(body) });
      event.currentTarget.reset();
      $("#community-type").dispatchEvent(new Event("change"));
      toast("Communauté créée.");
      await refreshData();
    } catch (error) {
      toast(error.message, true);
    } finally {
      button.disabled = false;
    }
  });

  $("#community-list").addEventListener("click", async (event) => {
    const button = event.target.closest(".community-action");
    if (!button || button.disabled) return;
    const id = encodeURIComponent(button.dataset.id);
    try {
      if (button.dataset.action === "join") {
        let body;
        if (button.dataset.private === "true") {
          const key = window.prompt("Saisissez la clé d’accès de cette communauté :");
          if (key === null) return;
          body = JSON.stringify({ key });
        }
        await request(`/api/communities/${id}/join`, { method: "POST", ...(body ? { body } : {}) });
        toast("Vous avez rejoint la communauté.");
      } else if (button.dataset.action === "leave") {
        await request(`/api/communities/${id}/leave`, { method: "POST" });
        toast("Vous avez quitté la communauté.");
      } else if (button.dataset.action === "delete") {
        if (!window.confirm("Supprimer cette communauté et ses publications ?")) return;
        await request(`/api/communities/${id}`, { method: "DELETE" });
        toast("Communauté supprimée.");
      }
      await refreshData();
    } catch (error) {
      toast(error.message, true);
    }
  });

  $("#user-search").addEventListener("input", renderUsers);
  $("#refresh-users").addEventListener("click", loadUsers);
  $("#user-list").addEventListener("click", (event) => {
    const button = event.target.closest(".user-details-button");
    if (button) showUserDetails(button.dataset.id);
  });
  $("#user-detail").addEventListener("click", (event) => {
    if (event.target.closest(".close-user-detail")) $("#user-detail").classList.add("hidden");
  });
  $("#refresh-admin").addEventListener("click", loadAdminData);
  ["#pending-user-list", "#admin-user-list"].forEach((selector) => {
    $(selector).addEventListener("click", async (event) => {
      const button = event.target.closest("[data-admin-action]");
      if (!button) return;
      const action = button.dataset.adminAction;
      const id = encodeURIComponent(button.dataset.id);
      if (action === "delete" && !window.confirm("Supprimer ce compte et ses publications/commentaires ?")) return;
      button.disabled = true;
      try {
        if (action === "delete") {
          await request(`/api/admin/users/${id}`, { method: "DELETE" });
          toast("Compte supprimé.");
        } else {
          await request(`/api/admin/users/${id}/${action}`, { method: "PUT" });
          toast(action === "approve" ? "Inscription approuvée." : "Inscription refusée.");
        }
        await loadAdminData();
        if (action === "delete") await loadUsers();
      } catch (error) {
        toast(error.message, true);
      } finally {
        button.disabled = false;
      }
    });
  });

  $("#refresh-feed").addEventListener("click", async () => {
    try { await refreshData(); toast("Fil actualisé."); }
    catch (error) { toast(error.message, true); }
  });
  $("#logout-button").addEventListener("click", signOut);
  $("#check-api").addEventListener("click", testApi);
  $("#response-toggle").addEventListener("click", (event) => {
    const drawer = $("#response-drawer");
    const open = drawer.classList.toggle("open");
    event.currentTarget.setAttribute("aria-expanded", String(open));
  });
  $$(".nav-link").forEach((link) => link.addEventListener("click", () => {
    $$(".nav-link").forEach((item) => item.classList.toggle("active", item === link));
    const target = link.hash ? $(link.hash) : null;
    if (target) target.classList.remove("hidden");
    const labels = {
      "#communities": "Communautés",
      "#users": "Utilisateurs",
      "#admin": "Administration"
    };
    $("#crumb").textContent = labels[link.hash] || "Fil d’actualité";
    if (link.hash === "#users") loadUsers();
    if (link.hash === "#admin") loadAdminData();
  }));

  async function initialize() {
    setApiStatus("", "API non testée");
    try {
      await request("/v3/api-docs");
    } catch {
      // The visible response panel and API indicator already show the connection failure.
    }
    if (!state.token) return;
    try {
      const user = await request("/api/auth/me");
      setSignedIn(user);
      await refreshData();
      if (user.userType === "ADMIN") await loadAdminData();
    } catch {
      signOut();
    }
  }

  initialize();
})();
