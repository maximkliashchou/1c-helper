let currentUser = null;

let dragIndex = null;

let editingTopicId = null;
let globalSearchQuery = '';

let createTaskState = {
  tests: []
};

let topicBlocks = [
  { id: Date.now(), type: 'text', content: '' }
];

function showView(id) {
  document.querySelectorAll('[id^="view-"]').forEach(el => el.classList.add('hidden'));
  const v = document.getElementById('view-' + id);
  if (v) v.classList.remove('hidden');
}

function setNav() {
  const guest = document.getElementById('nav-guest');
  const user = document.getElementById('nav-user');
  const admin = document.getElementById('nav-admin');
  const logout = document.getElementById('logout-btn');
  const topAdmin = document.getElementById('top-admin-link');
  const userLabel = document.getElementById('current-user-label');
  const userChip = document.querySelector('.user-chip');
  const isAdmin = currentUser && currentUser.roles && currentUser.roles.includes('ADMIN');

  if (currentUser) {
    guest.classList.add('hidden');
    user.classList.remove('hidden');
    logout?.classList.remove('hidden');
    admin.classList.toggle('hidden', !isAdmin);
    topAdmin?.classList.toggle('hidden', !isAdmin);
    if (userLabel) userLabel.textContent = currentUser.username || 'Профиль';
    if (userChip) userChip.href = '#/profile';
  } else {
    guest.classList.remove('hidden');
    user.classList.add('hidden');
    admin.classList.add('hidden');
    logout?.classList.add('hidden');
    topAdmin?.classList.add('hidden');
    if (userLabel) userLabel.textContent = 'Гость';
    if (userChip) userChip.href = '#/login';
  }
  setActiveNav();
}

async function loadUser() {
  if (!getToken()) {
    currentUser = null;
    setNav();
    return;
  }
  try {
    currentUser = await apiClient.profile.me();
    setNav();
  } catch (_) {
    currentUser = null;
    setToken(null);
    setNav();
  }
}

window.addEventListener('auth:logout', () => {
  currentUser = null;
  setNav();
});

document.getElementById('logout-btn')?.addEventListener('click', (e) => {
  e.preventDefault();
  setToken(null);
  currentUser = null;
  setNav();
  location.hash = '#/';
});

document.getElementById('global-search-input')?.addEventListener('input', (e) => {
  globalSearchQuery = e.target.value;
  const { path } = parseHash();
  if (path !== 'main') {
    location.hash = '#/';
    return;
  }
  const localSearch = document.getElementById('search-input');
  if (localSearch) {
    localSearch.value = globalSearchQuery;
    localSearch.dispatchEvent(new Event('input'));
  }
});

function escapeHtml(s) {
  if (s == null) return '';
  const div = document.createElement('div');
  div.textContent = s;
  return div.innerHTML;
}

function parseHash() {
  const hash = (location.hash || '#/').slice(1);
  const parts = hash.split('/').filter(Boolean);
  return { path: parts[0] || 'main', id: parts[1], id2: parts[2] };
}

function navPathForRoute(path) {
  if (path === 'edit-topic' || path === 'create-topic' || path === 'create-task') return 'admin';
  if (path === 'verify-email') return 'register';
  return path || 'main';
}

function setActiveNav() {
  const { path } = parseHash();
  const activePath = navPathForRoute(path);
  document.querySelectorAll('[data-nav-path]').forEach(link => {
    link.classList.toggle('active', link.dataset.navPath === activePath);
  });
}

async function render() {
  const { path, id, id2 } = parseHash();
  setActiveNav();
  document.getElementById('view-loading').classList.remove('hidden');
  document.querySelectorAll('[id^="view-"]').forEach(el => { if (el.id !== 'view-loading') el.classList.add('hidden'); });

  try {
    if (path === 'main') {
      await renderMain();
    } else if (path === 'topic' && id) {
      await renderTopic(id);
    } else if (path === 'tasks' && id) {
      await renderTasks(id);
    } else if (path === 'task' && id) {
      await renderTask(id);
    } else if (path === 'login') {
      renderLogin();
    } else if (path === 'register') {
      renderRegister();
    } else if (path === 'verify-email') {
      renderEmailVerification();
    } else if (path === 'profile') {
      await renderProfile(id || (currentUser && currentUser.username));
    } else if (path === 'admin') {
      await renderAdmin();
    } else if (path === 'create-topic') {
      renderCreateTopic();
    } else if (path === 'edit-topic' && id) {
      await renderCreateTopic(id);
    } else if (path === 'create-task' && id) {
      renderCreateTask(id);
    } else {
      await renderMain();
    }
  } catch (err) {
    document.getElementById('view-main').innerHTML = '<p class="error-msg">Ошибка: ' + escapeHtml(err.message) + '</p>';
    document.getElementById('view-main').classList.remove('hidden');
  }
  document.getElementById('view-loading').classList.add('hidden');
}

