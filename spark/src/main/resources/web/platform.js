const state = {
    datasets: [], datasetId: null, dataset: null, mappings: [], capabilities: null,
    jobs: [], jobId: null, jobFilter: "ALL", datasetTab: "basic", analysisModule: "overview",
    profiles: new Map(), system: null
};

const MODULES = ["overview", "daily_trend", "hourly", "behavior", "funnel", "items", "categories", "user_segmentation", "repurchase", "sales"];
const MODULE_CAPABILITY = {overview: "overview", daily_trend: "time_analysis", hourly: "time_analysis", behavior: "behavior_analysis", funnel: "funnel_analysis", items: "item_analysis", categories: "category_analysis", user_segmentation: "segmentation_analysis", repurchase: "repurchase_analysis", sales: "sales_analysis"};
const MODULE_LABEL = {overview: "总览", daily_trend: "每日趋势", hourly: "时段分析", behavior: "用户行为", funnel: "行为路径", items: "商品分析", categories: "类目分析", user_segmentation: "用户分群", repurchase: "复购分析", sales: "销售分析"};
const CAPABILITY_LABEL = {overview: "数据概览", behavior_analysis: "用户行为分析", item_analysis: "商品分析", category_analysis: "类目分析", time_analysis: "时间趋势分析", funnel_analysis: "行为路径分析", repurchase_analysis: "复购分析", order_analysis: "订单分析", sales_analysis: "销售分析", revenue_analysis: "销售额分析", segmentation_analysis: "用户分群"};
const FIELD_LABEL = {user_id: "用户 ID", item_id: "商品 ID", category_id: "类目 ID", event_type: "行为类型", event_time: "行为时间", order_id: "订单 ID", order_time: "下单时间", quantity: "数量", unit_price: "单价", amount: "金额"};
const STANDARD_FIELDS = ["", "user_id", "item_id", "category_id", "event_type", "event_time", "order_id", "order_time", "quantity", "unit_price", "amount"];
const BEHAVIOR_LABEL = {pv: "浏览", cart: "加购", fav: "收藏", buy: "购买"};
const COLORS = ["#2f72dc", "#f5a623", "#32a673", "#e25762", "#8468d7", "#27a5b8"];
const numberFormat = new Intl.NumberFormat("zh-CN");
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const esc = value => String(value ?? "").replace(/[&<>"']/g, char => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[char]));

async function api(path, options = {}) {
    const response = await fetch(path, {headers: {"Content-Type": "application/json", ...(options.headers || {})}, ...options});
    if (!response.ok) {
        let message;
        try { message = (await response.json()).error; } catch { message = await response.text(); }
        throw new Error(message || `请求失败（${response.status}）`);
    }
    return (response.headers.get("content-type") || "").includes("json") ? response.json() : response.text();
}

function toast(message) {
    const node = $("#toast");
    node.textContent = message;
    node.style.display = "block";
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.style.display = "none", 3600);
}

function fmtNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? numberFormat.format(number) : "待分析";
}

function fmtCompact(value) {
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0) return "等待统计";
    if (number >= 100000000) return `约 ${(number / 100000000).toFixed(2)} 亿条`;
    if (number >= 10000) return `约 ${(number / 10000).toFixed(1)} 万条`;
    return `${fmtNumber(number)} 条`;
}

function fmtBytes(value) {
    const bytes = Number(value);
    if (!Number.isFinite(bytes) || bytes < 0) return "-";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let size = bytes, index = 0;
    while (size >= 1024 && index < units.length - 1) { size /= 1024; index++; }
    return `${index === 0 ? size.toFixed(0) : size.toFixed(2)} ${units[index]}`;
}

function fmtTime(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return esc(value);
    return new Intl.DateTimeFormat("zh-CN", {year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false}).format(date).replaceAll("/", "-");
}

function duration(job) {
    if (!job?.startedAt) return "-";
    const end = job.finishedAt ? new Date(job.finishedAt) : new Date();
    const seconds = Math.max(0, Math.round((end - new Date(job.startedAt)) / 1000));
    if (seconds < 60) return `${seconds} 秒`;
    return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
}

function statusMeta(status) {
    return ({READY: ["就绪", "ready"], RUNNING: ["运行中", "running"], SUCCESS: ["成功", "success"], FAILED: ["失败", "failed"], PENDING: ["等待执行", "pending"], UP: ["正常", "up"], IDLE: ["空闲", "idle"], NOT_INITIALIZED: ["未初始化", "warning"], NOT_CONFIGURED: ["未配置", "neutral"], DOWN: ["异常", "down"]})[status] || [status || "未知", "neutral"];
}

function badge(status) {
    const [label, style] = statusMeta(status);
    return `<span class="badge ${style}">${esc(label)}</span>`;
}

function friendlyDatasetName(dataset) {
    if (!dataset) return "请选择数据集";
    const path = String(dataset.filePath || "").toLowerCase();
    const name = String(dataset.name || "");
    if (String(dataset.datasetType).toUpperCase() === "TAOBAO_BEHAVIOR" && Number(dataset.fileSize) > 1000000000) return "淘宝用户行为数据（正式数据）";
    if (path.includes("sample_order.csv") || name.toLowerCase().startsWith("mysql-acceptance")) return "订单交易样例数据";
    if (String(dataset.datasetType).toUpperCase() === "TAOBAO_BEHAVIOR") return name || "淘宝用户行为数据";
    return name || `数据集 ${dataset.id}`;
}

function datasetPurpose(dataset) {
    if (!dataset) return "";
    if (String(dataset.datasetType).toUpperCase() === "TAOBAO_BEHAVIOR" && Number(dataset.fileSize) > 1000000000) return "正式实验数据 · 淘宝用户行为分析";
    if (String(dataset.filePath || "").toLowerCase().includes("sample_order.csv")) return "泛化验证数据 · 订单与销售分析";
    return dataset.description || "电商分析数据";
}

