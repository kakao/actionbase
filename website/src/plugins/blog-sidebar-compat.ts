import { defineRouteMiddleware } from '@astrojs/starlight/route-data';

/**
 * Ensures starlight-utils multi-sidebar and navLinks work on blog pages.
 *
 * starlight-blog replaces the entire sidebar on blog pages with flat link
 * entries. This causes two issues:
 * 1. Multi-sidebar requires all top-level entries to be groups.
 * 2. The navLinks "Nav" group is missing from the replaced sidebar.
 *
 * This middleware runs at `post` order (before starlight-utils) to:
 * - Wrap non-group entries in a "Blog" group
 * - Re-inject the "Nav" group so navLinks can find it (locale-aware)
 */
export const onRequest = defineRouteMiddleware((context) => {
  const sidebar = context.locals.starlightRoute.sidebar;
  const hasNavGroup = sidebar.some((entry) => entry.label === 'Nav');
  const hasNonGroup = sidebar.some((entry) => entry.type !== 'group');

  if (hasNonGroup) {
    context.locals.starlightRoute.sidebar = [
      {
        type: 'group' as const,
        label: 'Blog',
        entries: sidebar,
        badge: undefined,
        collapsed: false,
      },
    ];
  }

  if (!hasNavGroup) {
    // Derive locale prefix from the current route
    const locale = context.locals.starlightRoute.locale;
    const localePrefix = locale ? `/${locale}` : '';

    context.locals.starlightRoute.sidebar.unshift({
      type: 'group' as const,
      label: 'Nav',
      entries: [
        {
          type: 'link' as const,
          label: 'Docs',
          href: `${localePrefix}/introduction/`,
          isCurrent: false,
          badge: undefined,
          attrs: {},
        },
        {
          type: 'link' as const,
          label: 'Blog',
          href: '/blog/',
          isCurrent: false,
          badge: undefined,
          attrs: {},
        },
        {
          type: 'link' as const,
          label: 'GitHub',
          href: 'https://github.com/kakao/actionbase',
          isCurrent: false,
          badge: undefined,
          attrs: { class: 'nav-external', target: '_blank', rel: 'noopener' },
        },
      ],
      badge: undefined,
      collapsed: false,
    });
  }
});
