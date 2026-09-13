// ==========================================
// ★★★ 全局安全网：彻底封杀浏览器任何形式的默认刷新跳转 ★★★
// ==========================================
window.addEventListener("dragover", (e) => e.preventDefault(), false);
window.addEventListener("drop", (e) => e.preventDefault(), false);

// --- 侧边栏切换逻辑 ---
const sidebarBtns = document.querySelectorAll('.sidebar-btn');
const mainPanels = document.querySelectorAll('.main-panel');
const headerBreadcrumb = document.getElementById('header-breadcrumb');
const urlParams = new URLSearchParams(window.location.search);
const courseId = urlParams.get('courseId') || urlParams.get('id') || 1;

// ★★★ 判断当前是教师端还是学生端（班级空间）★★★
const isClassCenter = window.IS_CLASS_CENTER === true;

sidebarBtns.forEach(btn => {
    btn.addEventListener('click', function() {
        sidebarBtns.forEach(b => {
            b.className = "sidebar-btn w-full flex items-center px-3 py-2 text-sm font-medium rounded-md text-gray-600 hover:bg-gray-50 hover:text-gray-900 transition outline-none";
        });
        this.className = "sidebar-btn w-full flex items-center px-3 py-2 text-sm font-medium rounded-md bg-indigo-50 text-indigo-700 transition outline-none";

        mainPanels.forEach(panel => { panel.classList.add('hidden'); panel.classList.remove('flex'); });

        const targetId = this.getAttribute('data-target');
        const targetEl = document.getElementById(targetId);
        if(targetEl) {
            targetEl.classList.remove('hidden');
            targetEl.classList.add('flex');
        }

        const menuName = this.innerText.replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]|\uD83C[\uDC00-\uDFFF]|\uD83D[\uDC00-\uDFFF]|\uD83E[\uDC00-\uDFFF]/g, '').trim();

        // ★★★ 根据页面类型显示不同的前缀 ★★★
        const prefix = isClassCenter ? '班级空间' : '教学管理';
        headerBreadcrumb.innerHTML = `<span>${prefix}</span> <span class="mx-2">/</span> <span class="text-gray-900 font-medium">${menuName}</span>`;

        // ★★★ 核心新增：数据拉取拦截器 ★★★
        if (targetId === 'panel-grading') {
            loadGradingList();
        } else if (targetId === 'panel-applications' || targetId === 'panel-content' || menuName.includes('审批') || menuName.includes('成员')) {
            loadCourseApplications();
        } else if (targetId === 'panel-config') {
            loadConfigPanel();
        }
    });
});

// ==========================================
// ★★★ 加课申请页真实数据逻辑 (匹配你的数据库) ★★★
// ==========================================
window.loadCourseApplications = function() {
    if (!courseId) return;

    fetch(`/api/applications/course/${courseId}/pending`)
        .then(res => res.json())
        .then(data => {
            renderApplicationTable(data);
        })
        .catch(err => console.error("获取申请列表失败:", err));
}

// 将真实数据渲染到表格中（修复位置错乱问题）
// ==========================================
// ★★★ 加课申请页真实数据逻辑 (完美对齐表头) ★★★
// ==========================================

window.renderApplicationTable = function(data) {
    let tbody = document.getElementById('approval-list-tbody') || document.getElementById('application-tbody');
    if (!tbody) {
        const panel = document.getElementById('panel-applications') || document.getElementById('panel-content');
        if(panel) tbody = panel.querySelector('tbody');
    }
    if (!tbody) return;

    const badge = document.querySelector('.bg-amber-50');

    if (!data || data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="py-12 text-center text-gray-400">🎉 太棒了，当前没有任何待处理的加课申请！</td></tr>`;
        if (badge) badge.innerText = "0 条待处理";
        return;
    }

    if (badge) badge.innerText = `${data.length} 条待处理`;

    tbody.innerHTML = data.map(app => {
        const date = app.applyTime ? new Date(app.applyTime) : new Date();
        const timeStr = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')} ${String(date.getHours()).padStart(2,'0')}:${String(date.getMinutes()).padStart(2,'0')}`;

        // ★ 核心调整：优先显示 user_id 对应的账号名(如果后端传了 userName)，否则显示 userId 兜底
        const accountName = app.userName || app.userId || '未知账号';
        const firstChar = accountName.toString().charAt(0).toUpperCase();

        return `
        <tr class="hover:bg-gray-50 transition border-b border-gray-50 last:border-0 group">
            <!-- 1. 申请人账号 (带小头像) -->
            <td class="py-3 px-4">
                <div class="flex items-center space-x-3">
                    <div class="w-6 h-6 rounded-full bg-indigo-50 flex items-center justify-center text-indigo-600 text-xs font-bold">${firstChar}</div>
                    <span class="text-gray-900 text-sm font-medium">${accountName}</span>
                </div>
            </td>
            
            <!-- 2. 真实姓名 (对应 real_name) -->
            <td class="py-3 px-4">
                <span class="text-gray-700 text-sm">${app.realName || '-'}</span>
            </td>
            
            <!-- 3. 学号 (对应 student_number) -->
            <td class="py-3 px-4">
                <p class="text-gray-500 font-mono text-sm">${app.studentNumber || '-'}</p>
            </td>
            
            <!-- 4. 申请时间 -->
            <td class="py-3 px-4">
                <p class="text-gray-400 text-xs">${timeStr}</p>
            </td>
            
            <!-- 5. 操作按钮 -->
            <td class="py-3 px-4 text-right space-x-2">
                <button onclick="handleApplication(${app.id}, 'reject')" class="bg-white border border-gray-200 text-gray-500 px-4 py-1.5 rounded text-xs font-semibold hover:bg-gray-100 hover:text-gray-700 transition shadow-sm active:scale-95">拒绝</button>
                <button onclick="handleApplication(${app.id}, 'approve')" class="bg-indigo-50 text-indigo-600 px-4 py-1.5 rounded text-xs font-semibold hover:bg-indigo-100 hover:text-indigo-700 transition shadow-sm active:scale-95">同意</button>
            </td>
        </tr>
        `;
    }).join('');
};

// 处理同意或拒绝操作 (精准匹配你的 /approve 和 /reject)
window.handleApplication = function(appId, action) {
    // action 将会传入 'approve' 或 'reject'
    fetch(`/api/applications/${appId}/${action}`, { method: 'PUT' })
        .then(res => {
            if (res.ok) {
                loadCourseApplications(); // 成功后无刷新重载列表
            } else {
                alert("操作失败，请检查后端运行状态。");
            }
        })
        .catch(err => console.error("审批操作出错:", err));
};
// ==========================================
// ★★★ 批改大厅逻辑 ★★★
// ==========================================
window.allGradingData = [];

function loadGradingList() {
    const tbody = document.querySelector('#panel-grading tbody');
    if (!tbody) return;

    tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-400">正在扫描当前课程的学生提交数据...</td></tr>`;

    Promise.all([
        fetch(`/courses/${courseId}/labs`).then(res => res.ok ? res.json() : []),
        fetch('/api/submissions/all').then(res => res.ok ? res.json() : [])
    ])
        .then(([labs, allSubmissions]) => {
            const currentCourseLabIds = labs.map(l => l.id);

            // ★★★ 新增：建立 实验ID → 展示权限 的对照表 ★★★
            const labModeMap = new Map(labs.map(l => [l.id, l.showcaseMode]));

            const courseSubmissions = allSubmissions.filter(sub => currentCourseLabIds.includes(sub.labId));

            // ★★★ 新增：把展示权限挂到每条提交记录上 ★★★
            courseSubmissions.forEach(sub => {
                sub.showcaseMode = Number(labModeMap.get(sub.labId) ?? 1);
            });

            if (!courseSubmissions || courseSubmissions.length === 0) {
                tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-400">当前课程还没有学生提交实验哦</td></tr>`;
                updateDashboardStats([]);
                return;
            }

            window.allGradingData = courseSubmissions;
            initFilters(courseSubmissions);
            renderGradingTable(courseSubmissions);
        })
        .catch(err => {
            console.error("加载批改列表失败:", err);
            tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-red-500">❌ 加载失败，请检查后端接口。</td></tr>`;
        });
}
//批改大厅AI预检判定
function getAiStatusText(score) {
    if (score >= 60) return '运行通过';
    if (score !== null && score < 60) return '检出异常';
    return '等待评测';
}