function datasetTypeLabel(type) {
    return String(type).toUpperCase() === "TAOBAO_BEHAVIOR" ? "淘宝行为" : "通用 CSV";
}

function metricCard(label, value, note) {
    return `<div class="metric-card"><div class="metric-top"><span>${esc(label)}</span></div><strong>${esc(value)}</strong><small>${esc(note || "")}</small></div>`;
}

function empty(message, detail = "") {
    return `<div class="empty"><div><strong>${esc(message)}</strong><span>${esc(detail)}</span></div></div>`;
}

function showPage(name) {
    $$(".page").forEach(node => node.classList.remove("active"));
    $(`#page-${name}`).classList.add("active");
    $$("#nav button").forEach(node => node.classList.toggle("active", node.dataset.page === name));
    if (name === "jobs") refreshJobs();
    if (name === "analytics") renderAnalytics();
    if (name === "status") renderSystemStatus();
    window.scrollTo({top: 0, behavior: "smooth"});
}

function setDatasetTab(name) {
    state.datasetTab = name;
    $$("#dataset-tabs button").forEach(node => node.classList.toggle("active", node.dataset.tab === name));
    $$("#dataset-workspace .tab-pane").forEach(node => node.classList.toggle("active", node.id === `dataset-tab-${name}`));
}

function availableModules() {
    const enabled = new Set(state.capabilities?.enabled || []);
    return MODULES.filter(module => enabled.has(MODULE_CAPABILITY[module]));
}

function latestSuccessfulJob(datasetId) {
    return state.jobs.filter(job => job.datasetId === datasetId && job.status === "SUCCESS").sort((a, b) => b.id - a.id)[0];
}

function chart(id, option) {
    const node = document.getElementById(id);
    if (!node || typeof echarts === "undefined") return;
    let instance = echarts.getInstanceByDom(node);
    if (!instance) instance = echarts.init(node);
    instance.clear();
    instance.setOption(option);
    requestAnimationFrame(() => instance.resize());
}

function axisNumber(value) {
    const number = Number(value);
    if (number >= 100000000) return `${(number / 100000000).toFixed(1)}亿`;
    if (number >= 10000) return `${(number / 10000).toFixed(0)}万`;
    return numberFormat.format(number);
}

const baseGrid = {left: 25, right: 24, top: 32, bottom: 28, containLabel: true};
const baseTooltip = {trigger: "axis", axisPointer: {type: "shadow"}};

async function safeAnalytics(module) {
    try { return await api(`/api/datasets/${state.datasetId}/analytics/${module}`); }
    catch { return []; }
}

async function loadInitialData() {
    const [datasets, jobs] = await Promise.all([api("/api/datasets"), api("/api/jobs")]);
    state.datasets = datasets;
    state.jobs = jobs;
    if (!state.datasetId || !datasets.some(dataset => dataset.id === state.datasetId)) {
        state.datasetId = datasets.slice().sort((a, b) => Number(b.fileSize) - Number(a.fileSize))[0]?.id || null;
    }
    renderDatasetSelector();
    await loadDatasetContext();
    await loadDatasetProfiles();
    renderDatasetPage();
    renderJobs();
    await renderOverview();
}

function renderDatasetSelector() {
    const select = $("#dataset-select");
    if (!state.datasets.length) {
        select.innerHTML = '<option value="">请选择数据集</option>';
        select.disabled = true;
        return;
    }
    select.disabled = false;
    select.innerHTML = state.datasets.map(dataset => `<option value="${dataset.id}" title="Dataset ID ${dataset.id}">${esc(friendlyDatasetName(dataset))}</option>`).join("");
    select.value = String(state.datasetId);
}

async function loadDatasetContext() {
    if (!state.datasetId) return;
    const [dataset, mappings, capabilities] = await Promise.all([
        api(`/api/datasets/${state.datasetId}`),
        api(`/api/datasets/${state.datasetId}/mapping`),
        api(`/api/datasets/${state.datasetId}/capabilities`)
    ]);
    state.dataset = dataset;
    state.mappings = mappings;
    state.capabilities = capabilities;
    if (!availableModules().includes(state.analysisModule)) state.analysisModule = availableModules()[0] || null;
}

async function loadDatasetProfiles() {
    const profiles = await Promise.all(state.datasets.map(async dataset => {
        try {
            const [mappings, capabilities] = await Promise.all([api(`/api/datasets/${dataset.id}/mapping`), api(`/api/datasets/${dataset.id}/capabilities`)]);
            return [dataset.id, {mappings, capabilities}];
        } catch { return [dataset.id, {mappings: [], capabilities: {enabled: [], disabledReasons: {}}}]; }
    }));
    state.profiles = new Map(profiles);
}

async function selectDataset(id, targetPage, targetTab) {
    state.datasetId = Number(id);
    renderDatasetSelector();
    await loadDatasetContext();
    renderDatasetPage();
    renderJobs();
    await renderOverview();
    if (targetPage) showPage(targetPage);
    if (targetTab) setDatasetTab(targetTab);
}

