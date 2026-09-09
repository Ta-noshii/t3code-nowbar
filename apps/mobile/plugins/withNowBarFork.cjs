// Fork identity and release channel stay in one plugin to minimize upstream conflicts.
module.exports = function withNowBarFork(config) {
  config.name = "T3 Code Now Bar";
  config.slug = "t3-code-nowbar";
  config.scheme = "t3code-nowbar";
  config.android = {
    ...config.android,
    package: "com.tanoshii.t3code.nowbar",
    versionCode: Number(process.env.NOWBAR_VERSION_CODE || 1),
  };
  config.version = process.env.NOWBAR_VERSION || "1.0.0";
  // An upstream OTA would replace this fork's JS with code unaware of our
  // native module. Fork upgrades are whole, consistently signed APKs instead.
  config.updates = { enabled: false };
  delete config.owner;
  if (config.extra) delete config.extra.eas;
  return config;
};
