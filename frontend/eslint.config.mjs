import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    // Public firm sites size their photos and firm logos with CSS (aspect ratio, object-position),
    // and logos can come from any host the firm uses, so plain <img> is deliberate here.
    files: ["src/components/public-site/**/*.tsx"],
    rules: { "@next/next/no-img-element": "off" },
  },
];

export default eslintConfig;
