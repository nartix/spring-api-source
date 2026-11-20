/** @type {import("prettier").Config} */
module.exports = {
  plugins: ["prettier-plugin-java"],
  printWidth: 100,
  tabWidth: 2,
  useTabs: false,
  overrides: [
    {
      files: ["*.java"],
      options: {
        printWidth: 100,
        tabWidth: 2,
        useTabs: false,
      },
    },
    {
      files: ["*.md"],
      options: {
        proseWrap: "preserve",
      },
    },
    {
      files: ["*.yml", "*.yaml"],
      options: {
        singleQuote: false,
      },
    },
  ],
};
