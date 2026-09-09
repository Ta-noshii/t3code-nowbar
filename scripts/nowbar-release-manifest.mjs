import * as NodeCrypto from "node:crypto";
import * as NodeFS from "node:fs";

const version = process.env.NOWBAR_VERSION;
const versionCode = Number(process.env.NOWBAR_VERSION_CODE);
if (!version || !Number.isSafeInteger(versionCode) || versionCode < 1)
  throw new Error("Missing release version");
const sha256 = NodeCrypto.createHash("sha256")
  .update(NodeFS.readFileSync("release/t3code-nowbar.apk"))
  .digest("hex");
NodeFS.writeFileSync(
  "release/update.json",
  JSON.stringify(
    {
      version,
      versionCode,
      sha256,
      url: `https://github.com/Ta-noshii/t3code-nowbar/releases/download/nowbar-${version}/t3code-nowbar.apk`,
    },
    null,
    2,
  ) + "\n",
);
NodeFS.writeFileSync("release/SHA256SUMS", `${sha256}  t3code-nowbar.apk\n`);