async function renderMain() {
  const topics = await apiClient.topics.list();

  const html = `
    <div class="page-heading">
      <div>
        <h1 class="page-title">Темы</h1>
        <p class="page-subtitle">Выберите тему для изучения и решения задач</p>
      </div>
    </div>
    <label class="search-panel" for="search-input">
      <span class="search-icon">⌕</span>
      <input type="text" class="search-box" id="search-input" placeholder="Поиск по темам...">
      <button type="button" class="search-clear" id="search-clear" aria-label="Очистить поиск">×</button>
    </label>
    <p class="results-count" id="topics-count"></p>
    <ul class="topic-list" id="topics-list"></ul>
  `;

  document.getElementById('view-main').innerHTML = html;
  document.getElementById('view-main').classList.remove('hidden');

  const input = document.getElementById('search-input');
  const list = document.getElementById('topics-list');
  const count = document.getElementById('topics-count');
  const clearBtn = document.getElementById('search-clear');
  const globalSearch = document.getElementById('global-search-input');
  const iconClasses = ['icon-blue', 'icon-green', 'icon-purple', 'icon-orange'];
  const icons = ['▯', '▱', '▤', '☆'];

  function renderList(filtered) {
    count.textContent = `Найдено тем: ${filtered.length}`;
    clearBtn.classList.toggle('hidden', !input.value.trim());
    list.innerHTML = filtered.length
        ? filtered.map((t, i) => `
      <li>
        <a href="#/topic/${t.id}" class="topic-card">

          <div class="topic-icon ${iconClasses[i % iconClasses.length]}">${icons[i % icons.length]}</div>

          <div class="topic-info">
            <h3>${escapeHtml(t.title)}</h3>
            <p>${escapeHtml(t.description || '')}</p>

            <div class="topic-meta">
              ${t.hasTasks ? `<span class="badge">Есть задачи</span>` : ''}
              <span class="badge badge-muted">ID: ${escapeHtml(t.id)}</span>
            </div>
          </div>

          <div class="topic-arrow">›</div>

        </a>
      </li>
    `).join('')
        : `
      <li>
        <div class="empty-search-card">
          <div class="empty-illustration">⌕</div>
          <div>
            <h3>Не нашли нужную тему?</h3>
            <p>Попробуйте изменить поисковый запрос</p>
          </div>
        </div>
      </li>
    `;
  }

  function applySearch(value) {
    const q = value.toLowerCase().trim();
    const filtered = topics.filter(t =>
        t.title.toLowerCase().includes(q) ||
        (t.description || '').toLowerCase().includes(q)
    );
    renderList(filtered);
  }

  input.value = globalSearchQuery;
  if (globalSearch) globalSearch.value = globalSearchQuery;
  applySearch(globalSearchQuery);

  input.addEventListener('input', (e) => {
    globalSearchQuery = e.target.value;
    if (globalSearch) globalSearch.value = globalSearchQuery;
    applySearch(globalSearchQuery);
  });

  clearBtn.addEventListener('click', () => {
    globalSearchQuery = '';
    input.value = '';
    if (globalSearch) globalSearch.value = '';
    applySearch('');
    input.focus();
  });
}

async function renderTopic(topicId) {
  const topic = await apiClient.topics.get(topicId);
  const tasks = await apiClient.tasks.byTopic(topicId);
  const content = (topic.content || '')
    .replace(/<pre>/g, '<pre class="code-block">')
    .replace(/<code>/g, '<code>');
  // Allow safe HTML from admin
  const html = `
    <a href="#/" class="back-link">← Назад к темам</a>
    <div class="topic-content">
      <h1>${escapeHtml(topic.title)}</h1>
      ${topic.imagePath ? `<img src="${escapeHtml(topic.imagePath)}" alt="">` : ''}
      <div>${content || '<p>Нет описания.</p>'}</div>
      ${tasks.length ? `
        <p style="margin-top:1.5rem;">
          <a href="#/tasks/${topicId}" class="btn btn-primary">Перейти к задачам (${tasks.length})</a>
        </p>
        <p class="meta">Доступ к задачам — только для авторизованных пользователей.</p>
      ` : ''}
    </div>
  `;
  document.getElementById('view-topic').innerHTML = html;
  showView('topic');
}