async function renderOverview() {
    if (!state.dataset) {
        $("#overview-subtitle").textContent = "请先登记一个数据集。";
        $("#overview-cards").innerHTML = empty("暂无数据集");
        return;
    }
    const latest = latestSuccessfulJob(state.datasetId);
    const modules = availableModules();
    let system;
    try { system = await api("/api/status"); state.system = system; } catch { system = null; }
    $("#overview-subtitle").textContent = `${friendlyDatasetName(state.dataset)} · ${datasetPurpose(state.dataset)}`;
    $("#overview-cards").innerHTML = [
        metricCard("当前数据集", friendlyDatasetName(state.dataset), `内部 Dataset ID ${state.dataset.id}`),
        metricCard("最近分析", latest ? fmtTime(latest.finishedAt) : "暂无成功任务", latest ? `Job #${latest.id}` : "等待首次运行"),
        metricCard("数据规模", fmtNumber(state.dataset.rowCount), fmtCompact(state.dataset.rowCount)),
        metricCard("分析模块", `${modules.length} 个`, "根据已确认字段动态启用"),
        metricCard("平台状态", system?.api === "UP" && system?.mysql === "UP" ? "运行正常" : "需要检查", `MySQL ${statusMeta(system?.mysql)[0]} · Spark ${statusMeta(system?.spark)[0]}`)
    ].join("");

    const recent = state.jobs.slice().sort((a, b) => b.id - a.id).slice(0, 4);
    $("#overview-jobs").innerHTML = recent.length ? `<table><thead><tr><th>任务</th><th>数据集</th><th>状态</th><th>完成时间</th></tr></thead><tbody>${recent.map(job => `<tr><td class="number">Job #${job.id}</td><td>${esc(friendlyDatasetName(state.datasets.find(item => item.id === job.datasetId)))}</td><td>${badge(job.status)}</td><td>${fmtTime(job.finishedAt || job.createdAt)}</td></tr>`).join("")}</tbody></table>` : empty("暂无任务");

    const confirmed = state.mappings.filter(mapping => mapping.confirmed).length;
    const qualityItems = [
        ["文件已登记", fmtBytes(state.dataset.fileSize), state.dataset.fileSize > 0],
        ["字段映射", `${confirmed}/${state.mappings.length} 已确认`, confirmed === state.mappings.length && confirmed > 0],
        ["标准行数", fmtNumber(state.dataset.rowCount), state.dataset.rowCount >= 0],
        ["最近任务", latest ? `Job #${latest.id} 成功` : "暂无成功任务", Boolean(latest)]
    ];
    $("#quality-content").innerHTML = `<div class="quality-list">${qualityItems.map(([label, detail, ok]) => `<div class="quality-row"><span class="quality-check">${ok ? "✓" : "!"}</span><div><strong>${label}</strong><small>${esc(detail)}</small></div>${ok ? badge("READY") : badge("PENDING")}</div>`).join("")}</div>`;

    const enabled = new Set(state.capabilities?.enabled || []);
    const [behavior, trend] = await Promise.all([
        enabled.has("behavior_analysis") ? safeAnalytics("behavior") : Promise.resolve([]),
        enabled.has("time_analysis") ? safeAnalytics("daily_trend") : Promise.resolve([])
    ]);
    renderBehaviorPie("overview-behavior-chart", behavior);
    renderTrend("overview-trend-chart", trend);
}

function renderBehaviorPie(id, rows) {
    const node = document.getElementById(id);
    if (!rows?.length) { node.innerHTML = empty("当前数据集无行为结构结果", "Capability 不支持或尚未生成成功版本"); return; }
    node.innerHTML = "";
    chart(id, {color: COLORS, tooltip: {trigger: "item", formatter: params => `${params.name}<br/><b>${fmtNumber(params.value)}</b>（${params.percent}%）`}, legend: {bottom: 0}, series: [{type: "pie", radius: ["42%", "70%"], center: ["50%", "45%"], itemStyle: {borderRadius: 7, borderColor: "#fff", borderWidth: 3}, label: {formatter: "{b}\n{d}%"}, data: rows.map(row => ({name: BEHAVIOR_LABEL[row.event_type] || row.event_type, value: row.count}))}]});
}

function renderTrend(id, rows) {
    const node = document.getElementById(id);
    if (!rows?.length) { node.innerHTML = empty("暂无时间趋势", "完成分析后显示"); return; }
    node.innerHTML = "";
    const isOrder = Object.hasOwn(rows[0], "order_count");
    chart(id, {color: [COLORS[0], COLORS[3]], tooltip: {trigger: "axis"}, legend: {bottom: 0, data: isOrder ? ["订单数", "销售额"] : ["行为数", "购买数"]}, grid: {...baseGrid, bottom: 48}, xAxis: {type: "category", data: rows.map(row => row.day), axisLabel: {rotate: rows.length > 12 ? 28 : 0}}, yAxis: {type: "value", axisLabel: {formatter: axisNumber}}, series: isOrder ? [{name: "订单数", type: "line", smooth: true, data: rows.map(row => row.order_count)}, {name: "销售额", type: "line", smooth: true, data: rows.map(row => row.revenue)}] : [{name: "行为数", type: "line", smooth: true, areaStyle: {opacity: .08}, data: rows.map(row => row.event_count)}, {name: "购买数", type: "line", smooth: true, data: rows.map(row => row.buy_count)}]});
}

function renderDatasetPage() {
    $("#dataset-count").textContent = `${state.datasets.length} 个数据集`;
    $("#dataset-table").innerHTML = state.datasets.length ? `<table><thead><tr><th>名称</th><th>类型</th><th>数据规模</th><th>字段</th><th>可用分析</th><th>状态</th><th>最近分析</th><th>操作</th></tr></thead><tbody>${state.datasets.map(dataset => {
        const profile = state.profiles.get(dataset.id) || {mappings: [], capabilities: {enabled: []}};
        return `<tr><td class="primary-cell"><strong>${esc(friendlyDatasetName(dataset))}</strong><small>${esc(datasetPurpose(dataset))}</small></td><td>${datasetTypeLabel(dataset.datasetType)}</td><td><strong>${fmtNumber(dataset.rowCount)}</strong><br><small>${fmtBytes(dataset.fileSize)}</small></td><td>${profile.mappings.length} 个</td><td>${availableModuleCount(profile.capabilities)} 个模块</td><td>${badge(dataset.status)}</td><td>${fmtTime(dataset.lastAnalyzedAt)}</td><td><button type="button" class="table-action dataset-view" data-id="${dataset.id}">查看</button> <button type="button" class="table-action dataset-analyze" data-id="${dataset.id}">分析</button></td></tr>`;
    }).join("")}</tbody></table>` : empty("尚未接入数据集");

    $$(".dataset-view").forEach(button => button.onclick = () => selectDataset(button.dataset.id, "datasets", "basic"));
    $$(".dataset-analyze").forEach(button => button.onclick = () => selectDataset(button.dataset.id, "analytics"));
    renderDatasetWorkspace();
}

