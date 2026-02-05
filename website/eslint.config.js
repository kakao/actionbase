// ESLint flat config for the website
// Using typescript-eslint for .ts/.tsx support
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import globals from 'globals';

export default [
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // skip build output and dependencies
    ignores: ['dist/**', 'node_modules/**', '.astro/**'],
  },
  {
    files: ['**/*.{js,mjs,ts,tsx}'],
    languageOptions: {
      // need both node and browser globals since astro runs in both
      globals: {
        ...globals.node,
        ...globals.browser,
      },
    },
    rules: {
      // keep these as warnings for now, too many errors to fix at once
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
];
