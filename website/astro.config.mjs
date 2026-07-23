// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLinksValidator from 'starlight-links-validator';
import markdocGrammar from './grammars/markdoc.tmLanguage.json';
import starlightLlmsTxt from 'starlight-llms-txt';
import mermaid from 'astro-mermaid';
import { remarkHeadingId } from 'remark-custom-heading-id';
import starlightUtils from '@lorenzo_lewis/starlight-utils';

export const locales = {
  root: { label: 'English', lang: 'en' },
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
  redirects: {
    '/stories/kakaotalk-gift-wish/': '/stories/use-cases/kakaotalk-gift-wish/',
    '/stories/kakaotalk-gift-recent-views/': '/stories/use-cases/kakaotalk-gift-recent-views/',
    '/stories/kakaotalk-friends/': '/stories/use-cases/kakaotalk-friends/',
    '/stories/pipeline/': '/stories/engineering/pipeline/',
    '/stories/contracts/': '/stories/how-we-survived/contracts/',
    '/stories/shadow-testing/': '/stories/how-we-survived/shadow-testing/',
    '/stories/migration-verification/': '/stories/how-we-survived/migration-verification/',
    '/stories/hbase-consistency/': '/stories/how-we-survived/hbase-consistency/',
    '/stories/unified-graph/': '/stories/vision/unified-graph/',
  },
  markdown: {
    remarkPlugins: [remarkHeadingId],
  },
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
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/kakao/actionbase' }],
      head: [
        {
          tag: 'link',
          attrs: { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        },
        {
          tag: 'link',
          attrs: { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: true },
        },
        {
          tag: 'link',
          attrs: {
            rel: 'stylesheet',
            href: 'https://fonts.googleapis.com/css2?family=Cormorant:wght@500;600;700&family=Manrope:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap',
          },
        },
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
          label: 'Main',
          items: [
            {
              label: 'Getting Started',
              items: ['introduction', 'quick-start', 'faq', 'for-rdb-users', 'llms-txt'],
            },
            {
              label: 'Design',
              autogenerate: { directory: 'design' },
            },
            {
              label: 'Guides',
              autogenerate: { directory: 'guides' },
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
              label: 'Internals',
              autogenerate: { directory: 'internals' },
            },
            {
              label: 'API References',
              autogenerate: { directory: 'api-references' },
            },
            {
              label: 'Project',
              autogenerate: { directory: 'project' },
            },
            'community',
          ],
        },
        {
          label: 'Stories',
          items: [
            'stories',
            { label: 'Use Cases', autogenerate: { directory: 'stories/use-cases' } },
            { label: 'Engineering', autogenerate: { directory: 'stories/engineering' } },
            { label: 'How We Survived', autogenerate: { directory: 'stories/how-we-survived' } },
            { label: 'Vision', autogenerate: { directory: 'stories/vision' } },
          ],
        },
      ],
      components: {
        Head: './src/components/Head.astro',
        PageSidebar: './src/components/PageSidebar.astro',
        ThemeSelect: './src/components/ThemeSelect.astro',
        Footer: './src/components/Footer.astro',
      },
      expressiveCode: {
        // A single dark theme keeps code blocks on the terminal surface in both
        // light and dark site themes, matching the design mocks.
        themes: ['github-dark'],
        styleOverrides: {
          borderRadius: '10px',
          borderColor: 'transparent',
          codeBackground: '#1b1c2b',
          frames: {
            editorBackground: '#1b1c2b',
            terminalBackground: '#1b1c2b',
            editorActiveTabBackground: '#1b1c2b',
            terminalTitlebarBackground: '#1b1c2b',
            shadowColor: 'transparent',
          },
        },
        shiki: { langs: [markdocGrammar] },
      },
      plugins: [
        starlightUtils({
          multiSidebar: {
            switcherStyle: 'horizontalList',
          },
        }),
        starlightLinksValidator({
          errorOnFallbackPages: false,
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
                'community',
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
      ],
    }),
  ],
});
