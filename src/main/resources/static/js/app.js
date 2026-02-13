let currentUser = null;

function showView(id) {
  document.querySelectorAll('[id^="view-"]').forEach(el => el.classList.add('hidden'));
  const v = document.getElementById('view-' + id);
  if (v) v.classList.remove('hidden');
}

function setNav() {
  const guest = document.getElementById('nav-guest');
  const user = document.getElementById('nav-user');
  const admin = document.getElementById('nav-admin');
  if (currentUser) {
    guest.classList.add('hidden');
    user.classList.remove('hidden');
    admin.classList.toggle('hidden', !currentUser.roles || !currentUser.roles.includes('ADMIN'));
  } else {
    guest.classList.remove('hidden');
    user.classList.add('hidden');
    admin.classList.add('hidden');
  }
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

async function render() {
  const { path, id, id2 } = parseHash();
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
    } else if (path === 'profile') {
      await renderProfile(id || (currentUser && currentUser.username));
    } else if (path === 'admin') {
      await renderAdmin();
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
  const q = new URLSearchParams(location.hash.split('?')[1] || '').get('q') || '';
  const topics = q ? await apiClient.topics.search(q) : await apiClient.topics.list();
  const html = `
    <h1 class="page-title">Темы</h1>
    <input type="text" class="search-box" id="search-input" placeholder="Поиск по темам..." value="${escapeHtml(q)}">
    <ul class="topic-list">
      ${topics.length ? topics.map(t => `
        <li>
          <a href="#/topic/${t.id}" class="topic-card">
            <h3>${escapeHtml(t.title)}</h3>
            ${t.description ? `<p class="meta">${escapeHtml(t.description)}</p>` : ''}
            ${t.hasTasks ? '<p class="meta">Есть задачи</p>' : ''}
          </a>
        </li>
      `).join('') : '<li class="empty-state">Ничего не найдено</li>'}
    </ul>
  `;
  document.getElementById('view-main').innerHTML = html;
  document.getElementById('view-main').classList.remove('hidden');
  document.getElementById('search-input')?.addEventListener('input', (e) => {
    const v = e.target.value.trim();
    location.hash = v ? '#/?q=' + encodeURIComponent(v) : '#/';
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
  }
  const html = `
    <a href="#/tasks/${task.topicId}" class="back-link">← ${escapeHtml(topicTitle)} — Задачи</a>
    <h1 class="page-title">${escapeHtml(task.title)}</h1>
    <div class="task-condition">${escapeHtml(task.condition)}</div>
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
  if (currentUser) {

    const adminHtml = `
      <div class="editor-wrap" style="margin-top:2rem;">
        <h2 style="margin-bottom:0.5rem;">Тесты задачи</h2>
        <p class="meta">Добавление тест-кейсов для автопроверки</p>

        <label>Input</label>
        <textarea id="test-input" class="code-editor" placeholder="Входные данные теста..."></textarea>

        <label style="margin-top:1rem;">Expected output</label>
        <textarea id="test-output" class="code-editor" placeholder="Ожидаемый результат..."></textarea>

        <div class="form-actions">
          <button type="button" class="btn btn-primary" id="add-test-btn">Добавить тест</button>
        </div>

        <div id="add-test-result"></div>
      </div>
    `;

    document.getElementById('view-task').insertAdjacentHTML('beforeend', adminHtml);

    document.getElementById('add-test-btn').addEventListener('click', async () => {
      const input = document.getElementById('test-input').value;
      const expectedOutput = document.getElementById('test-output').value;
      const resultEl = document.getElementById('add-test-result');

      if (!expectedOutput.trim()) {
        resultEl.innerHTML = '<div class="result-box error">Введите expectedOutput</div>';
        return;
      }

      resultEl.innerHTML = '<div class="loading">Добавление теста...</div>';

      try {
        await apiClient.admin.addTest(taskId, input, expectedOutput);

        resultEl.innerHTML = '<div class="result-box success">✓ Тест успешно добавлен</div>';

        document.getElementById('test-input').value = '';
        document.getElementById('test-output').value = '';

      } catch (e) {
        resultEl.innerHTML = '<div class="result-box error">' + escapeHtml(e.message) + '</div>';
      }
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
      setToken(res.token);
      await loadUser();
      errEl.classList.add('hidden');
      location.hash = '#/';
    } catch (err) {
      errEl.textContent = err.message || 'Ошибка регистрации';
      errEl.classList.remove('hidden');
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
            <button class="btn btn-secondary edit-topic-btn" data-id="${t.id}">Изменить</button>
            <button class="btn btn-secondary add-task-btn" data-topic-id="${t.id}">+ Задача</button>
          </li>
        `).join('')}
      </ul>
      <button class="btn btn-primary" id="admin-add-topic">+ Добавить тему</button>
    </div>
    <p class="meta">Редактирование тем и задач: через API (PUT /api/admin/topics/:id, POST /api/admin/topics/:topicId/tasks). Добавление тестов: POST /api/admin/tasks/:taskId/tests с body { "input": "...", "expectedOutput": "..." }. Минимум 4 теста на задачу.</p>
  `;
  document.getElementById('view-admin').innerHTML = html;
  showView('admin');
  document.getElementById('admin-add-topic').addEventListener('click', async () => {
    const title = prompt('Название темы:');
    if (!title) return;
    const description = prompt('Краткое описание (необязательно):') || '';
    const content = prompt('Содержимое темы (HTML/текст, необязательно):') || '';
    try {
      await apiClient.admin.createTopic({ title, description, content, sortOrder: 0 });
      renderAdmin();
    } catch (e) {
      alert(e.message);
    }
  });
  document.getElementById('view-admin').querySelectorAll('.add-task-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const topicId = btn.dataset.topicId;
      const title = prompt('Название задачи:');
      if (!title) return;
      const condition = prompt('Условие задачи (текст):') || '';
      try {
        await apiClient.admin.createTask(topicId, { title, condition, sortOrder: 0 });
        alert('Задача создана. Добавьте минимум 4 теста через API: POST /api/admin/tasks/{taskId}/tests');
        renderAdmin();
      } catch (e) {
        alert(e.message);
      }
    });
  });
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

window.addEventListener('hashchange', render);
window.addEventListener('load', async () => {
  await loadUser();
  render();
});
