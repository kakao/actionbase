// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLinksValidator from 'starlight-links-validator';
import markdocGrammar from './grammars/markdoc.tmLanguage.json';
import starlightLlmsTxt from 'starlight-llms-txt'
import mermaid from 'astro-mermaid';

export const locales = {
	root: { label: 'English', lang: 'en' },
	ko: { label: '한국어', lang: 'ko' },
};

/* https://docs.netlify.com/configure-builds/environment-variables/#read-only-variables */
const NETLIFY_PREVIEW_SITE = process.env.CONTEXT !== 'production' && process.env.DEPLOY_PRIME_URL;
const site = process.env.SITE_URL || NETLIFY_PREVIEW_SITE || 'https://starlight.astro.build/';
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
const ogUrl = new URL('og.jpg?v=1', site).href;
const ogImageAlt = 'Make your docs shine with Starlight';

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
					curve: 'basis'
				}
			},
			iconPacks: [
				{
					name: 'logos',
					loader: () => fetch('https://unpkg.com/@iconify-json/logos@1/icons.json').then(res => res.json())
				},
				{
					name: 'iconoir',
					loader: () => fetch('https://unpkg.com/@iconify-json/iconoir@1/icons.json').then(res => res.json())
				}
			]
		}),
		starlight({
			title: 'Actionbase',
			// logo: {
			// 	light: '/src/assets/logo-light.svg',
			// 	dark: '/src/assets/logo-dark.svg',
			// 	replacesTitle: true,
			// },
			lastUpdated: true,
			editLink: {
				baseUrl: 'https://github.com/withastro/starlight/edit/main/docs/',
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/withastro/starlight' },
				{ icon: 'discord', label: 'Discord', href: 'https://astro.build/chat' },
			],
			head: [
				{
					tag: 'script',
					attrs: {
						src: 'https://cdn.usefathom.com/script.js',
						'data-site': 'EZBHTSIG',
						defer: true,
					},
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
					items: [
						'introduction',
						'quick-start',
						'faq',
						'about-kakao'
					],
				},
				{
					label: 'Design',
					autogenerate: { directory: 'design' },
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
					label: 'Tutorials',
					autogenerate: { directory: 'tutorials' },
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
			expressiveCode: { shiki: { langs: [markdocGrammar] } },
			plugins: process.env.CHECK_LINKS
				? [
						starlightLinksValidator({
							errorOnFallbackPages: false,
							errorOnInconsistentLocale: true,
						}),
					starlightLlmsTxt(),
					]
				: [
					starlightLlmsTxt(),
				],
		}),
	],
});
