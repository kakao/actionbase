import { defineCollection, z } from 'astro:content';
import { docsLoader } from '@astrojs/starlight/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

export const collections = {
  docs: defineCollection({
    loader: docsLoader(),
    schema: docsSchema({
      extend: z.object({
        // Custom: hide Overview from TOC
        hideOverview: z.boolean().optional(),
        // Custom: category for grouping stories
        category: z.string().optional(),
      }),
    }),
  }),
};