function initFilters(data) {
    const selects = document.querySelectorAll('#panel-grading select');
    if (selects.length < 3) return;

    const labSelect = selects[0];
    const statusSelect = selects[1];

    const uniqueLabs = [...new Set(data.map(d => d.labTitle || '未命名实验'))];
    const uniqueStatuses = [...new Set(data.map(d => getAiStatusText(d.score)))];

    labSelect.innerHTML = `<option value="">所有实验项目</option>` +
        uniqueLabs.map(lab => `<option value="${lab}">${lab}</option>`).join('');

    statusSelect.innerHTML = `<option value="">所有状态</option>` +
        uniqueStatuses.map(status => `<option value="${status}">${status}</option>`).join('');

    labSelect.onchange = applyFilters;
    statusSelect.onchange = applyFilters;

    selects[2].onchange = applyFilters;
    selects[2].value = "";   // 重新加载列表时重置回"所有作品"
}

function applyFilters() {
    const selects = document.querySelectorAll('#panel-grading select');
    const selectedLab = selects[0].value;
    const selectedStatus = selects[1].value;
    const selectedFeature = selects[2].value;

    let filteredData = window.allGradingData;

    if (selectedLab) {
        filteredData = filteredData.filter(d => (d.labTitle || '未命名实验') === selectedLab);
    }
    if (selectedStatus) {
        filteredData = filteredData.filter(d => getAiStatusText(d.score) === selectedStatus);
    }
    if (selectedFeature === 'featured') {
        filteredData = filteredData.filter(d => d.isFeatured === 1);
    } else if (selectedFeature === 'unfeatured') {
        filteredData = filteredData.filter(d => d.isFeatured !== 1);
    }

    renderGradingTable(filteredData);
}

function renderGradingTable(data) {
    updateDashboardStats(data);
    const isClass = window.IS_CLASS_CENTER === true;   // ★ 判断当前是教师端还是班级中心

    const tbody = document.querySelector('#panel-grading tbody');
    if (!data || data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-400">没有找到符合条件的提交记录</td></tr>`;
        return;
    }

    let html = '';
    data.forEach(sub => {
        const studentName = sub.studentName || '神秘学生';

        let timeStr = '-';
        if (sub.submitTime) {
            const date = new Date(sub.submitTime);
            const y = date.getFullYear();
            const m = String(date.getMonth() + 1).padStart(2, '0');
            const d = String(date.getDate()).padStart(2, '0');
            const h = String(date.getHours()).padStart(2, '0');
            const min = String(date.getMinutes()).padStart(2, '0');
            const s = String(date.getSeconds()).padStart(2, '0');
            timeStr = `${y}-${m}-${d} ${h}:${min}:${s}`;
        }

        let aiBadgeHtml = '';
        if (sub.score >= 60) {
            aiBadgeHtml = `<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 border border-green-200">✓ 运行通过</span>`;
        } else if (sub.score !== null && sub.score < 60) {
            aiBadgeHtml = `<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-50 text-red-700 border border-red-200">✕ 检出异常</span>`;
        } else {
            aiBadgeHtml = `<span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-orange-50 text-orange-600 border border-orange-200">〇 等待评测</span>`;
        }

        // ★ 按钮文字和函数调用根据页面类型动态切换
        const btnText = isClass ? '借鉴与欣赏' : '审查与批改';
        const btnFunc = isClass ? 'openStudentDrawer' : 'openDrawer';
        const btnArgs = isClass ? `${sub.id}, this` : `${sub.id}`;  // 班级中心多传 this 用于小字提示

        html += `
        <tr class="hover:bg-gray-50 transition group">
            <td class="py-2 px-4"><span class="font-mono text-gray-500 text-xs bg-gray-100 px-1.5 py-0.5 rounded border border-gray-200">#${sub.id}</span></td>
            <td class="py-2 px-4 pl-8">
                <p class="text-gray-700 group-hover:text-indigo-600 transition">${studentName}</p>
            </td>
            <td class="py-2 px-4"><p class="font-medium text-gray-900 truncate max-w-[200px]" title="${sub.labTitle || '未命名实验'}">${sub.labTitle || '未命名实验'}</p></td>
            <td class="py-2 px-4 pl-12"><p class="text-gray-900 text-xs font-medium">${timeStr}</p></td>
            <td class="py-2 px-4 pl-12">${aiBadgeHtml}</td>
            <td class="py-2 px-4 text-right">
                <button onclick="${btnFunc}(${btnArgs})" class="bg-gray-100 text-gray-800 px-4 py-1.5 rounded text-xs font-normal hover:bg-gray-200 transition active:scale-95">${btnText}</button>
            </td>
        </tr>`;
    });
    tbody.innerHTML = html;
}

function updateDashboardStats(data) {
    if (!data || data.length === 0) {
        document.getElementById('stat-submit-users').innerText = '0';
        document.getElementById('stat-pass-users').innerText = '0';
        document.getElementById('stat-total-subs').innerText = '0';
        document.getElementById('stat-avg-score').innerText = '0';
        return;
    }

    document.getElementById('stat-total-subs').innerText = data.length;

    const uniqueSubmitters = new Set();
    const uniquePassers = new Set();
    // ★ key 从"学生"改为"学生|实验"，同一学生同一实验取最高分
    const bestScoreByStudentLab = new Map();

    data.forEach(sub => {
        const student = sub.studentName || '神秘学生';
        uniqueSubmitters.add(student);

        // ★ 通过标准：>= 60（原来是 === 100）
        if (sub.score !== null && sub.score !== undefined && sub.score >= 60) {
            uniquePassers.add(student);
        }

        if (sub.score !== null && sub.score !== undefined) {
            const key = student + '|' + (sub.labId ?? sub.labTitle ?? '未知实验');
            const prev = bestScoreByStudentLab.get(key);
            if (prev === undefined || sub.score > prev) {
                bestScoreByStudentLab.set(key, sub.score);
            }
        }
    });

    document.getElementById('stat-submit-users').innerText = uniqueSubmitters.size;
    document.getElementById('stat-pass-users').innerText = uniquePassers.size;

    if (bestScoreByStudentLab.size > 0) {
        const sum = [...bestScoreByStudentLab.values()].reduce((a, b) => a + b, 0);
        document.getElementById('stat-avg-score').innerText = (sum / bestScoreByStudentLab.size).toFixed(1);
    } else {
        document.getElementById('stat-avg-score').innerText = '0';
    }
}
const searchRunIdInput = document.getElementById('filter-run-id');
if (searchRunIdInput) {
    searchRunIdInput.addEventListener('input', (e) => {
        const keyword = e.target.value.trim();
        if (!window.allGradingData) return;

        if (keyword === "") {
            renderGradingTable(window.allGradingData);
            return;
        }

        const filteredData = window.allGradingData.filter(sub => {
            return sub.id && sub.id.toString().includes(keyword);
        });

        renderGradingTable(filteredData);
    });
}

// ==========================================
// ★★★ 学情追踪：双模态切换逻辑 ★★★
// ==========================================
function toggleTrackingMode(mode) {
    const listMode = document.getElementById('tracking-mode-list');
    const chartMode = document.getElementById('tracking-mode-chart');
    const btnList = document.getElementById('btn-view-list');
    const btnChart = document.getElementById('btn-view-chart');

    const inactiveClass = ['text-gray-500', 'hover:text-gray-800', 'hover:bg-gray-100'];
    const activeClass = ['bg-white', 'shadow-sm', 'text-indigo-600'];

    if (mode === 'list') {
        listMode.classList.remove('hidden');
        listMode.classList.add('flex');
        chartMode.classList.add('hidden');
        chartMode.classList.remove('flex');

        btnList.classList.add(...activeClass);
        btnList.classList.remove(...inactiveClass);
        btnChart.classList.add(...inactiveClass);
        btnChart.classList.remove(...activeClass);
    } else {
        chartMode.classList.remove('hidden');
        chartMode.classList.add('flex');
        listMode.classList.add('hidden');
        listMode.classList.remove('flex');

        btnChart.classList.add(...activeClass);
        btnChart.classList.remove(...inactiveClass);
        btnList.classList.add(...inactiveClass);
        btnList.classList.remove(...activeClass);
    }
}
// ==========================================
// ★★★ 学情追踪：数据与图表 ★★★
// ==========================================
let trackingStudents = [];
let chartLab = null, chartTrend = null;

