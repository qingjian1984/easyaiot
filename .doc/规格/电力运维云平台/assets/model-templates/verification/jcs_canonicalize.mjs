import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

function canonicalize(value) {
  if (value === null || typeof value === "boolean" || typeof value === "string") {
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new TypeError("JCS does not allow non-finite numbers");
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalize).join(",")}]`;
  }
  if (typeof value === "object") {
    const members = Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`);
    return `{${members.join(",")}}`;
  }
  throw new TypeError(`Unsupported JSON value type: ${typeof value}`);
}

const manifestPath = path.resolve(process.argv[2]);
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const baseDir = path.dirname(manifestPath);
const results = manifest.cases.map((testCase) => {
  const inputPath = path.resolve(baseDir, testCase.input);
  const input = JSON.parse(fs.readFileSync(inputPath, "utf8"));
  const actualCanonical = canonicalize(input);
  const expectedCanonical = Buffer.from(testCase.canonicalBase64, "base64").toString("utf8");
  const actualHash = crypto.createHash("sha256").update(actualCanonical, "utf8").digest("hex");
  if (actualCanonical !== expectedCanonical || actualHash !== testCase.sha256) {
    throw new Error(`JCS golden mismatch: ${testCase.name}`);
  }
  return { name: testCase.name, sha256: actualHash, bytes: Buffer.byteLength(actualCanonical, "utf8") };
});

process.stdout.write(JSON.stringify(results));
