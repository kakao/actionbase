import { defineRouteMiddleware } from '@astrojs/starlight/route-data';

/**
 * Wraps non-group top-level sidebar entries in a group so that
 * starlight-utils multi-sidebar validation passes on blog pages.
 *
 * starlight-blog replaces the entire sidebar on blog pages with flat link
 * entries. Multi-sidebar requires all top-level entries to be groups.
 * This middleware runs at `post` order (before starlight-utils) to wrap
 * those entries.
 */
export const onRequest = defineRouteMiddleware((context) => {
  const sidebar = context.locals.starlightRoute.sidebar;
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
});
