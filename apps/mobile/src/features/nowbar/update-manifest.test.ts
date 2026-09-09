import { describe, expect, it } from "vite-plus/test";
import { parseNowBarRelease, RELEASE_ROOT } from "./update-manifest";

const release = {
  version: "1.0.7",
  versionCode: 7,
  url: `${RELEASE_ROOT}download/nowbar-1.0.7/t3code-nowbar.apk`,
  sha256: "a".repeat(64),
};
describe("Now Bar updates", () => {
  it("only offers newer versions", () => {
    expect(parseNowBarRelease(release, 6)).toEqual(release);
    expect(parseNowBarRelease(release, 7)).toBeNull();
    expect(parseNowBarRelease(release, 8)).toBeNull();
  });
  it("rejects other repositories and missing checksums", () => {
    expect(() =>
      parseNowBarRelease({ ...release, url: "https://example.com/update.apk" }, 1),
    ).toThrow();
    expect(() => parseNowBarRelease({ ...release, sha256: "" }, 1)).toThrow();
    expect(() => parseNowBarRelease({ ...release, versionCode: 1.5 }, 1)).toThrow();
    expect(() => parseNowBarRelease(null, 1)).toThrow();
  });
});
