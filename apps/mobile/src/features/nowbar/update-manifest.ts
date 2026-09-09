export const RELEASE_ROOT = "https://github.com/Ta-noshii/t3code-nowbar/releases/";
export interface NowBarRelease {
  version: string;
  versionCode: number;
  url: string;
  sha256: string;
}

export function parseNowBarRelease(
  value: unknown,
  installedVersionCode: number,
): NowBarRelease | null {
  if (!value || typeof value !== "object") throw new Error("Invalid update manifest");
  const release = value as Record<string, unknown>;
  if (
    typeof release.version !== "string" ||
    typeof release.versionCode !== "number" ||
    !Number.isSafeInteger(release.versionCode) ||
    release.versionCode <= 0 ||
    typeof release.url !== "string" ||
    !release.url.startsWith(`${RELEASE_ROOT}download/`) ||
    !release.url.endsWith(".apk") ||
    typeof release.sha256 !== "string" ||
    !/^[a-f0-9]{64}$/i.test(release.sha256)
  ) {
    throw new Error("Invalid update manifest");
  }
  return release.versionCode > installedVersionCode ? (release as unknown as NowBarRelease) : null;
}