async function renderTasks(topicId) {
  let topicTitle = 'Задачи';
  try {
    const topic = await apiClient.topics.get(topicId);
    topicTitle = topic.title;
  } catch (_) {}
  const tasks = await apiClient.tasks.byTopic(topicId);
  const html = `
    <a href="#/topic/${topicId}" class="back-link">← Назад к теме</a>
    <h1 class="page-title">${escapeHtml(topicTitle)} — Задачи</h1>
    ${!currentUser ? `
      <div class="required-login">
        <p>Чтобы решать задачи, войдите или зарегистрируйтесь.</p>
        <a href="#/login" class="btn btn-primary">Вход</a>
        <a href="#/register" class="btn btn-secondary">Регистрация</a>
      </div>
    ` : `
      <ul class="task-list">
        ${tasks.length ? tasks.map(t => `
          <li><a href="#/task/${t.id}">${escapeHtml(t.title)}</a></li>
        `).join('') : '<li class="empty-state">Задач пока нет</li>'}
      </ul>
    `}
  `;
  document.getElementById('view-tasks').innerHTML = html;
  showView('tasks');
}

async function renderTask(taskId) {
  const task = await apiClient.tasks.get(taskId);
  let testsPreviewHtml = '';
  let topicTitle = '';
  try {
    const topic = await apiClient.topics.get(task.topicId);
    topicTitle = topic.title;
  } catch (_) {}
  let attemptsHtml = '';
  let taskAlreadyPassed = false;
  if (currentUser) {
    try {
      const attempts = await apiClient.attempts.myForTask(taskId);
      taskAlreadyPassed = attempts.some(a => a.passed);
      attemptsHtml = attempts.length ? `
        <h2 style="margin-top:1.5rem; font-size:1.1rem;">Мои попытки (${attempts.length})</h2>
        <table class="attempts-table">
          <thead><tr><th>Дата</th><th>Результат</th><th>Тесты</th><th></th></tr></thead>
          <tbody>
            ${attempts.map(a => `
              <tr>
                <td>${new Date(a.createdAt).toLocaleString('ru')}</td>
                <td><span class="badge ${a.passed ? 'badge-success' : 'badge-fail'}">${a.passed ? 'Зачёт' : 'Не зачёт'}</span></td>
                <td>${a.passedTests}/${a.totalTests}</td>
                <td><a href="#" data-attempt-id="${a.id}" class="view-attempt">Код</a></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      ` : '';
    } catch (_) {}

    try {
      const tests = await apiClient.tasks.getTests(taskId);

      if (tests.length) {
        const first = tests[0];
        testsPreviewHtml = `
      <div class="block" style="margin-top:2rem;">
        <h3>Пример теста</h3>

        <p><strong>Ввод:</strong></p>
        <pre class="code-block">${escapeHtml(first.input)}</pre>

        <p><strong>Вывод:</strong></p>
        <pre class="code-block">${escapeHtml(first.expectedOutput)}</pre>

        <p class="meta">Всего тестов: ${tests.length}</p>
      </div>
    `;
      }
    } catch (_) {}
  }
  const html = `
    <a href="#/tasks/${task.topicId}" class="back-link">← ${escapeHtml(topicTitle)} — Задачи</a>
    <h1 class="page-title">${escapeHtml(task.title)}</h1>
    <div class="task-condition">${escapeHtml(task.condition)}</div>
    ${testsPreviewHtml}
    ${!currentUser ? `
      <div class="required-login">
        <p>Чтобы отправить решение, войдите или зарегистрируйтесь.</p>
        <a href="#/login" class="btn btn-primary">Вход</a>
      </div>
    ` : taskAlreadyPassed ? `
      <div class="result-box success" style="margin-bottom:1rem;">
        ✓ Задача уже сдана. Повторная отправка недоступна.
      </div>
      ${attemptsHtml}
    ` : `
      <div class="editor-wrap">
        <label>Ваш код (1С или текст решения)</label>
        <textarea id="code-editor" class="code-editor" placeholder="Вставьте код или загрузите файл с кодом..."></textarea>
        <p class="meta" style="margin-top:0.5rem;">
          <label class="btn btn-secondary" style="cursor:pointer;margin:0;">Выбрать файл с кодом
            <input type="file" id="code-file-input" accept=".bsl,.txt,.1c,.xml" style="display:none">
          </label>
        </p>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-primary" id="submit-task-btn">Отправить на проверку</button>
      </div>
      <div id="submit-result"></div>
      ${attemptsHtml}
    `}
  `;
  document.getElementById('view-task').innerHTML = html;
  showView('task');
  if (currentUser) {
    document.getElementById('code-file-input')?.addEventListener('change', (e) => {
      const f = e.target.files[0];
      if (!f) return;
      const r = new FileReader();
      r.onload = () => { document.getElementById('code-editor').value = r.result; };
      r.readAsText(f, 'UTF-8');
      e.target.value = '';
    });
    document.getElementById('submit-task-btn')?.addEventListener('click', async () => {
      const code = document.getElementById('code-editor').value.trim();
      const resultEl = document.getElementById('submit-result');
      if (!code) {
        resultEl.innerHTML = '<div class="result-box error">Введите код.</div>';
        return;
      }
      resultEl.innerHTML = '<div class="loading">Проверка...</div>';
      try {
        const result = await apiClient.tasks.submit(taskId, code);
        resultEl.innerHTML = `
          <div class="result-box ${result.passed ? 'success' : 'error'}">
            ${result.passed ? '✓ Все тесты пройдены.' : '✗ ' + (result.message || 'Тесты не пройдены')}
            (${result.passedTests}/${result.totalTests})
          </div>
        `;
        renderTask(taskId);
      } catch (e) {
        resultEl.innerHTML = '<div class="result-box error">' + escapeHtml(e.message) + '</div>';
      }
    });
    document.getElementById('view-task').querySelectorAll('.view-attempt').forEach(link => {
      link.addEventListener('click', async (e) => {
        e.preventDefault();
        const attemptId = link.dataset.attemptId;
        try {
          const a = await apiClient.attempts.get(attemptId);
          const win = window.open('', '_blank', 'width=700,height=500');
          win.document.write('<pre style="padding:1rem;font-family:monospace;white-space:pre-wrap;">' + escapeHtml(a.code) + '</pre>');
        } catch (_) {}
      });
    });
  }
}

function renderLogin() {
  document.getElementById('view-login').innerHTML = `
    <div class="auth-card">
      <h1>Вход</h1>
      <form id="login-form">
        <div class="form-group">
          <label>Имя пользователя</label>
          <input type="text" name="username" required autocomplete="username">
        </div>
        <div class="form-group">
          <label>Пароль</label>
          <input type="password" name="password" required autocomplete="current-password">
        </div>
        <div id="login-error" class="error-msg hidden"></div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary">Войти</button>
          <a href="#/register" class="btn btn-secondary">Регистрация</a>
        </div>
      </form>
    </div>
  `;
  showView('login');
  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errEl = document.getElementById('login-error');
    const fd = new FormData(e.target);
    try {
      const res = await apiClient.auth.login(fd.get('username'), fd.get('password'));
      setToken(res.token);
      await loadUser();
      errEl.classList.add('hidden');
      location.hash = '#/';
    } catch (err) {
      errEl.textContent = err.message || 'Ошибка входа';
      errEl.classList.remove('hidden');
      if (err.status === 403) {
        errEl.innerHTML = escapeHtml(err.message || 'Подтвердите email перед входом') +
            ' <a href="#/verify-email">Ввести код</a>';
      }
    }
  });
}

function renderRegister() {
  document.getElementById('view-register').innerHTML = `
    <div class="auth-card">
      <h1>Регистрация</h1>
      <form id="register-form">
        <div class="form-group">
          <label>Имя пользователя</label>
          <input type="text" name="username" required minlength="2" autocomplete="username">
        </div>
        <div class="form-group">
          <label>Email</label>
          <input type="email" name="email" required autocomplete="email">
        </div>
        <div class="form-group">
          <label>Пароль</label>
          <input type="password" name="password" required minlength="6" autocomplete="new-password">
        </div>
        <div id="register-error" class="error-msg hidden"></div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary">Зарегистрироваться</button>
          <a href="#/login" class="btn btn-secondary">Вход</a>
        </div>
      </form>
    </div>
  `;
  showView('register');
  document.getElementById('register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errEl = document.getElementById('register-error');
    const fd = new FormData(e.target);
    try {
      const res = await apiClient.auth.register({
        username: fd.get('username'),
        email: fd.get('email'),
        password: fd.get('password')
      });
      errEl.classList.add('hidden');
      renderEmailVerification(res.username || fd.get('username'), res.email || fd.get('email'));
    } catch (err) {
      errEl.textContent = err.message || 'Ошибка регистрации';
      errEl.classList.remove('hidden');
    }
  });
}

function renderEmailVerification(usernameOrEmail = '', email = '') {
  const identifier = usernameOrEmail || email || '';
  document.getElementById('view-register').innerHTML = `
    <div class="auth-card">
      <h1>Подтверждение email</h1>
      <p class="empty-state">
        Мы отправили 6-значный код${email ? ' на ' + escapeHtml(email) : ' на вашу почту'}.
        Введите его, чтобы завершить регистрацию.
      </p>
      <form id="verify-email-form">
        <div class="form-group">
          <label>Имя пользователя или email</label>
          <input type="text" name="usernameOrEmail" required autocomplete="username">
        </div>
        <div class="form-group">
          <label>Код подтверждения</label>
          <input type="text" name="code" required minlength="6" maxlength="6" pattern="\\d{6}" inputmode="numeric" autocomplete="one-time-code">
        </div>
        <div id="verify-email-message" class="empty-state hidden"></div>
        <div id="verify-email-error" class="error-msg hidden"></div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary">Подтвердить</button>
          <button type="button" id="resend-code-btn" class="btn btn-secondary">Отправить ещё раз</button>
        </div>
      </form>
    </div>
  `;
  showView('register');

  const form = document.getElementById('verify-email-form');
  form.elements.usernameOrEmail.value = identifier;
  const errEl = document.getElementById('verify-email-error');
  const msgEl = document.getElementById('verify-email-message');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(form);
    try {
      const res = await apiClient.auth.verifyEmail(fd.get('usernameOrEmail'), fd.get('code'));
      setToken(res.token);
      await loadUser();
      errEl.classList.add('hidden');
      location.hash = '#/';
    } catch (err) {
      errEl.textContent = err.message || 'Ошибка подтверждения email';
      errEl.classList.remove('hidden');
      msgEl.classList.add('hidden');
    }
  });

  document.getElementById('resend-code-btn').addEventListener('click', async () => {
    const fd = new FormData(form);
    try {
      const res = await apiClient.auth.resendVerificationCode(fd.get('usernameOrEmail'));
      msgEl.textContent = res.message || 'Код отправлен повторно';
      msgEl.classList.remove('hidden');
      errEl.classList.add('hidden');
    } catch (err) {
      errEl.textContent = err.message || 'Не удалось отправить код';
      errEl.classList.remove('hidden');
      msgEl.classList.add('hidden');
    }
  });
}

async function renderProfile(username) {
  if (!username) {
    document.getElementById('view-profile').innerHTML = '<p class="empty-state">Укажите пользователя или войдите.</p>';
    showView('profile');
    return;
  }
  try {
    const user = await apiClient.profile.byUsername(username);
    let attemptsHtml = '';
    if (currentUser && (currentUser.username === username || (currentUser.roles && currentUser.roles.includes('ADMIN')))) {
      try {
        const attempts = await apiClient.attempts.my();
        attemptsHtml = attempts.length ? `
          <h2 style="margin-top:1.5rem; font-size:1.1rem;">Мои попытки</h2>
          <table class="attempts-table">
            <thead><tr><th>Задача</th><th>Тема</th><th>Результат</th><th>Тесты</th><th>Дата</th><th></th></tr></thead>
            <tbody>
              ${attempts.map(a => `
                <tr>
                  <td><a href="#/task/${a.taskId}">${escapeHtml(a.taskTitle)}</a></td>
                  <td><a href="#/topic/${a.topicId}">${escapeHtml(a.topicTitle)}</a></td>
                  <td><span class="badge ${a.passed ? 'badge-success' : 'badge-fail'}">${a.passed ? 'Зачёт' : 'Нет'}</span></td>
                  <td>${a.passedTests}/${a.totalTests}</td>
                  <td>${new Date(a.createdAt).toLocaleString('ru')}</td>
                  <td><a href="#" data-attempt-id="${a.id}" class="view-attempt-link">Код</a></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        ` : '<p class="empty-state">Попыток пока нет</p>';
      } catch (_) {
        attemptsHtml = '<p class="empty-state">Не удалось загрузить попытки</p>';
      }
    }
    const isMe = currentUser && currentUser.username === username;
    const html = `
      <h1 class="page-title">Профиль</h1>
      <div class="profile-header">
        ${user.avatarPath ? `<img src="${escapeHtml(user.avatarPath)}" alt="" class="avatar">` : '<div class="avatar" style="background:var(--bg-input);"></div>'}
        <div>
          <strong>${escapeHtml(user.username)}</strong>
          <p class="meta">${escapeHtml(user.email)}</p>
          ${isMe ? `
            <p style="margin-top:0.5rem;">
              <label class="btn btn-secondary" style="cursor:pointer;">Изменить аватар
                <input type="file" accept="image/*" id="avatar-input" class="hidden">
              </label>
            </p>
            <p><a href="#" id="edit-profile-btn">Изменить почту / пароль</a></p>
          ` : ''}
        </div>
      </div>
      ${attemptsHtml}
    `;
    document.getElementById('view-profile').innerHTML = html;
    showView('profile');
    if (isMe) {
      document.getElementById('avatar-input')?.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        try {
          const res = await apiClient.profile.uploadAvatar(file);
          await loadUser();
          renderProfile(username);
        } catch (err) {
          alert(err.message);
        }
      });
      document.getElementById('edit-profile-btn')?.addEventListener('click', (e) => {
        e.preventDefault();
        const newEmail = prompt('Новый email (оставьте пустым, чтобы не менять):', user.email);
        if (newEmail === null) return;
        const newPassword = prompt('Новый пароль (оставьте пустым, чтобы не менять):');
        if (newPassword === null) return;
        apiClient.profile.update({ email: newEmail || undefined, newPassword: newPassword || undefined })
          .then(() => renderProfile(username))
          .catch(err => alert(err.message));
      });
    }
    document.getElementById('view-profile').querySelectorAll('.view-attempt-link').forEach(link => {
      link.addEventListener('click', async (e) => {
        e.preventDefault();
        try {
          const a = await apiClient.attempts.get(link.dataset.attemptId);
          const w = window.open('', '_blank', 'width=700,height=500');
          w.document.write('<pre style="padding:1rem;font-family:monospace;white-space:pre-wrap;">' + escapeHtml(a.code) + '</pre>');
        } catch (_) {}
      });
    });
  } catch (err) {
    document.getElementById('view-profile').innerHTML = '<p class="error-msg">Пользователь не найден</p>';
    showView('profile');
  }
}

