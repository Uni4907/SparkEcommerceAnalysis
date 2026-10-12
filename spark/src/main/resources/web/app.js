// ============================================================
// Spark 电商用户分析与可视化系统 - 前端脚本
// 通过 fetch 读取 Spark 导出的 data/*.json 并使用 ECharts 渲染
// ============================================================

const BEHAVIOR_LABEL = {
    pv: "浏览",
    cart: "加购",
    fav: "收藏",
    buy: "购买"
};

const COLORS = ["#3b7dd8", "#f5a623", "#50b94e", "#e2574c", "#9b6de2", "#00a8a8"];

const numberFormat = new Intl.NumberFormat("zh-CN");

function fmt(n) {
    if (n === null || n === undefined || Number.isNaN(Number(n))) {
        return "-";
    }
    return numberFormat.format(Number(n));
}

function round2(n) {
    return Math.round(Number(n) * 100) / 100;
}

async function getJson(name) {
    const res = await fetch("data/" + name + ".json");
    if (!res.ok) {
        throw new Error("加载 data/" + name + ".json 失败: " + res.status);
    }
    return res.json();
}

function makeChart(id) {
    const dom = document.getElementById(id);
    if (!dom) {
        return null;
    }
    const inst = echarts.init(dom);
    window.addEventListener("resize", () => inst.resize());
    return inst;
}

function showMissing(domId, message) {
    const dom = document.getElementById(domId);
    if (dom) {
        dom.innerHTML = '<div class="hint">' + message + "</div>";
    }
}

const baseTooltip = {
    trigger: "axis",
    axisPointer: { type: "shadow" }
};

// ---------------- 总览 ----------------
async function renderOverview() {
    const stats = await getJson("overall_stats");
    const cards = document.getElementById("cards");
    const cardLabels = {
        "数据总量": "行为记录总量",
        "用户数量": "用户数",
        "商品数量": "商品数",
        "商品类别数量": "商品类别数",
        "购买用户数": "有购买行为的用户",
        "用户购买率(%)": "用户购买率 %"
    };
    cards.innerHTML = stats.map(row => {
        const raw = row.value;
        const value = typeof raw === "number" && Number.isInteger(raw)
            ? fmt(raw)
            : round2(raw);
        return '<div class="card"><div class="value">' + value +
            '</div><div class="label">' + (cardLabels[row.metric] || row.metric) + "</div></div>";
    }).join("");

    const percent = await getJson("behavior_percent");
    const pie = makeChart("chart-behavior-pie");
    pie.setOption({
        color: COLORS,
        tooltip: { trigger: "item", formatter: p => p.name + "：<b>" + p.value + "%</b>" },
        series: [{
            type: "pie",
            radius: ["38%", "68%"],
            itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
            label: { formatter: "{b}\n{d}%" },
            data: percent.map(r => ({
                name: BEHAVIOR_LABEL[r.behavior] || r.behavior,
                value: Math.round(r.percentage * 10000) / 10000
            }))
        }]
    });

    const counts = await getJson("behavior_counts");
    const bar = makeChart("chart-behavior-bar");
    bar.setOption({
        color: ["#3b7dd8"],
        tooltip: Object.assign({}, baseTooltip, { valueFormatter: v => fmt(v) }),
        grid: { containLabel: true, left: 50, right: 20, top: 30, bottom: 40 },
        xAxis: { type: "category", data: counts.map(r => BEHAVIOR_LABEL[r.behavior] || r.behavior) },
        yAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        series: [{ type: "bar", barWidth: 40, data: counts.map(r => r.count),
            label: { show: true, position: "top", formatter: p => fmt(p.value) } }]
    });

    const conversion = await getJson("conversion_rates");
    const conv = makeChart("chart-conversion");
    conv.setOption({
        color: ["#f5a623"],
        tooltip: Object.assign({}, baseTooltip, {
            formatter: p => {
                const row = conversion[p.dataIndex];
                return row.conversion + "<br/>基础次数：" + fmt(row.basis_count) +
                    "<br/>目标次数：" + fmt(row.target_count) +
                    "<br/>转化率：" + row.rate_percent + "%";
            }
        }),
        grid: { containLabel: true, left: 60, right: 60, top: 30, bottom: 60 },
        xAxis: {
            type: "category",
            data: conversion.map(r => r.conversion),
            axisLabel: { interval: 0, fontSize: 10 }
        },
        yAxis: { type: "value", name: "%", axisLabel: { formatter: v => v + "%" } },
        series: [{
            type: "bar",
            barWidth: 36,
            data: conversion.map(r => Math.round(r.rate_percent * 10000) / 10000),
            label: { show: true, position: "top", formatter: p => p.value + "%" }
        }]
    });
}