//图标切换
const _origToggleTrackingMode = toggleTrackingMode;
let echartsLoading = null;

function ensureEcharts() {
    if (window.echarts) return Promise.resolve();
    if (!echartsLoading) {
        echartsLoading = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = 'https://cdn.bootcdn.net/ajax/libs/echarts/5.4.3/echarts.min.js';
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }
    return echartsLoading;
}

toggleTrackingMode = function(mode) {
    _origToggleTrackingMode(mode);
    if (mode === 'chart') {
        ensureEcharts()
            .then(renderTrackingCharts)
            .catch(() => console.error("ECharts 加载失败，请检查网络"));
    }
};

// ---- 列表 ----
function loadTrackingStudents() {
    fetch(`/courses/${courseId}/tracking/students`)
        .then(r => r.json())
        .then(list => {
            trackingStudents = list || [];
            renderTrackingTable(trackingStudents);
        })
        .catch(err => console.error("学情列表加载失败", err));
}

function renderTrackingTable(list) {
    const tbody = document.getElementById('tracking-student-tbody');
    if (!tbody) return;
    if (!list.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-gray-400">没有找到符合条件的学生</td></tr>`;
        return;
    }
    tbody.innerHTML = list.map(s => {
        const total = s.totalLabs || 0;
        const done = s.completedLabs || 0;
        const rate = total === 0 ? 0 : Math.round(done / total * 100);
        const barColor = rate >= 80 ? 'bg-emerald-500' : rate >= 50 ? 'bg-amber-400' : 'bg-red-400';
        const safeName = (s.name || '').replace(/'/g, "\\'");
        return `
        <tr class="hover:bg-gray-50 transition group">
            <td class="p-4 font-bold text-gray-900">${s.name || '-'}</td>
            <td class="p-4 text-gray-500 font-mono">${s.studentNo || '-'}</td>
            <td class="p-4">
                <div class="flex items-center space-x-2">
                    <div class="w-full bg-gray-200 rounded-full h-1.5 max-w-[100px]">
                        <div class="${barColor} h-1.5 rounded-full" style="width: ${rate}%"></div>
                    </div>
                    <span class="text-xs text-gray-600 font-medium">${rate}%</span>
                    <span class="text-xs text-gray-400">(${done}/${total})</span>
                </div>
            </td>
            <td class="p-4 text-gray-500 text-xs">${formatRelativeTime(s.lastActiveTime)}</td>
            <td class="p-4 text-right space-x-3">
                <button onclick="openStudentProfile(${s.userId}, '${safeName}')" class="text-indigo-600 hover:text-indigo-800 text-xs font-medium">查看档案</button>
                <button onclick="removeStudentFromCourse(${s.userId}, '${safeName}')" class="text-red-400 hover:text-red-600 text-xs font-medium">删除</button>
            </td>
        </tr>`;
    }).join('');
}

window.filterTrackingStudents = function() {
    const kw = document.getElementById('tracking-search-input').value.trim().toLowerCase();
    if (!kw) { renderTrackingTable(trackingStudents); return; }
    renderTrackingTable(trackingStudents.filter(s =>
        (s.name || '').toLowerCase().includes(kw) ||
        String(s.studentNo || '').toLowerCase().includes(kw)
    ));
};

function formatRelativeTime(t) {
    if (!t) return '从未活跃';
    const diff = Date.now() - new Date(t).getTime();
    const min = Math.floor(diff / 60000);
    if (min < 1) return '刚刚';
    if (min < 60) return `${min}分钟前`;
    const h = Math.floor(min / 60);
    if (h < 24) return `${h}小时前`;
    const d = Math.floor(h / 24);
    if (d < 30) return `${d}天前`;
    const dt = new Date(t);
    const p = n => String(n).padStart(2, '0');
    return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`;
}

// ---- 查看档案 ----
window.openStudentProfile = function(userId, name) {
    document.getElementById('profile-student-name').innerText = name;
    const box = document.getElementById('profile-lab-list');
    box.innerHTML = `<p class="p-6 text-sm text-gray-400 text-center">加载中...</p>`;
    document.getElementById('student-profile-modal').classList.remove('hidden');

    fetch(`/courses/${courseId}/tracking/students/${userId}/profile`)
        .then(r => r.json())
        .then(labs => {
            if (!labs || !labs.length) {
                box.innerHTML = `<p class="p-6 text-sm text-gray-400 text-center">本课程暂无已部署的实验</p>`;
                return;
            }
            box.innerHTML = labs.map((l, i) => {
                let scoreHtml;
                if (!l.submitTime) {
                    scoreHtml = `<span class="text-xs text-gray-400 shrink-0">未提交</span>`;
                } else if (l.score === null || l.score === undefined) {
                    scoreHtml = `<span class="text-xs text-amber-500 font-medium shrink-0">已提交 · 待批改</span>`;
                } else {
                    scoreHtml = `<span class="text-sm font-bold shrink-0 ${l.score >= 60 ? 'text-emerald-600' : 'text-red-500'}">${l.score} 分</span>`;
                }
                return `
                <div class="flex items-center justify-between px-5 py-3 border-b border-gray-100 text-sm">
                    <div class="min-w-0 mr-4">
                        <p class="text-gray-800 truncate" title="${l.labTitle}">${i + 1}. ${l.labTitle}</p>
                        <p class="text-xs text-gray-400 mt-0.5">${l.submitTime ? '提交于 ' + formatRelativeTime(l.submitTime) : '—'}</p>
                    </div>
                    ${scoreHtml}
                </div>`;
            }).join('');
        })
        .catch(() => {
            box.innerHTML = `<p class="p-6 text-sm text-red-400 text-center">档案加载失败，请检查后端状态</p>`;
        });
};

window.closeStudentProfile = function() {
    document.getElementById('student-profile-modal').classList.add('hidden');
};

// ---- 逐出班级 ----
window.removeStudentFromCourse = function(userId, name) {
    if (!confirm(`确定将「${name}」移出本课程吗？\n该学生将无法再访问课程内容！`)) return;
    fetch(`/courses/${courseId}/students/${userId}`, { method: 'DELETE' })
        .then(res => {
            if (res.ok) loadTrackingStudents();
            else alert("移除失败，请检查后端状态");
        });
};

// ---- 图表分析 ----
function renderTrackingCharts() {
    fetch(`/courses/${courseId}/tracking/stats`)
        .then(r => r.json())
        .then(stats => {
            document.getElementById('tracking-stat-total').innerText = stats.totalStudents;
            document.getElementById('tracking-stat-avg').innerText = stats.avgCompletionRate;
            document.getElementById('tracking-stat-active').innerText = stats.activeCount7d;

            const labBox = document.getElementById('chart-lab-completion');
            const trendBox = document.getElementById('chart-activity-trend');
            if (!chartLab) chartLab = echarts.init(labBox);
            if (!chartTrend) chartTrend = echarts.init(trendBox);

            // clear 后重新 setOption，每次切换都重播入场动画
            chartLab.clear();
            chartLab.setOption({
                tooltip: { trigger: 'axis' },
                grid: { left: 45, right: 20, top: 35, bottom: 60 },
                xAxis: {
                    type: 'category',
                    data: stats.labStats.map(l => l.labTitle),
                    axisLabel: { interval: 0, rotate: 30, fontSize: 11, color: '#6b7280' }
                },
                yAxis: { type: 'value', minInterval: 1, name: '完成人数' },
                series: [{
                    type: 'bar',
                    data: stats.labStats.map(l => l.completedCount),
                    barMaxWidth: 36,
                    itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] },
                    label: { show: true, position: 'top', fontSize: 11, color: '#6b7280' },
                    animationDuration: 800,
                    animationEasing: 'elasticOut',
                    animationDelay: idx => idx * 100
                }]
            });

            chartTrend.clear();
            chartTrend.setOption({
                tooltip: { trigger: 'axis' },
                grid: { left: 45, right: 20, top: 35, bottom: 30 },
                xAxis: {
                    type: 'category',
                    boundaryGap: false,
                    data: stats.activityTrend.map(t => t.date),
                    axisLabel: { fontSize: 10, color: '#6b7280' }
                },
                yAxis: { type: 'value', minInterval: 1, name: '活跃人数' },
                series: [{
                    type: 'line',
                    smooth: true,
                    data: stats.activityTrend.map(t => t.count),
                    itemStyle: { color: '#6366f1' },
                    lineStyle: { width: 2.5 },
                    areaStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            { offset: 0, color: 'rgba(99, 102, 241, 0.25)' },
                            { offset: 1, color: 'rgba(99, 102, 241, 0.02)' }
                        ])
                    },
                    animationDuration: 1500
                }]
            });

            chartLab.resize();
            chartTrend.resize();
        })
        .catch(err => console.error("统计数据加载失败", err));
}

window.addEventListener('resize', () => {
    if (chartLab) chartLab.resize();
    if (chartTrend) chartTrend.resize();
});

document.addEventListener('DOMContentLoaded', loadTrackingStudents);

// --- 批改抽屉逻辑 ---
const drawerTabBtns = document.querySelectorAll('.drawer-tab-btn');
const drawerContents = document.querySelectorAll('.drawer-content');

drawerTabBtns.forEach(btn => {
    btn.addEventListener('click', function() {
        drawerTabBtns.forEach(b => b.className = "drawer-tab-btn border-b-2 border-transparent hover:text-gray-900 pb-2 outline-none transition");
        this.className = "drawer-tab-btn active border-b-2 border-indigo-600 text-indigo-600 pb-2 outline-none transition";

        drawerContents.forEach(content => { content.classList.add('hidden'); content.classList.remove('flex', 'flex-col'); });

        const targetId = this.getAttribute('data-target');
        const targetEl = document.getElementById(targetId);
        targetEl.classList.remove('hidden');
        if(targetId === 'drawer-attachments') {
            targetEl.classList.add('flex', 'flex-col');
        } else {
            targetEl.classList.add('flex', 'flex-col');
        }
    });
});

const drawer = document.getElementById('review-drawer');
const overlay = document.getElementById('drawer-overlay');

window.currentReviewSubmissionId = null;
window.currentFeatured = false;

function renderStar() {
    const btn = document.getElementById('btn-feature-star');
    if (!btn) return;
    if (window.currentFeatured) {
        btn.innerText = '★';
        btn.className = "text-2xl leading-none text-yellow-400 hover:text-yellow-500 transition";
    } else {
        btn.innerText = '☆';
        btn.className = "text-2xl leading-none text-gray-300 hover:text-yellow-400 transition";
    }
}

function toggleFeature() {
    if (!window.currentReviewSubmissionId) return;
    fetch(`/api/submissions/${window.currentReviewSubmissionId}/feature`, { method: 'PUT' })
        .then(res => res.ok ? res.json() : Promise.reject())
        .then(data => {
            window.currentFeatured = (data.isFeatured === 1);
            renderStar();
            const row = window.allGradingData.find(d => d.id === window.currentReviewSubmissionId);
            if (row) row.isFeatured = data.isFeatured;
        })
        .catch(() => alert("❌ 收藏操作失败，请检查后端"));
}

// 打开侧滑弹窗并加载提交详情
window.openDrawer = function(submissionId) {
    window.currentReviewSubmissionId = submissionId;

    document.getElementById('drawer-code-content').innerText = "获取代码中...";
    document.getElementById('drawer-score').value = '';
    document.getElementById('drawer-feedback').value = '';


    const webLinkInput = document.getElementById('drawer-web-link');
    const webLinkBtn = document.getElementById('drawer-web-link-btn');
    if (webLinkInput) webLinkInput.value = '获取中...';
    if (webLinkBtn) webLinkBtn.classList.add('hidden');

    const attachmentList = document.getElementById('drawer-attachment-list');
    if (attachmentList) attachmentList.innerHTML = `<p class="text-xs text-gray-400">加载附件中...</p>`;

    overlay.classList.remove('hidden');
    setTimeout(() => { overlay.classList.remove('opacity-0'); }, 10);
    drawer.classList.remove('translate-x-full');

    fetch(`/api/submissions/${submissionId}`)
        .then(res => res.json())
        .then(data => {
            document.getElementById('drawer-code-content').innerText = data.codeContent || '该学生未提交代码。';
            document.getElementById('drawer-code-language').innerText = data.language || '未知语言';
            window.currentDrawerSubmission = data;
            window.currentFeatured = (data.isFeatured === 1);
            renderStar();
            const webLink = data.webLink || data.web_link;
            if (webLinkInput && webLinkBtn) {
                if (webLink && webLink.trim() !== '') {
                    webLinkInput.value = webLink;
                    webLinkBtn.href = webLink;
                    webLinkBtn.classList.remove('hidden');
                    webLinkBtn.classList.add('flex');
                } else {
                    webLinkInput.value = '未提交网页链接';
                    webLinkBtn.classList.remove('flex');
                    webLinkBtn.classList.add('hidden');
                }
            }

            if (data.score !== null) document.getElementById('drawer-score').value = data.score;
            if (data.feedback !== null) document.getElementById('drawer-feedback').value = data.feedback;

            if (attachmentList) {
                if (data.files && data.files.length > 0) {
                    attachmentList.innerHTML = '';
                    data.files.forEach(file => {
                        attachmentList.innerHTML += `
                            <div class="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-md shadow-sm">
                                <span class="text-sm font-medium text-gray-700 truncate">${file.fileName}</span>
                                <a href="/api/submissions/download/${file.id}" 
                                   class="text-indigo-600 hover:text-indigo-800 text-sm font-semibold ml-4 shrink-0 transition">
                                   下载 ⬇️
                                </a>
                            </div>
                        `;
                    });
                } else {
                    attachmentList.innerHTML = `<p class="text-xs text-gray-400">该学生未提交任何文件附件。</p>`;
                }
            }
        })
        .catch(err => {
            console.error("获取提交详情失败:", err);
            document.getElementById('drawer-code-content').innerText = "❌ 获取数据失败，请检查后端接口。";
            if (attachmentList) attachmentList.innerHTML = `<p class="text-xs text-red-500">❌ 加载附件失败</p>`;
        });
};

// ★ 班级空间专属：欣赏与借鉴（带 showcase_mode 权限判定）
// ★ 屏幕中上方红色飘字提示（每点一次出一个，向上飘然后消失）
window.showToast = function(msg) {
    const toast = document.createElement('div');
    toast.innerText = msg;
    toast.style.cssText = `
        position: fixed;
        top: 7px;
        left: 50%;
        transform: translateX(-50%) translateY(0);
        background: #FEF2F2;
        color: #e21c1c;
        border: 1px solid #FECACA;
        padding: 8px 20px;
        border-radius: 8px;
        font-size: 14px;
        font-weight: 500;
        box-shadow: 0 4px 12px rgba(220, 38, 38, 0.15);
        z-index: 9999;
        pointer-events: none;
        opacity: 0;
        transition: transform 1.6s ease-out, opacity 1.6s ease-out;
    `;
    document.body.appendChild(toast);

    // 强制浏览器先渲染初始状态，再触发过渡动画
    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            toast.style.opacity = '1';
            // 先浮现
            setTimeout(() => {
                // 再向上飘 + 渐隐
                toast.style.transform = 'translateX(-50%) translateY(-60px)';
                toast.style.opacity = '0';
            }, 2000);
        });
    });

    // 动画结束后移除节点
    setTimeout(() => toast.remove(), 4000);
};

window.openStudentDrawer = function(id, btn) {
    const row = (window.allGradingData || []).find(d => d.id === id);
    if (!row) { showToast("数据还在加载，请稍候再试"); return; }

    const mode = Number(row.showcaseMode ?? 1);
    let allowed = true;
    if (mode === 3) {
        allowed = false;                                  // 该实验完全不开放
    } else if (mode === 2 && Number(row.isFeatured) !== 1) {
        allowed = false;                                  // 只开放收藏作品，这条没被收藏
    }

    if (!allowed) {
        showToast("⚠️ 权限暂未开放");
        return;
    }

    openDrawer(id);   // 有权限，复用原抽屉渲染
};
//关闭侧滑弹窗
function closeDrawer() {
    drawer.classList.add('translate-x-full');
    overlay.classList.add('opacity-0');
    setTimeout(() => { overlay.classList.add('hidden'); }, 300);
}

// ★ 还原：把该提交的代码/链接/依赖恢复到实验工作区（与快照回溯同款逻辑）
function restoreFromDrawer() {
    const sub = window.currentDrawerSubmission;
    if (!sub) { alert("⚠️ 数据尚未加载完成，请稍候再试"); return; }

    const labId = sub.labId;
    const rawCode = sub.codeContent || '';
    const webLink = sub.webLink || '';
    const deps = sub.dependencies || '';

    if (!labId) {
        alert("⚠️ 无法识别对应的实验 ID，跳转失败！");
        return;
    }

    if (confirm("⚡ 确认还原？\n将把该学生提交的代码、作品链接和依赖配置载入实验工作区，并跳转过去。")) {
        localStorage.setItem(`recovery_code_${labId}`, rawCode);
        if (webLink) {
            localStorage.setItem(`recovery_url_${labId}`, webLink);
        }
        localStorage.setItem(`recovery_deps_${labId}`, deps);
        window.location.href = `workspace.html?labId=${labId}&recover=true`;
    }
}
// 提交批改结果
window.submitReview = function() {
    if (!window.currentReviewSubmissionId) return;

    const finalScore = document.getElementById('drawer-score').value;
    const finalFeedback = document.getElementById('drawer-feedback').value;

    const requestData = {
        score: finalScore ? parseInt(finalScore) : null,
        feedback: finalFeedback
    };

    fetch(`/api/submissions/${window.currentReviewSubmissionId}/review`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData)
    })
        .then(res => {
            if (res.ok) {
                alert("✅ 批改完成！成绩与反馈已覆盖 AI 记录！");
                closeDrawer();
                loadGradingList();
            } else {
                alert("❌ 保存批改失败，请检查后端 PUT 接口。");
            }
        })
        .catch(err => console.error("提交批改出错:", err));
}
// --- 发布课程弹窗逻辑 ---
const courseModal = document.getElementById('course-modal');
const courseModalContent = document.getElementById('course-modal-content');

function openCourseModal() {
    courseModal.classList.remove('hidden');
    void courseModal.offsetWidth;
    courseModal.classList.remove('opacity-0');
    courseModalContent.classList.remove('scale-95');
}

function closeCourseModal() {
    courseModal.classList.add('opacity-0');
    courseModalContent.classList.add('scale-95');
    setTimeout(() => { courseModal.classList.add('hidden'); }, 300);
}

function submitNewCourse() {
    const title = document.getElementById('course-title').value;
    const desc = document.getElementById('course-desc').value;
    const cover = document.getElementById('course-cover').value;

    if(!title) {
        alert("课程名称不能为空哦！");
        return;
    }

    const newCourse = {
        title: title,
        description: desc,
        coverUrl: cover
    };

    fetch('/courses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newCourse)
    })
        .then(response => response.json())
        .then(data => {
            alert("🎉 课程创建成功！系统已存入 MySQL 数据库！");
            window.location.href = 'index.html';
            document.getElementById('course-title').value = '';
            document.getElementById('course-desc').value = '';
            document.getElementById('course-cover').value = '';
            closeCourseModal();
        })
        .catch(error => {
            console.error("保存失败:", error);
            alert("保存失败，请检查后端是否在运行！");
        });
}

// ================= 视频课件管理 =================
let currentVideoSource = 'url'; // 'url' | 'file'

// ★ 保留原函数名，页面上所有旧调用点无需任何修改 ★
function loadTeacherCourseData() {
    loadChapterList();
}

function loadChapterList() {
    fetch(`/courses/${courseId}/chapters`)
        .then(r => r.json())
        .then(list => {
            const container = document.getElementById('teacher-chapter-list');
            if (!container) return;
            const countEl = document.getElementById('chapter-count');
            if (countEl) countEl.innerText = `共 ${list.length} 个`;
            if (!list || list.length === 0) {
                container.innerHTML = `<p class="p-6 text-sm text-gray-400 text-center">还没有视频，点击右上角上传吧~</p>`;
                return;
            }
            container.innerHTML = list.map((c, i) => `
                <div class="grid grid-cols-12 gap-4 px-4 py-3 items-center border-b border-gray-100 text-sm hover:bg-[#f2f6fd] transition-colors">
                    <div class="col-span-6 flex items-center min-w-0">
                        <div class="w-6 h-6 bg-red-400 text-white rounded flex items-center justify-center text-xs shrink-0 mr-3">▶</div>
                        <span class="text-gray-800 truncate" title="${c.title}">${c.title}</span>
                    </div>
                    <div class="col-span-2 text-gray-600">${c.creator || '讲师'}</div>
                    <div class="col-span-2 text-gray-500">${formatChapterTime(c.createTime)}</div>
                    <div class="col-span-2 flex items-center justify-end space-x-1">
                        <button onclick="moveChapter(${c.id}, 'up')" ${i === 0 ? 'disabled class="text-gray-200 p-1 cursor-not-allowed"' : 'class="text-gray-400 hover:text-indigo-600 p-1 transition"'} title="上移">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"></path></svg>
                        </button>
                        <button onclick="moveChapter(${c.id}, 'down')" ${i === list.length - 1 ? 'disabled class="text-gray-200 p-1 cursor-not-allowed"' : 'class="text-gray-400 hover:text-indigo-600 p-1 transition"'} title="下移">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path></svg>
                        </button>
                        <button onclick="deleteChapter(${c.id}, '${c.title.replace(/'/g, "\\'")}')" class="text-red-400 hover:text-red-600 text-xs px-2 py-1 font-medium transition">删除</button>
                    </div>
                </div>
            `).join('');
        })
        .catch(error => console.error("加载视频失败:", error));
    fetch(`/courses/${courseId}/labs`)
        .then(res => res.json())
        .then(labs => {
            window.currentLabsData = labs;
            const labContainer = document.getElementById('teacher-lab-list');
            if(labContainer) {
                if(labs && labs.length > 0) {
                    let html = '';
                    labs.forEach((lab, index) => {
                        const title = lab.lab_title || lab.title || `实验任务 ${index + 1}`;
                        const desc = lab.lab_description || lab.description || '暂无描述';

                        const currentStatus = parseInt(lab.status) === 1 ? 1 : 0;
                        const badgeHtml = currentStatus === 1
                            ? `<span class="px-2 py-1 bg-blue-50 text-blue-600 text-xs rounded border border-blue-200">已部署</span>`
                            : `<span class="px-2 py-1 bg-gray-100 text-gray-500 text-xs rounded border border-gray-200">已下线 (草稿)</span>`;

                        const actionBtnHtml = currentStatus === 1
                            ? `<button type="button" onclick="toggleLabStatus(${lab.id}, 0)" class="text-red-500 hover:text-red-700 text-sm font-medium">下线</button>`
                            : `<button type="button" onclick="toggleLabStatus(${lab.id}, 1)" class="text-green-600 hover:text-green-800 text-sm font-medium">部署</button>`;

                        html += `
                        <div class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm hover:shadow-md transition p-5 flex flex-col justify-between">
                            <div>
                                <div class="flex justify-between items-start mb-2">
                                    <h3 class="font-bold text-gray-800 text-lg">${title}</h3>
                                    ${badgeHtml}
                                </div>
                                <p class="text-gray-500 text-sm mb-4 line-clamp-2">${desc}</p>
                            </div>
                            <div class="flex justify-between items-center pt-4 border-t border-gray-100">
                                <a href="workspace.html?labId=${lab.id}" target="_blank" class="text-xs text-gray-400 hover:text-indigo-600">预览环境</a>
                                
                                <div class="space-x-3">
                                    <button type="button" onclick="deleteLabAction(event, ${lab.id})" class="text-gray-400 hover:text-red-600 text-sm font-medium transition-colors">删除</button>
                                    <button type="button" onclick="openLabEdit(${lab.id})" class="text-indigo-600 hover:text-indigo-800 text-sm font-medium">配置</button>
                                    ${actionBtnHtml}
                                </div>
                                
                            </div>
                        </div>`;
                    });
                    labContainer.innerHTML = html;
                } else {
                    labContainer.innerHTML = `<div class="col-span-full p-8 text-center text-gray-400 border-2 border-dashed border-gray-200 rounded-lg bg-white">本课程暂未发布实验任务</div>`;
                }
            }
        })
        .catch(err => console.error("获取实验失败", err));
}


function formatChapterTime(t) {
    if (!t) return '-';
    const d = new Date(t);
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

window.moveChapter = function(id, direction) {
    fetch(`/chapters/${id}/move?direction=${direction}`, { method: 'PUT' })
        .then(() => loadChapterList());
};

window.deleteChapter = function(id, title) {
    if (!confirm(`确定删除视频「${title}」吗？此操作不可恢复！`)) return;
    fetch(`/chapters/${id}`, { method: 'DELETE' })
        .then(res => {
            if (res.ok) loadChapterList();
            else alert("删除失败");
        });
};

// ---- 弹窗 ----
window.switchVideoSource = function(mode) {
    currentVideoSource = mode;
    document.getElementById('video-source-url').classList.toggle('hidden', mode !== 'url');
    document.getElementById('video-source-file').classList.toggle('hidden', mode !== 'file');
    document.getElementById('tab-source-url').className = mode === 'url' ? 'flex-1 py-1.5 rounded bg-white shadow text-indigo-700' : 'flex-1 py-1.5 rounded text-gray-500';
    document.getElementById('tab-source-file').className = mode === 'file' ? 'flex-1 py-1.5 rounded bg-white shadow text-indigo-700' : 'flex-1 py-1.5 rounded text-gray-500';
};

function openVideoModal() {
    document.getElementById('upload-video-modal').classList.remove('hidden');
}

function closeVideoModal() {
    document.getElementById('upload-video-modal').classList.add('hidden');
    document.getElementById('video-title-input').value = '';
    document.getElementById('video-url-input').value = '';
    document.getElementById('video-file-input').value = '';
}

function getCurrentTeacherName() {
    try { return JSON.parse(localStorage.getItem('currentTeacher')).name || '讲师'; }
    catch (e) { return '讲师'; }
}

function submitNewVideo() {
    const title = document.getElementById('video-title-input').value.trim();
    if (!title) { alert("请填写视频标题！"); return; }

    if (currentVideoSource === 'url') {
        const url = document.getElementById('video-url-input').value.trim();
        if (!url) { alert("请填写视频链接！"); return; }
        saveChapter(title, url);
    } else {
        const fileInput = document.getElementById('video-file-input');
        if (!fileInput.files.length) { alert("请选择一个视频文件！"); return; }
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        document.getElementById('video-upload-progress').classList.remove('hidden');
        fetch('/chapters/upload-video', { method: 'POST', body: formData })
            .then(res => { if (!res.ok) throw new Error("上传失败"); return res.json(); })
            .then(data => saveChapter(title, data.url))
            .catch(err => alert(err.message))
            .finally(() => document.getElementById('video-upload-progress').classList.add('hidden'));
    }
}

function saveChapter(title, videoUrl) {
    fetch(`/courses/${courseId}/chapters`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: title, videoUrl: videoUrl, sortOrder: 999, creator: getCurrentTeacherName() })
    })
        .then(res => {
            if (res.ok) {
                alert("视频添加成功！");
                closeVideoModal();
                loadChapterList();
            } else {
                alert("添加失败，请检查后端状态！");
            }
        });
}


document.addEventListener("DOMContentLoaded", function() {
    const teacherDataStr = localStorage.getItem('currentTeacher');
    if (teacherDataStr) {
        const teacherData = JSON.parse(teacherDataStr);
        const nameEl = document.getElementById('current-user-name');
        if(nameEl) nameEl.innerText = teacherData.name;

        const titleEl = document.getElementById('current-user-title');
        if(titleEl) titleEl.innerText = teacherData.title || '讲师 / 开发者';

        const avatarUrl = teacherData.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(teacherData.name)}&background=E0E7FF&color=4F46E5&bold=true`;
        const avatarEl = document.getElementById('current-user-avatar');
        if(avatarEl) avatarEl.src = avatarUrl;
    }
});

