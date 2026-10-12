import fs from "node:fs";
import path from "node:path";

const debugPort = Number(process.argv[2] || 9223);
const baseUrl = process.argv[3] || "http://127.0.0.1:18092";
const outputDir = path.resolve(process.argv[4] || "docs/screenshots/v3.2");
fs.mkdirSync(outputDir, {recursive: true});

const target = await fetch(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(`${baseUrl}/platform.html`)}`, {method: "PUT"}).then(response => response.json());
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, {once: true});
    socket.addEventListener("error", reject, {once: true});
});

let nextId = 0;
const pending = new Map();
const exceptions = [];
const consoleErrors = [];
socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
        const {resolve, reject} = pending.get(message.id);
        pending.delete(message.id);
        message.error ? reject(new Error(message.error.message)) : resolve(message.result);
    }
    if (message.method === "Runtime.exceptionThrown") exceptions.push(message.params.exceptionDetails?.text || "Uncaught exception");
    if (message.method === "Runtime.consoleAPICalled" && message.params.type === "error") {
        consoleErrors.push(message.params.args.map(value => value.value || value.description || "").join(" "));
    }
});

function send(method, params = {}) {
    return new Promise((resolve, reject) => {
        const id = ++nextId;
        pending.set(id, {resolve, reject});
        socket.send(JSON.stringify({id, method, params}));
        setTimeout(() => {
            if (!pending.has(id)) return;
            pending.delete(id);
            reject(new Error(`CDP timeout: ${method}`));
        }, 15000);
    });
}

async function evaluate(expression) {
    const result = await send("Runtime.evaluate", {expression, awaitPromise: true, returnByValue: true});
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Browser evaluation failed");
    return result.result?.value;
}

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
await send("Page.enable");
await send("Runtime.enable");
await send("Log.enable");
await send("Emulation.setDeviceMetricsOverride", {width: 1366, height: 768, deviceScaleFactor: 1, mobile: false});
await send("Page.navigate", {url: `${baseUrl}/platform.html?page=overview`});

for (let attempt = 0; attempt < 40; attempt++) {
    await delay(250);
    if (await evaluate(`typeof state !== "undefined" && state.datasets.length === 2 && !!state.dataset`)) break;
    if (attempt === 39) throw new Error("Platform UI did not finish loading");
}

const scenarios = [
    ["01_platform_overview.png", `await selectDataset(2); showPage("overview"); window.scrollTo(0, 0);`],
    ["02_datasets.png", `await selectDataset(2); showPage("datasets"); window.scrollTo(0, 0);`],
    ["03_dataset_detail.png", `await selectDataset(2, "datasets", "basic"); document.querySelector("#dataset-workspace").scrollIntoView({block:"start"});`],
    ["04_field_mapping.png", `await selectDataset(2, "datasets", "mapping"); document.querySelector("#dataset-workspace").scrollIntoView({block:"start"});`],
    ["05_capabilities.png", `await selectDataset(2, "datasets", "capabilities"); document.querySelector("#dataset-workspace").scrollIntoView({block:"start"});`],
    ["06_jobs.png", `await selectDataset(2); showPage("jobs"); await refreshJobs(); window.scrollTo(0, 0);`],
    ["07_job_detail.png", `state.jobId=7; showPage("jobs"); await loadJobDetail(); document.querySelector("#job-detail-panel").scrollIntoView({block:"start"});`],
    ["08_analytics_overview.png", `await selectDataset(2); state.analysisModule="overview"; showPage("analytics"); await renderAnalytics(); window.scrollTo(0, 0);`],
    ["09_analytics_behavior.png", `await selectDataset(2); state.analysisModule="behavior"; showPage("analytics"); await renderAnalytics(); window.scrollTo(0, 0);`],
    ["10_analytics_trend.png", `await selectDataset(2); state.analysisModule="daily_trend"; showPage("analytics"); await renderAnalytics(); window.scrollTo(0, 0);`],
    ["11_order_dataset_analytics.png", `await selectDataset(1); state.analysisModule="sales"; showPage("analytics"); await renderAnalytics(); window.scrollTo(0, 0);`],
    ["12_system_status.png", `showPage("status"); await renderSystemStatus(); window.scrollTo(0, 0);`]
];

const captures = [];
for (const [filename, setup] of scenarios) {
    await evaluate(`(async()=>{${setup}})()`);
    await delay(900);
    const stateCheck = await evaluate(`({
        activePage: document.querySelector(".page.active")?.id,
        activeDataset: document.querySelector("#dataset-select")?.value,
        activeDatasetTab: document.querySelector("#dataset-tabs button.active")?.dataset.tab || null,
        activeAnalysisTab: document.querySelector("#analytics-tabs button.active")?.dataset.module || null,
        horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
        bodyTextLength: document.body.innerText.length
    })`);
    const image = await send("Page.captureScreenshot", {format: "png", fromSurface: true, captureBeyondViewport: false});
    fs.writeFileSync(path.join(outputDir, filename), Buffer.from(image.data, "base64"));
    captures.push({filename, ...stateCheck});
}

const report = {viewport: "1366x768", captures, uncaughtExceptions: exceptions, consoleErrors};
fs.writeFileSync(path.join(outputDir, "browser_acceptance.json"), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
socket.close();
await fetch(`http://127.0.0.1:${debugPort}/json/close/${target.id}`);
if (exceptions.length || consoleErrors.length || captures.some(item => item.horizontalOverflow)) process.exitCode = 1;