// ---------------- 用户行为 ----------------
function pieOption(data, labelKey, valueKey) {
    return {
        color: COLORS,
        tooltip: { trigger: "item", formatter: p => p.name + "：<b>" + fmt(p.value) + "</b>（" + p.percent + "%）" },
        series: [{
            type: "pie",
            radius: ["38%", "68%"],
            itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
            data: data.map(r => ({ name: r[labelKey], value: r[valueKey] }))
        }]
    };
}

async function renderUser() {
    const active = await getJson("activity_level_counts");
    makeChart("chart-active").setOption(pieOption(active, "user_active_level", "user_count"));

    const buyLevel = await getJson("buy_level_counts");
    const buy = makeChart("chart-buy-level");
    buy.setOption({
        color: ["#50b94e"],
        tooltip: baseTooltip,
        grid: { containLabel: true, left: 50, right: 20, top: 30, bottom: 60 },
        xAxis: { type: "category", data: buyLevel.map(r => r.user_buy_level), axisLabel: { interval: 0 } },
        yAxis: { type: "value" },
        series: [{ type: "bar", data: buyLevel.map(r => r.user_count), barWidth: 40 }]
    });

    const freq = await getJson("purchase_frequency");
    const limited = freq.filter(r => Number(r.purchase_count) <= 30);
    const line = makeChart("chart-purchase-freq");
    line.setOption({
        color: ["#e2574c"],
        tooltip: { trigger: "axis", valueFormatter: v => fmt(v) },
        grid: { containLabel: true, left: 60, right: 30, top: 30, bottom: 40 },
        xAxis: { type: "category", name: "购买次数", data: limited.map(r => r.purchase_count) },
        yAxis: { type: "value", name: "用户数", axisLabel: { formatter: v => fmt(v) } },
        series: [{
            type: "line",
            smooth: true,
            symbolSize: 5,
            data: limited.map(r => r.user_count),
            areaStyle: { opacity: 0.15 }
        }]
    });
}

// ---------------- 用户价值 ----------------
async function renderValue() {
    const level = await getJson("value_level_counts");
    makeChart("chart-value-level").setOption(pieOption(level, "user_value_level", "count"));

    const scores = await getJson("value_score_counts");
    const score = makeChart("chart-value-score");
    score.setOption({
        color: ["#9b6de2"],
        tooltip: { trigger: "axis", valueFormatter: v => fmt(v) },
        grid: { containLabel: true, left: 60, right: 20, top: 30, bottom: 40 },
        xAxis: { type: "category", name: "value_score", data: scores.map(r => r.value_score) },
        yAxis: { type: "value", name: "用户数", axisLabel: { formatter: v => fmt(v) } },
        series: [{
            type: "line",
            smooth: true,
            symbolSize: 6,
            data: scores.map(r => r.count),
            areaStyle: { opacity: 0.15 }
        }]
    });

    const spec = [
        { file: "a_score_counts", dom: "chart-a", scoreKey: "A_score" },
        { file: "f_score_counts", dom: "chart-f", scoreKey: "F_score" },
        { file: "r_score_counts", dom: "chart-r", scoreKey: "R_score" }
    ];
    for (const item of spec) {
        const data = await getJson(item.file);
        makeChart(item.dom).setOption({
            color: ["#00a8a8"],
            tooltip: { trigger: "axis", valueFormatter: v => fmt(v) },
            grid: { containLabel: true, left: 50, right: 20, top: 30, bottom: 40 },
            xAxis: { type: "category", data: data.map(r => r[item.scoreKey]) },
            yAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
            series: [{ type: "bar", barWidth: 30, data: data.map(r => r.count) }]
        });
    }
}