async function loadCourseBasicInfo() {
    if (!courseId) return;
    try {
        const res = await fetch(`/courses/${courseId}`);
        const course = await res.json();

        const titleInput = document.getElementById('edit-course-title');
        if(titleInput) titleInput.value = course.title || '';

        const tagsInput = document.getElementById('edit-course-tags');
        if(tagsInput) tagsInput.value = course.tags || '';

        const descInput = document.getElementById('edit-course-desc');
        if(descInput) descInput.value = course.description || '';

        const introInput = document.getElementById('edit-course-intro');
        if(introInput) introInput.value = course.courseIntro || '';
    } catch (error) {
        console.error("加载课程基础信息失败:", error);
    }
}

async function saveCourseInfo() {
    if (!courseId) return;
    const titleInput = document.getElementById('edit-course-title');
    if(!titleInput) return;

    const title = titleInput.value.trim();
    if (!title) { alert("课程标题不能为空！"); return; }
    const updatedData = {
        title: title,
        tags: document.getElementById('edit-course-tags').value.trim(),
        description: document.getElementById('edit-course-desc').value.trim(),
        courseIntro: document.getElementById('edit-course-intro').value.trim()
    };
    try {
        const res = await fetch(`/courses/${courseId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(updatedData)
        });
        if (res.ok) {
            alert("✅ 课程信息保存成功！");
            const titleSpan = document.querySelector('#header-breadcrumb span:last-child');
            if(titleSpan) titleSpan.textContent = title;
        } else {
            alert("❌ 保存失败，请检查后端运行状态。");
        }
    } catch (error) {
        console.error("保存失败:", error);
        alert("网络错误，保存失败！");
    }
}

let currentEditLabId = null;

window.backToLabList = function() {
    document.getElementById('lab-edit-view').classList.add('hidden');
    document.getElementById('lab-list-view').classList.remove('hidden');
    currentEditLabId = null;
    if(document.getElementById('filename-objective')) document.getElementById('filename-objective').innerHTML = '';
    if(document.getElementById('filename-example')) document.getElementById('filename-example').innerHTML = '';
};

window.openLabEdit = function(labId) {
    const lab = window.currentLabsData.find(l => l.id === labId);
    if (!lab) return;
    currentEditLabId = labId;

    document.getElementById('edit-lab-title').value = lab.title || lab.lab_title || '';
    document.getElementById('edit-lab-desc').value = lab.description || lab.lab_description || '';
    document.getElementById('edit-lab-objective').value = lab.objective || '';
    document.getElementById('edit-lab-example').value = lab.example || '';

    const objFileBox = document.getElementById('filename-objective');
    const exFileBox = document.getElementById('filename-example');
    if (objFileBox) objFileBox.innerHTML = '';
    if (exFileBox) exFileBox.innerHTML = '';

    if (lab.files && lab.files.length > 0) {
        lab.files.forEach(file => {
            const fileBadge = `<span class="inline-flex items-center bg-indigo-50 text-indigo-600 px-2 py-1 rounded text-xs mr-2 mb-2 relative z-50" onclick="event.stopPropagation();">
                📎 ${file.fileName}
                <button type="button" onclick="deleteFile(event, ${file.id}, this.parentElement)" class="ml-1 text-indigo-400 hover:text-red-500 font-bold focus:outline-none">×</button>
            </span>`;
            if (file.fileType === 'objective' && objFileBox) {
                objFileBox.innerHTML += fileBadge;
            } else if (file.fileType === 'example' && exFileBox) {
                exFileBox.innerHTML += fileBadge;
            }
        });
    }

    document.getElementById('lab-list-view').classList.add('hidden');
    document.getElementById('lab-edit-view').classList.remove('hidden');
};

function submitNewLab(e) {
    if (e) e.preventDefault();

    const titleInput = document.getElementById('new-lab-title');
    if (!titleInput || !titleInput.value.trim()) {
        alert("实验名称不能为空哦！");
        return;
    }

    const newLab = { title: titleInput.value.trim() };
    fetch(`/courses/${courseId}/labs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newLab)
    })
        .then(res => {
            if (res.ok) {
                alert("🎉 新实验创建成功！");
                document.getElementById('lab-create-modal').classList.add('hidden');
                titleInput.value = '';
                loadTeacherCourseData();
            } else {
                alert("❌ 创建失败，请检查后端。");
            }
        })
        .catch(err => console.error("创建实验失败:", err));
}

