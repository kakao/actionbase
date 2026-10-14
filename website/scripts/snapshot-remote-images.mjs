/**
 * Re-fetch images that used to be loaded from third-party services at view time.
 *
 * The docs serve committed snapshots so pages keep working if those services change.
 *
 * - Mutation state diagram: rendered by plantuml.com from the "Edit on PlantUML" link
 *   in design/mutation.mdx, so edit the diagram there, update the link, then re-run this.
 * - Contributors: rendered by contrib.rocks.
 *
 * To go back to live images instead, point the Markdown image at the source URL below.
 *
 * Usage (from website/):
 * node scripts/snapshot-remote-images.mjs
 */

import { readFile, writeFile } from 'fs/promises';

const mutationDoc = await readFile('src/content/docs/design/mutation.mdx', 'utf8');
const [, plantumlEncoded] = mutationDoc.match(/plantuml\.com\/plantuml\/uml\/([\w-]+)/) ?? [];
if (!plantumlEncoded) throw new Error('No "Edit on PlantUML" link found in design/mutation.mdx');

const snapshots = [
  {
    source: `https://www.plantuml.com/plantuml/svg/${plantumlEncoded}`,
    target: 'public/images/design/mutation-state-transitions.svg',
  },
  {
    source: 'https://contrib.rocks/image?repo=kakao/actionbase',
    target: 'public/images/community/contributors.svg',
  },
];

for (const { source, target } of snapshots) {
  const res = await fetch(source);
  if (!res.ok) throw new Error(`${source} responded ${res.status}`);
  await writeFile(target, Buffer.from(await res.arrayBuffer()));
  console.log(`${target} <- ${source}`);
}