// ---------------- 用户分群 ----------------
function renderDetailTable(domId, rows, labelMap, numericCols) {
    const table = document.createElement("table");
    table.className = "detail";
    const keys = Object.keys(rows[0]);
    const headers = keys.map(k => labelMap[k] || k);
    table.innerHTML = "<thead><tr><th>" + headers.join("</th><th>") +
        "</th></tr></thead><tbody>" + rows.map(row =>
            "<tr><td>" + keys.map((k, i) => {
                if (i === 0) {
                    return row[k];
                }
                const value = row[k];
                if (numericCols.includes(k)) {
                    return Number.isInteger(value) ? fmt(value) : round2(value);
                }
                return value;
            }).join("</td><td>") + "</td></tr>"
        ).join("") + "</tbody>";
    document.getElementById(domId).innerHTML = "";
    document.getElementById(domId).appendChild(table);
}

function hBar(inst, rows, nameKey, valueKey, color, extraTooltip) {
    const sorted = rows.slice().sort((a, b) => a[valueKey] - b[valueKey]);
    inst.setOption({
        color: [color],
        tooltip: Object.assign({}, baseTooltip, {
            trigger: "axis",
            formatter: p => {
                const row = sorted[p[0].dataIndex];
                let text = row[nameKey] + "<br/>" + p[0].marker + fmt(row[valueKey]);
                if (extraTooltip) {
                    text += extraTooltip(row);
                }
                return text;
            }
        }),
        grid: { containLabel: true, left: 100, right: 40, top: 10, bottom: 40 },
        xAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        yAxis: { type: "category", data: sorted.map(r => r[nameKey]) },
        series: [{
            type: "bar",
            data: sorted.map(r => r[valueKey]),
            label: { show: true, position: "right", formatter: p => fmt(p.value) }
        }]
    });
}

async function renderSegment() {
    const summary = await getJson("segment_summary");
    hBar(makeChart("chart-segment-size"), summary, "user_segment", "user_count", "#3b7dd8");

    const detail = await getJson("segment_behavior");
    hBar(makeChart("chart-segment-buy"), detail, "user_segment", "avg_purchase_count", "#f5a623",
        row => "<br/>人均行为 " + round2(row.avg_action_count) +
            " · 人均浏览 " + round2(row.avg_pv_count));

    renderDetailTable("table-segment", detail, {
        user_segment: "用户群体",
        user_count: "用户数",
        avg_action_count: "人均行为数",
        avg_pv_count: "人均浏览",
        avg_cart_count: "人均加购",
        avg_fav_count: "人均收藏",
        avg_buy_count: "人均购买",
        avg_purchase_count: "人均购买次数",
        avg_purchase_item_count: "人均购买商品数",
        avg_purchase_category_count: "人均购买类别数",
        avg_value_score: "平均价值分"
    }, ["user_count", "avg_action_count", "avg_pv_count", "avg_cart_count",
        "avg_fav_count", "avg_buy_count", "avg_purchase_count",
        "avg_purchase_item_count", "avg_purchase_category_count", "avg_value_score"]);
}

// ---------------- 用户聚类 ----------------
async function renderCluster() {
    const evaluation = await getJson("cluster_evaluation");
    const evalChart = makeChart("chart-cluster-eval");
    evalChart.setOption({
        tooltip: { trigger: "axis" },
        legend: { data: ["WSSSE（越小越好）", "轮廓系数（越大越好）"], top: 5 },
        grid: { containLabel: true, left: 10, right: 60, top: 40, bottom: 40 },
        xAxis: { type: "category", name: "K", data: evaluation.map(r => r.k) },
        yAxis: [
            { type: "value", name: "WSSSE", axisLabel: { formatter: v => fmt(v) } },
            { type: "value", name: "轮廓系数", min: 0, max: 1, axisLabel: { formatter: v => v } }
        ],
        series: [
            {
                name: "WSSSE（越小越好）",
                type: "line",
                smooth: true,
                symbolSize: 7,
                data: evaluation.map(r => r.wssse)
            },
            {
                name: "轮廓系数（越大越好）",
                type: "line",
                smooth: true,
                symbolSize: 7,
                yAxisIndex: 1,
                data: evaluation.map(r => r.silhouette)
            }
        ]
    });

    const summary = await getJson("cluster_summary");
    const sizeChart = makeChart("chart-cluster-size");
    const sorted = summary.slice().sort((a, b) => a.user_count - b.user_count);
    sizeChart.setOption({
        color: ["#00a8a8"],
        tooltip: Object.assign({}, baseTooltip, { valueFormatter: v => fmt(v) }),
        grid: { containLabel: true, left: 10, right: 30, top: 10, bottom: 30 },
        xAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        yAxis: { type: "category", data: sorted.map(r => "聚类 " + r.cluster) },
        series: [{
            type: "bar",
            data: sorted.map(r => r.user_count),
            label: { show: true, position: "right", formatter: p => fmt(p.value) }
        }]
    });

    renderDetailTable("table-cluster", summary, {
        cluster: "聚类",
        user_count: "用户数",
        avg_action_count: "人均行为数",
        avg_pv_count: "人均浏览",
        avg_cart_count: "人均加购",
        avg_fav_count: "人均收藏",
        avg_purchase_count: "人均购买次数",
        avg_purchase_item_count: "人均购买商品数",
        avg_purchase_category_count: "人均购买类别数"
    }, ["user_count", "avg_action_count", "avg_pv_count", "avg_cart_count",
        "avg_fav_count", "avg_purchase_count", "avg_purchase_item_count",
        "avg_purchase_category_count"]);

    const cross = await getJson("cluster_segment_cross");
    renderDetailTable("table-cluster-cross", cross, {}, []);
}