async function renderAdmin() {
  if (!currentUser || !currentUser.roles || !currentUser.roles.includes('ADMIN')) {
    document.getElementById('view-admin').innerHTML = '<p class="error-msg">Доступ только для администратора</p>';
    showView('admin');
    return;
  }
  const topics = await apiClient.topics.list();
  const html = `
    <h1 class="page-title">Админ-панель</h1>
    <div class="admin-section">
      <h2>Темы</h2>
      <ul class="topic-list">
        ${topics.map(t => `
          <li>
            <a href="#/topic/${t.id}" class="topic-card">${escapeHtml(t.title)}</a>
            <a href="#/edit-topic/${t.id}" class="btn btn-secondary">Изменить</a>
            <a href="#/create-task/${t.id}" class="btn btn-secondary">+ Задача</a>
          </li>
        `).join('')}
      </ul>
      <a href="#/create-topic" class="btn btn-primary">+ Добавить тему</a>
    </div>
  `;
  document.getElementById('view-admin').innerHTML = html;
  showView('admin');
  document.getElementById('view-admin').querySelectorAll('.edit-topic-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.id;
        const title = prompt('Новое название темы:');
        if (!title) return;
        const description = prompt('Описание:') || '';
        const content = prompt('Содержимое:') || '';
        try {
          await apiClient.admin.updateTopic(id, { title, description, content });
          alert('Тема обновлена');
          renderAdmin();
        } catch (e) {
          alert(e.message);
        }
      });
    });
}
function updateBlock(id, value) {
  const block = topicBlocks.find(b => b.id === id);
  if (block) {
    block.content = value;
  }
}

