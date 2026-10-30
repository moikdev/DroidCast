// Android host adapter. The upstream generated Tinycast runtime is loaded separately, unchanged.
(function () {
  const timers = new Map();
  const later = window.setTimeout.bind(window), cancel = window.clearTimeout.bind(window);
  const every = window.setInterval.bind(window), cancelEvery = window.clearInterval.bind(window);
  globalThis.__tinycastCompile = (code, filename) => new Function("exports", "require", "module", "__filename", "__dirname", code + "\n//# sourceURL=" + filename);
  globalThis.__tinycastHost = {
    invoke: (id, api, method, args) => AndroidHost.invoke(id, api, method, args),
    invokeSync: (api, method, args) => AndroidHost.invokeSync(api, method, args),
    render: (id, tree) => AndroidHost.render(tree),
    failed: (id, error) => AndroidHost.failed(error),
    finished: () => AndroidHost.finished(),
    navigationDepthChanged: (id, depth) => AndroidHost.depth(depth),
    fieldCommand: () => {},
    log: (level, message) => { if (level === "error") AndroidHost.log(message); },
    startTimer: (id, delay, repeats) => {
      const tick = () => { if (!repeats) timers.delete(id); globalThis.__tinycast?.fireTimer(id); };
      timers.set(id, {handle: repeats ? every(tick, Math.max(4, delay)) : later(tick, delay), repeats});
    },
    clearTimer: id => { const t = timers.get(id); if (t) (t.repeats ? cancelEvery : cancel)(t.handle); timers.delete(id); }
  };
  globalThis.__androidStopTimers = () => { for (const id of [...timers.keys()]) __tinycastHost.clearTimer(id); };
})();