function saveLabEdit(e) {
    if (e) e.preventDefault();
    if (!currentEditLabId) return;

    const updatedLab = {
        title: document.getElementById('edit-lab-title').value.trim(),
        description: document.getElementById('edit-lab-desc').value.trim(),
        objective: document.getElementById('edit-lab-objective').value.trim(),
        example: document.getElementById('edit-lab-example').value.trim()
    };

    fetch(`/labs/${currentEditLabId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedLab)
    })
        .then(res => {
            if (res.ok) {
                alert("✅ 实验信息保存成功！");
            } else {
                alert("❌ 保存失败，请检查后端 PUT 接口。");
            }
        })
        .catch(err => console.error("保存实验失败:", err));
}

function setupFileUpload(inputId, textId, fileType) {
    const fileInput = document.getElementById(inputId);
    const filenameDisplay = document.getElementById(textId);

    if (fileInput) {
        const dropZone = fileInput.parentElement;

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('border-indigo-500', 'bg-indigo-50');
        });
        dropZone.addEventListener('dragleave', (e) => {
            e.preventDefault();
            dropZone.classList.remove('border-indigo-500', 'bg-indigo-50');
        });
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('border-indigo-500', 'bg-indigo-50');

            if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                if (!currentEditLabId) {
                    alert("请先保存实验基础信息，才能上传附件！");
                    return;
                }
                Array.from(e.dataTransfer.files).forEach(file => {
                    uploadFile(file, fileType, filenameDisplay);
                });
            }
        });

        fileInput.addEventListener('change', (e) => {
            if (e.target.files && e.target.files.length > 0) {
                if (!currentEditLabId) {
                    alert("请先保存实验基础信息，才能上传附件！");
                    fileInput.value = '';
                    return;
                }
                Array.from(e.target.files).forEach(file => {
                    uploadFile(file, fileType, filenameDisplay);
                });
            }
        });
    }
}

function uploadFile(file, fileType, displayElement) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', fileType);

    fetch(`/labs/${currentEditLabId}/files`, {
        method: 'POST',
        body: formData
    })
        .then(res => {
            if(res.ok) return res.json();
            throw new Error("上传失败");
        })
        .then(data => {
            const fileBadge = `<span class="inline-flex items-center bg-indigo-50 text-indigo-600 px-2 py-1 rounded text-xs mr-2 mb-2 relative z-50" onclick="event.stopPropagation();">
            📎 ${file.name}
            <button type="button" onclick="deleteFile(event, ${data.id}, this.parentElement)" class="ml-1 text-indigo-400 hover:text-red-500 font-bold focus:outline-none">×</button>
        </span>`;
            displayElement.innerHTML += fileBadge;

            fetch(`/courses/${courseId}/labs`)
                .then(res => res.json())
                .then(labs => { window.currentLabsData = labs; });
        })
        .catch(err => {
            console.error("上传出错:", err);
            const fileBadge = `<span class="inline-block bg-red-50 text-red-600 px-2 py-1 rounded text-xs mr-2 mb-2">❌ ${file.name} (失败)</span>`;
            displayElement.innerHTML += fileBadge;
        });
}

window.toggleLabStatus = function(labId, newStatus) {
    fetch(`/labs/${labId}/status?status=${newStatus}`, { method: 'PUT' })
        .then(res => {
            if(res.ok) {
                console.log("✅ 状态成功保存到数据库");
                loadTeacherCourseData();
            } else {
                alert("❌ 后端未正确处理状态修改，请检查 Java 代码中的 PUT 接口！");
            }
        })
        .catch(err => console.error("切换状态出错:", err));
};

window.deleteFile = function(e, fileId, badgeElement) {
    e.preventDefault();
    e.stopPropagation();

    if(!confirm("确定要彻底删除这个附件吗？")) return;

    fetch(`/labs/files/${fileId}`, { method: 'DELETE' })
        .then(res => {
            if(res.ok) {
                console.log("✅ 文件彻底删除成功");
                badgeElement.remove();

                fetch(`/courses/${courseId}/labs`)
                    .then(r => r.json())
                    .then(labs => { window.currentLabsData = labs; });
            } else {
                alert("❌ 删除失败，后端未成功处理 DELETE 请求！");
            }
        })
        .catch(err => console.error("删除文件出错:", err));
};

window.deleteLabAction = function(e, labId) {
    if(e) { e.preventDefault(); e.stopPropagation(); }

    if(!confirm("⚠️ 警告：确定要彻底删除这个实验吗？\n删除后将无法恢复，且关联的所有附件也会被一同销毁！")) {
        return;
    }

    fetch(`/labs/${labId}`, { method: 'DELETE' })
        .then(res => {
            if(res.ok) {
                console.log("✅ 实验已彻底删除");
                loadTeacherCourseData();
            } else {
                alert("❌ 删除失败，请检查后端 Java 状态！");
            }
        })
        .catch(err => console.error("删除实验出错:", err));
};

// ==========================================
// ★★★ 无损新增：加课码专属逻辑模块 (绝对连通数据库) ★★★
// ==========================================
function updateInviteCodeDisplay(code) {
    // 智能寻址：兼容假数据带横杠的情况 (如 A7X9-P2M4 或 exactly 8位)
    const allTextNodes = document.querySelectorAll('span, div, p');
    allTextNodes.forEach(el => {
        const text = el.innerText ? el.innerText.trim() : '';
        if (text.match(/^[A-Z0-9]{8}$/) || text.match(/^[A-Z0-9]{4}-[A-Z0-9]{4}$/) || text === '生成中...') {
            el.innerText = code;
            el.dataset.isCode = "true"; // 打个标记
        }
    });

    // 如果已经打过标记了，直接更新
    document.querySelectorAll('[data-is-code="true"]').forEach(el => {
        el.innerText = code;
    });
}

function fetchCurrentInviteCode() {
    if (!courseId) return;
    fetch(`/courses/${courseId}`)
        .then(res => res.json())
        .then(course => {
            if (course.inviteCode) {
                updateInviteCodeDisplay(course.inviteCode);
            } else {
                // 如果数据库里目前是空的，自动触发一次生成
                generateNewInviteCode(true);
            }
        })
        .catch(err => console.error("抓取真实加课码失败:", err));
}

function generateNewInviteCode(isSilent = false) {
    if (!courseId) return;
    if (!isSilent && !confirm("⚠️ 警告：重新生成后，旧的加课码将立即失效！确定要重新生成吗？")) return;

    // 直接调用后端专属接口，让后端查重并生成
    fetch(`/courses/${courseId}/regenerate-code`, {
        method: 'PUT'
    })
        .then(res => res.json())
        .then(data => {
            if (data.inviteCode) {
                if (!isSilent) alert(`✅ 加课码已成功更新为: ${data.inviteCode}`);
                updateInviteCodeDisplay(data.inviteCode); // 瞬间无刷新更新页面文字
            }
        })
        .catch(err => {
            console.error("更新加课码失败:", err);
            if (!isSilent) alert("❌ 重新生成失败，请检查后端运行状态。");
        });
}

function initInviteCodeFeature() {
    fetchCurrentInviteCode(); // 页面一加载就把真实加课码刷上去

    // 智能寻址：找到那个写着“重新生成”的按钮，并绑定事件
    const allButtons = document.querySelectorAll('button');
    allButtons.forEach(btn => {
        if (btn.innerText && btn.innerText.includes('重新生成')) {
            btn.type = "button";
            btn.onclick = () => generateNewInviteCode(false);
        }
    });
}

// ==========================================
// ★★★ 权限与设置面板 ★★★
// ==========================================
function loadConfigPanel() {
    const tbody = document.getElementById('config-lab-tbody');

    // 公共课不开放此功能
    fetch('/courses')
        .then(res => res.json())
        .then(courses => {
            const course = courses.find(c => c.id == courseId);
            const cType = course ? (course.courseType || course.course_type) : '';
            if (cType === 'public') {
                tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-gray-400">公共课未开放该功能</td></tr>`;
                return;
            }
            loadConfigLabs();
        })
        .catch(() => {
            tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-red-400">课程信息加载失败</td></tr>`;
        });
}