// ---------------- 商品分析 ----------------
function idLabel(id) {
    return "ID " + fmt(id);
}

async function renderProduct() {
    const itemPv = await getJson("item_top_pv");
    hBar(makeChart("chart-item-pv"), itemPv, "item_id", "pv_count", "#3b7dd8",
        row => "<br/>购买 " + fmt(row.buy_count) + " · 加购 " + fmt(row.cart_count) +
            " · 收藏 " + fmt(row.fav_count));

    const itemBuy = await getJson("item_top_buy");
    const itemBuyChart = makeChart("chart-item-buy");
    const sortedBuy = itemBuy.slice().sort((a, b) => a.buy_count - b.buy_count);
    itemBuyChart.setOption({
        color: ["#e2574c"],
        tooltip: {
            trigger: "axis",
            formatter: p => {
                const row = sortedBuy[p[0].dataIndex];
                return "ID " + fmt(row.item_id) + "<br/>购买 " + fmt(row.buy_count) +
                    "<br/>浏览 " + fmt(row.pv_count) +
                    "<br/>转化率 " + Math.round(row.conversion_rate * 100) / 100 + "%";
            }
        },
        grid: { containLabel: true, left: 100, right: 40, top: 10, bottom: 40 },
        xAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        yAxis: { type: "category", data: sortedBuy.map(r => idLabel(r.item_id)) },
        series: [{
            type: "bar",
            data: sortedBuy.map(r => r.buy_count),
            label: { show: true, position: "right", formatter: p => fmt(p.value) }
        }]
    });

    const catPv = await getJson("category_top_pv");
    hBar(makeChart("chart-cat-pv"), catPv, "category_id", "pv_count", "#50b94e");

    const catBuy = await getJson("category_top_buy");
    const catBuyChart = makeChart("chart-cat-buy");
    const sortedCatBuy = catBuy.slice().sort((a, b) => a.buy_count - b.buy_count);
    catBuyChart.setOption({
        color: ["#9b6de2"],
        tooltip: {
            trigger: "axis",
            formatter: p => {
                const row = sortedCatBuy[p[0].dataIndex];
                return "类别 " + row.category_id + "<br/>购买 " + fmt(row.buy_count) +
                    "<br/>浏览 " + fmt(row.pv_count) +
                    "<br/>转化率 " + Math.round(row.conversion_rate * 100) / 100 + "%";
            }
        },
        grid: { containLabel: true, left: 110, right: 40, top: 10, bottom: 40 },
        xAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        yAxis: { type: "category", data: sortedCatBuy.map(r => "ID " + r.category_id) },
        series: [{
            type: "bar",
            data: sortedCatBuy.map(r => r.buy_count),
            label: { show: true, position: "right", formatter: p => fmt(p.value) }
        }]
    });
}

// ---------------- 行为路径 ----------------
async function renderPath() {
    const funnel = await getJson("behavior_path_funnel");
    hBar(makeChart("chart-funnel"), funnel, "stage", "pair_count", "#00a8a8",
        row => "<br/>去重用户 " + fmt(row.user_count) +
            "<br/>占购买商品对 " + row.pair_percent + "%");

    renderDetailTable("table-funnel", funnel, {
        stage: "环节",
        pair_count: "商品对数量",
        user_count: "去重用户数",
        pair_percent: "占购买商品对 %"
    }, ["pair_count", "user_count", "pair_percent"]);
}

