// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLinksValidator from 'starlight-links-validator';
import markdocGrammar from './grammars/markdoc.tmLanguage.json';
import starlightLlmsTxt from 'starlight-llms-txt';
import mermaid from 'astro-mermaid';
import starlightBlog from 'starlight-blog';

export const locales = {
  root: { label: 'English', lang: 'en' },
  ko: { label: '한국어', lang: 'ko' },
};

/* https://docs.netlify.com/configure-builds/environment-variables/#read-only-variables */
const NETLIFY_PREVIEW_SITE = process.env.CONTEXT !== 'production' && process.env.DEPLOY_PRIME_URL;
const site = process.env.SITE_URL || NETLIFY_PREVIEW_SITE || 'https://actionbase.io/';
const getBasePath = () => {
  if (process.env.SITE_URL) {
    try {
      const url = new URL(process.env.SITE_URL);
      return url.pathname.endsWith('/') ? url.pathname : url.pathname + '/';
    } catch {
      return '/';
    }
  }
  return '/';
};

const base = getBasePath();
const ogUrl = new URL('og.png?v=1', site).href;
const ogImageAlt =
  'Actionbase is a production-proven OLTP database serving tens of millions of users across Kakao.';

const GA_ID = 'G-2PY5JXRJ5J';

export default defineConfig({
  site,
  base,
  trailingSlash: 'always',
  integrations: [
    mermaid({
      theme: 'forest',
      autoTheme: true,
      mermaidConfig: {
        flowchart: {
          curve: 'basis',
        },
      },
      iconPacks: [
        {
          name: 'logos',
          loader: () =>
            fetch('https://unpkg.com/@iconify-json/logos@1/icons.json').then((res) => res.json()),
        },
        {
          name: 'iconoir',
          loader: () =>
            fetch('https://unpkg.com/@iconify-json/iconoir@1/icons.json').then((res) => res.json()),
        },
      ],
    }),
    starlight({
      title: 'Actionbase',
      logo: {
        light: '/src/assets/logo-light.svg',
        dark: '/src/assets/logo-dark.svg',
        replacesTitle: true,
      },
      lastUpdated: true,
      editLink: {
        baseUrl: 'https://github.com/kakao/actionbase/edit/main/website/',
      },
      social: [
        { icon: 'document', label: 'Documentation', href: '/introduction/' },
        { icon: 'github', label: 'GitHub', href: 'https://github.com/kakao/actionbase' },
      ],
      head: [
        {
          tag: 'script',
          attrs: { async: true, src: `https://www.googletagmanager.com/gtag/js?id=${GA_ID}` },
        },
        {
          tag: 'script',
          content: `
            			window.dataLayer = window.dataLayer || [];
            			function gtag(){dataLayer.push(arguments);}
            			gtag('js', new Date());
            			gtag('config', '${GA_ID}');
          			`,
        },
        {
          tag: 'meta',
          attrs: { property: 'og:image', content: ogUrl },
        },
        {
          tag: 'meta',
          attrs: { property: 'og:image:alt', content: ogImageAlt },
        },
      ],
      customCss: ['./src/assets/landing.css'],
      locales,
      sidebar: [
        {
          label: 'Getting Started',
          items: ['introduction', 'quick-start', 'faq', 'for-rdb-users', 'llms-txt'],
        },
        {
          label: 'Project',
          autogenerate: { directory: 'project' },
        },
        {
          label: 'Design',
          autogenerate: { directory: 'design' },
        },
        {
          label: 'Internals',
          autogenerate: { directory: 'internals' },
        },
        {
          label: 'Provisioning',
          autogenerate: { directory: 'provisioning' },
        },
        {
          label: 'Operations',
          autogenerate: { directory: 'operations' },
        },
        {
          label: 'Guides',
          autogenerate: { directory: 'guides' },
        },
        {
          label: 'API References',
          autogenerate: { directory: 'api-references' },
        },
        {
          label: 'Community',
          autogenerate: { directory: 'community' },
        },
      ],
      components: {
        Head: './src/components/Head.astro',
        PageSidebar: './src/components/PageSidebar.astro',
      },
      expressiveCode: { shiki: { langs: [markdocGrammar] } },
      plugins: [
        starlightLinksValidator({
          errorOnFallbackPages: false,
          errorOnInconsistentLocale: true,
        }),
        starlightLlmsTxt({
          exclude: ['404', 'api-references/**'],
          promote: ['introduction', 'quick-start', 'design/**'],
          demote: ['api-references/**'],
          customSets: [
            {
              label: 'Core',
              paths: [
                'introduction',
                'quick-start',
                'faq',
                'for-rdb-users',
                'design/**',
                'internals/**',
                'provisioning/**',
                'operations/**',
                'guides/**',
                'community/**',
                'project/**',
              ],
              description: 'Core documentation without API reference',
            },
            {
              label: 'API',
              paths: ['api-references/**'],
              description: 'API reference documentation',
            },
          ],
        }),
        starlightBlog({
          authors: {
            em3s: {
              name: 'Minseok Kim',
              title: 'Maintainer',
              picture: 'https://avatars.githubusercontent.com/u/1531387?s=200',
              url: 'https://github.com/em3s',
            },
          },
          metrics: {
            readingTime: true,
            words: 'total',
          },
        }),
      ],
    }),
  ],
});