function loadConfigLabs() {
    const tbody = document.getElementById('config-lab-tbody');
    fetch(`/courses/${courseId}/labs`)
        .then(res => res.json())
        .then(labs => {
            if (!labs || labs.length === 0) {
                tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-gray-400">本课程暂无实验</td></tr>`;
                return;
            }
            let html = '';
            labs.forEach(lab => {
                const mode = lab.showcaseMode || 1; // 默认开放所有
                html += `
                    <tr class="hover:bg-gray-50 transition">
                        <td class="p-4">
                            <p class="font-medium text-gray-900 truncate max-w-[300px]" title="${lab.title}">${lab.title}</p>
                        </td>
                        ${renderModeCell(lab.id, 1, mode)}
                        ${renderModeCell(lab.id, 2, mode)}
                        ${renderModeCell(lab.id, 3, mode)}
                    </tr>`;
            });
            tbody.innerHTML = html;
        })
        .catch(() => {
            tbody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-red-400">实验列表加载失败</td></tr>`;
        });
}

// 渲染单个勾选格子
function renderModeCell(labId, cellMode, currentMode) {
    const checked = (cellMode === currentMode);
    return `
        <td class="p-4 text-center">
            <button onclick="setShowcaseMode(${labId}, ${cellMode})"
                class="w-5 h-5 rounded border inline-flex items-center justify-center transition align-middle
                ${checked
        ? 'bg-indigo-600 border-indigo-600 text-white'
        : 'bg-white border-gray-300 hover:border-indigo-400'}">
                ${checked ? '✓' : ''}
            </button>
        </td>`;
}

