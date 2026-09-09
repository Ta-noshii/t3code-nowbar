// Fork identity and release channel stay in one plugin to minimize upstream conflicts.
module.exports = function withNowBarFork(config) {
  const upstreamVersion = config.version;
  const buildNumber = Number(process.env.NOWBAR_VERSION_CODE || 1);
  if (typeof upstreamVersion !== "string" || !/^\d+\.\d+\.\d+$/.test(upstreamVersion)) {
    throw new Error("Expected an upstream mobile version in major.minor.patch format");
  }
  if (!Number.isSafeInteger(buildNumber) || buildNumber < 1) {
    throw new Error("NOWBAR_VERSION_CODE must be a positive integer");
  }
  if (process.env.NOWBAR_REQUIRE_CLOUD_CONFIG === "1") {
    const cloudValues = [
      config.extra?.clerk?.publishableKey,
      config.extra?.clerk?.jwtTemplate,
      config.extra?.relay?.url,
    ];
    if (cloudValues.some((value) => typeof value !== "string" || !value.trim())) {
      throw new Error("Refusing to release a local-only app: T3 Connect configuration is missing");
    }
  }
  config.name = "T3 Code Now Bar";
  config.slug = "t3-code-nowbar";
  config.scheme = "t3code-nowbar";
  config.android = {
    ...config.android,
    package: "com.tanoshii.t3code.nowbar",
    versionCode: buildNumber,
  };
  config.version = `${upstreamVersion}-nowbar.${buildNumber}`;
  config.extra = {
    ...config.extra,
    nowbar: {
      upstreamVersion,
      buildNumber,
      firebaseConfigured: Boolean(config.android.googleServicesFile),
      pushTransport: process.env.NOWBAR_PUSH_TRANSPORT || "relay",
      remotePushConfigured:
        Boolean(config.android.googleServicesFile) && process.env.NOWBAR_PUSH_TRANSPORT !== "host",
    },
  };
  // An upstream OTA would replace this fork's JS with code unaware of our
  // native module. Fork upgrades are whole, consistently signed APKs instead.
  config.updates = { enabled: false };
  delete config.owner;
  if (config.extra) delete config.extra.eas;
  return config;
};