function deleteBlock(id) {
  topicBlocks = topicBlocks.filter(b => b.id !== id);
  renderBlocks();
}

function renderBlocks() {
  const container = document.getElementById('blocks-container');
  container.innerHTML = '';

  topicBlocks.forEach((block, index) => {
    const el = document.createElement('div');
    el.className = 'block';
    el.draggable = true;

    // DRAG START
    el.addEventListener('dragstart', () => {
      dragIndex = index;
      el.classList.add('dragging');
    });

    // DRAG END
    el.addEventListener('dragend', () => {
      el.classList.remove('dragging');
    });

    // DRAG OVER
    el.addEventListener('dragover', (e) => {
      e.preventDefault(); // обязательно!
      el.classList.add('drag-over');
    });

    el.addEventListener('dragleave', () => {
      el.classList.remove('drag-over');
    });

    // DROP
    el.addEventListener('drop', () => {
      el.classList.remove('drag-over');

      if (dragIndex === null || dragIndex === index) return;

      const moved = topicBlocks.splice(dragIndex, 1)[0];
      topicBlocks.splice(index, 0, moved);

      dragIndex = null;
      renderBlocks();
    });

    // --- HEADER ---
    const header = document.createElement('div');
    header.className = 'block-header';

    const badge = document.createElement('span');
    badge.className = 'badge';
    badge.textContent = block.type;

    header.appendChild(badge);

    // --- CONTENT ---
    let input;

    if (block.type === 'text' || block.type === 'code') {
      input = document.createElement('textarea');
      input.className = 'code-editor';
      input.value = block.content;

      input.addEventListener('input', (e) => {
        updateBlock(block.id, e.target.value);
      });
    }

    if (block.type === 'image') {
      const wrapper = document.createElement('div');

      const urlInput = document.createElement('input');
      urlInput.type = 'text';
      urlInput.placeholder = 'https://...';
      urlInput.value = block.content;

      urlInput.addEventListener('input', (e) => {
        updateBlock(block.id, e.target.value);
        renderBlocks();
      });

      wrapper.appendChild(urlInput);

      if (block.content) {
        const img = document.createElement('img');
        img.src = block.content;
        img.style.maxWidth = '100%';
        img.style.marginTop = '10px';
        wrapper.appendChild(img);
      }

      input = wrapper;
    }

    // --- CONTROLS ---
    const controls = document.createElement('div');
    controls.className = 'controls';

    const del = document.createElement('button');
    del.className = 'btn btn-secondary';
    del.textContent = 'Удалить';
    del.onclick = () => deleteBlock(block.id);

    controls.append(del);

    el.append(header, input, controls);
    container.appendChild(el);
  });
}async function renderCreateTopic(topicId = null) {
  editingTopicId = topicId;

  let title = '';
  let description = '';
  let content = '';

  // 👉 если редактирование — загружаем тему
  if (topicId) {
    try {
      const topic = await apiClient.topics.get(topicId);
      title = topic.title || '';
      description = topic.description || '';
      content = topic.content || '';

      topicBlocks = parseContentToBlocks(content);
    } catch (e) {
      alert('Ошибка загрузки темы');
      return;
    }
  } else {
    topicBlocks = [
      { id: Date.now(), type: 'text', content: '' }
    ];
  }

  const html = `
    <h1 class="page-title">
      ${topicId ? 'Редактирование темы' : 'Создание темы'}
    </h1>

    <div class="form-group">
      <label>Название</label>
      <input type="text" id="topic-title" value="${escapeHtml(title)}">
    </div>

    <div class="form-group">
      <label>Описание</label>
      <input type="text" id="topic-description" value="${escapeHtml(description)}">
    </div>

    <h2 style="margin-top:1.5rem;">Контент</h2>

    <div id="blocks-container"></div>

    <div class="block-add-panel">
      <button class="btn btn-secondary" data-type="text">Текст</button>
      <button class="btn btn-secondary" data-type="image">Картинка</button>
      <button class="btn btn-secondary" data-type="code">Код</button>
    </div>

    <div class="form-actions">
      <button class="btn btn-primary" id="save-topic-btn">
        ${topicId ? 'Сохранить изменения' : 'Создать тему'}
      </button>
    </div>
  `;

  document.getElementById('view-create-topic').innerHTML = html;
  showView('create-topic');

  renderBlocks();

  document.querySelectorAll('.block-add-panel button').forEach(btn => {
    btn.addEventListener('click', () => {
      addBlock(btn.dataset.type);
    });
  });

  document.getElementById('save-topic-btn').addEventListener('click', saveTopic);
}
function addBlock(type) {
  topicBlocks.push({
    id: Date.now(),
    type,
    content: ''
  });

  renderBlocks();
}
async function saveTopic() {
  const title = document.getElementById('topic-title').value.trim();
  const description = document.getElementById('topic-description').value.trim();

  if (!title) {
    alert('Введите название');
    return;
  }

  const content = buildContent();

  try {
    if (editingTopicId) {
      // ✏️ редактирование
      await apiClient.admin.updateTopic(editingTopicId, {
        title,
        description,
        content
      });
    } else {
      // ➕ создание
      await apiClient.admin.createTopic({
        title,
        description,
        content,
        sortOrder: 0
      });
    }

    location.hash = '#/';
  } catch (e) {
    alert(e.message);
  }
}
function parseContentToBlocks(html) {
  const wrapper = document.createElement('div');
  wrapper.innerHTML = html;

  const blocks = [];

  wrapper.childNodes.forEach(node => {
    if (node.nodeType !== 1) return;

    if (node.tagName === 'P') {
      blocks.push({
        id: Date.now() + Math.random(),
        type: 'text',
        content: node.textContent
      });
    }

    if (node.tagName === 'PRE') {
      blocks.push({
        id: Date.now() + Math.random(),
        type: 'code',
        content: node.innerText
      });
    }

    if (node.tagName === 'IMG') {
      blocks.push({
        id: Date.now() + Math.random(),
        type: 'image',
        content: node.src
      });
    }
  });

  return blocks.length ? blocks : [
    { id: Date.now(), type: 'text', content: '' }
  ];
}
function buildContent() {
  return topicBlocks.map(block => {
    if (block.type === 'text') {
      return `<p>${escapeHtml(block.content)}</p>`;
    }

    if (block.type === 'code') {
      return `<pre><code>${escapeHtml(block.content)}</code></pre>`;
    }

    if (block.type === 'image') {
      return `<img src="${escapeHtml(block.content)}" />`;
    }

    return '';
  }).join('\n');
}
function renderCreateTask(topicId) {

  createTaskState = {
    tests: []
  };

  const html = `
    <h1 class="page-title">Создание задачи</h1>

    <div class="form-group">
      <label>Название задачи</label>
      <input type="text" id="task-title">
    </div>

    <div class="form-group">
      <label>Условие</label>
      <textarea id="task-condition" class="code-editor"></textarea>
    </div>

    <h2 style="margin-top:1.5rem;">Тесты</h2>

<p class="meta">Загрузите файл с тестами</p>

<label class="btn btn-secondary" style="cursor:pointer;">
  Загрузить файл
  <input type="file" id="tests-file-input" accept=".txt" hidden>
</label>

<div id="tests-preview"></div>
<div id="tests-error" class="error-msg"></div>

    <div class="form-actions">
      <button type="button" class="btn btn-primary" id="save-task-btn">
        Сохранить задачу
      </button>
    </div>
<p class="meta">Минимум 4 теста на задачу.</p>
  `;

  document.getElementById('view-create-topic').innerHTML = html;
  showView('create-topic');

  document
      .getElementById('tests-file-input')
      .addEventListener('change', handleTestsUpload);

  document
      .getElementById('save-task-btn')
      .addEventListener('click', () => saveTask(topicId));
}
  async function saveTask(topicId) {
    const title = document.getElementById('task-title').value.trim();
    const condition = document.getElementById('task-condition').value.trim();

    const errorEl = document.getElementById('tests-error');
    errorEl.textContent = '';

    if (!title) {
      errorEl.textContent = 'Введите название задачи';
      return;
    }

    if (!createTaskState.tests.length) {
      errorEl.textContent = 'Сначала загрузите корректный файл с тестами';
      return;
    }

    try {
      const task = await apiClient.admin.createTask(topicId, {
        title,
        condition,
        sortOrder: 0
      });

      console.log('TASK:', task);

      await apiClient.admin.addTestsBulk(task.id, createTaskState.tests);

      console.log('TESTS ADDED');

      location.hash = '#/admin';

    } catch (e) {
      console.error('SAVE TASK ERROR:', e);
      console.error(e.status, e.data);
      errorEl.textContent = e.message || 'Ошибка сохранения задачи';
    }
  }