// ---------------- 时间趋势 ----------------
function trendOption(rows, xKey, xName, valueKeys) {
    return {
        color: COLORS,
        tooltip: { trigger: "axis", valueFormatter: v => fmt(v) },
        legend: { data: valueKeys.map(k => BEHAVIOR_LABEL[k] || k), top: 5 },
        grid: { containLabel: true, left: 60, right: 30, top: 40, bottom: 50 },
        xAxis: { type: "category", name: xName, data: rows.map(r => r[xKey]) },
        yAxis: { type: "value", axisLabel: { formatter: v => fmt(v) } },
        series: valueKeys.map(k => ({
            name: BEHAVIOR_LABEL[k] || k,
            type: "line",
            smooth: true,
            symbolSize: 4,
            data: rows.map(r => r[k + "_count"])
        }))
    };
}

async function renderTime() {
    const dateTrend = await getJson("date_trend");
    makeChart("chart-date").setOption(
        trendOption(dateTrend, "action_date", "日期", ["pv", "cart", "fav", "buy"]));

    const hourTrend = await getJson("hour_trend");
    makeChart("chart-hour").setOption(
        trendOption(hourTrend, "action_hour", "小时", ["pv", "cart", "fav", "buy"]));

    const heat = await getJson("day_hour_buy");
    const dates = Array.from(new Set(heat.map(r => r.action_date))).sort();
    const dateIndex = {};
    dates.forEach((d, i) => dateIndex[d] = i);
    const heatData = heat.map(r => [r.action_hour, dateIndex[r.action_date], r.buy_count]);
    makeChart("chart-heat").setOption({
        tooltip: {
            position: "top",
            formatter: p => "日期 " + dates[p.value[1]] + " · " + p.value[0] + " 时<br/>购买 " + fmt(p.value[2])
        },
        grid: { containLabel: true, left: 90, right: 40, top: 20, bottom: 60 },
        xAxis: { type: "category", name: "小时", data: Array.from({ length: 24 }, (_, i) => i) },
        yAxis: { type: "category", data: dates },
        visualMap: {
            min: 0,
            max: Math.max.apply(null, heat.map(r => r.buy_count)),
            calculable: true,
            orient: "horizontal",
            left: "center",
            bottom: 5,
            inRange: { color: ["#eef4ff", "#3b7dd8", "#1f3a93"] },
            textStyle: { fontSize: 10 }
        },
        series: [{
            type: "heatmap",
            data: heatData,
            label: { show: false },
            emphasis: { itemStyle: { shadowBlur: 8, shadowColor: "rgba(0,0,0,0.3)" } }
        }]
    });
}

// ---------------- 入口：逐页加载，单页失败不影响其它图表 ----------------
async function safeLoad(name, fn) {
    try {
        await fn();
    } catch (e) {
        console.error("渲染失败: " + name, e);
    }
}

// ---------------- 页面切换：切到哪个页面才渲染哪个 ----------------
// 关键点：display:none 页面里容器宽度为 0，若一次性提前初始化，
// 除总览外的图表都会被压缩在左上角一小块区域。
const pageRenderers = {
    "page-overview": renderOverview,
    "page-user": renderUser,
    "page-value": renderValue,
    "page-segment": renderSegment,
    "page-cluster": renderCluster,
    "page-product": renderProduct,
    "page-path": renderPath,
    "page-time": renderTime
};

const renderedPages = new Set();

function activatePage(pageId) {
    document.querySelectorAll("#tabs .tab").forEach(b => b.classList.remove("active"));
    document.querySelectorAll(".page").forEach(p => p.classList.remove("active"));
    const tab = document.querySelector('#tabs .tab[data-page="' + pageId + '"]');
    if (tab) {
        tab.classList.add("active");
    }
    document.getElementById(pageId).classList.add("active");

    // 首次打开时渲染该页图表；渲染后保留实例，避免重复请求数据
    if (!renderedPages.has(pageId) && pageRenderers[pageId]) {
        renderedPages.add(pageId);
        safeLoad(pageId, pageRenderers[pageId]);
    }
}

document.querySelectorAll("#tabs .tab").forEach(btn => {
    btn.addEventListener("click", () => activatePage(btn.dataset.page));
});

// 默认显示并渲染总览页
activatePage("page-overview");