function setShowcaseMode(labId, mode) {
    fetch(`/courses/labs/${labId}/showcase-mode`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode: mode })
    })
        .then(res => res.ok ? res.text() : Promise.reject())
        .then(() => loadConfigLabs())   // 重新渲染，保证三选一状态正确
        .catch(() => alert("❌ 设置失败，请检查后端"));
}

// 删除课程
function deleteCourse() {
    if (!confirm("确定要删除本课程吗？\n课程的章节、实验、成员数据将一并删除，此操作不可恢复！")) return;
    fetch(`/courses/${courseId}`, { method: 'DELETE' })
        .then(res => res.ok ? res.text() : Promise.reject())
        .then(() => {
            alert("课程已删除");
            window.location.href = "index.html"; // 回资源库
        })
        .catch(() => alert("❌ 删除失败，请检查后端"));
}



// ==========================================
// ==========================================
// ★★★ 课程封面 ★★★
// ==========================================
const PRESET_COVER_COUNT = 5;   // ★ 改成你 covers 文件夹里的实际张数
const coverState = { img: null, presetUrl: null };

function openCoverModal() {
    coverState.img = null;
    coverState.presetUrl = null;
    // 生成素材库网格
    let html = '';
    for (let i = 1; i <= PRESET_COVER_COUNT; i++) {
        const url = `/covers/cover${i}.jpg`;
        html += `<div onclick="selectPresetCover(this, '${url}')" class="preset-cover-item aspect-video rounded-lg overflow-hidden border-2 border-transparent cursor-pointer hover:opacity-80 transition">
                    <img src="${url}" class="w-full h-full object-cover">
                 </div>`;
    }
    document.getElementById('cover-panel-preset').innerHTML = html;
    switchCoverTab('preset');
    document.getElementById('cover-drop-hint').classList.remove('hidden');
    document.getElementById('cover-crop-area').classList.add('hidden');
    const m = document.getElementById('cover-modal');
    m.classList.remove('hidden');
    m.classList.add('flex');
}