async function handleTestsUpload(e) {
  const file = e.target.files[0];
  if (!file) return;

  const errorEl = document.getElementById('tests-error');
  const previewEl = document.getElementById('tests-preview');

  errorEl.textContent = '';
  previewEl.innerHTML = '';

  try {
    const res = await apiClient.admin.uploadTests(file);

    createTaskState.tests = res.tests;

    if (!createTaskState.tests.length) {
      throw new Error('Файл не содержит тестов');
    }

    // ✅ показываем превью
    renderTestsPreview(createTaskState.tests[0], createTaskState.tests.length);

  } catch (err) {
    createTaskState.tests = [];

    errorEl.textContent = err.message || 'Ошибка загрузки тестов';
  }
}
function renderTestsPreview(first, count) {
  document.getElementById('tests-preview').innerHTML = `
    <div class="block">
      <h3>Пример теста</h3>

      <p><strong>Ввод:</strong></p>
      <pre class="code-block">${escapeHtml(first.input)}</pre>

      <p><strong>Вывод:</strong></p>
      <pre class="code-block">${escapeHtml(first.expectedOutput)}</pre>

      <p class="meta">Всего тестов: ${count}</p>
    </div>
  `;
}
window.addEventListener('hashchange', render);
window.addEventListener('load', async () => {
  await loadUser();
  render();
});