function availableModuleCount(capabilities) {
    const enabled = new Set(capabilities?.enabled || []);
    return MODULES.filter(module => enabled.has(MODULE_CAPABILITY[module])).length;
}

function renderDatasetWorkspace() {
    if (!state.dataset) {
        $("#dataset-workspace").innerHTML = empty("请选择数据集");
        return;
    }
    $("#dataset-hero").innerHTML = `<h2>${esc(friendlyDatasetName(state.dataset))}</h2><p>${esc(datasetPurpose(state.dataset))}</p><div class="meta-line">${badge(state.dataset.status)}<span>Dataset ID ${state.dataset.id}</span><span>${esc(datasetTypeLabel(state.dataset.datasetType))}</span><span>${fmtBytes(state.dataset.fileSize)}</span></div>`;
    $("#dataset-tab-basic").innerHTML = `<div class="detail-grid">${[
        ["数据集名称", friendlyDatasetName(state.dataset)], ["内部名称", state.dataset.name], ["数据类型", datasetTypeLabel(state.dataset.datasetType)],
        ["数据行数", fmtNumber(state.dataset.rowCount)], ["文件大小", fmtBytes(state.dataset.fileSize)], ["最近分析", fmtTime(state.dataset.lastAnalyzedAt)],
        ["文件路径", state.dataset.filePath], ["表头", state.dataset.hasHeader ? "包含表头" : "无表头"], ["分隔符", state.dataset.delimiter]
    ].map(([label, value]) => `<div class="detail-item"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`).join("")}</div>`;
    renderPreview();
    renderMappings();
    renderCapabilities();
    renderDatasetHistory();
    setDatasetTab(state.datasetTab);
}