function closeCoverModal() {
    const m = document.getElementById('cover-modal');
    m.classList.add('hidden');
    m.classList.remove('flex');
}

function switchCoverTab(tab) {
    const isPreset = tab === 'preset';
    document.getElementById('cover-panel-preset').classList.toggle('hidden', !isPreset);
    document.getElementById('cover-panel-upload').classList.toggle('hidden', isPreset);
    document.getElementById('cover-tab-preset').className = `px-4 py-1.5 rounded-md text-sm font-medium transition ${isPreset ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`;
    document.getElementById('cover-tab-upload').className = `px-4 py-1.5 rounded-md text-sm font-medium transition ${!isPreset ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`;
}

function selectPresetCover(el, url) {
    coverState.presetUrl = url;
    coverState.img = null;   // 选了素材就作废本地上传
    document.querySelectorAll('.preset-cover-item').forEach(d => d.classList.replace('border-indigo-600', 'border-transparent'));
    el.classList.replace('border-transparent', 'border-indigo-600');
}

function handleCoverSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = function(e) {
        const img = new Image();
        img.onload = function() {
            coverState.img = img;
            coverState.presetUrl = null;   // 上传了就作废素材选择
            document.getElementById('cover-drop-hint').classList.add('hidden');
            document.getElementById('cover-crop-area').classList.remove('hidden');
            document.getElementById('cover-offset').value = 50;
            updateCoverPreview();
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

function updateCoverPreview() {
    if (!coverState.img) return;
    const p = document.getElementById('cover-offset').value;
    const preview = document.getElementById('cover-crop-preview');
    preview.src = coverState.img.src;
    // 图片比 16:9 高就上下滑，比 16:9 宽就左右滑，object-position 两个方向都设，只有溢出的方向生效
    preview.style.objectPosition = `${p}% ${p}%`;
}

function getCroppedCover() {
    const img = coverState.img;
    const canvas = document.createElement('canvas');
    canvas.width = 800;
    canvas.height = 450;
    const ctx = canvas.getContext('2d');
    const scale = Math.max(800 / img.width, 450 / img.height);
    const dw = img.width * scale;
    const dh = img.height * scale;
    const p = document.getElementById('cover-offset').value / 100;
    const dx = -(dw - 800) * p;
    const dy = -(dh - 450) * p;
    ctx.drawImage(img, dx, dy, dw, dh);
    return canvas.toDataURL('image/jpeg', 0.85);
}

function saveCover() {
    let coverValue = null;
    if (coverState.presetUrl) {
        coverValue = coverState.presetUrl;          // 素材库：只存 URL，很短
    } else if (coverState.img) {
        coverValue = getCroppedCover();             // 本地上传：存截取后的 base64
    } else {
        alert("请先选择一张封面");
        return;
    }

    fetch(`/courses/${courseId}/cover`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ coverUrl: coverValue })
    })
        .then(res => {
            if (!res.ok) throw new Error();
            alert("✅ 封面已更新");
            closeCoverModal();
        })
        .catch(() => alert("❌ 保存失败，请检查后端状态"));
}

// ==========================================

document.addEventListener('DOMContentLoaded', () => {
        loadCourseBasicInfo();
        loadTeacherCourseData();

        // 初始化时如果是批改大厅或者加课申请页，直接拉取数据
        const activePanel = document.querySelector('.main-panel.flex');
        if (activePanel) {
            if (activePanel.id === 'panel-grading') {
                loadGradingList();
            } else if (activePanel.id === 'panel-applications' || activePanel.innerHTML.includes('加课申请') || activePanel.innerHTML.includes('审批大厅')) {
                loadCourseApplications();
            }
        }

        setupFileUpload('file-objective', 'filename-objective', 'objective');
        setupFileUpload('file-example', 'filename-example', 'example');

        const confirmCreateBtn = document.querySelector('#lab-create-modal button:last-child');
        if (confirmCreateBtn) {
            confirmCreateBtn.type = "button";
            confirmCreateBtn.onclick = submitNewLab;
        }

        const saveEditBtn = document.querySelector('#lab-edit-view button:last-child');
        if (saveEditBtn) {
            saveEditBtn.type = "button";
            saveEditBtn.onclick = saveLabEdit;
        }

        // ★★★ 挂载专属加课码逻辑 ★★★
        initInviteCodeFeature();
    }






);