import { defineCollection, z } from 'astro:content';
import { docsLoader, i18nLoader } from '@astrojs/starlight/loaders';
import { docsSchema, i18nSchema } from '@astrojs/starlight/schema';

export const collections = {
  docs: defineCollection({
    loader: docsLoader(),
    schema: docsSchema({
      extend: z.object({
        // Blog fields for starlight-blog plugin
        date: z.coerce.date().optional(),
        authors: z.union([z.string(), z.array(z.string())]).optional(),
        tags: z.array(z.string()).optional(),
        draft: z.boolean().optional(),
        excerpt: z.string().optional(),
        featured: z.boolean().optional(),
        // Custom: hide Overview from TOC
        hideOverview: z.boolean().optional(),
        // Custom: mark document as translated by Kanana-2
        'translated-by-kanana-2': z.boolean().optional(),
      }),
    }),
  }),
  i18n: defineCollection({
    loader: i18nLoader(),
    schema: i18nSchema({
      extend: z.object({
        'component.preview': z.string().optional(),
      }),
    }),
  }),
};