function renderPreview() {
    if (!state.mappings.length) { $("#dataset-tab-preview").innerHTML = empty("暂无字段样例"); return; }
    const rows = Math.max(...state.mappings.map(mapping => (mapping.samples || []).length), 0);
    $("#dataset-tab-preview").innerHTML = `<div class="notice info">为避免扫描大型文件，此处展示 Dataset Manager 有界探测获得的字段样例，不复制原始数据。</div><div class="table-wrap"><table><thead><tr>${state.mappings.map(mapping => `<th>${esc(mapping.sourceField)}<br><small>${esc(FIELD_LABEL[mapping.standardField] || mapping.standardField || "未映射")}</small></th>`).join("")}</tr></thead><tbody>${Array.from({length: rows}, (_, index) => `<tr>${state.mappings.map(mapping => `<td>${esc((mapping.samples || [])[index] ?? "-")}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
}

function renderMappings() {
    $("#mapping-table").innerHTML = state.mappings.length ? `<div class="table-wrap"><table><thead><tr><th>原始字段</th><th>样例</th><th>推断类型</th><th>标准字段</th><th>匹配状态</th></tr></thead><tbody>${state.mappings.map((mapping, index) => `<tr><td><strong>${esc(mapping.sourceField)}</strong><br><small>列 ${mapping.sourceIndex + 1}</small></td><td>${esc((mapping.samples || []).slice(0, 3).join(" / "))}</td><td>${esc(mapping.inferredType)}</td><td><select data-map="${index}">${STANDARD_FIELDS.map(field => `<option value="${field}" ${field === mapping.standardField ? "selected" : ""}>${esc(field ? `${FIELD_LABEL[field] || field} · ${field}` : "不映射")}</option>`).join("")}</select></td><td>${mapping.confirmed ? '<span class="badge success">人工确认</span>' : mapping.confidence >= .9 ? '<span class="badge running">自动匹配</span>' : '<span class="badge pending">待确认</span>'}</td></tr>`).join("")}</tbody></table></div>` : empty("暂无映射建议");
}

function renderCapabilities() {
    const enabled = new Set(state.capabilities?.enabled || []);
    const disabled = state.capabilities?.disabledReasons || {};
    const keys = Object.keys(CAPABILITY_LABEL);
    $("#capabilities").innerHTML = keys.map(key => {
        const ok = enabled.has(key);
        const missing = disabled[key] || [];
        return `<div class="capability-card ${ok ? "enabled" : "disabled"}"><span class="cap-icon">${ok ? "✓" : "×"}</span><strong>${esc(CAPABILITY_LABEL[key])}</strong><p>${ok ? "标准字段完整，可用于分析" : `缺少：${missing.map(field => FIELD_LABEL[field] || field).join(" 或 ") || "必要字段"}`}</p></div>`;
    }).join("");
}

function renderDatasetHistory() {
    const jobs = state.jobs.filter(job => job.datasetId === state.datasetId).sort((a, b) => b.id - a.id);
    $("#dataset-history").innerHTML = jobs.length ? `<table><thead><tr><th>任务编号</th><th>状态</th><th>分析模块</th><th>开始时间</th><th>用时</th></tr></thead><tbody>${jobs.map(job => `<tr class="clickable dataset-job" data-id="${job.id}"><td>Job #${job.id}</td><td>${badge(job.status)}</td><td>${esc(moduleListLabel(job.requestedModules))}</td><td>${fmtTime(job.startedAt || job.createdAt)}</td><td>${duration(job)}</td></tr>`).join("")}</tbody></table>` : empty("当前数据集暂无分析记录");
    $$(".dataset-job").forEach(row => row.onclick = () => { state.jobId = Number(row.dataset.id); showPage("jobs"); loadJobDetail(); });
}

function moduleListLabel(value) {
    const list = String(value || "").split(",").filter(Boolean);
    return list.length ? list.map(item => MODULE_LABEL[item] || item).join("、") : "全部可用模块";
}

function jobStage(job) {
    if (job.status === "PENDING") return ["等待执行", 10];
    if (job.status === "RUNNING") return ["构建标准模型并执行分析", null];
    if (job.status === "SUCCESS") return ["结果写入完成", 100];
    if (job.status === "FAILED") return ["执行失败", 100];
    return ["状态未知", 0];
}

async function refreshJobs() {
    try { state.jobs = await api("/api/jobs"); renderJobs(); renderDatasetHistory(); }
    catch (error) { toast(error.message); }
}

function renderJobs() {
    renderModuleChecks();
    const filtered = state.jobs.filter(job => state.jobFilter === "ALL" || job.status === state.jobFilter).sort((a, b) => b.id - a.id);
    $("#job-table").innerHTML = filtered.length ? `<table><thead><tr><th>任务编号</th><th>数据集</th><th>状态</th><th>执行阶段</th><th>分析模块</th><th>开始时间</th><th>用时</th></tr></thead><tbody>${filtered.map(job => `<tr class="clickable job-row" data-id="${job.id}"><td><strong>Job #${job.id}</strong></td><td>${esc(friendlyDatasetName(state.datasets.find(dataset => dataset.id === job.datasetId)))}</td><td>${badge(job.status)}</td><td>${esc(jobStage(job)[0])}</td><td>${esc(moduleListLabel(job.requestedModules))}</td><td>${fmtTime(job.startedAt || job.createdAt)}</td><td>${duration(job)}</td></tr>`).join("")}</tbody></table>` : empty("当前筛选条件下暂无任务");
    $$(".job-row").forEach(row => row.onclick = () => { state.jobId = Number(row.dataset.id); loadJobDetail(); });
    if (!state.jobId && state.jobs.length) state.jobId = state.jobs.slice().sort((a, b) => b.id - a.id)[0].id;
    loadJobDetail();
}

function renderModuleChecks() {
    const enabled = new Set(state.capabilities?.enabled || []);
    $("#module-list").innerHTML = MODULES.map(module => `<label><input type="checkbox" value="${module}" ${enabled.has(MODULE_CAPABILITY[module]) ? "checked" : "disabled"}>${esc(MODULE_LABEL[module])}</label>`).join("");
}

async function loadJobDetail() {
    if (!state.jobId) { $("#job-detail").innerHTML = empty("请选择任务"); return; }
    try {
        const [job, log, results] = await Promise.all([api(`/api/jobs/${state.jobId}`), api(`/api/jobs/${state.jobId}/logs`), api(`/api/jobs/${state.jobId}/results`)]);
        const [stage, progress] = jobStage(job);
        const dataset = state.datasets.find(item => item.id === job.datasetId);
        $("#job-detail").innerHTML = `${job.errorMessage ? `<div class="error-box"><strong>失败原因：</strong>${esc(job.errorMessage)}</div>` : ""}<div class="job-summary">${[
            ["任务编号", `Job #${job.id}`], ["所属数据集", friendlyDatasetName(dataset)], ["当前状态", statusMeta(job.status)[0]], ["执行阶段", stage], ["总用时", duration(job)]
        ].map(([label, value]) => `<div class="job-stage"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`).join("")}</div><div class="job-progress"><div class="progress-label"><span>${esc(stage)}</span><span>${progress === null ? "正在执行" : `${progress}%`}</span></div><div class="progress ${progress === null ? "indeterminate" : ""}"><span style="width:${progress ?? 36}%"></span></div></div>`;
        $("#job-log").textContent = log || "暂无日志输出";
        $("#job-results").innerHTML = results.length ? `<table><thead><tr><th>模块</th><th>版本</th><th>生成时间</th></tr></thead><tbody>${results.map(result => `<tr><td>${esc(MODULE_LABEL[result.module] || result.module)}</td><td>Version #${result.id}</td><td>${fmtTime(result.createdAt)}</td></tr>`).join("")}</tbody></table>` : empty("暂无成功结果版本");
    } catch (error) { $("#job-detail").innerHTML = empty("任务详情加载失败", error.message); }
}

async function renderAnalytics() {
    if (!state.dataset) { $("#analytics-content").innerHTML = empty("请选择数据集"); return; }
    const modules = availableModules();
    if (!modules.includes(state.analysisModule)) state.analysisModule = modules[0] || null;
    $("#analytics-tabs").innerHTML = modules.map(module => `<button type="button" data-module="${module}" class="${module === state.analysisModule ? "active" : ""}">${esc(MODULE_LABEL[module])}</button>`).join("");
    $$("#analytics-tabs button").forEach(button => button.onclick = () => { state.analysisModule = button.dataset.module; renderAnalytics(); });
    if (!state.analysisModule) { $("#analytics-content").innerHTML = empty("当前数据集没有可用分析", "请先确认字段映射"); return; }
    $("#analytics-content").innerHTML = empty("正在加载分析结果…");
    $("#analytics-kpis").innerHTML = "";
    try {
        if (state.analysisModule === "overview") await renderAnalyticsOverview();
        else {
            const rows = await api(`/api/datasets/${state.datasetId}/analytics/${state.analysisModule}`);
            renderAnalyticsHeader(rows, state.analysisModule);
            renderModuleResult(state.analysisModule, rows);
        }
    } catch (error) {
        renderAnalyticsHeader([], state.analysisModule);
        $("#analytics-content").innerHTML = empty("暂无可展示的成功结果", error.message);
    }
}

function renderAnalyticsHeader(rows, module) {
    const first = Array.isArray(rows) ? rows[0] : rows;
    const jobId = first?.job_id;
    const job = state.jobs.find(item => item.id === jobId) || latestSuccessfulJob(state.datasetId);
    $("#analytics-header").innerHTML = `<h1>${esc(friendlyDatasetName(state.dataset))}</h1><p>${esc(MODULE_LABEL[module])} · 所有图表均读取 V3 REST API</p><div class="hero-meta"><span>${jobId ? `结果版本：Job #${jobId}` : "暂无版本"}</span><span>分析时间：${fmtTime(job?.finishedAt)}</span><span>数据规模：${fmtNumber(state.dataset.rowCount)} 行</span><span>${badge(job?.status || state.dataset.status)}</span></div>`;
}

async function renderAnalyticsOverview() {
    const enabled = new Set(state.capabilities.enabled || []);
    const [overview, behavior, trend, funnel, sales] = await Promise.all([
        safeAnalytics("overview"),
        enabled.has("behavior_analysis") ? safeAnalytics("behavior") : Promise.resolve([]),
        enabled.has("time_analysis") ? safeAnalytics("daily_trend") : Promise.resolve([]),
        enabled.has("funnel_analysis") ? safeAnalytics("funnel") : Promise.resolve([]),
        enabled.has("sales_analysis") ? safeAnalytics("sales") : Promise.resolve([])
    ]);
    if (!overview.length) throw new Error("当前数据集没有 overview 成功版本");
    renderAnalyticsHeader(overview, "overview");
    const metrics = Object.fromEntries(overview.map(row => [row.metric, row.value]));
    if (behavior.length) {
        const buyers = funnel.find(row => row.event_type === "buy")?.user_count ?? 0;
        const users = metrics.users || 0;
        $("#analytics-kpis").className = "metric-grid five";
        $("#analytics-kpis").innerHTML = [
            metricCard("总记录数", fmtNumber(metrics.behavior_records), fmtCompact(metrics.behavior_records)),
            metricCard("用户数", fmtNumber(users), "去重用户"),
            metricCard("商品数", fmtNumber(metrics.items), "去重商品"),
            metricCard("购买用户", fmtNumber(buyers), "产生购买行为的用户"),
            metricCard("用户购买率", users ? `${(buyers * 100 / users).toFixed(2)}%` : "-", "购买用户 / 总用户")
        ].join("");
        $("#analytics-content").innerHTML = `<article class="panel chart-panel"><div class="panel-title"><div><span>BEHAVIOR MIX</span><h2>用户行为占比</h2></div></div><div id="analysis-chart-a" class="chart"></div></article><article class="panel chart-panel"><div class="panel-title"><div><span>EVENT COUNT</span><h2>行为事件数量</h2></div></div><div id="analysis-chart-b" class="chart"></div></article><article class="panel chart-panel full"><div class="panel-title"><div><span>DAILY TREND</span><h2>每日行为与购买趋势</h2></div></div><div id="analysis-chart-c" class="chart"></div></article>`;
        renderBehaviorPie("analysis-chart-a", behavior);
        renderBehaviorBar("analysis-chart-b", behavior);
        renderTrend("analysis-chart-c", trend);
    } else {
        const salesRow = Array.isArray(sales) ? sales[0] : sales;
        $("#analytics-kpis").className = "metric-grid";
        $("#analytics-kpis").innerHTML = [metricCard("订单行", fmtNumber(metrics.order_rows), "订单明细记录"), metricCard("订单数", fmtNumber(metrics.orders), "去重订单"), metricCard("客户数", fmtNumber(metrics.users), "去重用户"), metricCard("销售额", `¥ ${fmtNumber(metrics.revenue)}`, "汇总金额")].join("");
        $("#analytics-content").innerHTML = `<article class="panel chart-panel full"><div class="panel-title"><div><span>SALES TREND</span><h2>订单与销售趋势</h2></div></div><div id="analysis-chart-c" class="chart tall"></div></article>${salesRow ? `<article class="panel full"><div class="panel-title"><div><span>SALES SUMMARY</span><h2>销售汇总</h2></div></div>${objectTable([salesRow])}</article>` : ""}`;
        renderTrend("analysis-chart-c", trend);
    }
}

function renderBehaviorBar(id, rows) {
    chart(id, {color: [COLORS[0]], tooltip: baseTooltip, grid: baseGrid, xAxis: {type: "category", data: rows.map(row => BEHAVIOR_LABEL[row.event_type] || row.event_type)}, yAxis: {type: "value", axisLabel: {formatter: axisNumber}}, series: [{type: "bar", barMaxWidth: 48, data: rows.map(row => row.count), label: {show: true, position: "top", formatter: params => axisNumber(params.value)}}]});
}

function renderModuleResult(module, rows) {
    const data = Array.isArray(rows) ? rows : [rows];
    $("#analytics-kpis").className = "metric-grid";
    $("#analytics-kpis").innerHTML = "";
    if (module === "behavior") {
        const total = data.reduce((sum, row) => sum + Number(row.count || 0), 0);
        $("#analytics-kpis").innerHTML = data.map(row => metricCard(BEHAVIOR_LABEL[row.event_type] || row.event_type, fmtNumber(row.count), total ? `${(row.count * 100 / total).toFixed(2)}%` : "-")).join("");
        $("#analytics-content").innerHTML = chartPanels("行为结构", "各行为事件数量");
        renderBehaviorPie("analysis-chart-a", data); renderBehaviorBar("analysis-chart-b", data); return;
    }
    if (module === "daily_trend") {
        $("#analytics-content").innerHTML = oneChart("每日趋势", true) + resultTablePanel(data); renderTrend("analysis-chart-a", data); return;
    }
    if (module === "hourly") {
        const key = Object.hasOwn(data[0] || {}, "order_count") ? "order_count" : "count";
        $("#analytics-content").innerHTML = oneChart("24 小时分布", true) + resultTablePanel(data);
        chart("analysis-chart-a", {color: [COLORS[0]], tooltip: baseTooltip, grid: baseGrid, xAxis: {type: "category", name: "小时", data: data.map(row => row.hour)}, yAxis: {type: "value", axisLabel: {formatter: axisNumber}}, series: [{type: "bar", data: data.map(row => row[key]), barMaxWidth: 34}]}); return;
    }
    if (module === "funnel") {
        $("#analytics-content").innerHTML = oneChart("行为阶段汇总", true) + `<article class="panel full"><div class="notice info">源数据不含 session_id，当前图表是阶段事件量与用户量汇总，不表示严格会话漏斗。</div>${objectTable(data)}</article>`;
        chart("analysis-chart-a", {color: [COLORS[0], COLORS[2]], tooltip: {trigger: "axis"}, legend: {bottom: 0, data: ["事件数", "用户数"]}, grid: {...baseGrid, bottom: 45}, xAxis: {type: "category", data: data.map(row => BEHAVIOR_LABEL[row.event_type] || row.event_type)}, yAxis: {type: "value", axisLabel: {formatter: axisNumber}}, series: [{name: "事件数", type: "bar", data: data.map(row => row.event_count)}, {name: "用户数", type: "bar", data: data.map(row => row.user_count)}]}); return;
    }
    if (module === "items" || module === "categories") {
        const labelKey = module === "items" ? "item_id" : "category_id";
        const valueKey = Object.hasOwn(data[0] || {}, "buys") ? "buys" : "revenue";
        $("#analytics-content").innerHTML = oneChart(module === "items" ? "热门商品 TOP 20" : "热门类目 TOP 20", true) + resultTablePanel(data);
        const sorted = data.slice().reverse();
        chart("analysis-chart-a", {color: [COLORS[0]], tooltip: baseTooltip, grid: {left: 25, right: 55, top: 15, bottom: 20, containLabel: true}, xAxis: {type: "value", axisLabel: {formatter: axisNumber}}, yAxis: {type: "category", data: sorted.map(row => row[labelKey])}, series: [{type: "bar", data: sorted.map(row => row[valueKey]), label: {show: true, position: "right", formatter: params => axisNumber(params.value)}}]}); return;
    }
    if (module === "user_segmentation") {
        $("#analytics-content").innerHTML = oneChart("用户分群结构", false) + resultTablePanel(data);
        chart("analysis-chart-a", {color: COLORS, tooltip: {trigger: "item"}, legend: {bottom: 0}, series: [{type: "pie", radius: ["38%", "70%"], itemStyle: {borderRadius: 7, borderColor: "#fff", borderWidth: 3}, data: data.map(row => ({name: segmentLabel(row.segment), value: row.count}))}]}); return;
    }
    if (module === "repurchase") {
        const row = data[0] || {};
        $("#analytics-kpis").innerHTML = [metricCard("购买用户", fmtNumber(row.buyers), "至少一次购买"), metricCard("复购用户", fmtNumber(row.repeat_buyers), "至少两次购买"), metricCard("复购率", `${Number(row.repeat_rate_percent || 0).toFixed(2)}%`, "复购用户 / 购买用户")].join("");
        $("#analytics-content").innerHTML = oneChart("购买用户构成", true);
        chart("analysis-chart-a", {color: [COLORS[2], "#dfe7f1"], tooltip: {trigger: "item"}, series: [{type: "pie", radius: ["45%", "72%"], label: {formatter: "{b}\n{d}%"}, data: [{name: "复购用户", value: row.repeat_buyers}, {name: "单次购买用户", value: Math.max(0, row.buyers - row.repeat_buyers)}]}]}); return;
    }
    if (module === "sales") {
        const row = data[0] || {};
        $("#analytics-kpis").innerHTML = [metricCard("订单数", fmtNumber(row.orders), "去重订单"), metricCard("买家数", fmtNumber(row.buyers), "去重客户"), metricCard("销售数量", fmtNumber(row.quantity), "商品数量汇总"), metricCard("销售额", `¥ ${fmtNumber(row.revenue)}`, "订单金额汇总")].join("");
        $("#analytics-content").innerHTML = `<article class="panel full"><div class="panel-title"><div><span>SALES SUMMARY</span><h2>销售指标明细</h2></div></div>${objectTable(data)}</article>`; return;
    }
    $("#analytics-content").innerHTML = resultTablePanel(data);
}

function segmentLabel(value) {
    return ({repeat_buyer: "复购用户", one_time_buyer: "单次购买用户", active_non_buyer: "活跃未购用户", light_non_buyer: "轻度未购用户"})[value] || value;
}

function chartPanels(titleA, titleB) {
    return `<article class="panel chart-panel"><div class="panel-title"><div><span>CHART</span><h2>${esc(titleA)}</h2></div></div><div id="analysis-chart-a" class="chart"></div></article><article class="panel chart-panel"><div class="panel-title"><div><span>CHART</span><h2>${esc(titleB)}</h2></div></div><div id="analysis-chart-b" class="chart"></div></article>`;
}

function oneChart(title, full) {
    return `<article class="panel chart-panel ${full ? "full" : ""}"><div class="panel-title"><div><span>ANALYSIS</span><h2>${esc(title)}</h2></div></div><div id="analysis-chart-a" class="chart tall"></div></article>`;
}

function resultTablePanel(rows) {
    return `<article class="panel full"><div class="panel-title"><div><span>RESULT DATA</span><h2>结果明细</h2></div></div>${objectTable(rows)}</article>`;
}

function objectTable(rows) {
    if (!rows?.length) return empty("暂无结果");
    const hidden = new Set(["dataset_id", "job_id"]);
    const keys = Object.keys(rows[0]).filter(key => !hidden.has(key));
    return `<div class="table-wrap"><table><thead><tr>${keys.map(key => `<th>${esc(columnLabel(key))}</th>`).join("")}</tr></thead><tbody>${rows.map(row => `<tr>${keys.map(key => `<td>${typeof row[key] === "number" ? fmtNumber(row[key]) : esc(row[key])}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
}

function columnLabel(key) {
    return ({metric: "指标", value: "数值", event_type: "行为", count: "数量", event_count: "事件数", user_count: "用户数", day: "日期", buy_count: "购买数", hour: "小时", item_id: "商品 ID", category_id: "类目 ID", actions: "行为数", buys: "购买数", segment: "用户分群", orders: "订单数", buyers: "买家数", quantity: "销售数量", revenue: "销售额", repeat_buyers: "复购用户", repeat_rate_percent: "复购率（%）"})[key] || key;
}

async function renderSystemStatus() {
    try {
        const system = await api("/api/status");
        state.system = system;
        const components = [
            ["API 服务", system.api, `${system.bindHost}:${system.port}`],
            ["MySQL", system.mysql, `Repository：${system.repositoryMode}`],
            ["Hive", system.hive, "ecommerce_v3 标准模型"],
            ["Spark", system.spark, `${system.activeJobs} 个活动任务`],
            ["Warehouse", system.warehouseAvailable ? "UP" : "DOWN", system.warehouseAvailable ? "目录可访问" : "目录不可用"]
        ];
        $("#system-status-cards").innerHTML = components.map(([name, status, note]) => `<div class="status-card"><div class="status-head"><span>${esc(name)}</span>${badge(status)}</div><strong>${esc(statusMeta(status)[0])}</strong><small>${esc(note)}</small></div>`).join("");
        $("#system-runtime").innerHTML = `<div class="detail-grid">${[["Repository Mode", system.repositoryMode], ["数据集数量", system.datasetCount], ["任务总数", system.jobCount], ["活动任务", system.activeJobs], ["绑定地址", `${system.bindHost}:${system.port}`], ["Warehouse", system.warehouse]].map(([label, value]) => `<div class="detail-item"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`).join("")}</div>`;
    } catch (error) {
        $("#system-status-cards").innerHTML = `<div class="status-card"><div class="status-head"><span>API 服务</span>${badge("DOWN")}</div><strong>状态读取失败</strong><small>${esc(error.message)}</small></div>`;
        $("#system-runtime").innerHTML = empty("无法读取运行环境");
    }
}

$$("#nav button").forEach(button => button.onclick = () => showPage(button.dataset.page));
$$('[data-go]').forEach(button => button.onclick = () => showPage(button.dataset.go));
$$("#dataset-tabs button").forEach(button => button.onclick = () => setDatasetTab(button.dataset.tab));

$("#dataset-select").onchange = event => selectDataset(event.target.value);
$("#toggle-register").onclick = () => $("#register-drawer").classList.toggle("hidden");
$("#toggle-job-form").onclick = () => $("#job-form").classList.toggle("hidden");
$("#refresh-status").onclick = renderSystemStatus;

$$("#job-filters button").forEach(button => button.onclick = () => {
    state.jobFilter = button.dataset.filter;
    $$("#job-filters button").forEach(item => item.classList.toggle("active", item === button));
    renderJobs();
});

$("#register-form").onsubmit = async event => {
    event.preventDefault();
    const form = new FormData(event.target);
    try {
        await api("/api/datasets", {method: "POST", body: JSON.stringify({name: form.get("name"), description: form.get("description"), datasetType: form.get("datasetType"), filePath: form.get("filePath"), hasHeader: form.get("hasHeader") === "true", delimiter: form.get("delimiter")})});
        toast("数据集已登记，请检查并确认字段映射");
        event.target.reset();
        await loadInitialData();
    } catch (error) { toast(error.message); }
};

$("#upload-form").onsubmit = async event => {
    event.preventDefault();
    const file = new FormData(event.target).get("file");
    try {
        const response = await fetch(`/api/datasets/upload?filename=${encodeURIComponent(file.name)}`, {method: "POST", headers: {"Content-Type": "text/csv"}, body: file});
        const value = await response.json();
        if (!response.ok) throw new Error(value.error);
        $("#upload-result").textContent = value.filePath;
        toast("上传完成，请使用返回路径登记数据集");
    } catch (error) { toast(error.message); }
};

$("#mapping-form").onsubmit = async event => {
    event.preventDefault();
    try {
        const values = state.mappings.map((mapping, index) => ({...mapping, standardField: document.querySelector(`[data-map="${index}"]`).value || null, confirmed: true}));
        await api(`/api/datasets/${state.datasetId}/mapping`, {method: "PUT", body: JSON.stringify(values)});
        toast("字段映射已保存并确认");
        await loadDatasetContext();
        await loadDatasetProfiles();
        renderDatasetPage();
        await renderOverview();
    } catch (error) { toast(error.message); }
};

$("#job-form").onsubmit = async event => {
    event.preventDefault();
    const selected = [...event.target.querySelectorAll("input:checked")].map(input => input.value);
    if (!selected.length) { toast("请至少选择一个分析模块"); return; }
    try {
        const job = await api("/api/jobs", {method: "POST", body: JSON.stringify({datasetId: state.datasetId, modules: selected})});
        state.jobId = job.id;
        toast(`Job #${job.id} 已创建`);
        $("#job-form").classList.add("hidden");
        await refreshJobs();
    } catch (error) { toast(error.message); }
};

window.addEventListener("resize", () => echarts?.getInstanceByDom && $$(".chart").forEach(node => echarts.getInstanceByDom(node)?.resize()));

(async () => {
    try {
        await loadInitialData();
        await renderSystemStatus();
        const requestedPage = new URLSearchParams(window.location.search).get("page");
        if (["overview", "datasets", "jobs", "analytics", "status"].includes(requestedPage)) showPage(requestedPage);
    }
    catch (error) {
        $("#overview-subtitle").textContent = `平台数据加载失败：${error.message}`;
        $("#overview-cards").innerHTML = empty("无法连接 V3 REST API", error.message);
    }
})();
