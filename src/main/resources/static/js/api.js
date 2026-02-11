const API_BASE = '/api';

function getToken() {
  return localStorage.getItem('token');
}

function setToken(token) {
  if (token) localStorage.setItem('token', token);
  else localStorage.removeItem('token');
}

function authHeaders() {
  const token = getToken();
  return token ? { 'Authorization': 'Bearer ' + token } : {};
}

async function api(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...authHeaders(),
    ...(options.headers || {})
  };
  const res = await fetch(API_BASE + url, { ...options, headers });
  if (res.status === 401) {
    setToken(null);
    window.dispatchEvent(new CustomEvent('auth:logout'));
  }
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = text;
  }
  if (!res.ok) {
    const err = new Error(data?.error || data?.message || res.statusText);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

const apiClient = {
  auth: {
    login: (username, password) => api('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
    register: (body) => api('/auth/register', { method: 'POST', body: JSON.stringify(body) })
  },
  topics: {
    list: () => api('/topics'),
    search: (q) => api('/topics/search?q=' + encodeURIComponent(q || '')),
    get: (id) => api('/topics/' + id)
  },
  tasks: {
    byTopic: (topicId) => api('/topics/' + topicId + '/tasks'),
    get: (id) => api('/tasks/' + id),
    submit: (taskId, code) => api('/tasks/' + taskId + '/submit', { method: 'POST', body: JSON.stringify({ taskId, code }) })
  },
  attempts: {
    my: () => api('/attempts/my'),
    myForTask: (taskId) => api('/attempts/my/task/' + taskId),
    get: (id) => api('/attempts/' + id)
  },
  profile: {
    me: () => api('/profile/me'),
    byUsername: (username) => api('/profile/user/' + encodeURIComponent(username)),
    update: (body) => api('/profile/me', { method: 'PUT', body: JSON.stringify(body) }),
    uploadAvatar: (file) => {
      const form = new FormData();
      form.append('file', file);
      return fetch(API_BASE + '/profile/me/avatar', {
        method: 'POST',
        headers: authHeaders(),
        body: form
      }).then(r => r.ok ? r.json() : r.json().then(d => { throw new Error(d.error || 'Ошибка загрузки'); }));
    }
  },
  admin: {
    createTopic: (body) => api('/admin/topics', { method: 'POST', body: JSON.stringify(body) }),
    updateTopic: (id, body) => api('/admin/topics/' + id, { method: 'PUT', body: JSON.stringify(body) }),
    deleteTopic: (id) => api('/admin/topics/' + id, { method: 'DELETE' }),
    createTask: (topicId, body) => api('/admin/topics/' + topicId + '/tasks', { method: 'POST', body: JSON.stringify(body) }),
    updateTask: (id, body) => api('/admin/tasks/' + id, { method: 'PUT', body: JSON.stringify(body) }),
    deleteTask: (id) => api('/admin/tasks/' + id, { method: 'DELETE' }),
    addTest: (taskId, input, expectedOutput) => api('/admin/tasks/' + taskId + '/tests', {
      method: 'POST',
      body: JSON.stringify({ input: input || '', expectedOutput })
    })
  }
};
